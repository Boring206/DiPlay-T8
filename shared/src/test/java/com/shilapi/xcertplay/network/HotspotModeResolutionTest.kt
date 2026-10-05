package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotspotModeResolutionTest {
    @Test fun appHotspotYieldsToARunningCarHotspotOnlyWhenItsCredentialsAreSaved() {
        val app = WirelessHotspotMode.LOCAL_ONLY_HOTSPOT
        assertEquals(app, resolveHotspotMode(app, carHotspotOn = false, carHotspotSaved = false))
        assertEquals(app, resolveHotspotMode(app, carHotspotOn = false, carHotspotSaved = true))
        assertEquals(WirelessHotspotMode.MANUAL, resolveHotspotMode(app, carHotspotOn = true, carHotspotSaved = true))
        // Nothing saved: the platform would refuse the app hotspot and no credentials exist to hand over.
        assertNull(resolveHotspotMode(app, carHotspotOn = true, carHotspotSaved = false))
    }

    @Test fun otherModesAreNeverRedirected() {
        for (mode in listOf(WirelessHotspotMode.MANUAL, WirelessHotspotMode.WIFI_P2P, WirelessHotspotMode.EXISTING_WIFI)) {
            assertEquals(mode, resolveHotspotMode(mode, carHotspotOn = true, carHotspotSaved = false))
        }
    }
}
