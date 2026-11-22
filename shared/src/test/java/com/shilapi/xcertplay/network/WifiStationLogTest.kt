package com.shilapi.xcertplay.network

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #1233: when the head unit joins or leaves a Wi-Fi network, such as the iPhone's hotspot, during wired CarPlay. */
class WifiStationLogTest {
    private val lines = mutableListOf<String>()
    private var now = 1_000L
    private val log = WifiStationLog(lines::add) { now }

    @Test fun joiningTheIphoneHotspotAndLeavingItAreEachALine() {
        val hotspot = Any()
        log.available(hotspot)
        log.flags(hotspot, validated = false, metered = true, internet = true)
        log.iphoneHotspotRange(hotspot, true)
        log.flags(hotspot, validated = false, metered = true, internet = true) // signal-strength updates repeat
        log.flags(hotspot, validated = true, metered = true, internet = true)
        now += 95_000
        log.lost(hotspot)

        assertEquals(listOf(
            "Android Wi-Fi station #1 connected stations=1",
            "Android Wi-Fi station #1 validated=false metered=true internet=true iPhoneHotspotRange=unknown",
            "Android Wi-Fi station #1 validated=false metered=true internet=true iPhoneHotspotRange=true",
            "Android Wi-Fi station #1 validated=true metered=true internet=true iPhoneHotspotRange=true",
            "Android Wi-Fi station #1 disconnected after 95s stations=0",
        ), lines)
    }

    @Test fun networksAreNumberedInTheOrderTheyAppearedAndUnknownOnesAreIgnored() {
        val first = Any()
        val second = Any()
        log.available(first)
        log.available(second)
        log.available(first)
        log.lost(Any())
        log.flags(Any(), validated = true, metered = false, internet = true)
        log.lost(first)

        assertEquals(listOf(
            "Android Wi-Fi station #1 connected stations=1",
            "Android Wi-Fi station #2 connected stations=2",
            "Android Wi-Fi station #1 disconnected after 0s stations=1",
        ), lines)
    }

    @Test fun onlyThe17220_10_0Slash28RangeIsTheIphoneHotspot() {
        assertTrue(WifiStationWatch.iphoneHotspotRange(InetAddress.getByName("172.20.10.2")))
        assertTrue(WifiStationWatch.iphoneHotspotRange(InetAddress.getByName("172.20.10.14")))
        assertFalse(WifiStationWatch.iphoneHotspotRange(InetAddress.getByName("172.20.10.17")))
        assertFalse(WifiStationWatch.iphoneHotspotRange(InetAddress.getByName("192.168.43.2")))
        assertFalse(WifiStationWatch.iphoneHotspotRange(InetAddress.getByName("fe80::1")))
    }
}
