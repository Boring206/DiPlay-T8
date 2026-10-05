package com.shilapi.xcertplay.network

import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class WirelessHostAddressTest {
    @Test fun manualApPrefersScopedLinkLocalEvenWhenIpv4ComesFirst() {
        val result = wirelessHostAddress(listOf(ip("192.168.43.1"), ip("fe80::1234")), 7) as Inet6Address
        assertTrue(result.isLinkLocalAddress)
        assertEquals(7, result.scopeId)
    }

    @Test fun replacesScopeFromAnotherInterface() {
        val wrongScope = Inet6Address.getByAddress(null, ip("fe80::1234").address, 3)
        assertEquals(8, (wirelessHostAddress(listOf(wrongScope), 8) as Inet6Address).scopeId)
    }

    @Test fun fallsBackToIpv4WithoutUsableLinkLocal() {
        val ipv4 = ip("192.168.43.1")
        assertEquals(ipv4, wirelessHostAddress(listOf(ip("::1"), ip("2001:db8::1"), ipv4), 7))
        assertEquals(ipv4, wirelessHostAddress(listOf(ip("fe80::1234"), ipv4), 0))
        assertNull(wirelessHostAddress(listOf(ip("0.0.0.0"), ip("127.0.0.1"), ip("224.0.0.251")), 7))
    }

    @Test fun stationDiscoveryCoversBothFamiliesButKeepsLegacyPrimaryPolicy() {
        val addresses = listOf(ip("fe80::1234"), ip("192.168.128.10"), ip("2001:db8::1"))
        val hosts = existingWifiHostAddresses(addresses, 7)
        assertEquals(ip("192.168.128.10"), hosts.first())
        assertEquals(7, (hosts.last() as Inet6Address).scopeId)
        assertEquals(hosts.last(), wirelessHostAddress(addresses, 7))
    }

    @Test fun stationDiscoveryRejectsUnusableAddressesAndUnscopedIpv6() {
        assertEquals(emptyList<InetAddress>(), existingWifiHostAddresses(
            listOf(ip("0.0.0.0"), ip("127.0.0.1"), ip("169.254.1.2"), ip("224.0.0.251"), ip("2001:db8::1")), 7))
        assertEquals(listOf(ip("192.0.2.10")), existingWifiHostAddresses(
            listOf(ip("fe80::1"), ip("192.0.2.10")), 0))
        assertEquals(7, (existingWifiHostAddresses(listOf(ip("fe80::1")), 7).single() as Inet6Address).scopeId)
    }

    private fun ip(value: String) = InetAddress.getByName(value)

    @Test fun unanswerableLinkLocalYieldsToIpv4ButNotToNothing() {
        // Android 8/9 hotspot interfaces receive on fe80:: and cannot reply.
        val ipv4 = ip("192.168.43.1")
        val linkLocal = ip("fe80::1234")
        assertEquals(ipv4, wirelessHostAddress(listOf(linkLocal, ipv4), 3) { false })
        assertTrue(wirelessHostAddress(listOf(linkLocal, ipv4), 3) { true } is Inet6Address)
        assertTrue(wirelessHostAddress(listOf(linkLocal), 3) { false } is Inet6Address)
    }
}
