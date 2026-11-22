package com.shilapi.xcertplay

import com.shilapi.xcertplay.hud.BydHudArrival
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #301: the N9 cluster layout (:common) and the N9 HUD arrival time (:shared) gate on the same firmware.
 * An N9 OTA added to one gate only would silently turn the other feature off on that car.
 */
class DenzaN9FingerprintTest {
    @Test fun theHudAndClusterGatesUseTheSameN9Firmware() {
        assertEquals(DiLink51ClusterLayout.DENZA_N9_FINGERPRINTS, BydHudArrival.DENZA_N9_FINGERPRINTS)
        assertEquals(DiLink51ClusterLayout.DENZA_N9_FINGERPRINT, BydHudArrival.DENZA_N9_FINGERPRINT)
        assertEquals(DiLink51ClusterLayout.DENZA_N9_V19_FINGERPRINT, BydHudArrival.DENZA_N9_V19_FINGERPRINT)
    }
}
