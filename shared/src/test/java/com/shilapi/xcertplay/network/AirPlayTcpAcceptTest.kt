package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.airplay.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class AirPlayTcpAcceptTest {
    @Test fun bothAddressFamiliesReportOwnerAndOldCleanupPreservesNewListener() {
        val service = Robolectric.buildService(CarPlayVpnService::class.java).create().get()
        val received = LinkedBlockingQueue<AirPlayTcpAccepted>()
        val address = InetAddress.getByName("127.0.0.1")
        val addresses = listOf(address, InetAddress.getByName("::1"))
        val config = AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(800, 480), port = 0)
        fun attach(owner: AirPlayListenerIdentity) {
            assertEquals(CarPlayVpnService.AttachResult.Started, service.attachWireless(address, config,
                AirPlayIdentity(ByteArray(32), ByteArray(32), "test"), PairingStore(), null,
                object : AirPlaySessionListener {
                    override fun onTcpAccepted(event: AirPlayTcpAccepted) { received.add(event) }
                }, object : AirPlayMediaHandler {},
                additionalBindAddresses = addresses.drop(1), listenerIdentity = owner))
        }
        try {
            val old = AirPlayListenerIdentity(1)
            attach(old)
            addresses.forEach { peer ->
                Socket(peer, service.boundPort()!!).use {
                    val event = received.poll(2, TimeUnit.SECONDS)!!
                    assertSame(old, event.listener)
                    assertTrue(event.internal)
                    assertTrue(event.acceptedAtNanos > 0)
                }
            }
            val current = AirPlayListenerIdentity(2)
            attach(current)
            service.detachWireless(old)
            assertTrue(service.isAttached())
            addresses.forEach { peer ->
                Socket(peer, service.boundPort()!!).use {
                    assertSame(current, received.poll(2, TimeUnit.SECONDS)!!.listener)
                }
            }
            service.detachWireless(current)
            assertFalse(service.isAttached())
        } finally { service.detach() }
    }

    @Test fun aRefusedAddressIsLeftOutAndTheOtherFamilyKeepsServing() {
        val service = Robolectric.buildService(CarPlayVpnService::class.java).create().get()
        val logs = LinkedBlockingQueue<String>()
        val loopback = InetAddress.getByName("127.0.0.1")
        val config = AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(800, 480), port = 0)
        val listener = object : AirPlaySessionListener {
            override fun onDebugLog(message: String) { logs.add(message) }
        }
        fun attach(bindAddress: InetAddress, additional: List<InetAddress>) = service.attachWireless(bindAddress, config,
            AirPlayIdentity(ByteArray(32), ByteArray(32), "test"), PairingStore(), null, listener,
            object : AirPlayMediaHandler {}, additionalBindAddresses = additional, listenerIdentity = AirPlayListenerIdentity(1))
        try {
            // 192.0.2.0/24 is never assigned to this host, as a DAD-failed address is not to the car (#1356).
            assertEquals(CarPlayVpnService.AttachResult.Started, attach(InetAddress.getByName("192.0.2.1"), listOf(loopback)))
            assertEquals(listOf(loopback), service.servedAddresses())
            Socket(loopback, service.boundPort()!!).use { assertTrue(it.isConnected) }
            assertTrue(logs.toString(), logs.any { it.startsWith("airplay listener dropped IPv4 address unusable: ") })

            val failed = attach(InetAddress.getByName("192.0.2.1"), listOf(InetAddress.getByName("192.0.2.2")))
            assertTrue(failed is CarPlayVpnService.AttachResult.Failed)
            val detail = service.lastAttachFailure.orEmpty()
            assertTrue(detail, detail.contains("[IPv4 address unusable: BindException: "))
            assertFalse(service.isAttached())
        } finally { service.detach() }
    }

    @Test fun remotePhoneAddressIsExternal() {
        assertFalse(isInternalAirPlayPeer(InetAddress.getByName("192.0.2.12"), InetAddress.getByName("192.0.2.1")))
    }
}
