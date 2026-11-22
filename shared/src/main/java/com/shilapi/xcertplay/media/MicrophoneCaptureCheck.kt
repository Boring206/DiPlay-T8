package com.shilapi.xcertplay.media

import android.media.AudioManager

/**
 * Judges once, about two seconds after a call's recorder first delivers data, whether it records in real time and
 * with sound (#1172).
 *
 * On a 2023 Camry head unit (MTK ac8257, Android 9) a 48 kHz VOICE_COMMUNICATION recorder in communication mode
 * delivered about a third of real time, sometimes a sixth: about 16 000 or 8 000 samples a second, the vocoder's
 * rate. Its samples were often all zeros while another recording held the microphone. The iPhone then got about a
 * third of the call's Opus frames, so the far end heard nothing or broken speech.
 *
 * The first [warmUpNs] after the first data are not measured, so a recorder that opens slowly or hands over a
 * burst it buffered while starting is not judged on that. [Verdict.SILENT] needs every sample to be zero, and the
 * audio mode a call records in. Some working units also record exact zeros at full rate for the first seconds of a
 * call (#1279), so [MicrophoneUplink] acts on it only after a stream was already rebuilt for a bad check.
 */
internal class MicrophoneCaptureCheck(
    /** What a recorder that keeps up delivers: the stream's rate × channels × 2 bytes. */
    private val bytesPerSecond: Long,
    private val warmUpNs: Long = WARM_UP_NS,
    private val windowNs: Long = WINDOW_NS,
) {
    enum class Verdict(val label: String) { OK("ok"), SLOW("slow-capture"), SILENT("silent-capture") }

    /** [peak] is the loudest 16-bit sample since the first data; [audioMode] is null when it could not be read. */
    class Result(val verdict: Verdict, val realtimePercent: Int, val peak: Int, val audioMode: Int?)

    private var firstDataNs = NOT_STARTED
    private var measureStartNs = NOT_STARTED
    private var measuredBytes = 0L
    private var peak = 0
    private var judged = false

    /** Starts over for a restarted or new recorder. */
    fun reset() {
        firstDataNs = NOT_STARTED
        measureStartNs = NOT_STARTED
        measuredBytes = 0
        peak = 0
        judged = false
    }

    /**
     * Call after each read that returned [count] bytes of 16-bit little-endian PCM in [pcm], with [nowNs] taken
     * after the read. Returns the verdict once per [reset], and null before it and after it.
     */
    fun read(pcm: ByteArray, count: Int, nowNs: Long, audioMode: () -> Int?): Result? {
        if (judged || count <= 0) return null
        peak = maxOf(peak, peakOf(pcm, count))
        if (firstDataNs == NOT_STARTED) {
            firstDataNs = nowNs
            return null
        }
        if (measureStartNs == NOT_STARTED) {
            // A blocking read returns what was captured while it waited, so the bytes of the read that starts the
            // window belong before it.
            if (nowNs - firstDataNs >= warmUpNs) measureStartNs = nowNs
            return null
        }
        measuredBytes += count
        val elapsedNs = nowNs - measureStartNs
        if (elapsedNs < windowNs) return null
        judged = true
        val percent = Math.round(measuredBytes * 100.0 * NANOS_PER_SECOND / (bytesPerSecond.toDouble() * elapsedNs)).toInt()
        val mode = runCatching(audioMode).getOrNull()
        val verdict = when {
            percent < MIN_REALTIME_PERCENT -> Verdict.SLOW
            peak == 0 && mode == AudioManager.MODE_IN_COMMUNICATION -> Verdict.SILENT
            else -> Verdict.OK
        }
        return Result(verdict, percent, peak, mode)
    }

    private fun peakOf(pcm: ByteArray, count: Int): Int {
        var loudest = 0
        var index = 0
        val end = minOf(count, pcm.size)
        while (index + 1 < end) {
            val sample = (pcm[index].toInt() and 0xff) or (pcm[index + 1].toInt() shl 8)
            loudest = maxOf(loudest, kotlin.math.abs(sample))
            index += 2
        }
        return loudest
    }

    internal companion object {
        const val WARM_UP_NS = 500_000_000L
        const val WINDOW_NS = 1_500_000_000L
        /** A third of real time on #1172 is far below; a recorder that keeps up measures about 100. */
        const val MIN_REALTIME_PERCENT = 80
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val NOT_STARTED = Long.MIN_VALUE
    }
}
