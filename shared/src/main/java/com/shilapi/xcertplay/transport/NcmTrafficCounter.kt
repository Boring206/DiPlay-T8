package com.shilapi.xcertplay.transport

/**
 * Counts the Ethernet frames on the NCM link, so a wired attempt that never gets its AirPlay session can tell an
 * iPhone that never tried (no frames, or no TCP connection attempt) from one whose packets arrive but do not reach
 * DiPlay's listener (#889, #890). Only headers are read, never payloads. Free of Android types for JVM tests.
 *
 * For each address and port the iPhone's connection attempts (TCP SYN) go to, it also counts what DiPlay sent back
 * from there: SYN-ACK (a listener took it), RST (nothing listens there), or nothing (#1338). A reply the USB link did
 * not take is counted apart, as is how many connections (source ports) the attempts were.
 */
internal class NcmTrafficCounter(private val nanoTime: () -> Long = System::nanoTime) {
    private var fromIphone = 0L
    private var ipv6 = 0L
    private var ipv4 = 0L
    private var toIphone = 0L
    private val synsByPort = LinkedHashMap<Int, Int>()
    private val lastSynNanosByPort = HashMap<Int, Long>()
    private val targets = ArrayList<SynTarget>()

    private class SynTarget(val address: ByteArray, val port: Int) {
        var syns = 0
        val sourcePorts = HashSet<Int>()
        var synAcks = 0
        var resets = 0
        var unsent = 0

        fun matches(address: ByteArray, port: Int) = this.port == port && this.address.contentEquals(address)
    }

    @Synchronized fun received(frame: ByteArray) {
        fromIphone++
        when (u16(frame, ETHER_TYPE_OFFSET)) {
            ETHER_TYPE_IPV6 -> ipv6++
            ETHER_TYPE_IPV4 -> ipv4++
        }
        val tcp = tcpOffset(frame)
        if (tcp < 0) return
        val flags = u8(frame, tcp + TCP_FLAGS_OFFSET) ?: return
        if (flags and TCP_SYN == 0 || flags and TCP_ACK != 0) return
        val port = u16(frame, tcp + TCP_DESTINATION_PORT_OFFSET) ?: return
        if (port in synsByPort || synsByPort.size < MAX_PORTS) {
            synsByPort[port] = (synsByPort[port] ?: 0) + 1
            lastSynNanosByPort[port] = nanoTime()
        }
        val address = address(frame, destination = true) ?: return
        val target = targets.firstOrNull { it.matches(address, port) }
            ?: SynTarget(address, port).takeIf { targets.size < MAX_TARGETS }?.also(targets::add)
            ?: return
        target.syns++
        val sourcePort = u16(frame, tcp + TCP_SOURCE_PORT_OFFSET) ?: return
        if (target.sourcePorts.size < MAX_SOURCE_PORTS) target.sourcePorts.add(sourcePort)
    }

    /** Whether the iPhone tried to connect to [port] at or after [sinceNanos] ([System.nanoTime] time). */
    @Synchronized fun connectionAttemptSince(port: Int, sinceNanos: Long): Boolean =
        lastSynNanosByPort[port]?.let { it - sinceNanos >= 0 } == true

    /** A frame DiPlay wrote towards the iPhone; [delivered] is false when the USB link did not take all of it. */
    @Synchronized fun sent(frame: ByteArray, delivered: Boolean) {
        if (delivered) toIphone++
        if (targets.isEmpty()) return
        val tcp = tcpOffset(frame)
        if (tcp < 0) return
        val flags = u8(frame, tcp + TCP_FLAGS_OFFSET) ?: return
        val reset = flags and TCP_RST != 0
        val synAck = !reset && flags and TCP_SYN != 0 && flags and TCP_ACK != 0
        if (!reset && !synAck) return
        val port = u16(frame, tcp + TCP_SOURCE_PORT_OFFSET) ?: return
        val address = address(frame, destination = false) ?: return
        val target = targets.firstOrNull { it.matches(address, port) } ?: return
        when {
            !delivered -> target.unsent++
            reset -> target.resets++
            else -> target.synAcks++
        }
    }

    /** One line for the report; [airPlayPort] is DiPlay's listener, whose connection attempts are named apart. */
    @Synchronized fun summary(airPlayPort: Int?): String {
        val toAirPlay = airPlayPort?.let { synsByPort[it] } ?: 0
        val others = synsByPort.filterKeys { it != airPlayPort }.entries.joinToString(",") { "${it.key}:${it.value}" }
        return "fromIphone=$fromIphone ipv6=$ipv6 ipv4=$ipv4 tcpConnectToAirPlay=$toAirPlay " +
            "tcpConnectOther=${others.ifEmpty { "none" }} toIphone=$toIphone"
    }

    /**
     * Where the iPhone's connection attempts went and what DiPlay answered from there, or null before the first one.
     * [advertisedAddress] is the address DiPlay gave the iPhone for AirPlay. Addresses are named by kind only, so
     * the line carries no address for the report to redact.
     */
    @Synchronized fun connectionAttemptSummary(advertisedAddress: ByteArray?): String? {
        if (targets.isEmpty()) return null
        return targets.joinToString("; ") { target ->
            "to=${kind(target.address, advertisedAddress)} port=${target.port} syn=${target.syns} " +
                "connections=${target.sourcePorts.size} synAck=${target.synAcks} rst=${target.resets} " +
                "unsent=${target.unsent}"
        }
    }

    private fun kind(address: ByteArray, advertised: ByteArray?): String {
        if (advertised != null && address.contentEquals(advertised)) return "advertised"
        if (address.size == IPV4_ADDRESS_BYTES) return "ipv4"
        val first = address[0].toInt() and 0xff
        val second = address[1].toInt() and 0xff
        return when {
            first == 0xfe && second and 0xc0 == 0x80 -> "otherLinkLocal"
            first == 0xff -> "multicast"
            first and 0xfe == 0xfc -> "uniqueLocal"
            else -> "otherIpv6"
        }
    }

    /** Offset of the TCP header in an IPv4 or IPv6 [frame], or -1 for anything else. */
    private fun tcpOffset(frame: ByteArray): Int = when (u16(frame, ETHER_TYPE_OFFSET)) {
        ETHER_TYPE_IPV6 ->
            if (u8(frame, ETHERNET_HEADER + IPV6_NEXT_HEADER_OFFSET) == PROTOCOL_TCP) ETHERNET_HEADER + IPV6_HEADER else -1
        ETHER_TYPE_IPV4 -> {
            val headerLength = ((u8(frame, ETHERNET_HEADER) ?: 0) and 0x0f) * 4
            if (headerLength > 0 && u8(frame, ETHERNET_HEADER + IPV4_PROTOCOL_OFFSET) == PROTOCOL_TCP) {
                ETHERNET_HEADER + headerLength
            } else {
                -1
            }
        }
        else -> -1
    }

    /** The IP source or [destination] address of a [frame] that [tcpOffset] accepted, or null when it is short. */
    private fun address(frame: ByteArray, destination: Boolean): ByteArray? {
        val v6 = u16(frame, ETHER_TYPE_OFFSET) == ETHER_TYPE_IPV6
        val bytes = if (v6) IPV6_ADDRESS_BYTES else IPV4_ADDRESS_BYTES
        val start = ETHERNET_HEADER + (if (v6) IPV6_SOURCE_OFFSET else IPV4_SOURCE_OFFSET) + if (destination) bytes else 0
        return if (frame.size >= start + bytes) frame.copyOfRange(start, start + bytes) else null
    }

    private fun u8(frame: ByteArray, offset: Int): Int? = frame.getOrNull(offset)?.toInt()?.and(0xff)

    private fun u16(frame: ByteArray, offset: Int): Int? {
        val high = u8(frame, offset) ?: return null
        val low = u8(frame, offset + 1) ?: return null
        return (high shl 8) or low
    }

    private companion object {
        const val ETHERNET_HEADER = 14
        const val ETHER_TYPE_OFFSET = 12
        const val ETHER_TYPE_IPV4 = 0x0800
        const val ETHER_TYPE_IPV6 = 0x86dd
        const val IPV6_HEADER = 40
        const val IPV6_NEXT_HEADER_OFFSET = 6
        const val IPV6_SOURCE_OFFSET = 8
        const val IPV6_ADDRESS_BYTES = 16
        const val IPV4_PROTOCOL_OFFSET = 9
        const val IPV4_SOURCE_OFFSET = 12
        const val IPV4_ADDRESS_BYTES = 4
        const val PROTOCOL_TCP = 6
        const val TCP_SOURCE_PORT_OFFSET = 0
        const val TCP_DESTINATION_PORT_OFFSET = 2
        const val TCP_FLAGS_OFFSET = 13
        const val TCP_SYN = 0x02
        const val TCP_RST = 0x04
        const val TCP_ACK = 0x10
        const val MAX_PORTS = 8
        const val MAX_TARGETS = 4
        const val MAX_SOURCE_PORTS = 64
    }
}
