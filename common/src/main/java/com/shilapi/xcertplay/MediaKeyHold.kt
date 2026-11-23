package com.shilapi.xcertplay

import android.os.SystemClock
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.orchestration.CarPlayController
import java.util.Locale

/**
 * When the events of a key DiPlay leaves alone came, for the diagnostic report (#1190). BYD sends a wheel
 * long press as a key of its own. Whether that key comes at the long-press threshold or at the release, and
 * whether it repeats while held, decides whether a seek can follow the driver's finger.
 *
 * The release reads twice: from the key's downTime, as the event is stamped, and from when its press
 * arrived here ([now], uptime). A key that BYD builds itself may carry no downTime, or stamp both events
 * when it sends them; the arrival gap still says when the release came. A tap the car sends as an instant
 * down/up pair reads "released at +0 s, arrived +0 s after the press", the same for every press, so the log
 * still counts such taps as one line. Main thread or one input thread; not synchronized.
 */
internal class KeyHoldTiming<K : Any>(private val now: () -> Long = SystemClock::uptimeMillis) {
    /** [downEventMillis] is null for a press with no downTime. */
    private class Press(val downEventMillis: Long?, val arrivedMillis: Long) {
        var repeats = 0
    }

    private val presses = LinkedHashMap<K, Press>()

    /** A first ACTION_DOWN of [key]. */
    fun pressed(key: K, event: KeyEvent) {
        presses.remove(key)
        presses[key] = Press(sinceDownTime(event), now())
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
                describe(sinceDownTime(event), press.downEventMillis, now() - press.arrivedMillis, press.repeats)
            }
            else -> null
        }
    }

    companion object {
        private const val MAX_KEYS = 16

        private fun sinceDownTime(event: KeyEvent): Long? =
            if (event.downTime <= 0) null else event.eventTime - event.downTime

        /**
         * [upMillis] and [downMillis] are the up and down events' times after the key's downTime, null when it
         * had none; [arrivedMillis] is how long after the press the release arrived.
         */
        internal fun describe(upMillis: Long?, downMillis: Long?, arrivedMillis: Long, repeats: Int): String = buildString {
            if (upMillis == null) append("; released, no downTime") else append("; released at +").append(span(upMillis))
            append(", arrived +").append(span(arrivedMillis)).append(" after the press")
            if (downMillis != null && downMillis >= AT_ONCE_MILLIS) append(", down event at +").append(span(downMillis))
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
 * own; 0.2.22 logged neither its repeats nor its release, so when the driver lets go is not known. The press
 * is held on the iPhone for [HOLD_MILLIS] and then released.
 *
 * It is released earlier when the key's own release arrives at least [MIN_RELEASE_MILLIS] after its press
 * arrived ([now], uptime): a long press sent as an instant down/up pair is not the driver letting go. The
 * gap is measured on arrival, not from the events' stamps, because BYD likely builds these events itself and
 * may leave the stamps at 0 or date the press from its start. A repeat of the held key restarts the timer,
 * so a car that repeats a held key seeks until its release. Main thread.
 */
internal class MediaSeekHold(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val note: (String) -> Unit,
    private val now: () -> Long = SystemClock::uptimeMillis,
) {
    private class Held(val index: Int, val source: String, val arrivedMillis: Long, val release: () -> Boolean)

    private var held: Held? = null
    private val timeout = Runnable { finish("after $HOLD_MILLIS ms") }

    /**
     * A long-press key's event with the long-press seek setting on: [index] is
     * [CarPlayMediaButton.FAST_FORWARD] or [CarPlayMediaButton.REWIND], [skip] the tap that the car's video
     * player gets instead ([carVideo] true when it took it), and [controller] the CarPlay connection.
     */
    fun onKey(index: Int, source: String, event: KeyEvent, carVideo: (Int) -> Boolean, controller: () -> CarPlayController?) {
        val skip = if (index == CarPlayMediaButton.FAST_FORWARD) CarPlayMediaButton.NEXT else CarPlayMediaButton.PREVIOUS
        when {
            event.action == KeyEvent.ACTION_UP -> up(index, source)
            event.action != KeyEvent.ACTION_DOWN -> Unit
            event.repeatCount > 0 -> repeated(index)
            // The car's video player skips for a long press as for next and previous; the iPhone is not asked.
            carVideo(skip) -> note("media key $source -> car video player $skip")
            else -> {
                val active = controller()
                when {
                    active == null -> note("media key $source not sent: no CarPlay session")
                    // The iPhone was told of the seek keys when this connection started.
                    !active.mediaSeekKeysDeclared() ->
                        note("media key $source not sent: long-press seek starts at the next connection")
                    else -> down(index, source, active::holdMediaButton, active::releaseMediaButton)
                }
            }
        }
    }

    /**
     * The long-press key's first ACTION_DOWN: [hold] presses [index] on the iPhone, [release] lets go of it.
     * A hold still running for another press ends first.
     */
    fun down(index: Int, source: String, hold: (Int) -> Boolean, release: () -> Boolean) {
        if (held != null) finish("for a new press")
        val sent = hold(index)
        note("media key $source -> CarPlay $index held sent=$sent")
        if (!sent) return
        held = Held(index, source, now(), release)
        restartTimer()
    }

    /** A repeat of the held key: the driver still holds it. */
    fun repeated(index: Int) {
        if (held?.index == index) restartTimer()
    }

    /** The long-press key's ACTION_UP. */
    fun up(index: Int, source: String) {
        val current = held?.takeIf { it.index == index } ?: return
        val afterPress = now() - current.arrivedMillis
        if (afterPress < MIN_RELEASE_MILLIS) {
            note("media key $source released $afterPress ms after its press arrived; " +
                "CarPlay $index stays held for $HOLD_MILLIS ms")
            return
        }
        finish("at the key's release, $afterPress ms after its press arrived")
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
