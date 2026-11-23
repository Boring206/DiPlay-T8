package com.shilapi.xcertplay.media

import android.media.AudioManager
import com.shilapi.xcertplay.media.MicrophoneCaptureCheck.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The #1172 call check, driven by reads of a fixed size on a fake clock. */
class MicrophoneCaptureCheckTest {
    private val check = MicrophoneCaptureCheck(bytesPerSecond = BYTES_PER_SECOND)
    private var now = 1_000_000_000L
    private var mode: Int? = AudioManager.MODE_IN_COMMUNICATION

    @Test fun aRecorderThatKeepsUpWithSoundIsOkAfterAboutTwoSeconds() {
        val result = readUntilVerdict(speed = 1.0, sound = true)

        assertEquals(Verdict.OK, result.verdict)
        assertEquals(100, result.realtimePercent)
        assertEquals(LOUD, result.peak)
        assertEquals("judged within about two seconds of the first data", true, verdictAfterNs in 2_000_000_000L..2_100_000_000L)
    }

    @Test fun aThirdOfRealTimeIsSlow() {
        // #1172: 2048-byte reads at 48 kHz blocked about 69 ms instead of 21 ms.
        val result = readUntilVerdict(speed = 1.0 / 3, sound = true)

        assertEquals(Verdict.SLOW, result.verdict)
        assertEquals(33, result.realtimePercent)
    }

    @Test fun aSixthOfRealTimeIsSlow() {
        val result = readUntilVerdict(speed = 1.0 / 6, sound = true)

        assertEquals(Verdict.SLOW, result.verdict)
        assertEquals(17, result.realtimePercent)
    }

    @Test fun justUnderTheThresholdIsSlowAndJustOverIsNot() {
        assertEquals(Verdict.SLOW, readUntilVerdict(speed = 0.78, sound = true).verdict)
        check.reset()
        assertEquals(Verdict.OK, readUntilVerdict(speed = 0.82, sound = true).verdict)
    }

    @Test fun slowIsReportedBeforeSilent() {
        val result = readUntilVerdict(speed = 1.0 / 3, sound = false)

        assertEquals(Verdict.SLOW, result.verdict)
        assertEquals(0, result.peak)
    }

    @Test fun allZeroSamplesInCommunicationModeAreSilent() {
        val result = readUntilVerdict(speed = 1.0, sound = false)

        assertEquals(Verdict.SILENT, result.verdict)
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, result.audioMode)
    }

    @Test fun allZeroSamplesOutsideCommunicationModeAreNotJudgedSilent() {
        mode = AudioManager.MODE_NORMAL
        assertEquals(Verdict.OK, readUntilVerdict(speed = 1.0, sound = false).verdict)
        check.reset()
        mode = null
        assertEquals("an unreadable mode is not communication", Verdict.OK, readUntilVerdict(speed = 1.0, sound = false).verdict)
    }

    @Test fun aModeGetterThatThrowsLeavesTheModeUnknown() {
        val result = readUntilVerdict(speed = 1.0, sound = false, audioMode = { throw IllegalStateException("no mode") })

        assertEquals(Verdict.OK, result.verdict)
        assertNull(result.audioMode)
    }

    @Test fun oneLoudSampleAnywhereIsNotSilent() {
        val quiet = ByteArray(READ_BYTES)
        val once = ByteArray(READ_BYTES).also { it[100] = 1 }
        var result: MicrophoneCaptureCheck.Result? = null
        var reads = 0
        while (result == null) {
            now += readNs(1.0)
            result = check.read(if (reads++ == 3) once else quiet, READ_BYTES, now) { mode }
        }

        assertEquals(Verdict.OK, result.verdict)
        assertEquals(1, result.peak)
    }

    @Test fun aSlowFirstReadAndAStartingBurstAreNotMeasured() {
        // The first read stalls a second while the path opens, then a buffered burst arrives at once; after that the
        // recorder keeps up.
        now += 1_000_000_000L
        assertNull(check.read(loud(), READ_BYTES, now) { mode })
        repeat(20) { assertNull(check.read(loud(), READ_BYTES, now) { mode }) }

        val result = readUntilVerdict(speed = 1.0, sound = true)

        assertEquals(Verdict.OK, result.verdict)
        assertEquals(100, result.realtimePercent)
    }

    @Test fun theVerdictComesOncePerReset() {
        readUntilVerdict(speed = 1.0 / 3, sound = true)
        repeat(500) {
            now += readNs(1.0 / 3)
            assertNull(check.read(loud(), READ_BYTES, now) { mode })
        }

        check.reset()

        assertNotNull(readUntilVerdict(speed = 1.0, sound = true))
    }

    private var verdictAfterNs = 0L

    private fun readUntilVerdict(
        speed: Double,
        sound: Boolean,
        audioMode: () -> Int? = { mode },
    ): MicrophoneCaptureCheck.Result {
        val pcm = if (sound) loud() else ByteArray(READ_BYTES)
        val began = now
        repeat(1_000) {
            now += readNs(speed)
            check.read(pcm, READ_BYTES, now, audioMode)?.let {
                verdictAfterNs = now - began
                return it
            }
        }
        throw AssertionError("no verdict")
    }

    private fun loud() = ByteArray(READ_BYTES).also { pcm ->
        pcm[0] = (LOUD and 0xff).toByte()
        pcm[1] = (LOUD shr 8).toByte()
        pcm[2] = (-LOUD and 0xff).toByte()
        pcm[3] = (-LOUD shr 8).toByte()
    }

    private fun readNs(speed: Double) = (READ_BYTES * 1_000_000_000.0 / BYTES_PER_SECOND / speed).toLong()

    private companion object {
        const val BYTES_PER_SECOND = 48_000L * 2
        const val READ_BYTES = 2_048
        const val LOUD = 3_000
    }
}
