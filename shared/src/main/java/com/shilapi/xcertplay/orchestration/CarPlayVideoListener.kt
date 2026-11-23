package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.airplay.VideoInCar
import java.io.Closeable

/** iOS 27 video in car, played by the host in the car's own player (see [VideoInCar]). */
interface CarPlayVideoListener {
    /** Whether the car is in P, or null when it cannot tell. Blocking; read once a second while CarPlay runs. */
    fun readParked(): Boolean?

    /** Why the latest [readParked] returned null, for the report, such as "adb=NOT_APPROVED" (#1180). Same thread. */
    fun gearUnreadableReason(): String? = null

    /** The gear reading changed: true in P, false out of P, null unreadable. Gate thread. */
    fun onGearObserved(parked: Boolean?) = Unit

    /** Video became allowed or not; when not, the player must close. Any thread. */
    fun onVideoAllowedChanged(allowed: Boolean)

    /** A playback message from the iPhone, e.g. insertPlayQueueItem, setRate, seek, stop. Any thread. */
    fun onVideoMessage(streamId: Long, message: Map<String, Any?>)

    /** The iPhone asks the car to show its video player (requestUI "videoplayback:"). Any thread. */
    fun onVideoUiRequested()

    /** The CarPlay session ended; the player must close. Any thread. */
    fun onVideoSessionEnded()
}

/**
 * Keeps [VideoInCar.allowed] in step with the car: allowed only while the gear reads P, so an unknown
 * gear (no ADB) keeps video off. Changes go to [onChanged].
 */
internal class VideoInCarGate(
    private val readParked: () -> Boolean?,
    private val onChanged: (Boolean) -> Unit,
    private val onObserved: (parked: Boolean?, reason: String?) -> Unit = { _, _ -> },
    private val unreadableReason: () -> String? = { null },
) : Closeable {
    @Volatile private var closed = false
    private var observed = false
    private var lastParked: Boolean? = null
    private var lastReason: String? = null

    fun start() {
        Thread({
            while (!closed) {
                val reading = runCatching(readParked)
                val parked = reading.getOrNull()
                val reason = when {
                    parked != null -> null
                    reading.isFailure -> "read failed ${reading.exceptionOrNull()?.javaClass?.simpleName}"
                    else -> runCatching(unreadableReason).getOrNull()
                }
                update(parked, reason)
                try {
                    Thread.sleep(POLL_MILLIS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "diplay-video-gate").apply { isDaemon = true }.start()
    }

    /** [reason] says why [parked] is null; a new reason for an unreadable gear is a new observation. */
    internal fun update(parked: Boolean?, reason: String? = null) {
        if (!observed || parked != lastParked || reason != lastReason) {
            observed = true
            lastParked = parked
            lastReason = reason
            onObserved(parked, reason)
        }
        val allowed = parked == true
        if (allowed == VideoInCar.allowed || closed) return
        VideoInCar.allowed = allowed
        onChanged(allowed)
    }

    override fun close() {
        closed = true
        VideoInCar.allowed = false
    }

    private companion object {
        const val POLL_MILLIS = 1_000L
    }
}

/** Report lines for video in car. */
object VideoInCarDiagnostics {
    /** The gear the video gate read; an unreadable gear names why (#1180), since it keeps video off. */
    fun gearLine(parked: Boolean?, reason: String?): String = when (parked) {
        true -> "video in car gear=P"
        false -> "video in car gear=not-P"
        null -> "video in car gear=unknown ${reason ?: "reason=unknown"}; video stays off until the gear reads P"
    }
}
