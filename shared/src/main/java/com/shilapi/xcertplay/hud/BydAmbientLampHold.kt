package com.shilapi.xcertplay.hud

/**
 * Whether the lamp worker may write the next music value (#1332). Before each apply the worker reads the lamp;
 * a value DiPlay did not leave there means something else wrote the lamp, such as the car's blind-spot warning,
 * which turned a door lamp red on a 2023 Frigate 07. DiPlay then writes nothing until the lamp reads DiPlay's
 * last values again and no other value has been read for [HOLD_MILLIS]. It holds; it does not restore, so the
 * car keeps the colour it set.
 *
 * Nothing in DiPlay reads the blind-spot, lane-change or turn-indicator state, and it is not known whether the
 * car's warning shows in these lamp setting values at all. A change that never returns to DiPlay's values, such
 * as a colour picked in the car's own menu, keeps the writes paused until the lamp session ends.
 */
internal object BydAmbientLampHold {
    /** Spans several turn-signal flashes, so the dark phase of a flashing warning does not end the hold. */
    const val HOLD_MILLIS = 2_500L

    /** [expected] is the lamp as DiPlay last left it; [outsideAtMillis] the last read that differed, while holding. */
    data class State(val expected: BydAmbientLightPolicy.Snapshot, val outsideAtMillis: Long? = null) {
        val holding: Boolean get() = outsideAtMillis != null
    }

    data class Decision(val state: State, val write: Boolean)

    /** [current] is the lamp read just before the write; [nowMillis] a monotonic clock. */
    fun beforeApply(state: State, current: BydAmbientLightPolicy.Snapshot, nowMillis: Long): Decision {
        val outsideAt = state.outsideAtMillis
        return when {
            current != state.expected -> Decision(state.copy(outsideAtMillis = nowMillis), write = false)
            outsideAt == null -> Decision(state, write = true)
            nowMillis - outsideAt < HOLD_MILLIS -> Decision(state, write = false)
            else -> Decision(state.copy(outsideAtMillis = null), write = true)
        }
    }

    /** The lamp as read back after DiPlay's own write becomes the value the next read is compared with. */
    fun written(readback: BydAmbientLightPolicy.Snapshot): State = State(readback)
}
