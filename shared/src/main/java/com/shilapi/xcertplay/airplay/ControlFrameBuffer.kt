package com.shilapi.xcertplay.airplay

/**
 * Reassembles control-channel requests that arrive in arbitrary chunks, without copying the whole
 * backlog on every read.
 *
 * `RtspMessage.parseMessages` returns the unconsumed tail as a fresh array. Calling it after each
 * socket read therefore copies the entire buffered message twice: once for the `+=` that appended
 * the new bytes, once for that tail. For a body arriving in N pieces the two copies add up to
 * O(N^2) bytes: the largest legitimate request (a corner-mask PNG plus its plist, about 576 KiB)
 * copies about 21 MiB in 16 KiB reads, and a peer dripping one byte at a time turns that into
 * hundreds of gigabytes. The header/body caps bound a single message, not the number of reads.
 *
 * This buffer grows geometrically (O(L) copying in total) and is scanned only while the header is
 * incomplete or once every byte needed by the message at the front has arrived. The caps are still
 * checked on the read that completes a header, so an oversized header or Content-Length is rejected
 * at exactly the same point as before.
 */
internal class ControlFrameBuffer(initialCapacity: Int = 16 * 1024) {
    private var buffer = ByteArray(initialCapacity)
    private var size = 0

    /** Total buffered bytes needed by the message at the front, or -1 while its header is incomplete. */
    private var needed = -1

    /** Scans performed, for tests and diagnostics; independent of how many reads delivered a body. */
    internal var scans = 0
        private set

    fun append(bytes: ByteArray, count: Int = bytes.size) {
        if (count <= 0) return
        ensureCapacity(size + count)
        bytes.copyInto(buffer, size, 0, count)
        size += count
    }

    private fun ensureCapacity(required: Int) {
        if (required <= buffer.size) return
        var capacity = buffer.size
        while (capacity < required && capacity < Int.MAX_VALUE / 2) capacity *= 2
        buffer = buffer.copyOf(maxOf(required, capacity))
    }

    /** Returns the complete messages buffered so far, keeping any partial tail for the next read. */
    fun drain(): List<RtspMessage.Request> {
        if (size == 0) return emptyList()
        // A known body length means the bytes seen so far cannot complete the front message. No
        // scan, no copy: this is the whole point of the buffer.
        if (needed >= 0 && size < needed) return emptyList()
        scans++
        val scanned = RtspMessage.scan(buffer, size)
        if (scanned.consumed > 0) {
            val remaining = size - scanned.consumed
            // System.arraycopy handles overlapping ranges, so compacting in place is safe.
            if (remaining > 0) buffer.copyInto(buffer, 0, scanned.consumed, size)
            size = remaining
            needed = if (scanned.needed < 0) -1 else scanned.needed - scanned.consumed
        } else {
            needed = scanned.needed
        }
        return scanned.messages
    }
}
