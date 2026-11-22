package com.shilapi.xcertplay.airplay

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TLV8 bodies come from /pair-setup and /pair-verify RTSP requests before pair-verify has
 * authenticated the peer, so decode() must stay linear and bounded for hostile input.
 */
class Tlv8CodecTest {
    /** One raw TLV8 item: [type, length, value...]. `value.size` is used as the length byte. */
    private fun tlv(type: Int, value: ByteArray): ByteArray =
        byteArrayOf(type.toByte(), value.size.toByte()) + value

    @Test fun aSingleTlvAndSeveralTlvsDecode() {
        val single = Tlv8Codec.decode(tlv(1, byteArrayOf(9, 8, 7)))
        assertEquals(1, single.size)
        assertArrayEquals(byteArrayOf(9, 8, 7), single[1])

        val many = Tlv8Codec.decode(
            tlv(1, byteArrayOf(1)) +
                tlv(2, byteArrayOf(2, 2)) +
                tlv(3, byteArrayOf(3, 3, 3)),
        )
        assertEquals(3, many.size)
        assertArrayEquals(byteArrayOf(1), many[1])
        assertArrayEquals(byteArrayOf(2, 2), many[2])
        assertArrayEquals(byteArrayOf(3, 3, 3), many[3])

        // A zero-length value is valid (the encoder's separator uses one too).
        val empty = Tlv8Codec.decode(tlv(4, ByteArray(0)))
        assertArrayEquals(ByteArray(0), empty[4])
    }

    @Test fun aRepeatedTypeStillKeepsTheLastValue() {
        // Non-fragmented repeats overwrite, exactly as before.
        val decoded = Tlv8Codec.decode(
            tlv(6, byteArrayOf(1)) + tlv(6, byteArrayOf(2)),
        )
        assertArrayEquals(byteArrayOf(2), decoded[6])
    }

    @Test fun fullFragmentsPlusAShortTailAreReassembled() {
        val first = ByteArray(255) { 0x11.toByte() }
        val second = ByteArray(255) { 0x22.toByte() }
        val tail = byteArrayOf(0x33, 0x44, 0x55)
        val body = tlv(6, first) + tlv(6, second) + tlv(6, tail)

        val decoded = Tlv8Codec.decode(body)

        assertEquals(1, decoded.size)
        assertArrayEquals(first + second + tail, decoded[6])
    }

    @Test fun anEncodedLargeValueRoundTripsThroughFragments() {
        val value = ByteArray(600) { (it and 0xff).toByte() }
        val encoded = Tlv8Codec.encode(listOf(Tlv8Item(6, value)))

        val decoded = Tlv8Codec.decode(encoded)

        assertArrayEquals(value, decoded[6])
    }

    @Test(timeout = 10_000) fun aMegabyteOfFullFragmentsIsRejectedQuickly() {
        // ~4,000 x 255-byte fragments of one type: the old `previous + value` merge copied ~2 GB.
        val fragment = tlv(6, ByteArray(255))
        val hostile = ByteArrayOutputStream(1024 * 1024)
        while (hostile.size() < 1024 * 1024) hostile.write(fragment)

        val decoded = Tlv8Codec.decode(hostile.toByteArray())

        // Reassembly is capped: decode returns promptly with a bounded value, no OOM, no exception.
        val value = decoded[6]
        assertNotNull(value)
        assertTrue(value!!.size <= 64 * 1024)
    }

    @Test fun aReassemblyOverTheByteCapIsRejected() {
        // 300 x 255 bytes = 76,500 bytes, above the 64 KiB cap. It must not be fully reassembled.
        val fragment = tlv(6, ByteArray(255))
        val body = ByteArrayOutputStream()
        repeat(300) { body.write(fragment) }

        val decoded = Tlv8Codec.decode(body.toByteArray())

        val value = decoded[6]
        assertNotNull(value)
        val size = value!!.size
        assertTrue(size <= 64 * 1024)
        assertTrue(size < 300 * 255)
    }

    @Test fun tooManyFragmentsIsRejected() {
        // Break each chain with a short TLV of another type so the per-type byte cap never fires:
        // only the total-fragment cap can stop this. A trailing marker TLV must never be reached.
        val full = tlv(6, ByteArray(255))
        val breaker = tlv(1, ByteArray(0))
        val marker = tlv(99, byteArrayOf(7, 7, 7))
        val body = ByteArrayOutputStream()
        repeat(1100) {
            body.write(full)
            body.write(breaker)
        }
        body.write(marker)

        val decoded = Tlv8Codec.decode(body.toByteArray())

        assertNotNull(decoded[6])
        assertNull(decoded[99])
    }
}
