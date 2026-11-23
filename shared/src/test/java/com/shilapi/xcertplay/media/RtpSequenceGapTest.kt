package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Test

class RtpSequenceGapTest {
    private fun rtp(sequence: Int) = ByteArray(12).also {
        it[2] = (sequence shr 8).toByte()
        it[3] = sequence.toByte()
    }

    private fun lostBefore(vararg sequences: Int): List<Int> {
        val gap = RtpSequenceGap()
        return sequences.map { gap.lostBefore(rtp(it)) }
    }

    @Test fun consecutivePacketsLoseNothing() {
        assertEquals(listOf(0, 0, 0), lostBefore(7, 8, 9))
    }

    @Test fun aSkipCountsTheMissingPackets() {
        assertEquals(listOf(0, 0, 3), lostBefore(7, 8, 12))
    }

    @Test fun theSequenceWrapsWithoutALoss() {
        assertEquals(listOf(0, 0, 0), lostBefore(65534, 65535, 0))
    }

    @Test fun aLossAcrossTheWrapIsCounted() {
        assertEquals(listOf(0, 2), lostBefore(65534, 1))
    }

    @Test fun aRepeatedPacketLosesNothing() {
        assertEquals(listOf(0, 0, 0, 0), lostBefore(7, 8, 8, 9))
    }

    // 8 is still decoded after 9, so its hole was already counted when 9 arrived.
    @Test fun aLatePacketLosesNothingAndKeepsTheNewestSequence() {
        assertEquals(listOf(0, 1, 0, 0), lostBefore(7, 9, 8, 10))
    }

    @Test fun aRunOfLatePacketsIsARestartThatIsFollowed() {
        assertEquals(listOf(0, 0, 0, 0, 0, 1), lostBefore(1000, 50000, 50001, 50002, 50003, 50005))
    }

    @Test fun aPacketTooShortForASequenceLosesNothing() {
        assertEquals(0, RtpSequenceGap().lostBefore(ByteArray(3)))
    }
}
