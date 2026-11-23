package com.shilapi.xcertplay.transport

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class UsbNeighborDiscoveryTest {
    private val host = InetAddress.getByName("fe80::2").address
    private val peer = InetAddress.getByName("fe80::1234").address
    private val mac = byteArrayOf(2, 0, 0, 0, 0, 2)
    private fun solicitation(withOption: Boolean = false): ByteArray {
        return ByteArray(if (withOption) 72 else 64).also {
            it[0] = 0x60; it[5] = (it.size - 40).toByte(); it[6] = 58; it[7] = 255.toByte()
            peer.copyInto(it, 8)
            InetAddress.getByName("ff02::1:ff00:2").address.copyInto(it, 24)
            it[40] = 135.toByte(); host.copyInto(it, 48)
            if (withOption) { it[64] = 1; it[65] = 1; mac.copyInto(it, 66) }
            sign(it)
        }
    }
    // Independent checksum computation across IPv6 pseudo-header + ICMPv6 payload.
    private fun sum(packet: ByteArray): Int {
        val count = ((packet[4].toInt() and 255) shl 8) or (packet[5].toInt() and 255)
        val pseudo = packet.copyOfRange(8, 40) + byteArrayOf(0, 0, (count ushr 8).toByte(), count.toByte(), 0, 0, 0, 58) + packet.copyOfRange(40, 40 + count)
        var sum = 0L
        for (i in pseudo.indices step 2) {
            sum += (pseudo[i].toInt() and 255) * 256 + (if (i + 1 < pseudo.size) pseudo[i + 1].toInt() and 255 else 0)
        }
        while (sum > 65535) sum = (sum and 65535) + (sum ushr 16)
        return sum.toInt()
    }
    private fun sign(packet: ByteArray) {
        packet[42] = 0; packet[43] = 0
        val value = sum(packet).inv() and 65535
        packet[42] = (value ushr 8).toByte(); packet[43] = value.toByte()
    }
    @Test fun `answers multicast discovery with correct unicast advertisement and checksum`() {
        for (option in listOf(false, true)) {
            val reply = UsbNeighborDiscovery.advertisement(solicitation(option), host, mac)!!
            assertEquals(72, reply.size); assertEquals(136, reply[40].toInt() and 255)
            assertEquals(255, reply[7].toInt() and 255); assertEquals(0x60, reply[44].toInt())
            assertArrayEquals(host, reply.copyOfRange(8, 24)); assertArrayEquals(peer, reply.copyOfRange(24, 40))
            assertArrayEquals(host, reply.copyOfRange(48, 64)); assertArrayEquals(mac, reply.copyOfRange(66, 72))
            assertEquals(65535, sum(reply))
        }
    }
    @Test fun `answers unicast reachability probes and learns peer dynamically`() {
        val packet = solicitation()
        host.copyInto(packet, 24)
        val other = InetAddress.getByName("fe80::abcd").address
        other.copyInto(packet, 8); sign(packet)
        assertArrayEquals(other, UsbNeighborDiscovery.advertisement(packet, host, mac)!!.copyOfRange(24, 40))
    }
    @Test fun `does not answer another target DAD other protocols or bad hop code checksum`() {
        val changes = listOf<Pair<Int, Byte>>(7 to 64, 40 to 136.toByte(), 41 to 1, 63 to 3, 24 to 0xfe.toByte())
        for ((offset, value) in changes) {
            val packet = solicitation(); packet[offset] = value; sign(packet)
            assertNull("offset $offset", UsbNeighborDiscovery.advertisement(packet, host, mac))
        }
        val dad = solicitation(); ByteArray(16).copyInto(dad, 8); sign(dad)
        assertNull(UsbNeighborDiscovery.advertisement(dad, host, mac))
        val corrupt = solicitation(); corrupt[42] = (corrupt[42].toInt() xor 1).toByte()
        assertNull(UsbNeighborDiscovery.advertisement(corrupt, host, mac))
        assertNull(UsbNeighborDiscovery.advertisement(solicitation().copyOf(60), host, mac))
    }
    @Test fun `validates a maximum size options payload without checksum overflow`() {
        val packet = solicitation().copyOf(40 + 65528)
        packet[4] = 255.toByte(); packet[5] = 248.toByte()
        var offset = 64
        while (offset < packet.size) {
            val bytes = minOf(2040, packet.size - offset)
            packet.fill(255.toByte(), offset, offset + bytes)
            packet[offset + 1] = (bytes / 8).toByte()
            offset += bytes
        }
        sign(packet)
        val reply = UsbNeighborDiscovery.advertisement(packet, host, mac)!!
        assertEquals(65535, sum(reply))
    }
    @Test fun `rejects malformed option lengths and payload lengths`() {
        for (length in listOf(0, 2)) {
            val packet = solicitation(true); packet[65] = length.toByte(); sign(packet)
            assertNull(UsbNeighborDiscovery.advertisement(packet, host, mac))
        }
        val packet = solicitation(); packet[5] = 100
        assertNull(UsbNeighborDiscovery.advertisement(packet, host, mac))
    }
}
