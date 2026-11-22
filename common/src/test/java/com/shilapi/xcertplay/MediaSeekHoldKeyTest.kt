package com.shilapi.xcertplay

import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton.FAST_FORWARD
import com.shilapi.xcertplay.airplay.CarPlayMediaButton.NEXT
import com.shilapi.xcertplay.airplay.CarPlayMediaButton.PREVIOUS
import com.shilapi.xcertplay.airplay.CarPlayMediaButton.REWIND
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A long-press key's events as CarPlayMediaKeys hands them over (#1190): the release is timed by when the
 * events arrive, because BYD likely builds these events itself and their stamps may say nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class MediaSeekHoldKeyTest {
    private val notes = mutableListOf<String>()
    private var timer: Runnable? = null
    private var uptime = 50_000L
    private val hold = MediaSeekHold(
        schedule = { task, _ -> timer = task },
        cancel = { task -> if (timer === task) timer = null },
        note = { notes += it },
        now = { uptime },
    )
    private val carVideo = mutableListOf<Int>()
    private var videoPlayerTakes = false
    private val controller = mock(CarPlayController::class.java).also {
        `when`(it.mediaSeekKeysDeclared()).thenReturn(true)
        `when`(it.holdMediaButton(anyInt())).thenReturn(true)
        `when`(it.releaseMediaButton()).thenReturn(true)
    }
    private var active: CarPlayController? = controller

    /** [event] arrives at uptime [at]. */
    private fun arrive(at: Long, event: KeyEvent, index: Int = FAST_FORWARD) {
        uptime = at
        hold.onKey(index, "LP", event, { carVideo += it; videoPlayerTakes }) { active }
    }

    @Test fun anInstantPairWithNoStampsStaysHeldForTheFixedTime() {
        arrive(50_000, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, 401, 0))
        arrive(50_005, KeyEvent(0, 0, KeyEvent.ACTION_UP, 401, 0))
        verify(controller).holdMediaButton(FAST_FORWARD)
        verify(controller, never()).releaseMediaButton()
        assertEquals("media key LP released 5 ms after its press arrived; CarPlay $FAST_FORWARD stays held for 2500 ms",
            notes.last())
        timer!!.run()
        verify(controller).releaseMediaButton()
    }

    // The press dated from the driver's touch, the release from when it was sent, both sent at the threshold.
    @Test fun anInstantPairStampedSecondsApartStaysHeld() {
        arrive(50_000, KeyEvent(49_200, 49_200, KeyEvent.ACTION_DOWN, 401, 0))
        arrive(50_002, KeyEvent(49_200, 50_000, KeyEvent.ACTION_UP, 401, 0))
        verify(controller, never()).releaseMediaButton()
        assertEquals(true, timer != null)
    }

    @Test fun aReleaseThatArrivesThreeSecondsLaterEndsTheHoldThen() {
        arrive(50_000, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, 402, 0), REWIND)
        arrive(53_000, KeyEvent(0, 0, KeyEvent.ACTION_UP, 402, 0), REWIND)
        verify(controller).holdMediaButton(REWIND)
        verify(controller).releaseMediaButton()
        assertNull(timer)
        assertEquals("media key LP -> CarPlay $REWIND released at the key's release, 3000 ms after its press arrived sent=true",
            notes.last())
    }

    @Test fun withNoSessionNothingIsHeld() {
        active = null
        arrive(50_000, KeyEvent(KeyEvent.ACTION_DOWN, 401))
        assertEquals(listOf("media key LP not sent: no CarPlay session"), notes)
        assertNull(timer)
    }

    @Test fun aConnectionThatDidNotDeclareTheSeekKeysWaitsForTheNext() {
        `when`(controller.mediaSeekKeysDeclared()).thenReturn(false)
        arrive(50_000, KeyEvent(KeyEvent.ACTION_DOWN, 401))
        arrive(53_000, KeyEvent(KeyEvent.ACTION_UP, 401))
        verify(controller, never()).holdMediaButton(anyInt())
        verify(controller, never()).releaseMediaButton()
        assertEquals(listOf("media key LP not sent: long-press seek starts at the next connection"), notes)
        assertNull(timer)
    }

    @Test fun theCarsVideoPlayerSkipsInsteadOfTheIphoneSeeking() {
        videoPlayerTakes = true
        arrive(50_000, KeyEvent(KeyEvent.ACTION_DOWN, 401))
        arrive(50_000, KeyEvent(KeyEvent.ACTION_DOWN, 402), REWIND)
        assertEquals(listOf(NEXT, PREVIOUS), carVideo)
        verify(controller, never()).holdMediaButton(anyInt())
        assertEquals("media key LP -> car video player $NEXT", notes.first())
    }
}
