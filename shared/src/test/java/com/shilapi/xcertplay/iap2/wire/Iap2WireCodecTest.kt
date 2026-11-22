package com.shilapi.xcertplay.iap2.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class Iap2WireCodecTest {
    @Test fun aNulTerminatedStringRoundTrips() {
        assertEquals("hello", Iap2WireCodec.readString(Iap2WireCodec.string("hello")))
    }

    @Test fun anEmbeddedNulIsRejectedJustAsTheEncoderRejectsIt() {
        val encoded = byteArrayOf('a'.code.toByte(), 0, 'b'.code.toByte(), 0)
        try {
            val decoded = Iap2WireCodec.readString(encoded)
            fail("An embedded NUL must be rejected, got $decoded")
        } catch (error: Iap2ProtocolException) {
            assertEquals("CSM string contains an embedded NUL", error.message)
        }
    }

    @Test fun aStringWithoutATerminatorIsRejected() {
        try {
            Iap2WireCodec.readString("abc".encodeToByteArray())
            fail("A missing terminator must be rejected")
        } catch (error: Iap2ProtocolException) {
            assertEquals("CSM string is not NUL terminated", error.message)
        }
    }
}
