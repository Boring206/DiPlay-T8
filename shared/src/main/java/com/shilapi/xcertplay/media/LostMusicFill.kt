package com.shilapi.xcertplay.media

/**
 * Plays silence where Wi-Fi lost music packets (#1205).
 *
 * Without it the decoded music closes up over each hole and runs ahead of the iPhone: every lost packet
 * drains the buffer by its length, the clock trim slows the music to its limit and still falls behind, and
 * playback stops to refill. One head unit lost packets in most 5 s windows and refilled 23 times in about
 * five minutes. Silence keeps the music on the iPhone's clock, so the buffer holds its level.
 *
 * Only what Wi-Fi lost is put back: at most the lost packets' length, and only while the track still plays
 * what it held before the hole. A track that ran dry or is refilling has already spent that time, so silence
 * there would only add latency. Packets DiPlay drops on purpose to catch up keep their hole, and a decoder
 * that does not pass the packets' timestamps through shows no hole, so it plays as before.
 */
internal class LostMusicFill(private val sampleRate: Int) {
    private class Mark(val ptsUs: Long, val lostPackets: Int)

    private val marks = ArrayDeque<Mark>()
    private var nextUs: Long? = null

    /** The packet that starts at [ptsUs] arrived after Wi-Fi lost [lostPackets] packets. */
    fun lossBefore(ptsUs: Long, lostPackets: Int) {
        if (marks.size == MAX_PENDING) marks.removeFirst()
        marks.addLast(Mark(ptsUs, lostPackets))
    }

    /**
     * Frames of silence to play before a decoded buffer that starts at [ptsUs] and holds [frames], never more
     * than [maxFrames]: the renderer's start level, or 0 when the track ran dry or is refilling.
     */
    fun silenceBefore(ptsUs: Long, frames: Int, maxFrames: Int): Int {
        val halfFrameUs = frames * 500_000L / sampleRate
        // A timestamp restart leaves marks far ahead that no buffer will reach.
        while (marks.isNotEmpty() && marks.first().ptsUs.let { it < ptsUs - halfFrameUs || it > ptsUs + STALE_AHEAD_US }) {
            marks.removeFirst()
        }
        val expected = nextUs
        nextUs = ptsUs + frames * 1_000_000L / sampleRate
        val mark = marks.firstOrNull()
        if (expected == null || mark == null || mark.ptsUs > ptsUs + halfFrameUs) return 0
        marks.removeFirst()
        val missingUs = ptsUs - expected
        if (missingUs < halfFrameUs) return 0
        val missingFrames = (missingUs * sampleRate + 500_000L) / 1_000_000L
        return minOf(missingFrames, mark.lostPackets.toLong() * frames, maxFrames.toLong()).coerceAtLeast(0L).toInt()
    }

    internal companion object {
        const val STALE_AHEAD_US = 2_000_000L
        const val MAX_PENDING = 64
    }
}

/** Counts the packets lost before each RTP packet from its sequence number. One thread feeds it. */
internal class RtpSequenceGap {
    private var last = -1
    private var lateInARow = 0

    /** Packets missing between the newest packet so far and [rtp]: 0 for the next one, a repeat or a late one. */
    fun lostBefore(rtp: ByteArray): Int {
        if (rtp.size < 4) return 0
        val sequence = ((rtp[2].toInt() and 0xff) shl 8) or (rtp[3].toInt() and 0xff)
        val previous = last
        if (previous < 0) {
            last = sequence
            return 0
        }
        val step = (sequence - previous) and 0xffff
        if (step == 0) return 0
        if (step >= 0x8000) {
            // Reordering makes one or two packets late. A run of them means the sequence restarted, so follow it.
            if (++lateInARow < RESTART_AFTER_LATE) return 0
            lateInARow = 0
            last = sequence
            return 0
        }
        lateInARow = 0
        last = sequence
        return step - 1
    }

    internal companion object {
        const val RESTART_AFTER_LATE = 4
    }
}
