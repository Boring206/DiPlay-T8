package com.shilapi.xcertplay.media

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaFormat
import android.view.Surface
import com.shilapi.xcertplay.airplay.VideoCodec
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaCodec
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], manifest = Config.NONE)
class VideoOutputFailureLifecycleTest {
    private object PassThroughCodec : ShadowMediaCodec.CodecConfig.Codec {
        override fun process(input: ByteBuffer, output: ByteBuffer) = Unit
        override fun onConfigured(format: MediaFormat?, surface: Surface?, crypto: MediaCrypto?, flags: Int) = Unit
    }

    @Before fun setUp() {
        ShadowMediaCodec.addDecoder(MediaFormat.MIMETYPE_VIDEO_AVC, ShadowMediaCodec.CodecConfig(65536, 65536, PassThroughCodec))
    }

    @After fun tearDown() = ShadowMediaCodec.clearCodecs()

    @Test fun failureFromRetiredCodecDoesNotCarryIntoReplacement() {
        val sink = AndroidMediaSink()
        val create = AndroidMediaSink::class.java.getDeclaredMethod(
            "newVideoDecoder", Int::class.javaPrimitiveType, Surface::class.java, String::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val worker = create.invoke(sink, 110, Surface(SurfaceTexture(0)), null, false)
        val type = worker.javaClass
        val configure = type.getDeclaredMethod("configureDecoder", VideoJob.Config::class.java).apply { isAccessible = true }
        val release = type.getDeclaredMethod("releaseDecoder").apply { isAccessible = true }
        val decoder = type.getDeclaredField("decoder").apply { isAccessible = true }
        val failure = type.getDeclaredField("outputFailure").apply { isAccessible = true }
        try {
            configure.invoke(worker, VideoJob.Config(VideoCodec.H264, byteArrayOf()))
            assertNotNull(decoder.get(worker))
            // Output thread may report a failure just before the worker starts replacing the codec.
            failure.set(worker, IllegalStateException("old codec"))
            configure.invoke(worker, VideoJob.Config(VideoCodec.H264, byteArrayOf(1)))
            assertNotNull(decoder.get(worker))
            assertNull("a retired codec's failure must not tear down its replacement", failure.get(worker))
        } finally {
            release.invoke(worker)
            sink.close()
        }
    }
}
