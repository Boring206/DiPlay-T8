package com.shilapi.xcertplay.media

import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A call recorder that delivers a fraction of real time, or only zeros, in communication mode (#1172) is rebuilt at
 * the vocoder's rate, then from another source, at most once each. The recorders here run on a fake clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SlowCallMicrophoneTest {
    private val diagnostics = CopyOnWriteArrayList<String>()
    private val built = CopyOnWriteArrayList<PacedRecorder>()
    private val clock = AtomicLong(1_000_000_000L)
    private var uplink: MicrophoneUplink? = null

    @After fun tearDown() {
        uplink?.close()
    }

    @Test fun aCallRecordingAThirdOfRealTimeIsRebuiltAtTheVocoderRate() {
        // #1172: the 48 kHz VOICE_COMMUNICATION recorder delivers 16 000 samples a second.
        start(pace = { recorder -> if (recorder.rate == 48_000) Pace(speed = 1.0 / 3) else Pace() })

        awaitChecks(2)

        val first = checks()[0]
        assertTrue(first, first.contains("type=telephony source=VOICE_COMMUNICATION codec=LPCM rate=48000 channels=1 frameMs=20 " +
            "realtimePercent=33 peakDbfs=-21 audioMode=3 verdict=slow-capture " +
            "action=rebuild nextSource=VOICE_COMMUNICATION nextCaptureRate=16000"))
        assertEquals(listOf(48_000, 16_000), built.map { it.rate })
        assertEquals(listOf(COMMUNICATION, COMMUNICATION), built.map { it.source })
        val restarted = line("Microphone: recording restarted")
        assertTrue(restarted, restarted.contains("source=VOICE_COMMUNICATION codec=LPCM rate=48000 channels=1 frameMs=20 captureRate=16000"))
        assertTrue(restarted, restarted.contains("attempt=1 reason=slow-capture routedDeviceType=unknown recorder=new"))
        val second = checks()[1]
        assertTrue(second, second.contains("captureRate=16000 realtimePercent=100"))
        assertTrue(second, second.endsWith("verdict=ok action=keep"))
        assertTrue(built[0].released)
        assertFalse(built[1].released)
        awaitTrue { built[1].dataReads.get() > 300 }
        assertEquals("one rebuild", 2, built.size)
    }

    @Test fun aCallThatStaysSlowAtTheVocoderRateFallsBackToVoiceRecognitionAtTheStreamRate() {
        // 8 000 samples a second: a sixth of real time at 48 kHz, half of it at 16 kHz.
        start(pace = { recorder -> if (recorder.source == COMMUNICATION) Pace(speed = 8_000.0 / recorder.rate) else Pace() })

        awaitChecks(3)

        assertTrue(checks()[0], checks()[0].contains("realtimePercent=17"))
        assertTrue(checks()[1], checks()[1].contains("captureRate=16000 realtimePercent=50"))
        assertTrue(checks()[1], checks()[1].endsWith(
            "verdict=slow-capture action=rebuild nextSource=VOICE_RECOGNITION nextCaptureRate=48000"))
        assertTrue(checks()[2], checks()[2].contains("source=VOICE_RECOGNITION codec=LPCM rate=48000 channels=1 frameMs=20 realtimePercent=100"))
        assertTrue(checks()[2], checks()[2].endsWith("verdict=ok action=keep"))
        assertEquals(listOf(COMMUNICATION, COMMUNICATION, RECOGNITION), built.map { it.source })
        assertEquals(listOf(48_000, 16_000, 48_000), built.map { it.rate })
        assertTrue(diagnostics.none { it.startsWith("Microphone: source fallback") })
    }

    @Test fun aSilentCallInCommunicationModeIsRebuiltThenMovedToVoiceRecognition() {
        // #1172: full rate, every sample zero, while another recording holds the microphone.
        start(pace = { recorder -> if (recorder.source == COMMUNICATION) Pace(sound = false) else Pace() })

        awaitChecks(3)

        assertTrue(checks()[0], checks()[0].contains("realtimePercent=100 peakDbfs=silent audioMode=3 verdict=silent-capture " +
            "action=rebuild nextSource=VOICE_COMMUNICATION nextCaptureRate=16000"))
        assertTrue(checks()[1], checks()[1].contains("captureRate=16000"))
        assertTrue(checks()[1], checks()[1].endsWith("verdict=silent-capture action=rebuild nextSource=VOICE_RECOGNITION nextCaptureRate=48000"))
        assertTrue(checks()[2], checks()[2].endsWith("verdict=ok action=keep"))
        assertEquals(listOf(COMMUNICATION, COMMUNICATION, RECOGNITION), built.map { it.source })
        val restarts = diagnostics.filter { it.startsWith("Microphone: recording restarted") }
        assertEquals(2, restarts.size)
        assertTrue(restarts[1], restarts[1].contains("source=VOICE_RECOGNITION"))
        assertTrue(restarts[1], restarts[1].contains("attempt=2 reason=silent-capture"))
    }

    @Test fun silenceOutsideCommunicationModeIsLeftAlone() {
        start(pace = { Pace(sound = false) }, mode = AudioManager.MODE_NORMAL)

        awaitChecks(1)

        assertTrue(checks()[0], checks()[0].endsWith("peakDbfs=silent audioMode=0 verdict=ok action=keep"))
        awaitTrue { built[0].dataReads.get() > 300 }
        assertEquals(1, built.size)
    }

    @Test fun aHealthyCallIsCheckedOnceAndKeepsItsRecorder() {
        start(pace = { Pace() })

        awaitChecks(1)
        awaitTrue { built[0].dataReads.get() > 400 }

        assertEquals(1, checks().size)
        assertTrue(checks()[0], checks()[0].contains("source=VOICE_COMMUNICATION codec=LPCM rate=48000"))
        assertTrue(checks()[0], checks()[0].endsWith("realtimePercent=100 peakDbfs=-21 audioMode=3 verdict=ok action=keep"))
        assertEquals(1, built.size)
        assertTrue(diagnostics.none { it.startsWith("Microphone: recording restarted") })
    }

    @Test fun aCallThatNeverRecordsWellIsRebuiltTwiceAndThenKept() {
        start(pace = { Pace(speed = 1.0 / 3) })

        awaitChecks(3)
        awaitTrue { built.size == 3 && built[2].dataReads.get() > 300 }

        assertEquals(3, checks().size)
        assertTrue(checks()[2], checks()[2].contains("source=VOICE_RECOGNITION"))
        assertTrue(checks()[2], checks()[2].endsWith("verdict=slow-capture action=keep"))
        assertEquals(3, built.size)
        assertFalse(built[2].released)
        assertTrue(diagnostics.none { it.startsWith("Microphone: capture stopped") })
    }

    @Test fun aGeelyCallStaysOnVoiceCommunication() {
        // That firmware only records from VOICE_COMMUNICATION; the vocoder rate is still worth one try.
        start(pace = { Pace(speed = 1.0 / 3) }, geely = true)

        awaitChecks(2)
        awaitTrue { built.size == 2 && built[1].dataReads.get() > 300 }

        assertTrue(checks()[1], checks()[1].contains("captureRate=16000"))
        assertTrue(checks()[1], checks()[1].endsWith("verdict=slow-capture action=keep"))
        assertEquals(2, checks().size)
        assertTrue(built.all { it.source == COMMUNICATION })
    }

    @Test fun aCallInTheCarsPhoneAudioStateNeverMovesToTheMutedMicSource() {
        start(pace = { Pace(speed = 1.0 / 3) }, carPhoneState = true)

        awaitChecks(2)
        awaitTrue { built.size == 2 && built[1].dataReads.get() > 300 }

        assertTrue(checks()[0], checks()[0].contains("source=VOICE_RECOGNITION"))
        assertTrue(checks()[1], checks()[1].endsWith("verdict=slow-capture action=keep"))
        assertEquals(listOf(RECOGNITION, RECOGNITION), built.map { it.source })
    }

    @Test fun aCallWhoseUnitRefusesVoiceRecognitionMovesToMic() {
        start(pace = { recorder -> if (recorder.source == MIC) Pace() else Pace(speed = 1.0 / 3) }, rejects = { source, _ -> source == RECOGNITION })

        awaitChecks(3)

        assertTrue(checks()[1], checks()[1].endsWith("action=rebuild nextSource=VOICE_RECOGNITION nextCaptureRate=48000"))
        assertTrue(line("Microphone: failure ").contains("source=VOICE_RECOGNITION"))
        assertTrue(line("Microphone: failure ").contains("stage=RECORDER_CREATION"))
        assertTrue(line("Microphone: recording restart failed").contains("reason=slow-capture recorder=new recordingState=none"))
        assertTrue(checks()[2], checks()[2].contains("source=MIC"))
        assertTrue(checks()[2], checks()[2].endsWith("verdict=ok action=keep"))
        assertEquals(listOf(COMMUNICATION, COMMUNICATION, MIC), built.map { it.source })
    }

    @Test fun aUnitThatRefusesTheVocoderRateRecordsAtTheStreamRateAgain() {
        start(pace = { recorder -> if (recorder.source == COMMUNICATION) Pace(speed = 1.0 / 3) else Pace() },
            rejects = { _, rate -> rate == 16_000 })

        awaitChecks(3)

        assertTrue(line("Microphone: failure ").contains("captureRate=16000 stage=RECORDER_CREATION"))
        val restarted = line("Microphone: recording restarted")
        assertFalse(restarted, restarted.contains("captureRate="))
        assertTrue(checks()[1], checks()[1].endsWith("action=rebuild nextSource=VOICE_RECOGNITION nextCaptureRate=48000"))
        assertTrue(checks()[2], checks()[2].endsWith("verdict=ok action=keep"))
        assertEquals(listOf(48_000, 48_000, 48_000), built.map { it.rate })
        assertEquals(listOf(COMMUNICATION, COMMUNICATION, RECOGNITION), built.map { it.source })
    }

    @Test fun aSixteenKilohertzStreamGoesStraightToTheSourceFallback() {
        start(pace = { recorder -> if (recorder.source == COMMUNICATION) Pace(speed = 1.0 / 3) else Pace() }, rate = 16_000)

        awaitChecks(2)

        assertTrue(checks()[0], checks()[0].endsWith("action=rebuild nextSource=VOICE_RECOGNITION nextCaptureRate=16000"))
        assertTrue(checks()[1], checks()[1].endsWith("verdict=ok action=keep"))
        assertEquals(listOf(16_000, 16_000), built.map { it.rate })
    }

    @Test fun siriIsNotChecked() {
        start(pace = { Pace(speed = 1.0 / 3) }, type = "speechrecognition")

        awaitTrue { built[0].dataReads.get() > 100 }

        assertTrue(checks().isEmpty())
        assertEquals(1, built.size)
    }

    private fun start(
        pace: (PacedRecorder) -> Pace,
        type: String = "telephony",
        rate: Int = 48_000,
        mode: Int = AudioManager.MODE_IN_COMMUNICATION,
        geely: Boolean = false,
        carPhoneState: Boolean = false,
        rejects: (source: Int, rate: Int) -> Boolean = { _, _ -> false },
    ) {
        val factory = MicrophoneRecorderFactory { source, sampleRate, _, _ ->
            if (rejects(source, sampleRate)) throw UnsupportedOperationException("source $source at $sampleRate Hz rejected")
            PacedRecorder(source, sampleRate, built.size, clock).also { recorder ->
                recorder.pace = pace(recorder)
                built += recorder
            }
        }
        val next = MicrophoneUplink(
            config(type, rate),
            onDiagnostic = diagnostics::add,
            recorderFactory = factory,
            rebuildBackoffMillis = listOf(1L, 1L, 1L, 1L),
            geelyHeadUnit = { geely },
            carPhoneState = { carPhoneState },
            audioMode = { mode },
            captureCheckClock = clock::get,
        )
        uplink = next
        assertTrue(next.start())
    }

    private fun checks() = diagnostics.filter { it.startsWith("Microphone: capture check ") }

    private fun awaitChecks(count: Int) = awaitTrue { checks().size >= count }

    private fun line(prefix: String): String =
        diagnostics.firstOrNull { it.startsWith(prefix) } ?: throw AssertionError("no \"$prefix\" in:\n${diagnostics.joinToString("\n")}")

    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out; diagnostics:\n${diagnostics.joinToString("\n")}")
            Thread.sleep(5)
        }
    }

    private fun config(type: String, rate: Int) = MicrophoneConfig(
        audioType = type,
        sampleRate = rate,
        channels = 1,
        payloadType = 100,
        frameMillis = 20,
        host = InetAddress.getLoopbackAddress(),
        port = 9,
        key = ByteArray(32),
    )

    /** [speed] is the fraction of real time the recorder delivers; without [sound] every sample is zero. */
    class Pace(val speed: Double = 1.0, val sound: Boolean = true)

    /** Fills every read it is asked for and moves the fake clock by how long that much audio takes at [pace]. */
    class PacedRecorder(val source: Int, val rate: Int, index: Int, private val clock: AtomicLong) : MicrophoneRecorder {
        @Volatile var pace = Pace()
        val dataReads = AtomicInteger()
        @Volatile var released = false
            private set
        @Volatile private var recording = false

        override val state = AudioRecord.STATE_INITIALIZED
        override val recordingState get() = if (recording) AudioRecord.RECORDSTATE_RECORDING else AudioRecord.RECORDSTATE_STOPPED
        override val audioSessionId = 2_000 + index

        override fun routedDeviceType(): Int? = null

        override fun startRecording() {
            check(!released) { "released" }
            recording = true
        }

        override fun stop() {
            recording = false
        }

        override fun read(buffer: ByteArray, offset: Int, size: Int): Int {
            if (released || !recording) return AudioRecord.ERROR_INVALID_OPERATION
            Thread.sleep(1)
            val bytes = size - size % 2
            buffer.fill(0, offset, offset + bytes)
            if (pace.sound) {
                // A constant level: -21 dBFS, the same after upsampling.
                for (sample in 0 until bytes / 2) {
                    buffer[offset + sample * 2] = (LEVEL and 0xff).toByte()
                    buffer[offset + sample * 2 + 1] = (LEVEL shr 8).toByte()
                }
            }
            clock.addAndGet((bytes / 2 * 1_000_000_000.0 / rate / pace.speed).toLong())
            dataReads.incrementAndGet()
            return bytes
        }

        override fun timestamp(into: AudioTimestamp) = false

        override fun release() {
            released = true
            recording = false
        }
    }

    private companion object {
        const val LEVEL = 2_900
        const val COMMUNICATION = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        const val RECOGNITION = MediaRecorder.AudioSource.VOICE_RECOGNITION
        const val MIC = MediaRecorder.AudioSource.MIC
    }
}
