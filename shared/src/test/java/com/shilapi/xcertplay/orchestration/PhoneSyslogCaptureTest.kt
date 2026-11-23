package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSyslogCaptureTest {
    private class Relay(vararg chunks: String) : BlockingDuplexByteStream {
        private val pending = ArrayDeque(chunks.map(String::toByteArray))
        var closed = false

        override fun send(data: ByteArray) = Unit
        override fun recv(maxBytes: Int, timeoutMillis: Long): ByteArray = pending.removeFirstOrNull() ?: ByteArray(0)
        override fun close() {
            closed = true
        }
    }

    // Each line carries the iPhone's name, so without the trace tag a wired start must not even open the service.
    @Test fun withoutTheTraceTagTheServiceIsNeverOpened() {
        val log = mutableListOf<String>()
        var opened = false
        PhoneSyslogCapture.start(wanted = false, open = { opened = true; Relay() }, isClosed = { false }, log = log::add)
        assertFalse(opened)
        assertTrue(log.isEmpty())
    }

    @Test fun onlyAccessoryAndAuthenticationLinesReachTheLog() {
        val log = mutableListOf<String>()
        val relay = Relay(
            "Nov 16 12:00:01 iPhone accessoryd[120] <Notice>: auth ok\u0000Nov 16 12:00:01 iPhone kernel[0] <Notice>: wifi\n",
            "Nov 16 12:00:02 iPhone iap2d(IAP2)[88] <Notice>: link up\n",
        )
        PhoneSyslogCapture.copy(relay, isClosed = { false }, log = log::add)
        assertEquals(listOf(
            "PHONE Nov 16 12:00:01 iPhone accessoryd[120] <Notice>: auth ok",
            "PHONE Nov 16 12:00:02 iPhone iap2d(IAP2)[88] <Notice>: link up",
        ), log)
        assertTrue(relay.closed)
    }

    @Test fun aServiceThatCannotOpenIsReportedOnce() {
        val log = mutableListOf<String>()
        PhoneSyslogCapture.start(wanted = true, open = { throw IllegalStateException("InvalidService") },
            isClosed = { false }, log = log::add)
        assertEquals(listOf("phone authentication diagnostics unavailable: InvalidService"), log)
    }
}
