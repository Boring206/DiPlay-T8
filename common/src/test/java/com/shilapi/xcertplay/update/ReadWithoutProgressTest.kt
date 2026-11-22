package com.shilapi.xcertplay.update

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A non-standard stream can return 0 from read(byte[]) without making progress. Every byte-count
 * cap in the update path is expressed in terms of bytes read, so 0-byte reads bypass the cap and
 * leave the loop spinning. The stream below stops after a bounded number of zero-progress reads so
 * the tests stay deterministic even on an unfixed build.
 */
class ReadWithoutProgressTest {
    @get:Rule val temporary = TemporaryFolder()

    private class ZeroProgressStream : InputStream() {
        var calls = 0
        override fun read(): Int = -1
        override fun read(buffer: ByteArray): Int {
            calls++
            return if (calls >= 64) -1 else 0
        }
    }

    private class StubConnection(private val stream: InputStream) : HttpURLConnection(URL("http://localhost/")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = 200
        override fun getInputStream(): InputStream = stream
        override fun getContentLengthLong(): Long = -1
    }

    @Test fun aChecksumStreamThatMakesNoProgressIsReadOnceNotSixtyFourTimes() {
        val stream = ZeroProgressStream()
        UpdateChecksums.sha256Hex(stream)
        assertEquals("read(byte[]) was called ${stream.calls} times", 1, stream.calls)
    }

    @Test fun aTextResponseThatMakesNoProgressIsReadOnce() {
        val stream = ZeroProgressStream()
        assertEquals("", UpdateClient.fetchText("http://localhost/releases", null, "DiPlay/test") { StubConnection(stream) })
        assertEquals("read(byte[]) was called ${stream.calls} times", 1, stream.calls)
    }

    @Test fun aDownloadThatMakesNoProgressIsReadOnce() {
        val stream = ZeroProgressStream()
        val destination = temporary.newFile("apk")
        UpdateClient.download("http://localhost/apk", destination, { StubConnection(stream) }) { _, _ -> }
        assertEquals("read(byte[]) was called ${stream.calls} times", 1, stream.calls)
    }
}
