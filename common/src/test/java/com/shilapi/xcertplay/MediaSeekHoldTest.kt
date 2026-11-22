package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.CarPlayMediaButton.FAST_FORWARD
import com.shilapi.xcertplay.airplay.CarPlayMediaButton.REWIND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** A wheel long press holds Fast Forward or Rewind on the iPhone for a fixed time, or until a real release (#1190). */
class MediaSeekHoldTest {
    private val iphone = mutableListOf<String>()
    private val notes = mutableListOf<String>()
    private var timer: Pair<Runnable, Long>? = null
    private var timersStarted = 0
    private var accept = true
    private var uptime = 0L
    private val hold = MediaSeekHold(
        schedule = { task, delayMillis ->
            timer = task to delayMillis
            timersStarted++
        },
        cancel = { task -> if (timer?.first === task) timer = null },
        note = { notes += it },
        now = { uptime },
    )

    /** The press arrives at uptime [at]. */
    private fun down(index: Int, at: Long) {
        uptime = at
        hold.down(index, "LP", { iphone += "hold $it"; accept }, { iphone += "release"; true })
    }

    /** The release arrives at uptime [at]. */
    private fun up(index: Int, at: Long) {
        uptime = at
        hold.up(index, "LP")
    }

    private fun runTimer() {
        val (task, _) = timer ?: error("no timer")
        timer = null
        task.run()
    }

    @Test fun aLongPressIsHeldForTheFixedTimeThenReleased() {
        down(FAST_FORWARD, at = 1_000)
        assertEquals(listOf("hold $FAST_FORWARD"), iphone)
        assertEquals(MediaSeekHold.HOLD_MILLIS, timer?.second)
        assertEquals(2_500L, MediaSeekHold.HOLD_MILLIS)

        runTimer()
        assertEquals(listOf("hold $FAST_FORWARD", "release"), iphone)
        assertEquals("media key LP -> CarPlay $FAST_FORWARD released after 2500 ms sent=true", notes.last())
    }

    @Test fun theInstantReleaseOfADownUpPairDoesNotCutTheHoldShort() {
        down(REWIND, at = 1_000)
        up(REWIND, at = 1_000 + MediaSeekHold.MIN_RELEASE_MILLIS - 1)
        assertEquals(listOf("hold $REWIND"), iphone)
        assertEquals(MediaSeekHold.HOLD_MILLIS, timer?.second)
        runTimer()
        assertEquals(listOf("hold $REWIND", "release"), iphone)
    }

    @Test fun aRealReleaseEndsTheHoldAtOnce() {
        down(FAST_FORWARD, at = 1_000)
        up(FAST_FORWARD, at = 1_000 + MediaSeekHold.MIN_RELEASE_MILLIS)
        assertEquals(listOf("hold $FAST_FORWARD", "release"), iphone)
        assertNull(timer)
        assertEquals("media key LP -> CarPlay $FAST_FORWARD released at the key's release, 200 ms after its press arrived sent=true", notes.last())
        // A second release, or one for the other key, sends nothing more.
        up(FAST_FORWARD, at = 5_000)
        up(REWIND, at = 5_000)
        assertEquals(2, iphone.size)
    }

    @Test fun theOtherKeysReleaseLeavesTheHoldAlone() {
        down(FAST_FORWARD, at = 1_000)
        up(REWIND, at = 3_000)
        assertEquals(listOf("hold $FAST_FORWARD"), iphone)
        runTimer()
        assertEquals(listOf("hold $FAST_FORWARD", "release"), iphone)
    }

    @Test fun repeatsOfTheHeldKeyKeepItHeld() {
        down(FAST_FORWARD, at = 1_000)
        val first = timer?.first
        hold.repeated(FAST_FORWARD)
        hold.repeated(FAST_FORWARD)
        assertEquals(3, timersStarted)
        assertSame(first, timer?.first)
        hold.repeated(REWIND)
        assertEquals(3, timersStarted)
        assertEquals(listOf("hold $FAST_FORWARD"), iphone)
    }

    @Test fun aNewPressLetsGoOfTheOldOneFirst() {
        down(FAST_FORWARD, at = 1_000)
        down(REWIND, at = 1_500)
        assertEquals(listOf("hold $FAST_FORWARD", "release", "hold $REWIND"), iphone)
        assertEquals(MediaSeekHold.HOLD_MILLIS, timer?.second)
        runTimer()
        assertEquals(listOf("hold $FAST_FORWARD", "release", "hold $REWIND", "release"), iphone)
    }

    @Test fun aPressTheSessionDidNotTakeIsNeitherTimedNorReleased() {
        accept = false
        down(FAST_FORWARD, at = 1_000)
        assertNull(timer)
        up(FAST_FORWARD, at = 4_000)
        hold.repeated(FAST_FORWARD)
        assertEquals(listOf("hold $FAST_FORWARD"), iphone)
        assertEquals(listOf("media key LP -> CarPlay $FAST_FORWARD held sent=false"), notes)
    }

    @Test fun releaseTimingReadsTheSameForEachTapAndShowsAHold() {
        // An instant down/up pair: the same text each time, so the log still counts taps as one line.
        assertEquals("; released at +0 s, arrived +0 s after the press",
            KeyHoldTiming.describe(upMillis = 0, downMillis = 0, arrivedMillis = 0, repeats = 0))
        assertEquals("; released at +0 s, arrived +0 s after the press",
            KeyHoldTiming.describe(upMillis = 49, downMillis = 3, arrivedMillis = 12, repeats = 0))
        assertEquals("; released at +<0.5 s, arrived +<0.5 s after the press",
            KeyHoldTiming.describe(upMillis = 180, downMillis = 0, arrivedMillis = 175, repeats = 0))
        // Held until the driver let go.
        assertEquals("; released at +4.2 s, arrived +4.2 s after the press, 3 repeats",
            KeyHoldTiming.describe(upMillis = 4_210, downMillis = 0, arrivedMillis = 4_200, repeats = 3))
        // Sent at the long-press threshold but dated from the press: both events late, nothing between.
        assertEquals("; released at +0.8 s, arrived +0 s after the press, down event at +0.8 s",
            KeyHoldTiming.describe(upMillis = 812, downMillis = 800, arrivedMillis = 3, repeats = 0))
        assertEquals("; released at +1.0 s, arrived +1.0 s after the press, 1 repeat",
            KeyHoldTiming.describe(upMillis = 1_000, downMillis = 0, arrivedMillis = 1_000, repeats = 1))
        // A key built with no downTime: its stamps say nothing, its arrival does.
        assertEquals("; released, no downTime, arrived +3.0 s after the press",
            KeyHoldTiming.describe(upMillis = null, downMillis = null, arrivedMillis = 3_000, repeats = 0))
    }
}
