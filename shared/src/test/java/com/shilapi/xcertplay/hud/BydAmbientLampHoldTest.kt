package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.hud.BydAmbientLampHold.HOLD_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #1332: the car's blind-spot warning turned a door lamp red and DiPlay's music colours overwrote it. */
class BydAmbientLampHoldTest {
    private val music = BydAmbientLightPolicy.Snapshot(15, 15, 6, 6, 3)
    private val nextMusic = BydAmbientLightPolicy.Snapshot(22, 22, 4, 4, 3)
    // Colour code 1 stands for any value DiPlay did not set; the warning's real codes are not known.
    private val warning = music.copy(frontColor = 1)
    private val dark = music.copy(frontBrightness = 1)

    private fun baseline(expected: BydAmbientLightPolicy.Snapshot) = BydAmbientLampHold.State(expected)

    @Test fun lampAsDiPlayLeftItIsWritten() {
        val decision = BydAmbientLampHold.beforeApply(baseline(music), music, 1_000)
        assertTrue(decision.write)
        assertFalse(decision.state.holding)
    }

    @Test fun lampChangedOutsideDiPlayIsHeldNotRestored() {
        val decision = BydAmbientLampHold.beforeApply(baseline(music), warning, 1_000)
        assertFalse(decision.write)
        assertEquals(1_000L, decision.state.outsideAtMillis)
        assertEquals(music, decision.state.expected) // Compared with DiPlay's value, not adopted.
    }

    @Test fun valuesOutsideDiPlaysRangeAreHeldToo() {
        val off = BydAmbientLightPolicy.Snapshot(0, 15, 0, 6, 3)
        assertFalse(BydAmbientLightPolicy.validSnapshot(off))
        val (state, reply) = BydAmbientLightWorkerProtocol.applyOrHold(baseline(music), 0, { off }) {
            error("must not write")
        }
        assertTrue(state.holding)
        assertEquals("held=0,15,0,6,3", reply)
    }

    @Test fun steadyWarningHoldsForAsLongAsItLasts() {
        var state = baseline(music)
        for (now in 0L..60_000L step 200) {
            val decision = BydAmbientLampHold.beforeApply(state, warning, now)
            assertFalse("t=$now", decision.write)
            state = decision.state
        }
    }

    @Test fun flashingWarningKeepsHoldingThroughItsPhasesThatMatchDiPlay() {
        // Turn signal on: red for 350 ms, then DiPlay's own colour or dark for 350 ms, read every 200 ms.
        var state = baseline(music)
        var lastOutside = 0L
        for (now in 0L..6_000L step 200) {
            val phase = (now / 350) % 3
            val lamp = when (phase) { 0L -> warning; 1L -> music; else -> dark }
            val decision = BydAmbientLampHold.beforeApply(state, lamp, now)
            assertFalse("t=$now", decision.write)
            if (lamp != music) lastOutside = now
            state = decision.state
        }
        // The warning clears and the car returns the lamp to DiPlay's last value.
        val justBefore = BydAmbientLampHold.beforeApply(state, music, lastOutside + HOLD_MILLIS - 1)
        assertFalse(justBefore.write)
        val after = BydAmbientLampHold.beforeApply(justBefore.state, music, lastOutside + HOLD_MILLIS)
        assertTrue(after.write)
        assertNull(after.state.outsideAtMillis)
    }

    @Test fun holdOffRestartsWhenTheWarningReturnsBeforeItEnds() {
        var state = BydAmbientLampHold.beforeApply(baseline(music), warning, 0).state
        state = BydAmbientLampHold.beforeApply(state, music, 2_000).state
        state = BydAmbientLampHold.beforeApply(state, warning, 2_400).state
        assertFalse(BydAmbientLampHold.beforeApply(state, music, 2_400 + HOLD_MILLIS - 1).write)
        assertTrue(BydAmbientLampHold.beforeApply(state, music, 2_400 + HOLD_MILLIS).write)
    }

    @Test fun lampLeftAtAnotherValueStaysHeldUntilTheSessionEnds() {
        // A colour picked in the car's menu never returns to DiPlay's value; DiPlay does not take it back.
        var state = BydAmbientLampHold.beforeApply(baseline(music), nextMusic, 0).state
        for (now in listOf(HOLD_MILLIS, 30_000L, 600_000L)) {
            val decision = BydAmbientLampHold.beforeApply(state, nextMusic, now)
            assertFalse(decision.write)
            state = decision.state
        }
    }

    @Test fun diPlaysOwnReadbackBecomesTheValueToCompareWith() {
        val state = BydAmbientLampHold.written(nextMusic)
        assertTrue(BydAmbientLampHold.beforeApply(state, nextMusic, 0).write)
        assertFalse(BydAmbientLampHold.beforeApply(state, music, 0).write)
    }

    @Test fun workerReadsFirstAndNeverWritesWhileHeld() {
        var writes = 0
        val (held, heldReply) = BydAmbientLightWorkerProtocol.applyOrHold(baseline(music), 0, { warning }) {
            writes++; nextMusic
        }
        assertEquals(0, writes)
        assertEquals("held=1,15,6,6,3", heldReply)
        assertTrue(held.holding)

        val (stillHeld, _) = BydAmbientLightWorkerProtocol.applyOrHold(held, 1_000, { music }) { writes++; nextMusic }
        assertEquals(0, writes)
        val (written, reply) = BydAmbientLightWorkerProtocol.applyOrHold(stillHeld, HOLD_MILLIS, { music }) {
            writes++; nextMusic
        }
        assertEquals(1, writes)
        assertEquals("applied=22,22,4,4,3", reply)
        assertEquals(BydAmbientLampHold.State(nextMusic), written)
    }
}
