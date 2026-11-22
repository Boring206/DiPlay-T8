package com.shilapi.xcertplay

import android.media.AudioManager
import android.media.AudioTrack

/** Observations only: the public API cannot prove the output rate or a vendor stream's route. */
internal data class MusicOutputDiagnostics(
    val configuredMediaStream: Int,
    val queriedStream: Int,
    val aospEquivalentStream: Int,
    val reportedRate: Int?,
    val failureClass: String?,
) {
    fun summary(audioOffered: Boolean): String {
        val rateEvidence = when {
            reportedRate == null || reportedRate <= 0 -> "unavailable"
            reportedRate == 44_100 -> "ambiguous-aosp-default"
            else -> "reported-only"
        }
        val mapping = if (queriedStream == aospEquivalentStream) "aosp-standard"
            else "aosp-remaps-vendor-unverified"
        return "Audio: music output diagnostic configuredStream=$configuredMediaStream " +
            "configuredRoute=${if (configuredMediaStream == 0) "usage-media" else "legacy-stream"} " +
            "queriedStream=$queriedStream aospEquivalentStream=$aospEquivalentStream " +
            "mapping=$mapping reportedRate=${reportedRate ?: "unavailable"} evidence=$rateEvidence " +
            "queryFailure=${failureClass ?: "none"} " +
            "carPlayAudio=${if (audioOffered) "unchanged" else "not-offered"}"
    }

    companion object {
        fun read(
            mediaStreamOverride: Int,
            query: (Int) -> Int = AudioTrack::getNativeOutputSampleRate,
        ): MusicOutputDiagnostics {
            val requested = mediaStreamOverride.takeIf { it > 0 } ?: AudioManager.STREAM_MUSIC
            // AOSP's android_media_AudioTrack_get_output_sample_rate forwards only 0..6 and 8.
            // Other values become AUDIO_STREAM_DEFAULT (STREAM_MUSIC). Vendor patches are
            // not observable through this API, so this is an AOSP equivalent, not a route proof.
            val equivalent = if (requested in 0..6 || requested == AudioManager.STREAM_DTMF) requested
                else AudioManager.STREAM_MUSIC
            // JNI failures also return 44100, indistinguishable from a real 44.1 kHz mixer.
            // Never use this observation to choose an AirPlay format or change the audio route.
            val result = runCatching { query(requested) }
            return MusicOutputDiagnostics(mediaStreamOverride, requested, equivalent,
                result.getOrNull(), result.exceptionOrNull()?.javaClass?.simpleName)
        }
    }
}
