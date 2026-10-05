package com.shilapi.xcertplay.network

import org.junit.Assert.*
import org.junit.Test

class LocalOnlyHotspotInterfacePolicyTest {
    private fun net(name: String, ip: String? = null, mac: String? = null) =
        LocalOnlyHotspotInterfacePolicy.Candidate(name, setOfNotNull(ip), mac)

    @Test fun waitsForApAddressInsteadOfAdvertisingEthernetOrStation() {
        val existing = listOf(net("eth0", "192.168.5.1"), net("wlan0", "192.168.31.73"))
        val before = existing.flatMap { it.ipv4 }.toSet()
        assertNull(LocalOnlyHotspotInterfacePolicy.select(existing + net("ap0"), before, setOf("wlan0"), null))
        val ap = net("ap0", "192.168.43.1")
        assertEquals(ap, LocalOnlyHotspotInterfacePolicy.select(existing + ap, before, setOf("wlan0"), null))
    }

    @Test fun excludesNewStationAddressAndForeignNetworks() {
        val networks = listOf(net("wlan0", "192.168.31.74"), net("p2p0", "192.168.49.1"),
            net("ccmni0", "10.0.0.1"), net("eth0", "192.168.5.1"), net("apcli0", "192.168.6.1"))
        assertNull(LocalOnlyHotspotInterfacePolicy.select(networks, emptySet(), setOf("wlan0"), null))
    }

    @Test fun apMayTakeOverTheInterfaceOfAStationThatWasSwitchedOff() {
        // One radio: wlan0 was the station on a phone hotspot, then became the AP.
        val ap = net("wlan0", "192.168.43.1")
        val before = setOf("172.20.10.9")
        val concurrent = LocalOnlyHotspotInterfacePolicy.upstreams(setOf("wlan0"), emptySet(), stationSwitchedOff = false)
        assertNull(LocalOnlyHotspotInterfacePolicy.select(listOf(ap), before, concurrent, null))
        val shared = LocalOnlyHotspotInterfacePolicy.upstreams(setOf("wlan0"), emptySet(), stationSwitchedOff = true)
        assertEquals(ap, LocalOnlyHotspotInterfacePolicy.select(listOf(ap), before, shared, null))
        // Still a live upstream: never the AP, whatever the station switch says.
        val live = LocalOnlyHotspotInterfacePolicy.upstreams(emptySet(), setOf("wlan0"), stationSwitchedOff = true)
        assertNull(LocalOnlyHotspotInterfacePolicy.select(listOf(ap), before, live, null))
    }

    @Test fun refusesAmbiguityOrUnprovenExistingAp() {
        val ap = net("ap0", "192.168.43.1")
        assertNull(LocalOnlyHotspotInterfacePolicy.select(listOf(ap, net("wlan1", "192.168.44.1")), emptySet(), emptySet(), null))
        assertNull(LocalOnlyHotspotInterfacePolicy.select(listOf(ap), ap.ipv4, emptySet(), null))
    }

    @Test fun configuredIdentityCanResolveAnExistingSharedApButNeverAnotherBssid() {
        val ap = net("ap0", "192.168.43.1", "00:11:22:33:44:55")
        assertEquals(ap, LocalOnlyHotspotInterfacePolicy.select(listOf(ap), ap.ipv4, emptySet(), ap.bssid))
        assertNull(LocalOnlyHotspotInterfacePolicy.select(listOf(ap), emptySet(), emptySet(), "00:11:22:33:44:66"))
    }
}
