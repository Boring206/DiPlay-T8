package com.shilapi.xcertplay.media

import android.media.AudioTimestamp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Raises interleaved 16-bit little-endian PCM by a whole [factor], such as 16 kHz to 48 kHz, through a
 * windowed-sinc low-pass filter, so the spectrum's images above the input's Nyquist frequency are not encoded.
 * One instance holds the filter history of one stream; it is not thread-safe.
 */
internal class PcmUpsampler(private val factor: Int, private val channels: Int, private val tapsPerPhase: Int = TAPS_PER_PHASE) {
    init {
        require(factor >= 1) { "factor $factor" }
        require(channels >= 1) { "channels $channels" }
        require(tapsPerPhase >= 1) { "tapsPerPhase $tapsPerPhase" }
    }

    /** phases[p][j] is tap j·factor + p of the prototype filter; each phase sums to 1, so DC passes unchanged. */
    private val phases: Array<FloatArray> = design(factor, tapsPerPhase)
    /** The last [tapsPerPhase] input samples of each channel, newest at [newest]. */
    private val history = Array(channels) { FloatArray(tapsPerPhase) }
    private var newest = 0

    /** Bytes of output for [inputBytes] bytes of input. */
    fun outputBytes(inputBytes: Int): Int = inputBytes / (channels * 2) * channels * 2 * factor

    /**
     * Upsamples the whole frames among [inputBytes] bytes of [input] from [inputOffset] into [output] from
     * [outputOffset], and returns the bytes written: [outputBytes] of [inputBytes].
     */
    fun process(input: ByteArray, inputOffset: Int, inputBytes: Int, output: ByteArray, outputOffset: Int): Int {
        val frameBytes = channels * 2
        val frames = inputBytes / frameBytes
        var out = outputOffset
        for (frame in 0 until frames) {
            newest = if (newest + 1 == tapsPerPhase) 0 else newest + 1
            for (channel in 0 until channels) {
                val at = inputOffset + frame * frameBytes + channel * 2
                history[channel][newest] = ((input[at].toInt() and 0xff) or (input[at + 1].toInt() shl 8)).toFloat()
            }
            for (phase in 0 until factor) {
                val taps = phases[phase]
                for (channel in 0 until channels) {
                    val samples = history[channel]
                    var sum = 0f
                    var index = newest
                    for (tap in 0 until tapsPerPhase) {
                        sum += samples[index] * taps[tap]
                        index = if (index == 0) tapsPerPhase - 1 else index - 1
                    }
                    val value = sum.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    output[out] = value.toByte()
                    output[out + 1] = (value shr 8).toByte()
                    out += 2
                }
            }
        }
        return out - outputOffset
    }

    internal companion object {
        /** 72 taps for ×3 from 16 kHz: flat to 6 kHz, -4 dB at 7 kHz, and images from 9 kHz up at least 69 dB down. */
        const val TAPS_PER_PHASE = 24
        /** The pass band ends at 90 % of the input's Nyquist frequency: 7.2 kHz for 16 kHz input. */
        private const val CUTOFF_OF_INPUT_NYQUIST = 0.9

        private fun design(factor: Int, tapsPerPhase: Int): Array<FloatArray> {
            val length = factor * tapsPerPhase
            if (factor == 1) return arrayOf(FloatArray(tapsPerPhase) { if (it == 0) 1f else 0f })
            val cutoff = CUTOFF_OF_INPUT_NYQUIST * 0.5 / factor // cycles per output sample
            val center = (length - 1) / 2.0
            val prototype = DoubleArray(length) { k ->
                val t = k - center
                val sinc = if (t == 0.0) 1.0 else sin(2 * PI * cutoff * t) / (2 * PI * cutoff * t)
                val window = 0.42 - 0.5 * cos(2 * PI * k / (length - 1)) + 0.08 * cos(4 * PI * k / (length - 1))
                sinc * window
            }
            return Array(factor) { phase ->
                val taps = DoubleArray(tapsPerPhase) { tap -> prototype[tap * factor + phase] }
                val sum = taps.sum()
                FloatArray(tapsPerPhase) { tap -> (taps[tap] / sum).toFloat() }
            }
        }
    }
}

/**
 * A recorder at a lower rate that reads like one at [factor] times its rate, so a 16 kHz recorder can feed a 48 kHz
 * call stream: the encoder, the echo canceller and the RTP clock keep the stream's rate (#1172).
 */
internal class UpsamplingRecorder(
    private val inner: MicrophoneRecorder,
    private val factor: Int,
    channels: Int,
) : MicrophoneRecorder by inner {
    private val frameBytes = channels * 2
    private val upsampler = PcmUpsampler(factor, channels)
    private var scratch = ByteArray(0)
    /** Bytes of a frame that a read split, kept at the start of [scratch] for the next read. */
    private var carried = 0

    override fun read(buffer: ByteArray, offset: Int, size: Int): Int {
        val wanted = size / factor / frameBytes * frameBytes - carried
        if (wanted <= 0) return 0
        if (scratch.size < carried + wanted) scratch = scratch.copyOf(carried + wanted)
        val count = inner.read(scratch, carried, wanted)
        if (count <= 0) return count
        val available = carried + count
        val whole = available / frameBytes * frameBytes
        val written = upsampler.process(scratch, 0, whole, buffer, offset)
        carried = available - whole
        if (carried > 0) scratch.copyInto(scratch, 0, whole, available)
        return written
    }

    /** The inner recorder's timestamp, with its frame position on the stream's clock. */
    override fun timestamp(into: AudioTimestamp): Boolean {
        if (!inner.timestamp(into)) return false
        into.framePosition *= factor
        return true
    }
}
