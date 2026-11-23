package com.shilapi.xcertplay.transport

/** Neighbor discovery for the Ethernet side of the NCM/TUN adapter (RFC 4861 sections 7.1/7.2).
 * Android's per-app VPN routing can select Wi-Fi for kernel multicast ND processing. Answer
 * solicitations for our one USB address here, without changing system routes or other traffic.
 */
object UsbNeighborDiscovery {
    fun advertisement(packet: ByteArray, hostAddress: ByteArray, hostMac: ByteArray): ByteArray? {
        require(hostAddress.size == 16 && hostMac.size == 6)
        if (packet.size < 64 || (packet[0].toInt() and 0xf0) != 0x60 ||
            u8(packet[6]) != 58 || u8(packet[7]) != 255 ||
            u8(packet[40]) != 135 || u8(packet[41]) != 0) return null
        val length = (u8(packet[4]) shl 8) or u8(packet[5])
        if (length < 24 || 40 + length > packet.size) return null
        if (!packet.copyOfRange(48, 64).contentEquals(hostAddress)) return null
        // Leave DAD (unspecified source) and non-link-local sources to the kernel.
        if (u8(packet[8]) != 0xfe || (u8(packet[9]) and 0xc0) != 0x80) return null
        val group = ByteArray(16).also {
            it[0] = 0xff.toByte(); it[1] = 2; it[11] = 1; it[12] = 0xff.toByte()
            hostAddress.copyInto(it, 13, 13, 16)
        }
        val destination = packet.copyOfRange(24, 40)
        if (!destination.contentEquals(hostAddress) && !destination.contentEquals(group)) return null
        var option = 64
        while (option < 40 + length) {
            if (option + 2 > 40 + length) return null
            val bytes = u8(packet[option + 1]) * 8
            if (bytes == 0 || option + bytes > 40 + length) return null
            if (u8(packet[option]) == 1 && bytes != 8) return null
            option += bytes
        }
        if (checksumSum(packet, length) != 0xffff) return null
        return ByteArray(72).also { reply ->
            reply[0] = 0x60; reply[5] = 32; reply[6] = 58; reply[7] = 255.toByte()
            hostAddress.copyInto(reply, 8)
            packet.copyInto(reply, 24, 8, 24)
            reply[40] = 136.toByte(); reply[44] = 0x60 // Solicited + Override, never Router.
            hostAddress.copyInto(reply, 48)
            reply[64] = 2; reply[65] = 1
            hostMac.copyInto(reply, 66)
            val checksum = checksumSum(reply, 32).inv() and 0xffff
            reply[42] = (checksum ushr 8).toByte(); reply[43] = checksum.toByte()
        }
    }

    private fun u8(value: Byte) = value.toInt() and 0xff
    private fun checksumSum(packet: ByteArray, length: Int): Int {
        var sum = length.toLong() + 58
        fun add(start: Int, count: Int) {
            for (i in start until start + count step 2) {
                sum += u8(packet[i]).toLong() shl 8
                if (i + 1 < start + count) sum += u8(packet[i + 1])
            }
        }
        add(8, 32); add(40, length)
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.toInt()
    }
}
