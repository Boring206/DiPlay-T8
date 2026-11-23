package com.shilapi.xcertplay

import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.media.CarPlayVideoLayout
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedConstruction
import org.mockito.Mockito.mockConstruction
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.util.concurrent.PausedExecutorService
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowProcess

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayHostDisplaySizeTest {
    private lateinit var activity: CarPlayHostActivity
    private lateinit var controllerConstruction: MockedConstruction<CarPlayController>
    private val sizeClass = Class.forName("com.shilapi.xcertplay.CarPlayHostActivity\$DisplaySize")

    @Before fun setUp() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        AirPlayPersistence.saveAdaptPipResolution(activity, false)
        AirPlayPersistence.saveHarmanFullscreen(activity, false)
        // Source-only tests have no provisioned local authentication identity.
        AirPlayPersistence.saveMfiTarget(activity, MfiTarget.USB_CH341)
        // Exercise host startup without launching vendor-service workers or real transports.
        controllerConstruction = mockConstruction(CarPlayController::class.java) { controller, _ ->
            org.mockito.Mockito.`when`(controller.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
        }
        (getField("teardownExecutor") as ExecutorService).shutdownNow()
        setField("teardownExecutor", PausedExecutorService())
        CarPlayBackgroundSession::class.java.getDeclaredField("owner").apply { isAccessible = true }
            .set(CarPlayBackgroundSession, activity)
        setField("activeDisplaySize", size(1920, 990))
    }

    @After fun tearDown() {
        CarPlayVideo.playerOnScreen = false
        VideoInCar.allowed = false
        for (name in listOf("appContext", "controller", "streamId")) {
            CarPlayVideo::class.java.getDeclaredField(name).apply { isAccessible = true }.set(CarPlayVideo, null)
        }
        (getField("shuttingDown") as AtomicBoolean).set(true)
        (getField("mainHandler") as Handler).removeCallbacksAndMessages(null)
        AirPlayPersistence.overlaySettingsListener = null
        com.shilapi.xcertplay.hud.BydNavigationOutputs.setTurnOverlayListener(null)
        (getField("controller") as? CarPlayController)?.let {
            CarPlayMediaKeys.detach(it)
            it.close()
            it.awaitClosed(1000)
        }
        (getField("sink") as? AndroidMediaSink)?.close()
        (getField("teardownExecutor") as ExecutorService).shutdownNow()
        (getField("airPlayCommandExecutor") as ExecutorService).shutdownNow()
        CarPlayBackgroundSession.clear()
        controllerConstruction.close()
    }

    @Test fun surroundViewOpenAndCloseKeepsTheNegotiatedCanvas() {
        val display = startSession()
        applySize(1920, 942)
        assertEquals(size(1920, 942), getField("activeDisplaySize"))
        applySize(1920, 990)
        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
        assertFalse(getField("handshakeResetInProgress") as Boolean)
        assertEquals(2, keepLogs())
    }

    @Test fun aNarrowWindowIsNotTreatedAsScreenRotation() {
        val display = startSession()
        applySize(700, 990)
        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
        assertEquals(1, keepLogs())
    }

    @Test fun aNarrowWindowWithAdaptPipResolutionTriggersReconnect() {
        AirPlayPersistence.saveAdaptPipResolution(activity, true)
        startSession()
        shadowOf(activity).setInMultiWindowMode(true)
        applySize(700, 990)
        assertEquals(1, getField("restartGeneration"))
        assertNull(getField("sessionDisplay"))
        assertTrue(restartLog(), restartLog().contains("multi-window aspect 1.94 -> 0.71"))
    }

    // #1219, DiLink 2.0: the system bars come back about 200 ms before DiPlay stops as another app opens
    // (1920x1080 -> 1920x952). "Adapt multi-window resolution" refits multi-window, launcher cards and
    // launcher PiP, not a full-screen window on the main display, so this keeps the session instead of
    // reconnecting twice.
    @Test fun barsReturningAsAnotherAppOpensKeepTheSessionWithAdaptMultiWindowResolution() {
        AirPlayPersistence.saveAdaptPipResolution(activity, true)
        val display = startSession(windowHeight = 1080)
        applySize(1920, 952)
        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
        assertEquals(1, keepLogs())
        applySize(1920, 1080)
        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
    }

    @Test fun aNarrowFullScreenWindowWithAdaptPipResolutionKeepsTheSession() {
        AirPlayPersistence.saveAdaptPipResolution(activity, true)
        val display = startSession()
        applySize(700, 990)
        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
    }

    // A launcher card or launcher PiP (#406, #1003, #1052) shows DiPlay on another display, where Android
    // reports no multi-window. That is what "Adapt multi-window resolution" was written for: it still refits.
    @Test fun aLauncherCardOnAnotherDisplayStillAdaptsItsShape() {
        AirPlayPersistence.saveAdaptPipResolution(activity, true)
        startSession()
        val card = ShadowDisplayManager.addDisplay("w700dp-h990dp")
        hostVideoView(700, 990, displayManager().getDisplay(card))

        applySize(700, 990)

        assertEquals(1, getField("restartGeneration"))
        assertNull(getField("sessionDisplay"))
        assertTrue(restartLog(), restartLog().contains("display $card aspect 1.94 -> 0.71"))
    }

    @Test fun theSameShapeOnTheMainDisplayOutsideMultiWindowKeepsTheCanvas() {
        AirPlayPersistence.saveAdaptPipResolution(activity, true)
        val display = startSession()
        hostVideoView(700, 990, displayManager().getDisplay(android.view.Display.DEFAULT_DISPLAY))

        applySize(700, 990)

        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
        assertEquals(1, keepLogs())
    }

    @Test fun resumingASquareCanvasInTheSameWindowDoesNotReconnect() {
        AirPlayPersistence.saveAdaptPipResolution(activity, true)
        val display = startSession(windowHeight = 1080, canvasHeight = 1920)
        val video = object : TextureView(activity) {
            override fun post(action: Runnable): Boolean = Handler(Looper.getMainLooper()).post(action)
        }.apply { layout(0, 0, 1920, 1080) }
        setField("videoView", video)

        invoke("onResume")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

        assertSame(display, getField("sessionDisplay"))
        assertEquals(size(1920, 1080), getField("activeDisplaySize"))
        assertEquals(0, getField("restartGeneration"))
        assertNull(getField("pendingDisplaySize"))
    }

    @Test fun aRealPipAspectChangeStillReconnectsWithASquareCanvas() {
        AirPlayPersistence.saveAdaptPipResolution(activity, true)
        startSession(windowHeight = 1080, canvasHeight = 1920)

        shadowOf(activity).setInMultiWindowMode(true)
        applySize(700, 1080)

        assertEquals(1, getField("restartGeneration"))
        assertNull(getField("sessionDisplay"))
    }


    @Test fun connectingInANarrowWindowRebuildsWhenTheCameraCloses() {
        startSession(windowWidth = 700)
        applySize(1920, 990)
        assertEquals(size(1920, 990), getField("activeDisplaySize"))
        assertEquals(1, getField("restartGeneration"))
        assertTrue(getField("handshakeResetInProgress") as Boolean)
        assertNull(getField("sessionDisplay"))
    }

    @Test fun connectingInAReducedHeightWindowRebuildsWhenTheCameraCloses() {
        startSession(windowHeight = 942)
        applySize(1920, 990)
        assertEquals(1, getField("restartGeneration"))
        assertNull(getField("sessionDisplay"))
    }

    @Test fun aScaledDownCanvasKeepsTheSessionWhenTheOriginalWindowReturns() {
        val display = startSession(canvasWidth = 1536, canvasHeight = 792)
        applySize(700, 990)
        applySize(1920, 990)
        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
    }

    @Test fun aScaledUpCanvasStillRebuildsWhenTheStartupWindowGrows() {
        startSession(windowWidth = 700, canvasWidth = 1400, canvasHeight = 1980)
        applySize(1000, 990)
        assertEquals(1, getField("restartGeneration"))
        assertNull(getField("sessionDisplay"))
    }

    @Test fun actualScreenRotationStillRebuildsTheSession() {
        startSession(rotation = Surface.ROTATION_90)
        applySize(990, 1920)
        assertEquals(1, getField("restartGeneration"))
        assertTrue(getField("handshakeResetInProgress") as Boolean)
        assertNull(getField("sessionDisplay"))
        assertTrue(restartLog(), restartLog().contains("(rotation 1 -> 0)"))
    }

    @Test fun rotationIsHandledEvenIfTheViewSizeIsUnchanged() {
        startSession(rotation = Surface.ROTATION_180)
        scheduleSize(1920, 990)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(1, getField("restartGeneration"))
    }

    @Test fun anExplicitBarLayoutChangeStillRebuildsTheSession() {
        startSession()
        setField("hideTopBar", false)
        applySize(1920, 942)
        assertEquals(1, getField("restartGeneration"))
        assertTrue(restartLog(), restartLog().contains("(saved bars hideTop=true -> false hideBottom=true -> true)"))
    }

    @Test fun growingBeyondTheSessionWindowNamesTheReason() {
        startSession(windowHeight = 942)
        applySize(1920, 990)
        assertTrue(restartLog(), restartLog().contains("1920x942 -> 1920x990 (larger than the session window 1920x942)"))
    }

    // #1176: on a 16:10 screen a side-by-side split window (1270x1500) is taller than wide. Leaving it is
    // not a turn: CarPlay goes back to the full-screen area instead of drawing the split layout stretched.
    @Test @Config(qualifiers = "w2560dp-h1600dp-land-mdpi")
    fun leavingATallSplitWindowOnA16By10ScreenSendsTheFullScreenArea() {
        val areas = CarPlayViewAreas.build(2560, 1600, CarPlayDock.AUTOMATIC, 1270f / 2560 to 1500f / 1600)!!
        val full = areas.current
        val split = areas.index(CarPlayViewAreas.Kind.SPLIT_SCREEN, false, null)!!
        assertEquals(1270, areas.areas[split].width)
        assertEquals(1500, areas.areas[split].height)
        val controller = org.mockito.Mockito.mock(CarPlayController::class.java)
        org.mockito.Mockito.`when`(controller.showViewArea(org.mockito.ArgumentMatchers.anyInt())).thenReturn(true)
        setField("controller", controller)
        val display = CarPlaySessionDisplay(2560, 1600, invoke("displayRotation") as Int, true, true, 2560, 1600, areas)
        setField("activeDisplaySize", size(2560, 1600))
        setField("sessionDisplay", display)

        shadowOf(activity).setInMultiWindowMode(true)
        applySize(1270, 1500)
        org.mockito.Mockito.verify(controller).showViewArea(split)
        assertEquals(split, areas.current)

        shadowOf(activity).setInMultiWindowMode(false)
        applySize(2560, 1600)

        org.mockito.Mockito.verify(controller).showViewArea(full)
        assertEquals(full, areas.current)
        assertEquals(size(2560, 1600), getField("activeDisplaySize"))
        assertEquals(0, getField("restartGeneration"))
        assertEquals(0, keepLogs())
    }

    // #1180, DiLink 4.0 with the navigation bar shown: DiPlay's own video player hides the system bars and
    // the CarPlay window behind it grew from 1920x990 to 1920x1032 just after the video stream's SETUP,
    // which reconnected CarPlay and stopped the video. The bars come back about 0.5 s after DiPlay does.
    @Test fun theVideoPlayerHidingTheBarsKeepsTheCanvasUntilDiPlayIsBack() {
        val display = startSession()
        val video = hostVideoView(1920, 1032)
        CarPlayVideo.playerOnScreen = true

        applySize(1920, 1032)

        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
        assertEquals(size(1920, 990), getField("activeDisplaySize"))
        assertEquals(1, ShadowLog.getLogsForTag("xcertplay-usb").count { it.msg.contains("video player is open") })

        CarPlayVideo.onPlayerGone()
        invoke("onResume")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(0, getField("restartGeneration"))
        video.layout(0, 0, 1920, 990)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))

        assertSame(display, getField("sessionDisplay"))
        assertEquals(size(1920, 990), getField("activeDisplaySize"))
        assertEquals(0, getField("restartGeneration"))
    }

    @Test fun aWindowThatStaysLargerAfterTheVideoPlayerClosesIsCheckedAgain() {
        startSession()
        hostVideoView(1920, 1032)
        CarPlayVideo.playerOnScreen = true
        applySize(1920, 1032)
        assertEquals(0, getField("restartGeneration"))

        CarPlayVideo.onPlayerGone()
        invoke("onResume")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_100))

        assertEquals(1, getField("restartGeneration"))
        assertNull(getField("sessionDisplay"))
    }

    @Test fun aStaleVideoPlayerFlagDoesNotHoldAResumedHost() {
        startSession()
        CarPlayVideo.playerOnScreen = true
        setField("hostAppearanceResumed", true)
        applySize(1920, 1032)
        assertEquals(1, getField("restartGeneration"))
    }

    // Report 8 of #1180 ran on a square turning canvas (1920x990 window, 1920x1920 stream), where a size
    // change applies at once instead of after the 500 ms debounce.
    @Test fun theVideoPlayerHidingTheBarsKeepsATurningSquareCanvasUntilDiPlayIsBack() {
        val (landscape, portrait) = CarPlayRotation.turningAreas(1920, 1920, 990, 1920, 1080)
        val areas = CarPlayViewAreas.build(1920, 1920, listOf(
            CarPlayViewAreas.Screen(landscape.first, landscape.second, portrait = false),
            CarPlayViewAreas.Screen(portrait.first, portrait.second, portrait = true),
        ), CarPlayDock.AUTOMATIC, splitWindow = { null }, startPortrait = false)!!
        assertTrue(areas.turnsWithScreen)
        val full = areas.current
        val controller = org.mockito.Mockito.mock(CarPlayController::class.java)
        setField("controller", controller)
        val display = CarPlaySessionDisplay(1920, 1920, invoke("displayRotation") as Int, true, true, 1920, 990, areas)
        setField("activeDisplaySize", size(1920, 990))
        setField("sessionDisplay", display)
        val video = hostVideoView(1920, 1032)
        CarPlayVideo.playerOnScreen = true

        scheduleSize(1920, 1032)
        shadowOf(Looper.getMainLooper()).idle()

        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
        assertEquals(1, ShadowLog.getLogsForTag("xcertplay-usb").count { it.msg.contains("video player is open") })

        CarPlayVideo.onPlayerGone()
        invoke("onResume")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(0, getField("restartGeneration"))
        video.layout(0, 0, 1920, 990)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))

        assertSame(display, getField("sessionDisplay"))
        assertEquals(size(1920, 990), getField("activeDisplaySize"))
        assertEquals(0, getField("restartGeneration"))
        assertEquals(full, areas.current)
        org.mockito.Mockito.verify(controller, org.mockito.Mockito.never()).showViewArea(org.mockito.ArgumentMatchers.anyInt())
    }

    // On a non-turning canvas the change waits 500 ms. A player that fails or is closed within that time
    // must still have DiPlay wait for the bars to come back, or the pending change reconnects (#1180).
    @Test fun aPlayerClosedBeforeTheDebouncedChangeAppliesStillWaitsForTheBars() {
        val display = startSession()
        val video = hostVideoView(1920, 1032)
        CarPlayVideo.playerOnScreen = true

        scheduleSize(1920, 1032)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        CarPlayVideo.onPlayerGone()
        invoke("onResume")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
        video.layout(0, 0, 1920, 990)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))

        assertSame(display, getField("sessionDisplay"))
        assertEquals(size(1920, 990), getField("activeDisplaySize"))
        assertEquals(0, getField("restartGeneration"))
    }

    // Android can refuse to start the player from the background. When the item then stops, the flag set
    // for the opening player must not keep holding the CarPlay screen's size changes.
    @Test fun aPlayerThatNeverOpenedStopsHoldingWhenTheVideoSessionEnds() {
        CarPlayVideo.attach(activity, org.mockito.Mockito.mock(CarPlayController::class.java))
        VideoInCar.allowed = true
        CarPlayVideo.onVideoMessage(7L, mapOf("type" to "insertPlayQueueItem",
            "item" to mapOf("Content-Location" to "https://example.com/video.m3u8")))
        CarPlayVideo.onVideoMessage(7L, mapOf("type" to "setRate", "rate" to 1.0))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(CarPlayVideo.playerOnScreen)
        assertNull(CarPlayVideo.activity)

        CarPlayVideo.onVideoSessionEnded()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(CarPlayVideo.playerOnScreen)

        startSession()
        applySize(1920, 1032)
        assertEquals(1, getField("restartGeneration"))
        assertNull(getField("sessionDisplay"))
    }

    @Test fun closingTheCarPlayScreenCancelsTheCheckAfterTheVideoPlayer() {
        val display = startSession()
        hostVideoView(1920, 1032)
        CarPlayVideo.playerOnScreen = true
        applySize(1920, 1032)
        CarPlayVideo.onPlayerGone()
        invoke("onResume")
        // The screen is closed after the resume's own check, while the window still has the player's size.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertNull(getField("pendingDisplaySize"))

        invoke("onDestroy")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_500))

        assertNull(getField("pendingDisplaySize"))
        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
        assertFalse(getField("videoPlayerSettling") as Boolean)
        assertFalse(getField("videoPlayerHeldSize") as Boolean)
    }

    @Test fun resumingReadsSavedBarsAndRebuildsEvenWhenTheWindowSizeIsUnchanged() {
        startSession()
        // 测试窗口未挂载，使用主线程队列执行布局后的刷新。
        val video = object : TextureView(activity) {
            override fun post(action: Runnable): Boolean = Handler(Looper.getMainLooper()).post(action)
        }.apply { layout(0, 0, 1920, 990) }
        setField("videoView", video)
        AirPlayPersistence.saveHideTopBar(activity, true)
        AirPlayPersistence.saveHideBottomBar(activity, false)

        invoke("onResume")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

        assertEquals(true, getField("hideTopBar"))
        assertEquals(false, getField("hideBottomBar"))
        assertWindowBars(true, false)
        assertEquals(1, getField("restartGeneration"))
        assertTrue(getField("handshakeResetInProgress") as Boolean)
    }

    @Test fun resumingWithUnchangedBarsKeepsTheSession() {
        val display = startSession()

        invoke("onResume")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

        assertSame(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
    }

    @Test fun resumingWhileEditingDoesNotOverwriteTheBarPreview() {
        startSession()
        setField("menuOpen", true)
        setField("hideTopBar", false)
        setField("hideBottomBar", true)

        invoke("onResume")

        assertEquals(false, getField("hideTopBar"))
        assertEquals(true, getField("hideBottomBar"))
        assertWindowBars(false, true)
        assertTrue(AirPlayPersistence.loadHideTopBar(activity))
        assertEquals(0, getField("restartGeneration"))
    }

    @Test fun fullscreenApplicationSupportsAllFourBarCombinations() {
        for (hideTop in listOf(false, true)) {
            for (hideBottom in listOf(false, true)) {
                setField("harmanFullscreenEnabled", false)
                setField("hideTopBar", hideTop)
                setField("hideBottomBar", hideBottom)

                invoke("applyFullscreenMode")

                assertWindowBars(hideTop, hideBottom)
            }
        }
    }

    @Test fun optionalTemporaryBarsRevealAndRestoreAfterFiveSecondsWithoutRenegotiatingCarPlay() {
        val display = startSession()
        setField("temporarySystemBarsEnabled", true)
        setField("gestureFingerCount", 3)
        setField("hideTopBar", true)
        setField("hideBottomBar", true)
        invoke("applyFullscreenMode")
        assertWindowBars(true, true)

        assertEquals(true, invoke("showTemporarySystemBars"))
        assertWindowBars(false, false)
        assertEquals(display, getField("sessionDisplay"))
        assertEquals(0, getField("restartGeneration"))
        // Transient visibility must not change the persistent CarPlay fullscreen selection.
        assertTrue(getField("hideTopBar") as Boolean)
        assertTrue(getField("hideBottomBar") as Boolean)
        assertTrue(AirPlayPersistence.loadHideTopBar(activity))
        assertTrue(AirPlayPersistence.loadHideBottomBar(activity))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4_999))
        assertWindowBars(false, false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertWindowBars(true, true)
        assertEquals(false, getField("temporarySystemBarsVisible"))
        assertEquals(display, getField("sessionDisplay"))
    }

    @Test fun revealingAgainExtendsTheExpiryAndBackgroundCancelsIt() {
        setField("temporarySystemBarsEnabled", true)
        invoke("applyFullscreenMode")
        assertEquals(true, invoke("showTemporarySystemBars"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(true, invoke("showTemporarySystemBars"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertWindowBars(false, false)
        invoke("onPause")
        assertWindowBars(true, true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertWindowBars(true, true)
    }

    @Test fun temporarySystemBarsRequireOptInHiddenBarsAndCompatibleMode() {
        assertEquals(false, invoke("showTemporarySystemBars"))
        setField("temporarySystemBarsEnabled", true)
        setField("hideTopBar", false)
        setField("hideBottomBar", false)
        assertEquals(false, invoke("showTemporarySystemBars"))
        setField("hideTopBar", true)
        setField("harmanFullscreenEnabled", true)
        assertEquals(false, invoke("showTemporarySystemBars"))
        setField("harmanFullscreenEnabled", false)
        setField("gestureFingerCount", 0)
        assertEquals(false, invoke("showTemporarySystemBars"))
    }

    @Suppress("DEPRECATION")
    @Test fun harmanFullscreenUsesTheVerifiedLegacyFlags() {
        setField("harmanFullscreenEnabled", true)
        setField("hideTopBar", false)
        setField("hideBottomBar", false)

        invoke("applyFullscreenMode")

        assertEquals(0x080e, activity.window.decorView.systemUiVisibility)
    }

    @Suppress("DEPRECATION")
    @Test fun disablingHarmanFullscreenRestoresStandardBars() {
        setField("harmanFullscreenEnabled", true)
        invoke("applyFullscreenMode")
        setField("harmanFullscreenEnabled", false)
        setField("hideTopBar", false)
        setField("hideBottomBar", false)

        invoke("applyFullscreenMode")

        assertEquals(0, activity.window.decorView.systemUiVisibility and HarmanFullscreenCompat.OEM_FULLSCREEN_BIT)
        assertWindowBars(false, false)
    }

    @Test fun menuBarChangesPreviewWithoutSavingAndCancelRestoresTheSavedValues() {
        invoke("loadPersistedSettings")
        setField("settingsBaseline", invoke("captureSettingsBaseline"))
        setField("menuOpen", true)
        val controls = invoke("buildFullscreenSection") as ViewGroup

        menuBarSwitch(controls, R.string.hide_the_navigation_bar).performClick()

        assertEquals(false, getField("hideBottomBar"))
        assertWindowBars(true, false)
        assertTrue(AirPlayPersistence.loadHideBottomBar(activity))

        invoke("cancelSettingsEdits")

        assertEquals(true, getField("hideBottomBar"))
        assertWindowBars(true, true)
        assertTrue(AirPlayPersistence.loadHideBottomBar(activity))
    }

    @Test fun savingMenuBarsPreservesAnIndependentCombination() {
        AirPlayPersistence.saveWirelessHotspotMode(activity, WirelessHotspotMode.WIFI_P2P)
        invoke("loadPersistedSettings")
        setField("menuOpen", true)
        val controls = invoke("buildFullscreenSection") as ViewGroup
        menuBarSwitch(controls, R.string.hide_the_status_bar).performClick()

        invoke("saveSettingsAndReconnect")

        assertFalse(AirPlayPersistence.loadHideTopBar(activity))
        assertTrue(AirPlayPersistence.loadHideBottomBar(activity))
        assertEquals(false, getField("menuOpen"))
    }

    @Test fun quickOpenAndCloseCancelsThePendingShrink() {
        startSession()
        scheduleSize(700, 990)
        scheduleSize(1920, 990)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(size(1920, 990), getField("activeDisplaySize"))
        assertNull(getField("pendingDisplaySize"))
        assertEquals(0, getField("restartGeneration"))
    }

    @Test fun anOngoingHandshakeResetOnlyRecordsTheNewSize() {
        setField("handshakeResetInProgress", true)
        applySize(700, 990)
        assertEquals(size(700, 990), getField("activeDisplaySize"))
        assertEquals(0, getField("restartGeneration"))
    }

    @Test @Config(sdk = [30]) fun rotatingBackDuringTeardownStartsWithTheLatestSize() {
        allowStartup()
        startSession(windowWidth = 2250, windowHeight = 1080)
        applySize(1080, 2250)
        assertTrue(getField("handshakeResetInProgress") as Boolean)
        applySize(2250, 1080)

        finishTeardown()

        assertSessionSize(2250, 1080)
        assertEquals(1, getField("restartGeneration"))
        assertFalse(getField("handshakeResetInProgress") as Boolean)
    }

    @Test @Config(sdk = [30]) fun teardownWaitsForAPendingRotationToSettle() {
        allowStartup()
        startSession(windowWidth = 2250, windowHeight = 1080)
        applySize(1080, 2250)
        scheduleSize(2250, 1080)

        finishTeardown()
        assertNull(getField("sessionDisplay"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

        assertSessionSize(2250, 1080)
        assertNull(getField("pendingDisplaySize"))
        assertEquals(1, getField("restartGeneration"))
    }

    @Test @Config(sdk = [30]) fun cancellingAPendingRotationResumesStartup() {
        allowStartup()
        startSession(windowWidth = 2250, windowHeight = 1080)
        applySize(1080, 2250)
        scheduleSize(2250, 1080)
        finishTeardown()
        assertNull(getField("sessionDisplay"))

        scheduleSize(1080, 2250)

        assertSessionSize(1080, 2250)
        assertNull(getField("pendingDisplaySize"))
        assertEquals(1, getField("restartGeneration"))
    }

    @Test @Config(sdk = [30]) fun aPendingSameSizeRotationResumesAfterTeardown() {
        allowStartup()
        startSession(rotation = Surface.ROTATION_180)
        scheduleSize(1920, 990)
        activity.javaClass.getDeclaredMethod("restartCarPlay", String::class.java)
            .apply { isAccessible = true }.invoke(activity, "Test reconnect")
        finishTeardown()
        assertNull(getField("sessionDisplay"))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))

        assertSessionSize(1920, 990)
        assertNull(getField("pendingDisplaySize"))
        assertEquals(1, getField("restartGeneration"))
    }

    @Test @Config(sdk = [30]) fun wiredRestartTimesOutButDoesNotStartUntilOldOwnerCloses() {
        allowStartup()
        startSession()
        val old = org.mockito.Mockito.mock(CarPlayController::class.java)
        setField("controller", old)
        org.mockito.Mockito.`when`(old.isWiredTransport).thenReturn(true)
        org.mockito.Mockito.`when`(old.diagnosticAttempt).thenReturn(17)
        val diagnosticLog = SessionLogFile(java.io.File(activity.cacheDir, "wired-guard.log"))
        diagnosticLog.reset("test")
        setField("sessionLog", diagnosticLog)
        var closed = false
        org.mockito.Mockito.`when`(old.awaitClosed(org.mockito.ArgumentMatchers.anyLong())).thenAnswer { closed }
        try {
            activity.javaClass.getDeclaredMethod("restartCarPlay", String::class.java)
                .apply { isAccessible = true }.invoke(activity, "Test reconnect")
            finishTeardown()
            assertFalse(getField("handshakeResetInProgress") as Boolean)
            assertTrue(getField("startupRetryStopped") as Boolean)
            assertNull("timed-out owner must block a new controller", getField("controller"))
            invoke("maybeStartCarPlay")
            assertNull("retry must remain blocked while owner is active", getField("controller"))
            org.mockito.Mockito.verify(old).awaitClosed(20_000L)
            assertTrue(AsyncDiagnosticLog.awaitIdle(2_000))
            val diagnostic = diagnosticLog.file.readText()
            assertTrue(diagnostic.contains("restart blocked wiredTeardown=PENDING controllerAttempts=17"))
            assertTrue(diagnostic.contains("start blocked wiredTeardown=PENDING controllerAttempts=17"))
            closed = true
            invoke("maybeStartCarPlay")
            assertNotNull("a retry may start when the old owner closes", getField("controller"))
        } finally {
            closed = true
            WiredTeardownGuard.pendingControllerAttempts()
            diagnosticLog.close()
            setField("sessionLog", null)
        }
    }

    @Test @Config(sdk = [30]) fun blockedSynchronousCloseShowsRetryWithoutStartingAnotherController() {
        allowStartup()
        startSession()
        val old = org.mockito.Mockito.mock(CarPlayController::class.java)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var closed = false
        org.mockito.Mockito.`when`(old.isWiredTransport).thenReturn(true)
        org.mockito.Mockito.`when`(old.awaitClosed(org.mockito.ArgumentMatchers.anyLong())).thenAnswer { closed }
        org.mockito.Mockito.doAnswer {
            started.countDown()
            release.await(3, TimeUnit.SECONDS)
            null
        }.`when`(old).close()
        setField("controller", old)
        val retry = View(activity).apply { visibility = View.GONE }
        val stage = android.widget.TextView(activity)
        setField("startupRetryButton", retry)
        setField("stageStatusView", stage)
        try {
            activity.javaClass.getDeclaredMethod("restartCarPlay", String::class.java)
                .apply { isAccessible = true }.invoke(activity, "Test reconnect")
            finishTeardown()
            assertTrue("close started on a separate thread", started.await(2, TimeUnit.SECONDS))
            assertEquals(View.VISIBLE, retry.visibility)
            assertEquals(activity.getString(R.string.wired_teardown_pending, activity.getString(R.string.retry_carplay_connection)),
                stage.text.toString())
            assertFalse(getField("handshakeResetInProgress") as Boolean)
            assertNull("blocked close must prevent replacement", getField("controller"))
        } finally {
            closed = true
            release.countDown()
            WiredTeardownGuard.pendingControllerAttempts()
        }
    }

    // A replug or a returning surface starts through maybeStartCarPlay, not through the Retry button.
    @Test @Config(sdk = [30]) fun aStartAfterABlockedWiredStartCanReconnectAgain() {
        allowStartup()
        startSession()
        val old = org.mockito.Mockito.mock(CarPlayController::class.java)
        var closed = false
        org.mockito.Mockito.`when`(old.awaitClosed(org.mockito.ArgumentMatchers.anyLong())).thenAnswer { closed }
        val retry = View(activity).apply { visibility = View.GONE }
        setField("startupRetryButton", retry)
        WiredTeardownGuard.hold(old)
        try {
            invoke("maybeStartCarPlay")
            assertNull(getField("controller"))
            assertTrue(getField("startupRetryStopped") as Boolean)
            assertEquals(View.VISIBLE, retry.visibility)

            closed = true
            invoke("maybeStartCarPlay")

            assertNotNull(getField("controller"))
            assertFalse("the new session's end must reconnect", getField("startupRetryStopped") as Boolean)
            assertEquals(View.GONE, retry.visibility)
        } finally {
            closed = true
            WiredTeardownGuard.pendingControllerAttempts()
        }
    }

    // Disconnect clears the owner, so the screen a driver opens next is not the owner until its first start.
    @Test @Config(sdk = [30]) fun retryStartsAWiredConnectionThatWasBlockedAfterADisconnect() {
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        invoke("buildContentView")
        allowStartup()
        startSession()
        CarPlayBackgroundSession.clear()
        val old = org.mockito.Mockito.mock(CarPlayController::class.java)
        var closed = false
        org.mockito.Mockito.`when`(old.awaitClosed(org.mockito.ArgumentMatchers.anyLong())).thenAnswer { closed }
        WiredTeardownGuard.hold(old)
        try {
            invoke("maybeStartCarPlay")
            val retry = getField("startupRetryButton") as View
            assertEquals(View.VISIBLE, retry.visibility)

            retry.performClick()
            assertNull("the old owner still blocks a new controller", getField("controller"))
            assertEquals("the button must come back", View.VISIBLE, retry.visibility)

            closed = true
            retry.performClick()
            assertNotNull("retry must start the connection", getField("controller"))
            assertEquals(View.GONE, retry.visibility)
            assertTrue(CarPlayBackgroundSession.isOwner(activity))
        } finally {
            closed = true
            WiredTeardownGuard.pendingControllerAttempts()
        }
    }

    @Test @Config(sdk = [30]) fun wiredRestartContinuesAfterTheSinkReleaseWaitTimesOut() {
        allowStartup()
        startSession()
        val old = org.mockito.Mockito.mock(CarPlayController::class.java)
        val oldSink = org.mockito.Mockito.mock(AndroidMediaSink::class.java)
        org.mockito.Mockito.`when`(old.isWiredTransport).thenReturn(true)
        org.mockito.Mockito.`when`(old.awaitClosed(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true)
        setField("controller", old)
        setField("sink", oldSink)
        setField("sinkReleaseWaitMillis", 50L)

        activity.javaClass.getDeclaredMethod("restartCarPlay", String::class.java)
            .apply { isAccessible = true }.invoke(activity, "Test reconnect")
        finishTeardown()

        org.mockito.Mockito.verify(oldSink).close()
        org.mockito.Mockito.verify(oldSink).awaitVideoReleased(50L)
        assertNotNull("decoder release must not gate USB ownership", getField("controller"))
    }

    @Test @Config(sdk = [30]) fun wirelessRestartClosesTheControllerOnTheTeardownExecutor() {
        allowStartup()
        startSession()
        val old = org.mockito.Mockito.mock(CarPlayController::class.java)
        setField("controller", old)
        val closeThreads = java.util.concurrent.LinkedBlockingQueue<Thread>()
        val waitThreads = java.util.concurrent.LinkedBlockingQueue<Thread>()
        org.mockito.Mockito.doAnswer { closeThreads.add(Thread.currentThread()); null }.`when`(old).close()
        org.mockito.Mockito.`when`(old.awaitClosed(4_000L)).thenAnswer {
            waitThreads.add(Thread.currentThread())
            true
        }

        activity.javaClass.getDeclaredMethod("restartCarPlay", String::class.java)
            .apply { isAccessible = true }.invoke(activity, "Test reconnect")
        finishTeardown()

        val closeThread = closeThreads.poll(2, TimeUnit.SECONDS)
        assertNotNull(closeThread)
        assertSame(closeThread, waitThreads.poll(2, TimeUnit.SECONDS))
    }

    @Test @Config(sdk = [30]) fun exitAfterWiredSessionStopsVpnAndEndsProcessEvenIfTeardownTimesOut() {
        allowStartup()
        startSession()
        val old = org.mockito.Mockito.mock(CarPlayController::class.java)
        val oldSink = org.mockito.Mockito.mock(AndroidMediaSink::class.java)
        org.mockito.Mockito.`when`(old.isWiredTransport).thenReturn(true)
        setField("controller", old)
        setField("sink", oldSink)
        setField("sinkReleaseWaitMillis", 50L)
        ShadowProcess.clearKilledProcesses()
        try {
            invoke("exitApplication")
            finishTeardown()

            org.mockito.Mockito.verify(oldSink).close()
            org.mockito.Mockito.verify(oldSink).awaitVideoReleased(50L)
            val stoppedServices = generateSequence { shadowOf(activity.application).nextStoppedService }
            assertTrue(stoppedServices.any { it.component?.className ==
                com.shilapi.xcertplay.network.CarPlayVpnService::class.java.name })
            assertTrue(ShadowProcess.wasKilled(Process.myPid()))
        } finally {
            org.mockito.Mockito.`when`(old.awaitClosed(0)).thenReturn(true)
            WiredTeardownGuard.pendingControllerAttempts()
            ShadowProcess.clearKilledProcesses()
        }
    }

    @Test @Config(sdk = [30]) fun teardownRechecksStartupPrerequisites() {
        allowStartup()
        startSession()
        applySize(990, 1920)
        setField("vpnReady", false)

        finishTeardown()

        assertNull(getField("controller"))
        assertNull(getField("sessionDisplay"))
        assertFalse(getField("handshakeResetInProgress") as Boolean)
    }

    @Test fun initialSizeDetectionKeepsTheNormalStartupPath() {
        setField("activeDisplaySize", null)
        applySize(1920, 990)
        assertEquals(size(1920, 990), getField("activeDisplaySize"))
        assertEquals(0, getField("restartGeneration"))
        assertEquals(0, keepLogs())
    }

    @Test fun resizeWithoutASessionRecordsTheSizeWithoutAnotherTeardown() {
        applySize(700, 990)
        assertEquals(size(700, 990), getField("activeDisplaySize"))
        assertEquals(0, getField("restartGeneration"))
        assertFalse(getField("handshakeResetInProgress") as Boolean)
    }

    @Test fun textureTransformFitsTheNegotiatedCanvas() {
        startSession()
        val view = TextureView(activity).apply { layout(0, 0, 1920, 942) }
        setField("videoView", view)
        activity.javaClass.getDeclaredMethod("updateVideoLayout", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, 1920, 942)
        val points = floatArrayOf(0f, 0f, 1920f, 942f)
        view.getTransform(Matrix()).mapPoints(points)
        val content = CarPlayVideoLayout.fit(1920, 990, 1920, 942)
        assertArrayEquals(floatArrayOf(content.left, content.top, content.left + content.width,
            content.top + content.height), points, 0.001f)
    }

    @Test fun touchesStartingInABarStaySuppressedUntilRelease() {
        startSession()
        val view = View(activity).apply { layout(0, 0, 1920, 942) }
        touch(view, MotionEvent.ACTION_DOWN, 1f, 471f)
        assertEquals(true, getField("touchOutsideContent"))
        touch(view, MotionEvent.ACTION_MOVE, 960f, 471f)
        assertEquals(true, getField("touchOutsideContent"))
        touch(view, MotionEvent.ACTION_UP, 960f, 471f)
        assertEquals(false, getField("touchOutsideContent"))
        touch(view, MotionEvent.ACTION_DOWN, 960f, 471f)
        assertEquals(false, getField("touchOutsideContent"))
    }

    // Galaxy Note20: after a language change, or Back and then Open CarPlay, the quick menu's connection
    // page said only "Wireless hotspot: active". The new host never saw the session's HotspotReady.
    @Test fun aHostThatAdoptsTheSessionStillNamesItsHotspot() {
        val display = CarPlaySessionDisplay(1920, 990, Surface.ROTATION_0, true, true, 1920, 990)
        val sink = AndroidMediaSink()
        val controller = CarPlayController(activity,
            CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL, identification = Iap2IdentificationConfig(
                name = "test", modelIdentifier = "test", manufacturer = "test", serialNumber = "test",
                firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3)),
            AirPlayConfig(deviceName = "test", deviceId = "02:00:00:00:00:02", btMac = "02:00:00:00:00:01",
                sourceVersion = "1", main = AirPlayDisplayConfig(widthPixels = 1920, heightPixels = 990)),
            AirPlayIdentity.generate(), PairingStore(), object : AirPlaySessionListener {},
            object : AirPlayMediaHandler {}, {})
        try {
            CarPlayBackgroundSession.store(controller, sink, 1920, 990, Any(), display) {}
            val starter = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
            starter.javaClass.getDeclaredField("wirelessEnabled").apply { isAccessible = true }.set(starter, true)
            starter.javaClass.getDeclaredMethod("updateHotspotStatus", CarPlayStatus::class.java)
                .apply { isAccessible = true }
                .invoke(starter, CarPlayStatus.HotspotReady("DIRECT-dp-test", "5 GHz", 36, "", "192.168.49.1", "Wi-Fi P2P"))
            val adopted = activity.javaClass.getDeclaredMethod("adoptBackgroundSession")
                .apply { isAccessible = true }.invoke(activity)
            assertEquals(true, adopted)
            val status = getField("hotspotStatus").toString()
            assertTrue(status, status.contains("ssid=DIRECT-dp-test") && status.contains("band=5 GHz") &&
                status.contains("channel=36") && status.contains("backend=Wi-Fi P2P"))
        } finally {
            controller.close()
            CarPlayBackgroundSession.clear()
        }
        assertNull(CarPlayBackgroundSession.hotspotReady)
    }

    @Test fun adoptingABackgroundSessionPreservesItsCanvasOnResize() {
        val display = CarPlaySessionDisplay(1536, 792, Surface.ROTATION_0, true, true, 1920, 990)
        val sink = AndroidMediaSink()
        val controller = CarPlayController(activity,
            CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL, identification = Iap2IdentificationConfig(
                name = "test", modelIdentifier = "test", manufacturer = "test", serialNumber = "test",
                firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3)),
            AirPlayConfig(deviceName = "test", deviceId = "02:00:00:00:00:02", btMac = "02:00:00:00:00:01",
                sourceVersion = "1", main = AirPlayDisplayConfig(widthPixels = 1536, heightPixels = 792)),
            AirPlayIdentity.generate(), PairingStore(), object : AirPlaySessionListener {},
            object : AirPlayMediaHandler {}, {})
        try {
            CarPlayBackgroundSession.store(controller, sink, 1920, 990, Any(), display) {}
            val adopted = activity.javaClass.getDeclaredMethod("adoptBackgroundSession")
                .apply { isAccessible = true }.invoke(activity)
            assertEquals(true, adopted)
            applySize(700, 990)
            assertSame(controller, getField("controller"))
            assertSame(sink, getField("sink"))
            assertEquals(display, getField("sessionDisplay"))
            assertEquals(display, CarPlayBackgroundSession.snapshot()?.display)
            assertFalse(controller.isClosed())
            assertEquals(0, getField("restartGeneration"))
            applySize(1920, 990)
            assertSame(display, getField("sessionDisplay"))
            assertEquals(0, getField("restartGeneration"))
            applySize(2000, 990)
            assertEquals(1, getField("restartGeneration"))
            assertNull(getField("sessionDisplay"))
        } finally {
            controller.close()
            controller.awaitClosed(1000)
            sink.close()
        }
    }

    @Test fun adoptingBackgroundClusterRestoresNativeAdbFlagButRejectsTheVirtualFallback() {
        installBydCarSettings(activity)
        AirPlayPersistence.saveAdbClusterEnabled(activity, true)
        val sink = AndroidMediaSink()
        val display = CarPlaySessionDisplay(1920, 990, Surface.ROTATION_0, true, true, 1920, 990)
        try {
            for ((clusterSize, native) in listOf((1920 to 720) to true, (1280 to 720) to false, null to false)) {
                val controller = org.mockito.Mockito.mock(CarPlayController::class.java)
                org.mockito.Mockito.`when`(controller.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
                org.mockito.Mockito.`when`(controller.configuredClusterSize()).thenReturn(clusterSize)
                CarPlayBackgroundSession.store(controller, sink, 1920, 990, Any(), display) {}
                assertEquals(true, invoke("adoptBackgroundSession"))
                assertEquals("Adopted $clusterSize must keep the native/virtual distinction", native,
                    getField("adbClusterConfigured"))
            }
        } finally {
            AirPlayPersistence.saveAdbClusterEnabled(activity, false)
            sink.close()
        }
    }

    @Test fun anAdoptedSessionStillGetsDayAndNight() {
        // A recreated host (for example after a change of the app language) adopts the running stream and
        // gets no session-active callback; day/night must still reach the iPhone.
        val session = org.mockito.Mockito.mock(AirPlaySession::class.java)
        val controller = org.mockito.Mockito.mock(CarPlayController::class.java)
        org.mockito.Mockito.`when`(controller.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
        org.mockito.Mockito.`when`(controller.activeAirPlaySession()).thenReturn(session)
        val sink = AndroidMediaSink()
        try {
            CarPlayBackgroundSession.store(controller, sink, 1920, 990, Any(),
                CarPlaySessionDisplay(1920, 990, Surface.ROTATION_0, true, true, 1920, 990)) {}
            assertEquals(true, invoke("adoptBackgroundSession"))
            assertSame(session, getField("activeAirPlaySession"))
            setField("darkMode", true)
            activity.javaClass.getDeclaredMethod("syncAirPlayDarkMode", ThemeModeDiagnostics.Source::class.java)
                .apply { isAccessible = true }.invoke(activity, ThemeModeDiagnostics.Source.CARPLAY_MODE)
            org.mockito.Mockito.verify(session, org.mockito.Mockito.timeout(2_000)).setNightMode(true)
        } finally {
            sink.close()
        }
    }

    private fun startSession(
        rotation: Int = Surface.ROTATION_0,
        windowWidth: Int = 1920,
        windowHeight: Int = 990,
        canvasWidth: Int = windowWidth,
        canvasHeight: Int = windowHeight,
    ): CarPlaySessionDisplay =
        CarPlaySessionDisplay(canvasWidth, canvasHeight, rotation, true, true, windowWidth, windowHeight).also {
            setField("activeDisplaySize", size(windowWidth, windowHeight))
            setField("sessionDisplay", it)
        }

    private fun keepLogs(): Int = ShadowLog.getLogsForTag("xcertplay-usb").count {
        it.msg.contains("keeping CarPlay session")
    }

    private fun restartLog(): String = ShadowLog.getLogsForTag("xcertplay-usb")
        .filter { it.msg.contains("rebuilding stack") }.joinToString("\n") { it.msg }

    // The test window is not attached, so the post after a layout runs on the main queue, and the view is on
    // [display] only when a test says so.
    private fun hostVideoView(width: Int, height: Int, display: android.view.Display? = null): TextureView =
        object : TextureView(activity) {
            override fun post(action: Runnable): Boolean = Handler(Looper.getMainLooper()).post(action)
            override fun getDisplay(): android.view.Display? = display
        }.apply { layout(0, 0, width, height) }.also { setField("videoView", it) }

    private fun displayManager(): android.hardware.display.DisplayManager =
        activity.getSystemService(android.hardware.display.DisplayManager::class.java)

    private fun allowStartup() {
        setField("airPlayIdentity", AirPlayIdentity.generate())
        setField("mfiTarget", MfiTarget.LOCAL)
        setField("vpnReady", true)
        setField("microphonePermissionResolved", true)
    }

    private fun finishTeardown() {
        (getField("teardownExecutor") as PausedExecutorService).runAll()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertSessionSize(width: Int, height: Int) {
        val display = getField("sessionDisplay") as? CarPlaySessionDisplay
        assertNotNull("CarPlay must resume after the display settles", display)
        assertEquals(width, display!!.windowWidth)
        assertEquals(height, display.windowHeight)
        assertEquals(width, display.width)
        assertEquals(height, display.height)
    }

    private fun size(width: Int, height: Int): Any = sizeClass
        .getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        .apply { isAccessible = true }.newInstance(width, height)

    private fun applySize(width: Int, height: Int) {
        activity.javaClass.getDeclaredMethod("applyDisplaySize", sizeClass)
            .apply { isAccessible = true }.invoke(activity, size(width, height))
    }

    private fun scheduleSize(width: Int, height: Int) {
        activity.javaClass.getDeclaredMethod("scheduleDisplaySize", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, width, height)
    }

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 0, action, x, y, 0)
        try {
            activity.javaClass.getDeclaredMethod("onHostTouch", View::class.java, MotionEvent::class.java)
                .apply { isAccessible = true }.invoke(activity, view, event)
        } finally {
            event.recycle()
        }
    }

    private fun getField(name: String): Any? = activity.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)

    private fun invoke(name: String): Any? = activity.javaClass.getDeclaredMethod(name)
        .apply { isAccessible = true }.invoke(activity)

    @Suppress("DEPRECATION")
    private fun assertWindowBars(hideTop: Boolean, hideBottom: Boolean) {
        assertEquals(hideTop, activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
        assertEquals(hideBottom, activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
    }

    private fun menuBarSwitch(controls: ViewGroup, label: Int): Switch = (0 until controls.childCount)
        .mapNotNull { controls.getChildAt(it) as? ViewGroup }
        .flatMap { row -> (0 until row.childCount).map { row.getChildAt(it) } }
        .filterIsInstance<Switch>().single { it.contentDescription == activity.getString(label) }

    private fun setField(name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}
