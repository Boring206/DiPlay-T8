package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Test

class LostMusicFillTest {
    private val frames = 1024
    private val holds: (Int) -> Int = { Int.MAX_VALUE }

    private fun ptsUs(packet: Int) = packet * frames * 1_000_000L / 48_000

    /**
     * Sends packets 0 until [count] except [missing] through a decoder and returns the frames played. Wi-Fi lost
     * the packets in [lost], and DiPlay dropped the rest of [missing] on purpose, after reading their sequence
     * numbers. The next packet that reaches the decoder after a Wi-Fi loss carries how many were lost, and a
     * packet dropped on purpose takes that count with it. [maxFrames] is the renderer's limit at each packet. A
     * decoder that does not pass timestamps through numbers its output buffers itself.
     */
    private fun play(
        count: Int,
        missing: Set<Int>,
        lost: Set<Int> = missing,
        maxFrames: (Int) -> Int = holds,
        passesTimestamps: Boolean = true,
    ): Long {
        val fill = LostMusicFill(48_000)
        var played = 0L
        var output = 0
        var lostBefore = 0
        for (packet in 0 until count) {
            if (packet in missing) {
                lostBefore = if (packet in lost) lostBefore + 1 else 0
                continue
            }
            if (lostBefore > 0) fill.lossBefore(ptsUs(packet), lostBefore)
            lostBefore = 0
            val pts = if (passesTimestamps) ptsUs(packet) else ptsUs(output)
            played += fill.silenceBefore(pts, frames, maxFrames(packet)) + frames
            output++
        }
        return played
    }

    @Test fun lostPacketsKeepTheMusicOnTheIphonesClock() {
        assertEquals(500L * frames, play(500, setOf(10, 50, 51, 52, 300)))
    }

    @Test fun packetsDroppedOnPurposeKeepTheirHole() {
        assertEquals(495L * frames, play(500, setOf(10, 50, 51, 52, 300), lost = emptySet()))
    }

    @Test fun aDropOnPurposeRightBeforeALossFillsOnlyTheLostPacket() {
        assertEquals(498L * frames, play(500, setOf(50, 51, 52), lost = setOf(52)))
    }

    @Test fun aDecoderThatNumbersItsOwnOutputPlaysAsBefore() {
        assertEquals(495L * frames, play(500, setOf(10, 50, 51, 52, 300), passesTimestamps = false))
    }

    @Test fun aFillStopsAtTheStartLevel() {
        val stall = (100 until 130).toSet()
        assertEquals((500L - stall.size + 10) * frames, play(500, stall, maxFrames = { 10 * frames }))
    }

    // A track that ran dry already spent the hole's time; silence there would only add latency.
    @Test fun aTrackThatRanDryGetsNoSilenceAndLaterHolesStillFill() {
        assertEquals(499L * frames, play(500, setOf(10, 50, 51, 52, 300), maxFrames = { if (it == 11) 0 else Int.MAX_VALUE }))
    }

    @Test fun aMarkFarAheadOfTheMusicDoesNotBlockLaterHoles() {
        val fill = LostMusicFill(48_000)
        fill.lossBefore(ptsUs(1_000_000), 1)
        fill.silenceBefore(ptsUs(0), frames, Int.MAX_VALUE)
        fill.lossBefore(ptsUs(2), 1)
        assertEquals(frames, fill.silenceBefore(ptsUs(2), frames, Int.MAX_VALUE))
    }
}
