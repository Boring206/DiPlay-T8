package com.shilapi.xcertplay.airplay

import java.net.ProtocolException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Partial-read coverage for the control-channel reassembly. The old loop called
 * `RtspMessage.parseMessages` after every socket read, which copied the whole backlog twice per
 * read; these tests pin both the framing behaviour and the number of scans.
 */
class ControlFrameBufferTest {
    private fun request(contentLength: Int, body: ByteArray, cseq: Int = 1): ByteArray =
        ("POST /x RTSP/1.0\r\nCSeq: $cseq\r\nContent-Length: $contentLength\r\n\r\n")
            .toByteArray(Charsets.US_ASCII) + body

    @Test fun everySplitPointKeepsMessagesWithoutLossOrDuplication() {
        val body = ByteArray(2_048) { (it % 251).toByte() }
        val tail = "GET /in".toByteArray(Charsets.US_ASCII)
        val wire = request(body.size, body, 1) + request(3, byteArrayOf(1, 2, 3), 2) + tail
        for (split in 0..wire.size) {
            val frames = ControlFrameBuffer()
            val first = wire.copyOfRange(0, split)
            val second = wire.copyOfRange(split, wire.size)
            frames.append(first)
            val seen = frames.drain().toMutableList()
            frames.append(second)
            seen += frames.drain()
            assertEquals("split=$split", 2, seen.size)
            assertArrayEquals("split=$split body0", body, seen[0].body)
            assertArrayEquals("split=$split body1", byteArrayOf(1, 2, 3), seen[1].body)
            assertEquals("split=$split cseq", "2", seen[1].headers["cseq"])
            // A third drain must not hand out the same messages twice.
            assertTrue("split=$split", frames.drain().isEmpty())
        }
    }

    @Test fun aHeaderTerminatorSplitAcrossReadsIsReassembled() {
        val wire = request(5, byteArrayOf(9, 8, 7, 6, 5))
        val terminator = (0..wire.size - 4).first { i ->
            wire[i] == '\r'.code.toByte() && wire[i + 1] == '\n'.code.toByte() &&
                wire[i + 2] == '\r'.code.toByte() && wire[i + 3] == '\n'.code.toByte()
        }
        val frames = ControlFrameBuffer()
        val seen = mutableListOf<RtspMessage.Request>()
        // The four-byte terminator arrives as two separate reads.
        for (piece in listOf(0 until terminator + 2, terminator + 2 until terminator + 4,
            terminator + 4 until wire.size)) {
            frames.append(wire.copyOfRange(piece.first, piece.last + 1))
            seen += frames.drain()
        }
        assertEquals(1, seen.size)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6, 5), seen[0].body)
    }

    @Test fun aBodyArrivingInOnePieceIsScannedOnlyTwice() {
        val body = ByteArray(256 * 1024) { (it % 251).toByte() }
        val wire = request(body.size, body)
        val headerLength = wire.size - body.size
        val frames = ControlFrameBuffer()
        frames.append(wire.copyOfRange(0, headerLength))
        assertTrue(frames.drain().isEmpty())
        assertEquals(1, frames.scans)
        frames.append(body)
        val seen = frames.drain()
        assertEquals(1, seen.size)
        assertArrayEquals(body, seen[0].body)
        assertEquals(2, frames.scans)
    }

    @Test fun aBodyDrippedOneByteAtATimeDoesNotRescanTheBacklog() {
        val body = ByteArray(64 * 1024) { (it % 251).toByte() }
        val wire = request(body.size, body)
        val headerLength = wire.size - body.size
        val frames = ControlFrameBuffer()
        val seen = mutableListOf<RtspMessage.Request>()
        for (byte in wire) {
            frames.append(byteArrayOf(byte))
            seen += frames.drain()
        }
        assertEquals(1, seen.size)
        assertArrayEquals(body, seen[0].body)
        // Only the header bytes need a scan. The old loop scanned (and copied) once per read,
        // i.e. wire.size times; here 64 KiB of body must not add a single scan.
        assertTrue("scans=${frames.scans} for ${wire.size} one-byte reads",
            frames.scans <= headerLength + 2)
        assertTrue("scans=${frames.scans} must stay far below the number of reads",
            frames.scans < wire.size / 100)
    }

    @Test fun anOversizedContentLengthIsRejectedWhenItsHeaderArrives() {
        val frames = ControlFrameBuffer()
        frames.append(request(1_500_000_000, ByteArray(0)))
        try {
            frames.drain()
            fail("expected ProtocolException")
        } catch (_: ProtocolException) {
        }
    }

    @Test fun aHeaderWithoutATerminatorIsStillRejectedAtTheCap() {
        val frames = ControlFrameBuffer()
        val huge = "GET /info RTSP/1.0\r\nX: ".toByteArray(Charsets.US_ASCII) +
            ByteArray(64 * 1024) { 'a'.code.toByte() }
        frames.append(huge)
        try {
            frames.drain()
            fail("expected ProtocolException")
        } catch (_: ProtocolException) {
        }
    }

    @Test fun staleBytesPastTheLimitAreNotMistakenForAHeaderTerminator() {
        val stale = request(0, ByteArray(0))
        val buffer = ByteArray(stale.size + 64)
        stale.copyInto(buffer)
        // Only the first 10 bytes are new; the old "\r\n\r\n" later in the reusable buffer is stale.
        val scan = RtspMessage.scan(buffer, 10)
        assertTrue(scan.messages.isEmpty())
        assertEquals(-1, scan.needed)
    }

    @Test fun scanAndParseMessagesAgreeOnEverySplitPoint() {
        val body = ByteArray(512) { (it % 251).toByte() }
        val wire = request(body.size, body) + request(0, ByteArray(0), 2) + "GET /".toByteArray()
        for (split in 0..wire.size) {
            val prefix = wire.copyOfRange(0, split)
            val parsed = RtspMessage.parseMessages(prefix)
            val scan = RtspMessage.scan(prefix, split)
            assertEquals("split=$split messages", parsed.messages.size, scan.messages.size)
            assertArrayEquals("split=$split rest", parsed.rest,
                prefix.copyOfRange(scan.consumed, prefix.size))
        }
    }
}
