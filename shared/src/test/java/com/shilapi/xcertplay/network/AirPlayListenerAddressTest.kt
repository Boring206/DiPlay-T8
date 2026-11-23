package com.shilapi.xcertplay.network

import java.net.BindException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import org.junit.Assert.*
import org.junit.Test

/** #1356: one address the kernel refuses must not stop the other family from serving CarPlay. */
class AirPlayListenerAddressTest {
    private val ipv6 = Inet6Address.getByAddress(null, InetAddress.getByName("fe80::1c2d:3e4f:5a6b:7c8d").address, 7)
    private val ipv4 = InetAddress.getByName("192.0.2.20")
    private val notAvailable = "bind failed: EADDRNOTAVAIL (Cannot assign requested address)"
    private val inUse = "bind failed: EADDRINUSE (Address already in use)"

    /** Opens no socket: it records each request and fails the ones [refuse] answers for. */
    private class FakeBinder(private val refuse: (InetAddress, Int) -> SocketException?) {
        val calls = mutableListOf<Pair<InetAddress, Int>>()
        val opened = mutableListOf<FakeServer>()
        private var ephemeral = 50_000

        fun bind(address: InetAddress, port: Int): ServerSocket {
            calls += address to port
            refuse(address, port)?.let { throw it }
            return FakeServer(if (port == 0) ephemeral++ else port).also { opened += it }
        }

        fun portsTried(address: InetAddress): List<Int> = calls.filter { it.first == address }.map { it.second }
    }

    private class FakeServer(private val port: Int) : ServerSocket() {
        var released = false
        override fun getLocalPort(): Int = port
        override fun close() {
            released = true
            super.close()
        }
    }

    @Test fun aRefusedIpv6AddressIsDroppedAndIpv4KeepsThePreferredPort() {
        val binder = FakeBinder { address, _ -> if (address == ipv6) BindException(notAvailable) else null }
        var fallback: Pair<Int, Int>? = null
        val listeners = AirPlayPortSelector.bindAll(listOf(ipv6, ipv4), 7000, binder = binder::bind) { busy, bound ->
            fallback = busy to bound
        }
        assertEquals(listOf(ipv4), listeners.addresses)
        assertEquals(listOf(7000), listeners.servers.map { it.localPort })
        assertNull(fallback)
        val dropped = listeners.dropped.single()
        assertSame(ipv6, dropped.address)
        assertTrue(dropped.addressUnusable)
        assertEquals("IPv6 address unusable: BindException: $notAvailable", dropped.message)
        // The kernel's answer does not depend on the port, so the address is not tried again.
        assertEquals(listOf(7000), binder.portsTried(ipv6))
    }

    @Test fun aRefusedIpv4AddressLeavesIpv6Serving() {
        val binder = FakeBinder { address, _ -> if (address == ipv4) BindException(notAvailable) else null }
        val listeners = AirPlayPortSelector.bindAll(listOf(ipv6, ipv4), 7000, binder = binder::bind)
        assertEquals(listOf(ipv6), listeners.addresses)
        assertEquals("IPv4", listeners.dropped.single().family)
        // The IPv6 listener opened before IPv4 failed was closed, then opened again alone.
        assertEquals(listOf(true, false), binder.opened.map { it.released })
    }

    @Test fun failingEvenOnAnEphemeralPortDropsTheAddressAndTheOtherFamilyStartsOver() {
        // A refusal on every port: only the ephemeral attempt shows that the address is at fault.
        val binder = FakeBinder { address, _ ->
            if (address == ipv6) BindException("bind failed: EACCES (Permission denied)") else null
        }
        val listeners = AirPlayPortSelector.bindAll(listOf(ipv6, ipv4), 7000, 7001..7002, binder::bind)
        assertEquals(listOf(7000, 7001, 7002, 0), binder.portsTried(ipv6))
        assertEquals(listOf(ipv4), listeners.addresses)
        assertEquals(7000, listeners.servers.single().localPort)
        assertTrue(listeners.dropped.single().addressUnusable)
    }

    @Test fun einvalFromAPlainSocketExceptionDropsTheAddress() {
        val binder = FakeBinder { address, _ ->
            if (address == ipv6) SocketException("bind failed: EINVAL (Invalid argument)") else null
        }
        val listeners = AirPlayPortSelector.bindAll(listOf(ipv6, ipv4), 7000, binder = binder::bind)
        assertEquals(listOf(ipv4), listeners.addresses)
        assertEquals("IPv6 address unusable: SocketException: bind failed: EINVAL (Invalid argument)",
            listeners.dropped.single().message)
    }

    @Test fun otherSocketErrorsStillFailTheBind() {
        val failure = SocketException("Socket is closed")
        val binder = FakeBinder { address, port -> if (address == ipv4 && port == 7000) failure else null }
        val thrown = assertThrows(SocketException::class.java) {
            AirPlayPortSelector.bindAll(listOf(ipv6, ipv4), 7000, binder = binder::bind)
        }
        assertSame(failure, thrown)
        assertTrue(binder.opened.all { it.released })
    }

    @Test fun aPortTakenOnOneFamilyMovesBothFamiliesTogether() {
        val binder = FakeBinder { address, port -> if (address == ipv4 && port == 7000) BindException(inUse) else null }
        var fallback: Pair<Int, Int>? = null
        val listeners = AirPlayPortSelector.bindAll(listOf(ipv6, ipv4), 7000, binder = binder::bind) { busy, bound ->
            fallback = busy to bound
        }
        assertEquals(listOf(ipv6, ipv4), listeners.addresses)
        assertEquals(listOf(7001, 7001), listeners.servers.map { it.localPort })
        assertTrue(listeners.dropped.isEmpty())
        assertEquals(7000 to 7001, fallback)
        assertTrue(binder.opened.first().released)
    }

    @Test fun whenNoAddressCanBeBoundEachFamilyKeepsItsOwnReason() {
        val binder = FakeBinder { _, _ -> BindException(notAvailable) }
        val error = assertThrows(BindException::class.java) {
            AirPlayPortSelector.bindAll(listOf(ipv6, ipv4), 7000, binder = binder::bind)
        }
        assertEquals("No AirPlay listener address could be bound", error.message)
        val reasons = error.suppressed.map { it as AirPlayPortSelector.AddressBindFailure }
        assertEquals(listOf("IPv6", "IPv4"), reasons.map { it.family })
        assertTrue(reasons.all { it.addressUnusable && it.cause is BindException })

        val detail = CarPlayVpnService.attachFailure(error)
        assertTrue(detail, detail.startsWith("BindException: No AirPlay listener address could be bound at "))
        assertTrue(detail, detail.endsWith(
            " [IPv6 address unusable: BindException: $notAvailable; IPv4 address unusable: BindException: $notAvailable]",
        ))
        assertFalse(detail, detail.contains("fe80") || detail.contains("192.0.2"))
    }

    @Test fun portExhaustionOnTheSecondFamilyKeepsTheCommonPortFailure() {
        // IPv6 binds every port it is offered, so no address is at fault on its own.
        val binder = FakeBinder { address, _ -> if (address == ipv4) BindException(inUse) else null }
        val error = assertThrows(BindException::class.java) {
            AirPlayPortSelector.bindAll(listOf(ipv6, ipv4), 7000, emptyList(), binder::bind)
        }
        assertEquals("No common AirPlay port available for the selected interface addresses", error.message)
        val reason = error.suppressed.single() as AirPlayPortSelector.AddressBindFailure
        assertEquals("IPv4", reason.family)
        assertFalse(reason.addressUnusable)
        assertTrue(binder.opened.all { it.released })
    }

    @Test fun theUnscopedLinkLocalErrorDoesNotCarryTheAddress() {
        val binder = FakeBinder { address, _ ->
            if (address == ipv6) SocketException("Can't bind to a link-local address without a scope id: fe80::1c2d:3e4f:5a6b:7c8d")
            else null
        }
        val dropped = AirPlayPortSelector.bindAll(listOf(ipv6, ipv4), 7000, binder = binder::bind).dropped.single()
        assertEquals("IPv6 address unusable: SocketException: Can't bind to a link-local address without a scope id",
            dropped.message)
    }

    @Test fun onlyErrorsThatNoPortFixesCountAsAddressErrors() {
        listOf(
            notAvailable, "Cannot assign requested address", "Can't assign requested address",
            "bind failed: EINVAL (Invalid argument)", "bind failed: EAFNOSUPPORT (Address family not supported by protocol)",
        ).forEach { assertTrue(it, AirPlayPortSelector.addressUnusable(BindException(it))) }
        listOf(inUse, "Address already in use", "bind failed: EACCES (Permission denied)")
            .forEach { assertFalse(it, AirPlayPortSelector.addressUnusable(BindException(it))) }
    }

    @Test fun anAddressThisHostDoesNotOwnIsDroppedWithRealSockets() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val preferred = ServerSocket(0, 50, loopback).use { it.localPort }
        val listeners = AirPlayPortSelector.bindAll(listOf(InetAddress.getByName("192.0.2.1"), loopback), preferred, emptyList())
        try {
            assertEquals(listOf(loopback), listeners.addresses)
            assertEquals(preferred, listeners.servers.single().localPort)
            assertTrue(listeners.dropped.single().addressUnusable)
        } finally {
            listeners.servers.forEach { it.close() }
        }
    }
}
