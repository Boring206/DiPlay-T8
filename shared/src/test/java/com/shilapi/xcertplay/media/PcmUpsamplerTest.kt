package com.shilapi.xcertplay.media

import android.media.AudioRecord
import android.media.AudioTimestamp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A 16 kHz call recorder feeding a 48 kHz stream after #1172. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PcmUpsamplerTest {
    @Test fun eachInputFrameBecomesFactorOutputFrames() {
        val upsampler = PcmUpsampler(factor = 3, channels = 1)
        val output = ByteArray(3 * 640)

        assertEquals(1_920, upsampler.process(ByteArray(640), 0, 640, output, 0))
        assertEquals(1_920, upsampler.outputBytes(640))
        assertEquals("a split frame is left for the caller", 1_920, upsampler.outputBytes(641))
    }

    @Test fun aConstantLevelPassesUnchanged() {
        val output = upsample(DoubleArray(400) { 5_000.0 }, factor = 3)

        for (index in 200 until output.size) assertEquals(5_000, output[index].toInt())
    }

    @Test fun speechBandTonesKeepTheirLevelAndHaveNoImages() {
        for (frequency in listOf(300.0, 1_000.0, 3_000.0, 6_000.0)) {
            val input = DoubleArray(1_600) { 10_000 * sin(2 * PI * frequency * it / 16_000) }
            val output = upsample(input, factor = 3).drop(300).map { it.toDouble() }

            val level = dbOf(output, frequency, 48_000)
            assertEquals("level of $frequency Hz", dbOf(input.drop(100), frequency, 16_000), level, 0.5)
            // 16 kHz content reappears around multiples of 16 kHz unless the filter removes it.
            for (image in listOf(16_000 - frequency, 16_000 + frequency)) {
                val below = level - dbOf(output, image, 48_000)
                assertTrue("image at $image Hz of $frequency Hz is only $below dB down", below > 60)
            }
        }
    }

    @Test fun stereoChannelsStaySeparate() {
        val upsampler = PcmUpsampler(factor = 3, channels = 2)
        val input = ByteArray(400 * 4)
        for (frame in 0 until 400) {
            put(input, frame * 2, 4_000)
            put(input, frame * 2 + 1, -2_000)
        }
        val output = ByteArray(input.size * 3)
        upsampler.process(input, 0, input.size, output, 0)

        for (frame in 300 until 1_200) {
            assertEquals(4_000, get(output, frame * 2).toInt())
            assertEquals(-2_000, get(output, frame * 2 + 1).toInt())
        }
    }

    @Test fun loudInputClipsInsteadOfWrapping() {
        // A full-scale square wave rings past full scale in any low-pass filter.
        val output = upsample(DoubleArray(400) { if ((it / 20) % 2 == 0) 32_767.0 else -32_768.0 }, factor = 3)

        assertTrue(output.any { it == Short.MAX_VALUE })
        assertTrue(output.any { it == Short.MIN_VALUE })
    }

    @Test fun anUpsamplingRecorderReadsAThirdAndCarriesASplitFrame() {
        val inner = ScriptedRecorder(listOf(681, 681, 682))
        val recorder = UpsamplingRecorder(inner, factor = 3, channels = 1)
        val buffer = ByteArray(2_048)

        // 2048 bytes at 48 kHz are 341 samples at 16 kHz: 682 bytes. The odd byte of the first read waits for the next.
        assertEquals(680 * 3, recorder.read(buffer, 0, buffer.size))
        assertEquals(682 * 3, recorder.read(buffer, 0, buffer.size))
        assertEquals(682 * 3, recorder.read(buffer, 0, buffer.size))
        assertEquals(listOf(682, 681, 682), inner.requested)
    }

    @Test fun anUpsamplingRecorderPassesErrorsAndCountsFramesOnTheStreamClock() {
        val inner = ScriptedRecorder(listOf(AudioRecord.ERROR_INVALID_OPERATION, 0))
        val recorder = UpsamplingRecorder(inner, factor = 3, channels = 1)

        assertEquals(AudioRecord.ERROR_INVALID_OPERATION, recorder.read(ByteArray(2_048), 0, 2_048))
        assertEquals(0, recorder.read(ByteArray(2_048), 0, 2_048))
        val stamp = AudioTimestamp()
        assertTrue(recorder.timestamp(stamp))
        assertEquals(3_000L, stamp.framePosition)
        assertEquals(77L, stamp.nanoTime)
        assertEquals(inner.audioSessionId, recorder.audioSessionId)
    }

    private fun upsample(input: DoubleArray, factor: Int): ShortArray {
        val bytes = ByteArray(input.size * 2)
        input.forEachIndexed { index, value -> put(bytes, index, value.toInt().coerceIn(-32_768, 32_767)) }
        val output = ByteArray(bytes.size * factor)
        PcmUpsampler(factor, channels = 1).process(bytes, 0, bytes.size, output, 0)
        return ShortArray(input.size * factor) { get(output, it) }
    }

    /**
     * The level of [frequency] in [samples], in dB, by correlation with a sine and a cosine. A Hann window keeps a
     * loud tone's leakage out of a quiet frequency nearby.
     */
    private fun dbOf(samples: List<Double>, frequency: Double, rate: Int): Double {
        var re = 0.0
        var im = 0.0
        var weights = 0.0
        samples.forEachIndexed { index, value ->
            val weight = 0.5 - 0.5 * cos(2 * PI * index / (samples.size - 1))
            val phase = 2 * PI * frequency * index / rate
            re += weight * value * cos(phase)
            im += weight * value * sin(phase)
            weights += weight
        }
        return 20 * log10(sqrt(re * re + im * im) * 2 / weights + 1e-9)
    }

    private fun put(pcm: ByteArray, sample: Int, value: Int) {
        pcm[sample * 2] = value.toByte()
        pcm[sample * 2 + 1] = (value shr 8).toByte()
    }

    private fun get(pcm: ByteArray, sample: Int): Short =
        ((pcm[sample * 2].toInt() and 0xff) or (pcm[sample * 2 + 1].toInt() shl 8)).toShort()

    /** Returns the scripted counts in turn, filling what it reports with a level. */
    private class ScriptedRecorder(private val counts: List<Int>) : MicrophoneRecorder {
        val requested = mutableListOf<Int>()
        override val state = AudioRecord.STATE_INITIALIZED
        override val recordingState = AudioRecord.RECORDSTATE_RECORDING
        override val audioSessionId = 42
        override fun routedDeviceType(): Int? = null
        override fun startRecording() = Unit
        override fun stop() = Unit
        override fun read(buffer: ByteArray, offset: Int, size: Int): Int {
            requested += size
            val count = counts[requested.size - 1]
            for (index in 0 until maxOf(0, minOf(count, size))) buffer[offset + index] = 1
            return count
        }
        override fun timestamp(into: AudioTimestamp): Boolean {
            into.framePosition = 1_000
            into.nanoTime = 77
            return true
        }
        override fun release() = Unit
    }
}
