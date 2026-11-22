package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SprdDecoderTuningTest {
    @Test fun recognisesSpreadtrumDecodersOnly() {
        assertTrue(SprdDecoderTuning.isDecoderName("OMX.sprd.h264.decoder"))
        assertTrue(SprdDecoderTuning.isDecoderName("OMX.sprd.hevc.decoder"))
        assertTrue(SprdDecoderTuning.isDecoderName("c2.sprd.avc.decoder"))
        assertTrue(SprdDecoderTuning.isDecoderName("omx.sprd.video.decoder.avc"))
        assertFalse(SprdDecoderTuning.isDecoderName("OMX.MTK.VIDEO.DECODER.AVC"))
        assertFalse(SprdDecoderTuning.isDecoderName("c2.qti.avc.decoder"))
        assertFalse(SprdDecoderTuning.isDecoderName("c2.android.avc.decoder"))
    }

    @Test fun hardwareSpreadtrumDecoderIsPreferred() {
        assertEquals(
            SprdDecoderCandidate("OMX.sprd.h264.decoder", hardware = true),
            SprdDecoderTuning.selectedDecoder(listOf(
                SprdDecoderCandidate("OMX.sprd.h264.decoder"),
                SprdDecoderCandidate("OMX.sprd.h264.decoder", hardware = true),
            )),
        )
    }

    @Test fun nonSpreadtrumDecoderIsNeverSelected() {
        assertEquals(
            SprdDecoderCandidate("OMX.sprd.h264.decoder"),
            SprdDecoderTuning.selectedDecoder(listOf(
                SprdDecoderCandidate("OMX.qcom.video.decoder.avc", hardware = true),
                SprdDecoderCandidate("OMX.sprd.h264.decoder"),
            )),
        )
    }

    @Test fun noSpreadtrumDecoderMeansNoSelection() {
        assertNull(SprdDecoderTuning.selectedDecoder(listOf(
            SprdDecoderCandidate("OMX.qcom.video.decoder.avc", hardware = true),
        )))
        assertNull(SprdDecoderTuning.selectedDecoder(emptyList()))
    }

    @Test fun tunedAttemptsForSpreadtrumDecoderComeBeforeTheDefaultDecoder() {
        assertEquals(
            listOf(
                DecoderAttempt("OMX.sprd.h264.decoder", tuned = true, operatingRate = 30),
                DecoderAttempt("OMX.sprd.h264.decoder", tuned = true),
                DecoderAttempt(null, tuned = true, operatingRate = 30),
                DecoderAttempt(null, tuned = true),
                DecoderAttempt(null, tuned = false),
            ),
            videoDecoderAttempts(
                operatingRate = 30,
                softwareDecoder = null,
                sprdDecoder = "OMX.sprd.h264.decoder",
            ),
        )
    }
}
