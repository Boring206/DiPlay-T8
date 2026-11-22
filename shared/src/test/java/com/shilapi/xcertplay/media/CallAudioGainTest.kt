package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 28, 33], manifest = Config.NONE)
class CallAudioGainTest {
    private fun renderer(sink: AndroidMediaSink, audioType: String): Any {
        val create = AndroidMediaSink::class.java.getDeclaredMethod("audioRenderer",
            AudioStreamId::class.java, AudioFormat::class.java).apply { isAccessible = true }
        return create.invoke(sink, AudioStreamId(96, audioType),
            AudioFormat(AudioCodecKind.LPCM, 48_000, 1, 96, audioType))
    }

    private fun Any.attenuateCall(pcm: ByteArray) {
        javaClass.getDeclaredMethod("attenuateCall", ByteArray::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(this, pcm, 0, pcm.size)
    }

    private fun Any.mapTo(channel: AudioChannel) =
        javaClass.getDeclaredField("mappedChannel").apply { isAccessible = true }.set(this, channel)

    private fun pcm16(vararg samples: Int): ByteArray = ByteArray(samples.size * 2).also {
        samples.forEachIndexed { i, s -> it[i * 2] = s.toByte(); it[i * 2 + 1] = (s shr 8).toByte() }
    }

    @Test fun aCallIsLeftAloneAtTheDefaultOfZeroDecibels() {
        val sink = AndroidMediaSink()
        try {
            val call = renderer(sink, "telephony")
            val pcm = pcm16(10000, -10000)
            call.attenuateCall(pcm)
            assertArrayEquals(pcm16(10000, -10000), pcm)
        } finally {
            sink.close()
        }
    }

    // The level follows the call, not the output it ends up on, so a call that plays as media is lowered too.
    @Test fun aCallIsLoweredOnWhicheverOutputItUses() {
        val sink = AndroidMediaSink(callAttenuationDb = -12)
        try {
            for (channel in AudioChannel.entries) {
                val call = renderer(sink, "telephony")
                call.mapTo(channel)
                val pcm = pcm16(10000, -10000)
                call.attenuateCall(pcm)
                assertArrayEquals("call on $channel", pcm16(2512, -2512), pcm)
            }
        } finally {
            sink.close()
        }
    }

    @Test fun musicAndNavigationAreNotLoweredByTheCallLevel() {
        val sink = AndroidMediaSink(callAttenuationDb = -20)
        try {
            for (audioType in listOf("media", "default")) {
                val other = renderer(sink, audioType)
                for (channel in AudioChannel.entries) {
                    other.mapTo(channel)
                    val pcm = pcm16(10000)
                    other.attenuateCall(pcm)
                    assertArrayEquals("$audioType on $channel", pcm16(10000), pcm)
                }
            }
        } finally {
            sink.close()
        }
    }

    @Test fun aChangeReachesACallThatIsAlreadyPlaying() {
        val sink = AndroidMediaSink()
        try {
            val call = renderer(sink, "telephony")
            sink.setCallAttenuationDb(-6)
            val lowered = pcm16(20000)
            call.attenuateCall(lowered)
            assertArrayEquals(pcm16(10024), lowered)
            sink.setCallAttenuationDb(0)
            val restored = pcm16(20000)
            call.attenuateCall(restored)
            assertArrayEquals(pcm16(20000), restored)
        } finally {
            sink.close()
        }
    }

    @Test fun theCallLevelCanOnlyLowerAndStaysWithinTheSliderRange() {
        val sink = AndroidMediaSink()
        try {
            val call = renderer(sink, "telephony")
            sink.setCallAttenuationDb(10)
            val boosted = pcm16(10000)
            call.attenuateCall(boosted)
            assertArrayEquals(pcm16(10000), boosted)
            sink.setCallAttenuationDb(-100)
            val floor = pcm16(10000)
            call.attenuateCall(floor)
            assertArrayEquals(pcm16(1000), floor)
        } finally {
            sink.close()
        }
    }

    @Test fun theCallLevelIsIndependentOfTheNavigationLevel() {
        val sink = AndroidMediaSink(navigationAttenuationDb = -20, callAttenuationDb = 0)
        try {
            val call = renderer(sink, "telephony")
            val pcm = pcm16(10000)
            call.attenuateCall(pcm)
            assertArrayEquals(pcm16(10000), pcm)
        } finally {
            sink.close()
        }
    }

    @Test fun eachChangeIsReportedOnceWithTheOutputItPlaysOn() {
        val reports = mutableListOf<String>()
        val sink = AndroidMediaSink(onAudioDiagnostic = reports::add)
        try {
            val call = renderer(sink, "telephony")
            call.mapTo(AudioChannel.MEDIA)
            val flush = call.javaClass.getDeclaredMethod("applyCallAttenuation").apply { isAccessible = true }
            repeat(5) { flush.invoke(call) }
            assertTrue(reports.none { it.contains("call attenuation") })
            sink.setCallAttenuationDb(-18)
            repeat(50) { flush.invoke(call) }
            assertEquals(1, reports.count { it.contains("call attenuation=-18dB") && it.contains("channel=MEDIA") })
            sink.setCallAttenuationDb(-10)
            repeat(50) { flush.invoke(call) }
            assertEquals(1, reports.count { it.contains("call attenuation=-10dB") })
        } finally {
            sink.close()
        }
    }
}
