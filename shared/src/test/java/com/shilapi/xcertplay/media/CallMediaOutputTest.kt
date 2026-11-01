package com.shilapi.xcertplay.media

import android.Manifest
import android.media.AudioManager
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioRecord

/** Head units that play music but stay silent on Android's call path (#1198, #1214) can play calls like music. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CallMediaOutputTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val telephony = AudioStreamId(100, "telephony")
    private val diagnostics = CopyOnWriteArrayList<String>()
    private lateinit var manager: AudioManager
    private var sink: AndroidMediaSink? = null

    @Before fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS)
        manager = context.getSystemService(AudioManager::class.java)
        ShadowAudioRecord.setSourceProvider { _ ->
            object : ShadowAudioRecord.AudioRecordSource {
                override fun readInByteArray(buffer: ByteArray, offset: Int, size: Int, blocking: Boolean): Int {
                    Thread.sleep(5)
                    return 0
                }
            }
        }
    }

    @After fun tearDown() {
        sink?.close()
        ShadowAudioRecord.clearSource()
    }

    @Test fun theOptionPlaysTheCallLikeMusicWithoutCallMode() {
        start(mediaOutput = true)

        assertEquals(AudioManager.MODE_NORMAL, manager.mode)
        assertTrue(log(), diagnostics.none { it.startsWith("Audio: mode entered communication") })
        assertTrue(log(), diagnostics.any { it.startsWith("Microphone: start type=telephony source=MIC ") })
        assertEquals(AudioChannel.MEDIA, callChannel())
    }

    @Test fun offByDefaultTheCallKeepsAndroidsCallPath() {
        start(mediaOutput = false)

        assertEquals(AudioManager.MODE_IN_COMMUNICATION, manager.mode)
        assertTrue(log(), diagnostics.any { it.startsWith("Microphone: start type=telephony source=VOICE_COMMUNICATION ") })
        assertEquals(AudioChannel.PHONE, callChannel())
    }

    private fun log() = diagnostics.joinToString("\n")

    private fun start(mediaOutput: Boolean) {
        sink = AndroidMediaSink(context = context, onAudioDiagnostic = diagnostics::add, callMediaOutput = { mediaOutput })
        sink!!.onMicrophoneStarted(telephony, MicrophoneConfig(
            audioType = "telephony",
            sampleRate = 16_000,
            channels = 1,
            payloadType = 100,
            frameMillis = 20,
            host = InetAddress.getLoopbackAddress(),
            port = 9,
            key = ByteArray(32),
        ))
    }

    /** The channel the sink's own renderer for the call's audio plays on, as a call's downlink creates it. */
    private fun callChannel(): AudioChannel {
        val renderer = AndroidMediaSink::class.java
            .getDeclaredMethod("audioRenderer", AudioStreamId::class.java, AudioFormat::class.java)
            .apply { isAccessible = true }
            .invoke(sink, telephony, AudioFormat(AudioCodecKind.LPCM, 16_000, 1, 100, "telephony"))!!
        val selection = renderer.javaClass.getDeclaredMethod("mappedSelection")
            .apply { isAccessible = true }
            .invoke(renderer) as AudioChannelSelection
        return selection.channel
    }
}
