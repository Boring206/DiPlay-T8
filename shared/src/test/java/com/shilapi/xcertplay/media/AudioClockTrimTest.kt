package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.media.AudioTrack
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import kotlin.math.roundToInt

/** The music renderer trims its track's playback rate from the queued level (#610). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, shadows = [AudioClockTrimTest.RateTrack::class])
class AudioClockTrimTest {
    private val reports = mutableListOf<String>()
    private val type = Class.forName("com.shilapi.xcertplay.media.AudioRenderer")
    private lateinit var renderer: Any
    private lateinit var track: AudioTrack

    @Before fun setUp() {
        MediaClockTrim.learnedTrim = 0.0
        RateTrack.rates.clear()
        RateTrack.result = AudioTrack.SUCCESS
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().build())
            .setAudioFormat(android.media.AudioFormat.Builder()
                .setSampleRate(48_000)
                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO)
                .build())
            .setBufferSizeInBytes(96_000)
            .build()
        renderer = type.declaredConstructors.single { it.parameterCount == 13 }.apply { isAccessible = true }
            .newInstance(AudioFormat(AudioCodecKind.LPCM, 48_000, 2, 96, "media"),
                false, false, 0, 0, AudioFocusCoordinator(null, false, false),
                0, 500, { message: String -> reports.add(message) }, 0L, false, null, false)
        set("track", track)
        set("mappedChannel", AudioChannel.MEDIA)
        set("playbackStarted", true)
        set("startThresholdBytes", 57_600)
        set("bytesPerSecond", 48_000 * 4)
        // 300 ms of music is the target; createTrack sets this up for music only.
        set("clockTrim", MediaClockTrim(48_000, 14_400))
    }

    @After fun tearDown() {
        track.release()
        MediaClockTrim.learnedTrim = 0.0
    }

    private fun set(name: String, value: Any?) = type.getDeclaredField(name).apply { isAccessible = true }.set(renderer, value)
    private fun get(name: String): Any? = type.getDeclaredField(name).apply { isAccessible = true }.get(renderer)

    private fun write(bytes: Int) = type.getDeclaredMethod("writePcm", ByteArray::class.java,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
        .invoke(renderer, ByteArray(bytes), 0, bytes)

    @Test fun aLowLevelSlowsTheTrackAndTheStatsShowTheRate() {
        // 150 ms queued against a 300 ms target.
        write(28_800)
        assertEquals(listOf(47_280), RateTrack.rates)
        type.getDeclaredMethod("logStatsIfDue", Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            .invoke(renderer, true)
        assertTrue(reports.toString(), reports.any { it.startsWith("audio stats") && it.contains(" playbackRate=47280 ended=true") })
    }

    @Test fun aTrackThatRefusesTheRateKeepsPlayingWithoutTheTrim() {
        RateTrack.result = AudioTrack.ERROR_INVALID_OPERATION
        write(28_800)
        assertNull(get("clockTrim"))
        assertTrue(reports.toString(), reports.contains("Audio: clock trim off audioType=media rate=47280 result=-3"))
        write(28_800)
        assertEquals(listOf(47_280), RateTrack.rates)
    }

    @Test fun nothingIsTrimmedBeforeMusicPlays() {
        set("playbackStarted", false)
        write(2_048)
        assertTrue(RateTrack.rates.isEmpty())
    }

    private fun submit(sample: Int) = type.getDeclaredMethod("submit", ByteArray::class.java, Int::class.javaPrimitiveType)
        .apply { isAccessible = true }.invoke(renderer, ByteArray(12 + PACKET_FRAMES * 4), sample)

    /**
     * Wired music after a camera stall (#1177): the trim plays 0.84 % slow, as it learned while the track ran dry,
     * and took the level the refill burst left, about 90 000 frames, as its target. It measured over the 3.5 s
     * that ended 1.5 s ago, so the next write may set a new rate.
     */
    private fun slowTrimMeasuredAtTheRefillBurst(): MediaClockTrim {
        set("started", true)
        MediaClockTrim.learnedTrim = -0.0084
        val trim = MediaClockTrim(48_000, 14_400, measuresTarget = true)
        val startNs = System.nanoTime() - 5_000_000_000L
        for (step in 0..35) {
            val nowNs = startNs + step * 100_000_000L
            trim.sample(92_160, nowNs)
            trim.rateDue(nowNs)
        }
        assertEquals(47_597, trim.rate)
        set("clockTrim", trim)
        return trim
    }

    @Test fun aFullQueueSpeedsUpASlowTrimInsteadOfMeasuringAgain() {
        slowTrimMeasuredAtTheRefillBurst()
        // 192 wired packets of 355 frames fill the queue, and the next one is dropped.
        for (packet in 0..192) submit(packet * PACKET_FRAMES)
        assertTrue(reports.toString(), reports.contains("Audio: queue full audioType=media count=1"))
        // A full 500 ms track. Measuring again kept the slow rate, so the queue stayed full and dropped a packet
        // a second. The trim now holds less than the renderer can, so a full queue reads as overfull.
        write(96_000)
        assertEquals(1, RateTrack.rates.size)
        assertTrue("rate ${RateTrack.rates}", RateTrack.rates.single() > 48_000)
    }

    @Test fun aLagGuardDiscardMeasuresTheLevelAgain() {
        val trim = slowTrimMeasuredAtTheRefillBurst()
        // Twice the target, more than a second after the last write: the trim plays fast.
        val nowNs = System.nanoTime() - 400_000_000L
        trim.sample(184_320, nowNs)
        val fast = trim.rateDue(nowNs)!!
        assertTrue("rate $fast", fast > 48_000)
        for (packet in 0..9) submit(packet * PACKET_FRAMES)
        // The backlog has been more than 1.5 s behind for 2 s: the next packet, 2 s on, discards it.
        val guard = get("mediaLagGuard")!!
        guard.javaClass.getDeclaredField("congestionSinceNs").apply { isAccessible = true }
            .setLong(guard, System.nanoTime() - 2_000_000_000L)
        submit(96_000)
        assertTrue(reports.toString(), reports.any { it.startsWith("Audio: stale media packet backlog recovered") })
        // The level the trim held is gone: it measures again, at the rate it learned.
        write(PACKET_FRAMES * 4)
        assertEquals(listOf((48_000 * (1 + MediaClockTrim.learnedTrim)).roundToInt()), RateTrack.rates)
    }

    @Implements(AudioTrack::class)
    class RateTrack {
        @Implementation fun getPlaybackHeadPosition() = 0
        @Implementation fun getUnderrunCount() = 0
        @Implementation fun play() = Unit
        @Implementation fun pause() = Unit
        @Implementation fun write(bytes: ByteArray, offset: Int, size: Int, mode: Int): Int = size
        @Implementation fun setPlaybackRate(rate: Int): Int {
            rates += rate
            return result
        }

        companion object {
            val rates = mutableListOf<Int>()
            var result = AudioTrack.SUCCESS
        }
    }

    private companion object {
        /** A wired LPCM packet: 7.4 ms at 48 kHz. */
        const val PACKET_FRAMES = 355
    }
}
