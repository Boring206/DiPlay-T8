package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NcmTrafficCounterTest {
    private fun ethernet(etherType: Int, payload: ByteArray) =
        ByteArray(12) + byteArrayOf((etherType shr 8).toByte(), etherType.toByte()) + payload

    private fun tcp(destinationPort: Int, flags: Int, sourcePort: Int = 49152) = ByteArray(20).also {
        it[0] = (sourcePort shr 8).toByte()
        it[1] = sourcePort.toByte()
        it[2] = (destinationPort shr 8).toByte()
        it[3] = destinationPort.toByte()
        it[13] = flags.toByte()
    }

    private fun address(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }

    /** fe80::[last] */
    private fun linkLocal(last: Int) = address(0xfe, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, last)

    private fun ipv6Tcp(source: ByteArray, destination: ByteArray, sourcePort: Int, destinationPort: Int, flags: Int) =
        ethernet(
            0x86dd,
            ByteArray(40).also {
                it[6] = 6
                source.copyInto(it, 8)
                destination.copyInto(it, 24)
            } + tcp(destinationPort, flags, sourcePort),
        )

    private fun ipv4Tcp(source: ByteArray, destination: ByteArray, sourcePort: Int, destinationPort: Int, flags: Int) =
        ethernet(
            0x0800,
            ByteArray(20).also {
                it[0] = 0x45
                it[9] = 6
                source.copyInto(it, 12)
                destination.copyInto(it, 16)
            } + tcp(destinationPort, flags, sourcePort),
        )

    private fun ipv6(nextHeader: Int, payload: ByteArray) =
        ethernet(0x86dd, ByteArray(40).also { it[6] = nextHeader.toByte() } + payload)

    private fun ipv4(protocol: Int, payload: ByteArray) =
        ethernet(0x0800, ByteArray(20).also { it[0] = 0x45; it[9] = protocol.toByte() } + payload)

    @Test fun countsConnectionAttemptsToTheAirPlayListenerApart() {
        val counter = NcmTrafficCounter()
        counter.received(ipv6(6, tcp(7000, 0x02)))
        counter.received(ipv6(6, tcp(7000, 0x02)))
        // SYN-ACK and plain ACK are replies, not attempts.
        counter.received(ipv6(6, tcp(7000, 0x12)))
        counter.received(ipv6(6, tcp(7000, 0x10)))
        counter.received(ipv6(17, ByteArray(8)))
        counter.received(ipv4(6, tcp(5353, 0x02)))
        counter.sent(ipv6(17, ByteArray(8)), delivered = true)
        // A frame the USB link did not take is not counted as sent.
        counter.sent(ipv6(17, ByteArray(8)), delivered = false)
        assertEquals(
            "fromIphone=6 ipv6=5 ipv4=1 tcpConnectToAirPlay=2 tcpConnectOther=5353:1 toIphone=1",
            counter.summary(7000),
        )
    }

    @Test fun remembersWhenTheIphoneLastTriedAPort() {
        var now = 100L
        val counter = NcmTrafficCounter { now }
        assertFalse(counter.connectionAttemptSince(7000, 0))
        counter.received(ipv6(6, tcp(7000, 0x02)))
        now = 200
        // A reply from the iPhone is not an attempt.
        counter.received(ipv6(6, tcp(7000, 0x12)))
        assertTrue(counter.connectionAttemptSince(7000, 100))
        assertFalse(counter.connectionAttemptSince(7000, 101))
        assertFalse(counter.connectionAttemptSince(5353, 0))
    }

    @Test fun shortAndForeignFramesAreCountedButNotParsed() {
        val counter = NcmTrafficCounter()
        counter.received(ByteArray(5))
        counter.received(ethernet(0x0806, ByteArray(28)))
        counter.received(ethernet(0x86dd, ByteArray(10)))
        assertEquals(
            "fromIphone=3 ipv6=1 ipv4=0 tcpConnectToAirPlay=0 tcpConnectOther=none toIphone=0",
            counter.summary(7000),
        )
    }

    @Test fun namesWhereTheIphonesConnectionAttemptsWentAndWhatDiPlayAnswered() {
        // #1338: 12 SYNs reached DiPlay in 30 s but no session followed; the report could not say why.
        val iphone = linkLocal(0x0a)
        val advertised = linkLocal(0x02)
        val other = linkLocal(0x03)
        val counter = NcmTrafficCounter()
        assertNull(counter.connectionAttemptSummary(advertised))
        // Before any attempt a reply is not looked at.
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50000, 0x12), delivered = true)
        repeat(3) { counter.received(ipv6Tcp(iphone, advertised, 50000, 7000, 0x02)) }
        counter.received(ipv6Tcp(iphone, advertised, 50001, 7000, 0x02))
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50000, 0x12), delivered = true)
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50001, 0x12), delivered = false)
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50001, 0x12), delivered = false)
        // Plain ACKs and data are not answers to an attempt.
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50000, 0x10), delivered = true)
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50000, 0x18), delivered = true)
        // A reply from a port the iPhone did not try is not counted.
        counter.sent(ipv6Tcp(advertised, iphone, 7001, 50000, 0x14), delivered = true)
        counter.received(ipv6Tcp(iphone, other, 50002, 7000, 0x02))
        counter.sent(ipv6Tcp(other, iphone, 7000, 50002, 0x14), delivered = true)
        counter.received(ipv4Tcp(address(169, 254, 1, 2), address(169, 254, 1, 1), 50003, 7000, 0x02))
        val line = counter.connectionAttemptSummary(advertised)!!
        assertEquals(
            "to=advertised port=7000 syn=4 connections=2 synAck=1 rst=0 unsent=2; " +
                "to=otherLinkLocal port=7000 syn=1 connections=1 synAck=0 rst=1 unsent=0; " +
                "to=ipv4 port=7000 syn=1 connections=1 synAck=0 rst=0 unsent=0",
            line,
        )
        // The report redacts addresses; this line names their kind and carries none.
        assertFalse(line.contains("::"))
        assertFalse(line.contains("fe80"))
        assertFalse(line.contains("169.254"))
        assertTrue(counter.summary(7000).endsWith("toIphone=6"))
    }

    @Test fun anRstThatEndsAnAcceptedConnectionIsNotCountedAsARefusal() {
        // rst counts refused attempts only. DiPlay also resets connections it had accepted, for example when a
        // session is torn down; those got a SYN-ACK first and say nothing about whether a listener was there.
        val iphone = linkLocal(0x0a)
        val advertised = linkLocal(0x02)
        val counter = NcmTrafficCounter()
        counter.received(ipv6Tcp(iphone, advertised, 50000, 7000, 0x02))
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50000, 0x12), delivered = true)
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50000, 0x14), delivered = true)
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50000, 0x04), delivered = false)
        // A SYN-ACK the USB link did not take still means a listener took the attempt.
        counter.received(ipv6Tcp(iphone, advertised, 50001, 7000, 0x02))
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50001, 0x12), delivered = false)
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50001, 0x14), delivered = true)
        // An attempt that got no SYN-ACK and an RST is a refusal.
        counter.received(ipv6Tcp(iphone, advertised, 50002, 7000, 0x02))
        counter.sent(ipv6Tcp(advertised, iphone, 7000, 50002, 0x14), delivered = true)
        assertEquals(
            "to=advertised port=7000 syn=3 connections=3 synAck=1 rst=1 unsent=1",
            counter.connectionAttemptSummary(advertised),
        )
    }

    @Test fun namesAddressKindsAndKeepsTheFirstFourTargets() {
        val iphone = linkLocal(0x0a)
        val counter = NcmTrafficCounter()
        val targets = listOf(
            address(0xfd, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            address(0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            address(0xff, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            linkLocal(0x02),
            linkLocal(0x03),
        )
        targets.forEachIndexed { index, target -> counter.received(ipv6Tcp(iphone, target, 50000 + index, 7000, 0x02)) }
        // Without a known advertised address, DiPlay's own is just another link-local one.
        assertEquals(
            "to=uniqueLocal port=7000 syn=1 connections=1 synAck=0 rst=0 unsent=0; " +
                "to=otherIpv6 port=7000 syn=1 connections=1 synAck=0 rst=0 unsent=0; " +
                "to=multicast port=7000 syn=1 connections=1 synAck=0 rst=0 unsent=0; " +
                "to=otherLinkLocal port=7000 syn=1 connections=1 synAck=0 rst=0 unsent=0",
            counter.connectionAttemptSummary(null),
        )
        // All five still count towards the per-port total.
        assertTrue(counter.summary(7000).contains("tcpConnectToAirPlay=5"))
    }
}
