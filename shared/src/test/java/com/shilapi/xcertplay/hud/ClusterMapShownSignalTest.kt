package com.shilapi.xcertplay.hud

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * One host report reaches two consumers. A centre-screen copy of the dashboard map (the centre card or
 * a launcher embedding it) must stop the stream pause, but must not switch the DiLink 3 cluster back
 * to its stock view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ClusterMapShownSignalTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var originalReadMode: (android.content.Context) -> BydClusterNaviMode?

    @Before
    fun setUp() {
        originalReadMode = BydClusterMapPause.readMode
    }

    @After
    fun tearDown() {
        // Without a context the ticker BydClusterMapPauseTest may have left running does nothing.
        pauseContext.set(null, null)
        BydOutputSettings.setClusterStreamPause(context, false)
        BydNavigationOutputs.setClusterMapShown(false, mirrored = false)
        BydClusterMapPause.readMode = originalReadMode
        BydClusterMapPause.streamControl = null
        BydClusterMapPause.onNaviMode = null
    }

    private val pauseContext
        get() = BydClusterMapPause::class.java.getDeclaredField("context").apply { isAccessible = true }

    @Test
    fun hostSignalNeverLetsACopyClearShown() {
        for (presentation in listOf(false, true)) for (route in listOf(false, true)) for (surface in listOf(false, true)) {
            val onCluster = presentation || (route && surface)
            for (mirrors in listOf(false, true)) for (player in listOf(false, true)) {
                val signal = BydNavigationOutputs.clusterMapSignal(presentation, route, surface, mirrors, player)
                val case = "presentation=$presentation route=$route surface=$surface mirrors=$mirrors player=$player"
                assertEquals(case, onCluster, signal.shown)
                assertEquals(case, mirrors && !player, signal.mirrored)

                // The pause keeps the value the host sent before the signal was split (#1188); the bridge
                // only follows the map window.
                BydNavigationOutputs.setClusterMapShown(signal.shown, signal.mirrored)
                assertEquals(case, onCluster && (!mirrors || player), BydClusterMapPause.clusterMapShown)
                assertEquals(case, onCluster, BydClusterBridge.mapShown)
            }
        }
    }

    @Test
    fun hostSignalWithCentreCopyKeepsClusterProjection() {
        val signal = BydNavigationOutputs.clusterMapSignal(
            presentation = true, confirmedRoute = false, clusterSurface = false, mirrors = true, player = false,
        )
        BydNavigationOutputs.setClusterMapShown(signal.shown, signal.mirrored)

        assertTrue("the centre card must not switch DiLink 3 to its stock view", BydClusterBridge.mapShown)
        assertFalse("the centre card still needs the stream", BydClusterMapPause.clusterMapShown)

        val routed = BydNavigationOutputs.clusterMapSignal(
            presentation = false, confirmedRoute = true, clusterSurface = true, mirrors = true, player = false,
        )
        BydNavigationOutputs.setClusterMapShown(routed.shown, routed.mirrored)
        assertTrue("an ADB cluster route with a centre copy stays projected", BydClusterBridge.mapShown)
    }

    @Test
    fun mapOnClusterWithoutCopyReachesBothConsumers() {
        BydNavigationOutputs.setClusterMapShown(true, mirrored = false)

        assertTrue(BydClusterMapPause.clusterMapShown)
        assertTrue(BydClusterBridge.mapShown)
    }

    @Test
    fun centreCopyStopsThePauseButKeepsClusterProjection() {
        BydNavigationOutputs.setClusterMapShown(true, mirrored = true)

        assertFalse("a copy on the centre screen must keep the stream running", BydClusterMapPause.clusterMapShown)
        assertTrue("the cluster still shows the map; DiLink 3 must stay in projection", BydClusterBridge.mapShown)
    }

    @Test
    fun mapOffClusterReachesBothConsumersWithOrWithoutCopy() {
        BydNavigationOutputs.setClusterMapShown(true, mirrored = false)
        BydNavigationOutputs.setClusterMapShown(false, mirrored = true)
        assertFalse(BydClusterMapPause.clusterMapShown)
        assertFalse(BydClusterBridge.mapShown)

        BydNavigationOutputs.setClusterMapShown(true, mirrored = false)
        BydNavigationOutputs.setClusterMapShown(false, mirrored = false)
        assertFalse(BydClusterMapPause.clusterMapShown)
        assertFalse(BydClusterBridge.mapShown)
    }

    @Test
    fun centreCopyKeepsTheStreamWhileTheWheelModeHidesTheMap() {
        BydClusterMapPause.readMode = { BydClusterNaviMode.OFF }
        var streaming = true
        // Only this thread's ticks count: a ticker left running by another test must not race the asserts.
        val testThread = Thread.currentThread()
        BydClusterMapPause.streamControl = { if (Thread.currentThread() === testThread) streaming = it }
        pauseContext.set(null, context)
        val tick = BydClusterMapPause::class.java.getDeclaredMethod("tick").apply { isAccessible = true }
        BydOutputSettings.setClusterStreamPause(context, true)

        BydNavigationOutputs.setClusterMapShown(true, mirrored = false)
        tick.invoke(BydClusterMapPause)
        assertFalse("without a copy the pause stops the stream in Off", streaming)

        BydNavigationOutputs.setClusterMapShown(true, mirrored = true)
        tick.invoke(BydClusterMapPause)
        assertTrue("the centre copy still needs the stream", streaming)
        assertTrue(BydClusterBridge.mapShown)
    }
}
