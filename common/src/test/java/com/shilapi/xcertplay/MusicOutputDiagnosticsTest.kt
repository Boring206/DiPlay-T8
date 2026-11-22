package com.shilapi.xcertplay

import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Test

class MusicOutputDiagnosticsTest {
    @Test fun usageBasedRoutingQueriesMusicAndMarks44100AsAmbiguous() {
        val queried = mutableListOf<Int>()
        val result = MusicOutputDiagnostics.read(0) { queried += it; 44_100 }
        assertEquals(listOf(AudioManager.STREAM_MUSIC), queried)
        assertEquals(AudioManager.STREAM_MUSIC, result.aospEquivalentStream)
        assertTrue(result.summary(true).contains("evidence=ambiguous-aosp-default"))
        assertTrue(result.summary(true).contains("carPlayAudio=unchanged"))
    }

    @Test fun standardStreamsRemainIdentifiableInTheReport() {
        for (stream in (1..6) + AudioManager.STREAM_DTMF) {
            val result = MusicOutputDiagnostics.read(stream) { 48_000 }
            assertEquals(stream, result.queriedStream)
            assertEquals(stream, result.aospEquivalentStream)
            assertTrue(result.summary(true).contains("mapping=aosp-standard"))
            assertTrue(result.summary(true).contains("reportedRate=48000 evidence=reported-only"))
        }
    }

    @Test fun vendorStreamQueryIsNotReportedAsProofOfTheConfiguredRoute() {
        for (stream in listOf(7) + (9..20)) {
            val queried = mutableListOf<Int>()
            val result = MusicOutputDiagnostics.read(stream) { queried += it; 44_100 }
            assertEquals(listOf(stream), queried)
            assertEquals(stream, result.configuredMediaStream)
            assertEquals(AudioManager.STREAM_MUSIC, result.aospEquivalentStream)
            assertTrue(result.summary(true).contains("mapping=aosp-remaps-vendor-unverified"))
            assertTrue(result.summary(true).contains("evidence=ambiguous-aosp-default"))
        }
    }

    @Test fun disabledCarPlayAudioIsExplicitlyReportedAsNotOffered() {
        val report = MusicOutputDiagnostics.read(14) { 44_100 }.summary(false)
        assertTrue(report.contains("carPlayAudio=not-offered"))
        assertFalse(report.contains("offered=44100"))
    }

    @Test fun vendorQueryExceptionRecordsOnlyTheClassWithoutChangingAnyOffer() {
        val result = MusicOutputDiagnostics.read(14) { throw IllegalStateException("private detail") }
        val report = result.summary(true)
        assertNull(result.reportedRate)
        assertTrue(report.contains("queryFailure=IllegalStateException"))
        assertTrue(report.contains("evidence=unavailable"))
        assertTrue(report.contains("carPlayAudio=unchanged"))
        assertFalse(report.contains("private detail"))
    }
}
