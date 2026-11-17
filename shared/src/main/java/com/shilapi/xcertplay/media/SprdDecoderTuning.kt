package com.shilapi.xcertplay.media

import android.media.MediaCodecList
import android.os.Build

/**
 * Detection for Spreadtrum (Unisoc) OMX and Codec2 decoders.
 *
 * Unlike MediaTek's ACodec keys, Spreadtrum publishes no vendor low-latency parameters, so the
 * tuning here is the vendor-independent part only: the decoder is selected explicitly and tried
 * with a tuned format, and decoded frames are released from a dedicated thread. A refused tuned
 * attempt falls back to the default decoder in the same configure round.
 */
object SprdDecoderTuning {
    fun isDecoderName(name: String): Boolean =
        name.startsWith("OMX.sprd.", ignoreCase = true) ||
            name.startsWith("c2.sprd.", ignoreCase = true)

    /** The Spreadtrum decoder for [mime], or null when the unit has none. */
    fun selectedDecoderName(mime: String): String? = candidates(mime).let(::selectedDecoder)?.name

    /** Prefer a hardware Spreadtrum decoder; fall back to any Spreadtrum decoder for [mime]. */
    internal fun selectedDecoder(candidates: List<SprdDecoderCandidate>): SprdDecoderCandidate? =
        candidates.firstOrNull { it.hardware } ?: candidates.firstOrNull()

    // The codec list does not change while DiPlay runs, and Settings asks on every render.
    private val candidatesByMime = java.util.concurrent.ConcurrentHashMap<String, List<SprdDecoderCandidate>>()

    private fun candidates(mime: String): List<SprdDecoderCandidate> =
        candidatesByMime.getOrPut(mime.lowercase()) { listCandidates(mime) }

    private fun listCandidates(mime: String): List<SprdDecoderCandidate> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.mapNotNull { info ->
            if (info.isEncoder || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info.isAlias) ||
                info.supportedTypes.none { it.equals(mime, ignoreCase = true) } ||
                !isDecoderName(info.name)) return@mapNotNull null
            SprdDecoderCandidate(info.name, info.isHardwareAccelerated)
        }
    }.getOrDefault(emptyList())
}

internal data class SprdDecoderCandidate(val name: String, val hardware: Boolean = false)
