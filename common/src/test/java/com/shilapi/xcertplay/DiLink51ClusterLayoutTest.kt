package com.shilapi.xcertplay

import com.shilapi.xcertplay.DiLink51ClusterLayout.Theme
import com.shilapi.xcertplay.DiLink51ClusterLayout.Plan
import org.junit.Assert.*
import org.junit.Test

class DiLink51ClusterLayoutTest {
    private val names = listOf(DiLink51ClusterLayout.BASE, DiLink51ClusterLayout.FULL, DiLink51ClusterLayout.SIDE)
    private val firmware = DiLink51ClusterLayout.FINGERPRINT

    @Test fun mapUsesFullLayerWhileBothOtherThemesUseSideLayer() {
        assertEquals(DiLink51ClusterLayout.FULL, DiLink51ClusterLayout.displayName(names, firmware, Theme.MAP))
        for (theme in listOf(Theme.SCENARIO, Theme.SIMPLE)) {
            assertEquals(DiLink51ClusterLayout.SIDE, DiLink51ClusterLayout.displayName(names, firmware, theme))
            assertEquals(Plan(1320, 0, 600, 720, false), DiLink51ClusterLayout.plan(1920, 720, theme))
        }
    }

    @Test fun missingSideLayerCannotCoverScenarioWithAFullMap() {
        assertNull(DiLink51ClusterLayout.displayName(listOf(DiLink51ClusterLayout.BASE, DiLink51ClusterLayout.FULL), firmware, Theme.SCENARIO))
    }

    @Test fun fullMapKeepsSpeedAndGearBandsOutsideTheVideoViewport() {
        val plan = DiLink51ClusterLayout.plan(1920, 720, Theme.MAP)!!
        assertTrue(plan.top >= 144)
        assertTrue(plan.top + plan.height <= 624)
        assertEquals(0, plan.left)
        assertEquals(1920, plan.width)
        assertEquals(0, plan.sourceLeft)
        assertEquals(240, plan.sourceTop)
        assertNull(DiLink51ClusterLayout.streamConfig().safeArea)
    }

    @Test fun oneContinuousStreamSupportsBothViewportsWithoutStretching() {
        val config = DiLink51ClusterLayout.streamConfig()
        assertEquals(1920, config.widthPixels)
        assertEquals(720, config.heightPixels)
        assertEquals(292, config.widthPhysicalMm)
        assertEquals(110, config.heightPhysicalMm)
        assertEquals(0, config.features)
        assertEquals(config, DiLink51ClusterLayout.streamConfig(1920, 720))
        for (theme in Theme.entries) {
            val plan = DiLink51ClusterLayout.plan(1920, 720, theme)!!
            assertEquals(config.widthPixels, plan.streamWidth)
            assertEquals(config.heightPixels, plan.streamHeight)
            assertTrue(plan.sourceLeft >= 0)
            assertTrue(plan.sourceTop >= 0)
            assertTrue(plan.sourceLeft + plan.width <= config.widthPixels)
            assertEquals(config.heightPixels, plan.sourceTop + plan.height)
            // Preserve the centered, bottom-anchored car marker in every crop.
            assertTrue(960 in plan.sourceLeft until plan.sourceLeft + plan.width)
            assertTrue(640 in plan.sourceTop until plan.sourceTop + plan.height)
        }
        val side = DiLink51ClusterLayout.plan(1920, 720, Theme.SIMPLE)!!
        assertEquals(660, side.sourceLeft)
        assertEquals(0, side.sourceTop)
    }

    @Test fun diLink5AndUnknownFirmwareRetainThePrDisplaySelection() {
        for (fingerprint in listOf("BYD-AUTO/DiLink5.0/DiLink5.0:12/test", "unknown")) {
            assertFalse(DiLink51ClusterLayout.supported(fingerprint))
            assertEquals(DiLink51ClusterLayout.BASE, DiLink51ClusterLayout.displayName(names, fingerprint, Theme.SIMPLE))
            assertEquals(DiLink51ClusterLayout.FULL, DiLink51ClusterLayout.displayName(names.drop(1), fingerprint, Theme.MAP))
        }
    }

    @Test fun theLeopard5FirmwareUsesTheSharedLayers() {
        // #301: the plain layer that unverified firmware gets is one this cluster never shows.
        val leopard = DiLink51ClusterLayout.LEOPARD5_FINGERPRINT
        assertTrue(DiLink51ClusterLayout.supported(leopard))
        assertEquals(DiLink51ClusterLayout.FULL, DiLink51ClusterLayout.displayName(names, leopard, Theme.MAP))
        assertEquals(DiLink51ClusterLayout.SIDE, DiLink51ClusterLayout.displayName(names, leopard, Theme.SCENARIO))
        assertFalse(DiLink51ClusterLayout.supported(leopard.replace("20260705", "20260706")))
    }

    @Test fun theDenzaN9FirmwareUsesTheSharedLayers() {
        // #301: like the Leopard 5, the N9 cluster never shows the plain layer. OTA V1.9 changed the build,
        // and with only V1.8 listed DiPlay gave the updated car the plain layer and a 1920x720 stream.
        assertEquals(
            setOf(
                "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260705.011226:user/release-keys", // V1.8
                "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260902.221210:user/release-keys", // V1.9
            ),
            DiLink51ClusterLayout.DENZA_N9_FINGERPRINTS,
        )
        for (n9 in DiLink51ClusterLayout.DENZA_N9_FINGERPRINTS) {
            assertTrue(n9, DiLink51ClusterLayout.supported(n9))
            assertEquals(DiLink51ClusterLayout.FULL, DiLink51ClusterLayout.displayName(names, n9, Theme.MAP))
            assertEquals(DiLink51ClusterLayout.SIDE, DiLink51ClusterLayout.displayName(names, n9, Theme.SCENARIO))
            assertEquals(2560 to 720, DiLink51ClusterLayout.streamSize(n9))
        }
        val v18 = DiLink51ClusterLayout.DENZA_N9_FINGERPRINT
        assertFalse(DiLink51ClusterLayout.supported(v18.replace("011226", "011227")))
        assertFalse(DiLink51ClusterLayout.supported(DiLink51ClusterLayout.DENZA_N9_V19_FINGERPRINT.replace("221210", "221211")))
    }

    @Test fun the1920ClustersKeepTheirMeasuredPlans() {
        // The DiLink 5.1 and Leopard 5 clusters are 1920x720; the N9 side-map change must not reach them.
        for (theme in listOf(Theme.SCENARIO, Theme.SIMPLE)) {
            assertEquals(Plan(1320, 0, 600, 720, false, 1920, 720), DiLink51ClusterLayout.plan(1920, 720, theme))
        }
        assertEquals(Plan(0, 144, 1920, 480, true, 1920, 720), DiLink51ClusterLayout.plan(1920, 720, Theme.MAP))
    }

    @Test fun theN9FullMapIsTheMeasuredOneScaledAcross() {
        val measured = DiLink51ClusterLayout.plan(1920, 720, Theme.MAP)!!
        val wide = DiLink51ClusterLayout.plan(2560, 720, Theme.MAP)!!
        assertEquals(Plan(0, 144, 2560, 480, true, 2560, 720), wide)
        assertEquals(measured.left * 4, wide.left * 3)
        assertEquals(measured.width * 4, wide.width * 3)
        assertEquals(measured.top, wide.top)
        assertEquals(measured.height, wide.height)
    }

    @Test fun theN9SideMapIsTheStockMiniMapBox() {
        // #301: BYD's launchermap dumps on N9 OTA V1.8 and V1.9. MeterSmallScreenActivity fills the 2560x720
        // cluster; its card (small_screen_son_layout, with the 720 px mask_layout) starts at x=1900 and its
        // map surface (small_map_layout, MapSurfaceView) is 600x720 at x=1960..2560.
        val stockCard = Box(1900, 0, 2560, 720)
        val stockMap = Box(1960, 0, 2560, 720)
        val measured1920 = DiLink51ClusterLayout.plan(1920, 720, Theme.SCENARIO)!!
        for (theme in listOf(Theme.SCENARIO, Theme.SIMPLE)) {
            val plan = DiLink51ClusterLayout.plan(2560, 720, theme)!!
            assertEquals(Plan(1960, 0, 600, 720, false, 2560, 720), plan)
            val side = Box(plan.left, plan.top, plan.left + plan.width, plan.top + plan.height)
            assertEquals(stockMap, side)
            assertTrue(stockCard.contains(side))
            // Not scaled with the wider cluster: the measured 1920 box's size, against the right edge.
            assertEquals(measured1920.width, plan.width)
            assertEquals(measured1920.height, plan.height)
            assertEquals(1920 - measured1920.left, 2560 - plan.left)
        }
    }

    @Test fun theN9StreamSizeIsKnownBeforeItsClusterDisplayAppears() {
        // The cluster stream is negotiated before the display exists (#981), so the size comes from the firmware.
        assertEquals(2560 to 720, DiLink51ClusterLayout.streamSize(DiLink51ClusterLayout.DENZA_N9_FINGERPRINT))
        assertEquals(2560 to 720, DiLink51ClusterLayout.streamSize(DiLink51ClusterLayout.DENZA_N9_V19_FINGERPRINT))
        assertEquals(1920 to 720, DiLink51ClusterLayout.streamSize(firmware))
        assertEquals(1920 to 720, DiLink51ClusterLayout.streamSize(DiLink51ClusterLayout.LEOPARD5_FINGERPRINT))
        val (width, height) = DiLink51ClusterLayout.streamSize(DiLink51ClusterLayout.DENZA_N9_FINGERPRINT)
        for (theme in Theme.entries) {
            val plan = DiLink51ClusterLayout.plan(2560, 720, theme)!!
            assertEquals(width, plan.streamWidth)
            assertEquals(height, plan.streamHeight)
        }
    }

    @Test fun theWideN9ClusterGetsAStreamOfItsOwnSizeCroppedAtNativeScale() {
        val config = DiLink51ClusterLayout.streamConfig(2560, 720)
        assertEquals(2560, config.widthPixels)
        assertEquals(720, config.heightPixels)
        assertEquals(0, config.features)
        assertNull(config.safeArea)
        for (theme in Theme.entries) {
            val plan = DiLink51ClusterLayout.plan(2560, 720, theme)!!
            assertEquals(config.widthPixels, plan.streamWidth)
            assertEquals(config.heightPixels, plan.streamHeight)
            assertTrue(plan.sourceLeft >= 0)
            assertTrue(plan.sourceLeft + plan.width <= config.widthPixels)
            assertEquals(config.heightPixels, plan.sourceTop + plan.height)
            assertTrue(1280 in plan.sourceLeft until plan.sourceLeft + plan.width)
            assertTrue(640 in plan.sourceTop until plan.sourceTop + plan.height)
        }
        assertEquals(980, DiLink51ClusterLayout.plan(2560, 720, Theme.SIMPLE)!!.sourceLeft)
        assertEquals(240, DiLink51ClusterLayout.plan(2560, 720, Theme.MAP)!!.sourceTop)
    }

    @Test fun theN9SideCropIsCentredAtFullHeight() {
        // Like the 1920 side crop (source 660,0): the middle 600 columns of the stream, every row.
        val plan = DiLink51ClusterLayout.plan(2560, 720, Theme.SCENARIO)!!
        assertEquals(980, plan.sourceLeft)
        assertEquals(1280, plan.sourceLeft + plan.width / 2)
        assertEquals(0, plan.sourceTop)
        assertEquals(720, plan.sourceTop + plan.height)
    }

    @Test fun unmeasuredPanelSizesAreNotGivenTheWrongReadoutMask() {
        assertNull(DiLink51ClusterLayout.plan(1280, 480, Theme.MAP))
        assertNull(DiLink51ClusterLayout.plan(2560, 1080, Theme.MAP))
        assertNull(DiLink51ClusterLayout.plan(2400, 720, Theme.SCENARIO))
    }

    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(other: Box) = other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom
    }
}
