package com.shilapi.xcertplay.hud

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** #1196: HUD, cluster and standalone outputs write one report line per state change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class NavigationOutputLogTest {
    private val lines = mutableListOf<String>()
    private val owner = Any()

    @Before fun setUp() = NavigationOutputLog.resetForTest()
    @After fun tearDown() = NavigationOutputLog.resetForTest()

    @Test fun aStateIsWrittenOnceAndAgainOnlyWhenItChanges() {
        NavigationOutputLog.attach(owner, lines::add)
        repeat(3) { NavigationOutputLog.note("t", "hud.send", NavigationOutputLines.hudFireEvent(0)) }
        NavigationOutputLog.note("t", "hud.send", NavigationOutputLines.hudFireEvent(-1))
        NavigationOutputLog.note("t", "hud.gateway", NavigationOutputLines.hudConnected(0, 13, true))
        NavigationOutputLog.note("t", "hud.send", NavigationOutputLines.hudFireEvent(-1))

        assertEquals(listOf(
            "Navigation output: HUD fireEvent result=0",
            "Navigation output: HUD fireEvent result=-1 (not accepted)",
            "Navigation output: HUD SOME/IP connected callback=0 startResult=13 started=true",
        ), lines)
    }

    @Test fun aSessionThatAttachesLaterGetsTheCurrentStateOfEachOutput() {
        // The outputs start with the app, before any session log exists.
        NavigationOutputLog.note("t", "hud.bind", NavigationOutputLines.hudBindRequested())
        NavigationOutputLog.note("t", "cluster.adapter", NavigationOutputLines.clusterAdapter("com.byd.amapservice", false))
        NavigationOutputLog.note("t", "hud.bind", NavigationOutputLines.hudBindRefused(30))

        NavigationOutputLog.attach(owner, lines::add)
        NavigationOutputLog.note("t", "cluster.guidance", NavigationOutputLines.clusterGuidanceSent("com.byd.amapservice"))

        assertEquals(listOf(
            "Navigation output (current): HUD SOME/IP bind refused; retrying with backoff up to 30s",
            "Navigation output (current): cluster AMap adapter com.byd.amapservice simpleNavigationMode=false",
            "Navigation output: cluster guidance dispatched to com.byd.amapservice",
        ), lines)
    }

    @Test fun aFlappingOutputIsCappedPerSessionAndEachNewSessionStartsAgain() {
        NavigationOutputLog.attach(owner, lines::add)
        repeat(NavigationOutputLog.MAX_LINES_PER_KEY + 10) {
            NavigationOutputLog.note("t", "hud.send", NavigationOutputLines.hudFireEvent(it % 2))
        }
        assertEquals(NavigationOutputLog.MAX_LINES_PER_KEY + 1, lines.size)
        assertEquals("Navigation output: hud.send changes again; further changes this session are in logcat only", lines.last())

        val next = mutableListOf<String>()
        NavigationOutputLog.attach(Any(), next::add)
        NavigationOutputLog.note("t", "hud.send", NavigationOutputLines.hudFireEvent(7))
        assertEquals(listOf(
            "Navigation output (current): HUD fireEvent result=1 (not accepted)",
            "Navigation output: HUD fireEvent result=7 (not accepted)",
        ), next)
    }

    @Test fun onlyTheOwnerThatAttachedLastCanDetach() {
        val newer = Any()
        val newerLines = mutableListOf<String>()
        NavigationOutputLog.attach(owner, lines::add)
        NavigationOutputLog.attach(newer, newerLines::add)
        NavigationOutputLog.detach(owner) // the old controller closes after the new one started
        NavigationOutputLog.note("t", "cluster.guidance", NavigationOutputLines.clusterGuidanceEnded())
        NavigationOutputLog.detach(newer)
        NavigationOutputLog.note("t", "cluster.guidance", NavigationOutputLines.clusterGuidanceSent("com.byd.amapservice"))

        assertTrue(lines.isEmpty())
        assertEquals(listOf("Navigation output: cluster guidance ended"), newerLines)
    }

    @Test fun aSinkThatThrowsCannotBreakAnOutput() {
        NavigationOutputLog.attach(owner) { throw IllegalStateException("log closed") }
        NavigationOutputLog.note("t", "cluster.broadcast", NavigationOutputLines.clusterBroadcastFailed(SecurityException("x")))
    }
}
