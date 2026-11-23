package com.shilapi.xcertplay.media

/**
 * Jitter buffer for streams mapped to the media channel. Wireless CarPlay delivers audio over the same Wi-Fi link as
 * video; radio gaps of several hundred milliseconds are normal, so music needs a buffer that
 * outlasts them. Siri and navigation prompts keep the small low-latency buffer; calls keep it over USB
 * and start a little later over Wi-Fi ([WIRELESS_CALL_MILLIS]).
 * Wireless and wired USB keep separate settings. Wireless offers the classic presets that outlast Wi-Fi gaps.
 * Wired USB has no radio gaps, so it also offers short presets that let music start and pause sooner.
 * The device minimum AudioTrack size can still make the real buffer longer than a short preset.
 */
object MediaAudioBuffer {
    const val DEFAULT_MILLIS = 300
    const val MOST_STABLE_MILLIS = 1000
    val wirelessPresets = listOf(DEFAULT_MILLIS, 500, MOST_STABLE_MILLIS)
    val wiredPresets = listOf(50, 100, 150, 200) + (DEFAULT_MILLIS..MOST_STABLE_MILLIS step 100)
    /** Every delay a session can use; [plan] accepts any of them. */
    val presets = (wiredPresets + wirelessPresets).distinct().sorted()

    private const val HEADROOM_MILLIS = 200 // room above the start level so bursts after a gap fit
    private const val MIN_TRACK_BUFFER_BYTES = 16 * 1024
    private const val MIN_START_BUFFER_BYTES = 4 * 1024

    fun sanitize(millis: Int): Int = millis.takeIf { it in presets } ?: DEFAULT_MILLIS
    fun sanitizeWireless(millis: Int): Int = millis.takeIf { it in wirelessPresets } ?: DEFAULT_MILLIS
    fun sanitizeWired(millis: Int): Int = millis.takeIf { it in wiredPresets } ?: DEFAULT_MILLIS

    data class Plan(val trackBufferBytes: Int, val startBytes: Int)

    /**
     * Over Wi-Fi the iPhone sent call audio in bursts about 60 ms apart (Geely Monjaro, ECARX KX11, car hotspot),
     * and the low-latency start level (about 40 ms) ran dry after each burst: WhatsApp calls broke up.
     * Only the first fill gets it: calls are not rebuffered after a longer gap, as music is.
     * Siri and prompts are left out: a sound shorter than the start level would wait for the tail timeout.
     */
    const val WIRELESS_CALL_MILLIS = 120

    /** The call start level for a session: [WIRELESS_CALL_MILLIS] over Wi-Fi, the low-latency level over USB. */
    fun callMillis(wireless: Boolean): Int = if (wireless) WIRELESS_CALL_MILLIS else 0

    /**
     * AudioTrack capacity and the amount to queue before play() for one output stream. [callMillis] above
     * zero, passed for a call only, queues at least that much before it starts, with the same headroom.
     */
    fun plan(isMedia: Boolean, sampleRate: Int, channels: Int, minBufferBytes: Int, mediaMillis: Int,
        callMillis: Int = 0): Plan {
        val lowLatency = Plan(
            trackBufferBytes = maxOf(minBufferBytes * 4, MIN_TRACK_BUFFER_BYTES),
            startBytes = maxOf(minBufferBytes, MIN_START_BUFFER_BYTES),
        )
        val bytesPerSecond = sampleRate.toLong() * channels.coerceIn(1, 2) * 2
        if (!isMedia) {
            if (callMillis <= 0) return lowLatency
            return Plan(
                trackBufferBytes = maxOf((bytesPerSecond * (callMillis + HEADROOM_MILLIS) / 1000).toInt(), lowLatency.trackBufferBytes),
                startBytes = maxOf((bytesPerSecond * callMillis / 1000).toInt(), lowLatency.startBytes),
            )
        }
        val start = (bytesPerSecond * sanitize(mediaMillis) / 1000).toInt()
        val capacity = (bytesPerSecond * (sanitize(mediaMillis) + HEADROOM_MILLIS) / 1000).toInt()
        return Plan(
            trackBufferBytes = maxOf(capacity, lowLatency.trackBufferBytes),
            startBytes = maxOf(start, lowLatency.startBytes),
        )
    }

    /**
     * The device may grant a smaller AudioTrack than requested. Writes block while the track is
     * paused and full, so the start level must stay below the real capacity or play() never runs.
     */
    fun startBytesFor(plannedStartBytes: Int, actualCapacityBytes: Int, writeChunkBytes: Int): Int {
        if (actualCapacityBytes <= 0) return plannedStartBytes
        return minOf(plannedStartBytes, actualCapacityBytes - writeChunkBytes).coerceAtLeast(writeChunkBytes)
    }
}
