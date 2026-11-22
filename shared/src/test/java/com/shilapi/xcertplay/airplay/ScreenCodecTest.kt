package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ScreenCodecTest {
    @Test
    fun validLengthPrefixesBecomeAnnexBInPlace() {
        val first = byteArrayOf(0x40, 0x01)
        val second = byteArrayOf(0x42, 0x01, 0x02)
        val payload =
            byteArrayOf(0, 0, 0, first.size.toByte()) + first +
                byteArrayOf(0, 0, 0, second.size.toByte()) + second

        val converted = ScreenCodec.lengthPrefixedToAnnexB(payload)

        assertSame(payload, converted)
        assertArrayEquals(
            byteArrayOf(0, 0, 0, 1) + first +
                byteArrayOf(0, 0, 0, 1) + second,
            converted,
        )
    }

    @Test
    fun malformedLengthsLeavePayloadUntouched() {
        val payload = byteArrayOf(0, 0, 0, 5, 0x40, 0x01)
        val original = payload.copyOf()

        assertSame(payload, ScreenCodec.lengthPrefixedToAnnexB(payload))
        assertArrayEquals(original, payload)
    }

    @Test
    fun overflowingLengthPrefixesLeavePayloadUntouched() {
        // A length such as 0x7FFFFFFF is a large positive Int: the old `offset + length` wrapped
        // negative and reached readU32Be with a negative index. Sign-bit lengths become negative
        // Ints. Any thrown exception fails the test, so these must return the original array intact.
        val malformed =
            listOf(
                byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0, 0, 0, 1),
                byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 1),
                byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0, 0, 0, 1),
            )
        for (payload in malformed) {
            val original = payload.copyOf()
            assertSame(payload, ScreenCodec.lengthPrefixedToAnnexB(payload))
            assertArrayEquals(original, payload)
        }
    }

    @Test
    fun nalLengthExactlyFillsRemainingBytes() {
        val nal = byteArrayOf(0x41, 0x02, 0x03, 0x04)
        // The single NAL's length equals the bytes left after its 4-byte prefix; the strict
        // `length > payload.size - offset` check must not reject this exact-fit boundary.
        val payload = byteArrayOf(0, 0, 0, nal.size.toByte()) + nal

        val converted = ScreenCodec.lengthPrefixedToAnnexB(payload)

        assertSame(payload, converted)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1) + nal, converted)
    }

    @Test fun readsTheFrameTimeFromTheScreenHeader() {
        val header = ByteArray(128)
        // NTP 32.32 little-endian at byte 8: 3 s + 0x40000000/2^32 s = 3.25 s.
        val raw = (3L shl 32) or 0x4000_0000L
        for (i in 0 until 8) header[8 + i] = (raw ushr (8 * i)).toByte()
        assertEquals(3_250_000_000L, ScreenCodec.senderNanos(header))
        // One 1/60 s step, as the iPhone stamps consecutive main-screen frames.
        val next = raw + 0x0444_4444L
        for (i in 0 until 8) header[8 + i] = (next ushr (8 * i)).toByte()
        assertEquals(16_666_666L, ScreenCodec.senderNanos(header) - 3_250_000_000L)
        assertEquals(0L, ScreenCodec.senderNanos(ByteArray(12)))
    }
}
