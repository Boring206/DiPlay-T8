package com.shilapi.xcertplay

import android.view.KeyEvent
import java.util.Locale

/**
 * When the events of a key DiPlay leaves alone came, for the diagnostic report (#1190). BYD sends a wheel
 * long press as a key of its own. Whether that key comes at the long-press threshold or at the release, and
 * whether it repeats while held, decides whether a seek can follow the driver's finger.
 *
 * Times count from the key's downTime. A tap the car sends as an instant down/up pair reads
 * "released at +0 s", the same for every press, so the log still counts such taps as one line. Main thread
 * or one input thread; not synchronized.
 */
internal class KeyHoldTiming<K : Any> {
    private class Press(val downEventMillis: Long) {
        var repeats = 0
    }

    private val presses = LinkedHashMap<K, Press>()

    /** A first ACTION_DOWN of [key]. */
    fun pressed(key: K, event: KeyEvent) {
        presses.remove(key)
        presses[key] = Press(event.eventTime - event.downTime)
        // A car that never sends a release must not grow this without bound.
        while (presses.size > MAX_KEYS) presses.remove(presses.keys.first())
    }

    /** Any other event of [key]; at the release of a pressed key, the text the report adds to its press. */
    fun after(key: K, event: KeyEvent): String? {
        val press = presses[key] ?: return null
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                press.repeats++
                null
            }
            KeyEvent.ACTION_UP -> {
                presses.remove(key)
                describe(event.eventTime - event.downTime, press.downEventMillis, press.repeats)
            }
            else -> null
        }
    }

    companion object {
        private const val MAX_KEYS = 16

        /** [upMillis] and [downMillis] are the up and down events' times after the key's downTime. */
        internal fun describe(upMillis: Long, downMillis: Long, repeats: Int): String = buildString {
            append("; released at +").append(span(upMillis))
            if (downMillis >= AT_ONCE_MILLIS) append(", down event at +").append(span(downMillis))
            if (repeats > 0) append(", ").append(repeats).append(if (repeats == 1) " repeat" else " repeats")
        }

        // Coarse on purpose: a press's line must read the same for each tap, or the log loses its count.
        private fun span(millis: Long): String = when {
            millis < AT_ONCE_MILLIS -> "0 s"
            millis < 500 -> "<0.5 s"
            else -> String.format(Locale.US, "%.1f s", millis / 1000.0)
        }

        internal const val AT_ONCE_MILLIS = 50L
    }
}

/**
 * Holds a CarPlay seek key for a wheel long press (#1190). DiLink 3 sends the long press as one key of its
 * own, once per hold, and no repeats were seen, so DiPlay cannot tell when the driver lets go. The press is
 * held on the iPhone for [HOLD_MILLIS] and then released.
 *
 * It is released earlier when the key's own release comes at least [MIN_RELEASE_MILLIS] after its press:
 * BYD sends a tap as an instant down/up pair, and that release is not the driver letting go. A repeat of the
 * held key restarts the timer, so a car that repeats a held key seeks until its release. Main thread.
 */
internal class MediaSeekHold(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val note: (String) -> Unit,
) {
    private class Held(val index: Int, val source: String, val downEventTime: Long, val release: () -> Boolean)

    private var held: Held? = null
    private val timeout = Runnable { finish("after $HOLD_MILLIS ms") }

    /**
     * The long-press key's first ACTION_DOWN at [eventTime]: [hold] presses [index] on the iPhone, [release]
     * lets go of it. A hold still running for another press ends first.
     */
    fun down(index: Int, source: String, eventTime: Long, hold: (Int) -> Boolean, release: () -> Boolean) {
        if (held != null) finish("for a new press")
        val sent = hold(index)
        note("media key $source -> CarPlay $index held sent=$sent")
        if (!sent) return
        held = Held(index, source, eventTime, release)
        restartTimer()
    }

    /** A repeat of the held key: the driver still holds it. */
    fun repeated(index: Int) {
        if (held?.index == index) restartTimer()
    }

    /** The long-press key's ACTION_UP at [eventTime]. */
    fun up(index: Int, source: String, eventTime: Long) {
        val current = held?.takeIf { it.index == index } ?: return
        val afterPress = eventTime - current.downEventTime
        if (afterPress < MIN_RELEASE_MILLIS) {
            note("media key $source released after $afterPress ms; CarPlay $index stays held for $HOLD_MILLIS ms")
            return
        }
        finish("at the key's release after $afterPress ms")
    }

    private fun restartTimer() {
        cancel(timeout)
        schedule(timeout, HOLD_MILLIS)
    }

    private fun finish(reason: String) {
        val current = held ?: return
        held = null
        cancel(timeout)
        note("media key ${current.source} -> CarPlay ${current.index} released $reason sent=${current.release()}")
    }

    companion object {
        const val HOLD_MILLIS = 2_500L
        const val MIN_RELEASE_MILLIS = 200L
    }
}
