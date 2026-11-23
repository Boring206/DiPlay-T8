package com.shilapi.xcertplay.airplay

/**
 * Measures the RTP clock the iPhone uses on one audio stream from its own downlink packets, so a
 * report shows which clock a microphone uplink on that stream must count in.
 */
internal class RtpClockProbe(private val windowNs: Long = 2_000_000_000L) {
    companion object {
        private const val MAX_PLAUSIBLE_HZ = 384_000
    }

    private var firstSample = 0L
    private var firstNs = 0L
    private var started = false
    private var done = false

    /** Returns the measured clock in Hz once, after [windowNs] of packets; null otherwise. */
    @Synchronized
    fun observe(sample: Int, nowNs: Long): Int? {
        if (done) return null
        val unsigned = sample.toLong() and 0xffff_ffffL
        if (!started) {
            started = true
            firstSample = unsigned
            firstNs = nowNs
            return null
        }
        val elapsedNs = nowNs - firstNs
        if (elapsedNs < windowNs) return null
        val ticks = (unsigned - firstSample) and 0xffff_ffffL
        val hz = Math.round(ticks * 1_000_000_000.0 / elapsedNs)
        if (hz > MAX_PLAUSIBLE_HZ) {
            // A late/reordered packet or a timeline reset made the delta implausible.
            // Restart the window instead of reporting a clock no audio stream uses.
            firstSample = unsigned
            firstNs = nowNs
            return null
        }
        done = true
        return hz.toInt()
    }
}
