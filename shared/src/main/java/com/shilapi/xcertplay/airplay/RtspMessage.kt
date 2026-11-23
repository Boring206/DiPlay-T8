package com.shilapi.xcertplay.airplay

import java.net.ProtocolException

/**
 * RTSP/HTTP-style request framing for the CarPlay control channel.
 *
 * The control connection on TCP :7000 speaks a text request line plus headers plus an optional
 * body, using the same framing RTSP and HTTP share. Encryption, once pair-verify completes, wraps
 * this framing in a separate layer.
 */
object RtspMessage {
    data class Request(
        val method: String,
        val path: String,
        val protocol: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    data class Response(
        val protocol: String? = null,
        val status: Int? = null,
        val statusText: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val body: ByteArray = ByteArray(0),
    )

    data class Parsed(val messages: List<Request>, val rest: ByteArray)

    /**
     * The result of scanning a reusable buffer in place. [consumed] is how many leading bytes
     * belong to complete messages; [needed] is the total buffer size required to finish the
     * message at the front, or -1 when its header has not ended yet.
     */
    internal data class Scan(val messages: List<Request>, val consumed: Int, val needed: Int)

    /** Real requests carry headers under 1 KiB; the cap leaves ample room. */
    private const val MAX_HEADER_BYTES = 16 * 1024

    /** Above the largest legitimate body, a corner-mask PNG plus its plist wrapper (about 576 KiB). */
    private const val MAX_BODY_BYTES = 1024 * 1024

    private val headerEnd = "\r\n\r\n".toByteArray(Charsets.US_ASCII)
    private val statusText = mapOf(
        200 to "OK",
        400 to "Bad Request",
        404 to "Not Found",
        500 to "Internal Server Error",
    )

    /**
     * Parses as many complete messages as are buffered, retaining any partial remainder.
     *
     * @throws ProtocolException when a header or body exceeds its cap, so a peer cannot make the caller buffer without limit.
     */
    fun parseMessages(buffer: ByteArray): Parsed {
        val scanned = scan(buffer, buffer.size)
        return Parsed(scanned.messages, buffer.copyOfRange(scanned.consumed, buffer.size))
    }

    /**
     * The same framing as [parseMessages], but reading only `[0, limit)` of a reusable [buffer] and
     * reporting a consumed offset instead of copying the remainder. A caller that appends arriving
     * bytes in place can therefore avoid copying the whole backlog on every read. [needed] lets it
     * skip scanning altogether until the current message is complete; the header and body caps are
     * still enforced on the read that finishes the header.
     */
    internal fun scan(buffer: ByteArray, limit: Int): Scan {
        val messages = ArrayList<Request>()
        var offset = 0
        while (offset < limit) {
            val headerEndIndex = indexOf(buffer, headerEnd, offset, limit)
            if (headerEndIndex == null || headerEndIndex - offset > MAX_HEADER_BYTES) {
                if (limit - offset > MAX_HEADER_BYTES) throw ProtocolException("RTSP header over $MAX_HEADER_BYTES bytes")
                return Scan(messages, offset, -1)
            }
            val headerText = String(buffer, offset, headerEndIndex - offset, Charsets.US_ASCII)
            val lines = headerText.split("\r\n")
            if (lines.isEmpty()) {
                offset = headerEndIndex + headerEnd.size
                continue
            }
            val requestLine = lines.first().split(" ", limit = 3)
            val headers = HashMap<String, String>()
            for (line in lines.drop(1)) {
                val separator = line.indexOf(':')
                if (separator == -1) continue
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
            val bodyStart = headerEndIndex + headerEnd.size
            val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            if (contentLength > MAX_BODY_BYTES) throw ProtocolException("RTSP body of $contentLength bytes over $MAX_BODY_BYTES")
            val bodyEnd = bodyStart + contentLength
            if (bodyEnd > limit) return Scan(messages, offset, bodyEnd)
            messages.add(
                Request(
                    method = requestLine[0],
                    path = requestLine.getOrElse(1) { "" },
                    protocol = requestLine.getOrElse(2) { "RTSP/1.0" },
                    headers = headers,
                    body = buffer.copyOfRange(bodyStart, bodyEnd),
                ),
            )
            offset = bodyEnd
        }
        return Scan(messages, offset, -1)
    }

    /** Builds a response, echoing the request protocol and CSeq. */
    fun buildResponse(request: Request, response: Response): ByteArray {
        val protocol = response.protocol ?: request.protocol
        val status = response.status ?: 200
        val statusLine = response.statusText ?: statusText[status] ?: "OK"
        val body = response.body
        val headers = LinkedHashMap<String, String>(response.headers)
        request.headers["cseq"]?.let { headers["CSeq"] = it }
        headers["Content-Length"] = body.size.toString()

        val head = buildString {
            append(protocol).append(' ').append(status).append(' ').append(statusLine).append("\r\n")
            for ((name, value) in headers) append(name).append(": ").append(value).append("\r\n")
            append("\r\n")
        }
        return head.toByteArray(Charsets.US_ASCII) + body
    }

    private fun indexOf(source: ByteArray, target: ByteArray, start: Int, limit: Int): Int? {
        if (target.isEmpty()) return start
        val first = target[0]
        // `limit`, not source.size: a reusable buffer holds stale bytes past the bytes read so far.
        for (index in start..limit - target.size) {
            if (source[index] == first && matches(source, index, target)) return index
        }
        return null
    }

    private fun matches(source: ByteArray, offset: Int, target: ByteArray): Boolean {
        for (index in target.indices) {
            if (source[offset + index] != target[index]) return false
        }
        return true
    }
}
