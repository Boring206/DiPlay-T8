package com.shilapi.xcertplay.media

import android.graphics.SurfaceTexture
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Whether the main screen could show a frame, which decides what proves a wireless session (#1215). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class AndroidMediaSinkVideoOutputTest {
    @Test fun theMainScreenHasOutputOnlyWhileASurfaceIsAttached() {
        val surface = Surface(SurfaceTexture(0))
        val sink = AndroidMediaSink()
        try {
            assertFalse(sink.hasVideoOutput(MAIN))
            sink.setSurface(MAIN, surface)
            assertTrue(sink.hasVideoOutput(MAIN))
            assertFalse("The cluster's surface is not the main screen", sink.hasVideoOutput(ALT))
            sink.clearSurface(MAIN, surface)
            assertFalse(sink.hasVideoOutput(MAIN))
        } finally { sink.close() }
    }

    @Test fun aDestroyedHostSurfaceLeavesNoOutput() {
        // The host activity stopped: its SurfaceView detaches the surface from every decoder.
        val surface = Surface(SurfaceTexture(0))
        val sink = AndroidMediaSink(surface = surface)
        try {
            assertTrue(sink.hasVideoOutput(MAIN))
            assertTrue(sink.beginSurfaceDetach(surface, parkMain = true).await())
            assertFalse(sink.hasVideoOutput(MAIN))
        } finally { sink.close() }
    }

    @Test fun theHostsAttachedSurfaceLeavesNoOutputOnceDestroyed() {
        // Production: the sink starts without a surface, the host attaches 110, and a stop detaches it (#1215).
        val surface = Surface(SurfaceTexture(0))
        val sink = AndroidMediaSink()
        try {
            sink.setSurface(MAIN, surface)
            assertTrue(sink.hasVideoOutput(MAIN))
            assertTrue(sink.beginSurfaceDetach(surface, parkMain = true).await())
            assertFalse(sink.hasVideoOutput(MAIN))
        } finally { sink.close() }
    }

    @Test fun attachmentsCountEveryMainScreenSurfaceAndMirrorEvenAfterTheyAreGone() {
        val surface = Surface(SurfaceTexture(0))
        val card = Surface(SurfaceTexture(0))
        val sink = AndroidMediaSink()
        try {
            assertEquals(0L, sink.videoOutputAttachments(MAIN))
            sink.setSurface(MAIN, surface)
            assertTrue(sink.beginSurfaceDetach(surface, parkMain = true).await())
            assertEquals(1L, sink.videoOutputAttachments(MAIN))
            sink.setMirrorSurface(MAIN, "card", card)
            sink.setMirrorSurface(MAIN, "card", null)
            assertEquals(2L, sink.videoOutputAttachments(MAIN))
            sink.setSurface(ALT, surface)
            assertEquals("The cluster's surface is not the main screen", 2L, sink.videoOutputAttachments(MAIN))
        } finally { sink.close() }
    }

    @Test fun aMirrorOfTheMainScreenCountsAsOutput() {
        val card = Surface(SurfaceTexture(0))
        val sink = AndroidMediaSink()
        try {
            sink.setMirrorSurface(MAIN, "card", card)
            assertTrue(sink.hasVideoOutput(MAIN))
            sink.setMirrorSurface(MAIN, "card", null)
            assertFalse(sink.hasVideoOutput(MAIN))
        } finally { sink.close() }
    }

    @Test fun aClosedSinkHasNoOutput() {
        val sink = AndroidMediaSink(surface = Surface(SurfaceTexture(0)))
        sink.close()
        assertFalse(sink.hasVideoOutput(MAIN))
    }

    private companion object {
        const val MAIN = 110
        const val ALT = 111
    }
}
