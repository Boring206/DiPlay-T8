package com.shilapi.xcertplay.airplay

import java.io.ByteArrayOutputStream

/** HomeKit/AirPlay-style TLV8 codec used by the pairing messages. */
object Tlv8Codec {
    private const val SEPARATOR_TYPE = 0xff
    private const val MAX_FRAGMENT = 255
    // A hostile /pair-setup or /pair-verify body (capped at 1 MiB) can be one long run of 255-byte
    // fragments of a single type. Bound a reassembled value and the total fragment count so such a
    // body cannot force unbounded copying/allocation before pair-verify has authenticated the peer.
    private const val MAX_REASSEMBLED_BYTES = 64 * 1024
    private const val MAX_TOTAL_FRAGMENTS = 1024

    fun encode(items: List<Tlv8Item>): ByteArray {
        val chunks = ArrayList<ByteArray>()
        var previousType: Int? = null
        for (item in items) {
            if (previousType == item.type) {
                chunks.add(byteArrayOf(SEPARATOR_TYPE.toByte(), 0))
            }
            var offset = 0
            do {
                val length = minOf(MAX_FRAGMENT, item.value.size - offset)
                chunks.add(byteArrayOf(item.type.toByte(), length.toByte()))
                chunks.add(item.value.copyOfRange(offset, offset + length))
                offset += length
            } while (offset < item.value.size)
            previousType = item.type
        }
        return concatBytes(*chunks.toTypedArray())
    }

    fun decode(buffer: ByteArray): Map<Int, ByteArray> {
        // Accumulate each type in a growable buffer. `previous + value` re-copied the whole value
        // on every fragment (O(n^2)); appending here is amortized O(1) per fragment. Like a
        // truncated item, hitting a cap just stops decoding and returns the partial map: no
        // exception escapes into the (pre-auth) control loop, matching this file's existing style.
        val output = HashMap<Int, ByteArrayOutputStream>()
        var position = 0
        var lastType: Int? = null
        var lastLength = 0
        var totalFragments = 0
        while (position + 2 <= buffer.size) {
            val type = buffer[position].toInt() and 0xff
            val length = buffer[position + 1].toInt() and 0xff
            val valueStart = position + 2
            if (valueStart + length > buffer.size) break
            position = valueStart + length

            if (type == lastType && lastLength == MAX_FRAGMENT) {
                // Continuation of a fragmented value: append without re-copying earlier bytes.
                val acc = output[type] ?: ByteArrayOutputStream().also { output[type] = it }
                if (acc.size() + length > MAX_REASSEMBLED_BYTES) break
                totalFragments += 1
                if (totalFragments > MAX_TOTAL_FRAGMENTS) break
                acc.write(buffer, valueStart, length)
            } else {
                // A fresh item (possibly the head of a new fragment chain). Replacing the buffer
                // keeps the original "last value wins" handling of repeated/separated types.
                if (length == MAX_FRAGMENT) {
                    totalFragments += 1
                    if (totalFragments > MAX_TOTAL_FRAGMENTS) break
                }
                val acc = ByteArrayOutputStream(length)
                acc.write(buffer, valueStart, length)
                output[type] = acc
            }
            lastType = type
            lastLength = length
        }
        val result = HashMap<Int, ByteArray>(output.size)
        for ((type, acc) in output) result[type] = acc.toByteArray()
        return result
    }
}

data class Tlv8Item(val type: Int, val value: ByteArray)
