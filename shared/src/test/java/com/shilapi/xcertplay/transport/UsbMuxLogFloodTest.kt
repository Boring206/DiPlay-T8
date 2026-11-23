package com.shilapi.xcertplay.transport

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** A wired session sends and receives thousands of ACKs and large writes. Logcat must not fill. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE,
    shadows = [EvidenceUsbConnectionShadow::class, EvidenceUsbRequestShadow::class])
class UsbMuxLogFloodTest {
    @Before fun reset() {
        UsbEvidenceReplay.reset()
        ShadowLog.clear()
    }

    @Test fun pureAckFramesDoNotLogOneLineEach() {
        UsbEvidenceReplay.transfers.add(versionReply)
        repeat(FRAMES) { UsbEvidenceReplay.transfers.add(pureAck(it)) }
        val host = Iap2UsbMuxHost.open(pipe())
        awaitReads(FRAMES + 1)
        host.close()
        val lines = lines("usbmux TCP control")
        println("RX pure-ACK frames=$FRAMES logged=$lines")
        assertTrue("logged $lines lines for $FRAMES pure ACK frames", lines <= MAX_LINES)
    }

    @Test fun largeWritesDoNotLogOneLineEach() {
        UsbEvidenceReplay.transfers.add(versionReply)
        val host = Iap2UsbMuxHost.open(pipe())
        repeat(FRAMES) { host.sendTcp(1, 2, it, 0, 0x18, ByteArray(600)) }
        host.close()
        val lines = lines("usbmux TX")
        println("TX writes=$FRAMES logged=$lines")
        assertTrue("logged $lines lines for $FRAMES large writes", lines <= MAX_LINES)
    }

    @Test fun theFirstFramesOfEachKindAreStillLogged() {
        UsbEvidenceReplay.transfers.add(versionReply)
        UsbEvidenceReplay.transfers.add(pureAck(0))
        val host = Iap2UsbMuxHost.open(pipe())
        awaitReads(2)
        host.sendTcp(1, 2, 0, 0, 0x18, ByteArray(600))
        host.close()
        assertEquals(1, lines("usbmux TCP control"))
        assertEquals(1, lines("usbmux TX"))
    }

    @Test fun resetAndFinFramesAreLoggedAfterTheAckCapIsReached() {
        UsbEvidenceReplay.transfers.add(versionReply)
        repeat(60) { UsbEvidenceReplay.transfers.add(pureAck(it)) }
        UsbEvidenceReplay.transfers.add(pureAck(60).also { it[29] = 0x04 })
        UsbEvidenceReplay.transfers.add(pureAck(61).also { it[29] = 0x01 })
        val host = Iap2UsbMuxHost.open(pipe())
        awaitReads(63)
        host.close()
        val control = ShadowLog.getLogsForTag("xcertplay-usb").map { it.msg }
            .filter { it.startsWith("usbmux TCP control") }
        val acks = control.count { it.contains("flags=16 ") }
        println("control lines=${control.size} acks=$acks rst=${control.count { it.contains("flags=4 ") }} fin=${control.count { it.contains("flags=1 ") }}")
        assertTrue("pure ACK lines $acks exceed the cap", acks <= MAX_LINES)
        assertEquals("RST line", 1, control.count { it.contains("flags=4 ") })
        assertEquals("FIN line", 1, control.count { it.contains("flags=1 ") })
    }

    @Test fun stalledReceiverIsCappedAndRejectedInsteadOfBufferingForever() {
        UsbEvidenceReplay.transfers.add(versionReply)
        val host = Iap2UsbMuxHost.open(pipe())
        try {
            val connection = Iap2UsbMuxTcpConnection(host, 1, 2)
            val payload = ByteArray(65_500) // a realistic maximum-size usbmux TCP payload
            // Never call recv(): ~12.5 MiB is injected, beyond the 8 MiB cap. Without the cap
            // the deque would retain every frame and grow without bound.
            repeat(200) { i -> connection.onPacket(TCP_ACK_FLAG, i, payload) }

            // The stalled connection must be rejected with a protocol error rather than OOM-ing.
            val failure = assertThrows(IphoneUsbException.Protocol::class.java) {
                connection.recv(16, 10L)
            }
            assertTrue(failure.message!!.contains("receive buffer"))
            assertTrue(failure.message!!.contains("port 2"))
            val queuedBytes = ReflectionHelpers.getField<Int>(connection, "receivedBytes")
            assertTrue("queuedBytes=$queuedBytes must stay within the cap", queuedBytes <= MAX_RECEIVED_BYTES)
            assertEquals("overflow must release the queued payloads", 0, queuedBytes)
            val queued: java.util.ArrayDeque<*> = ReflectionHelpers.getField(connection, "received")
            assertEquals(0, queued.size)

            // Overflow must not just close silently: leave the phone a RST|ACK (0x14), as abort() does.
            val writes = UsbEvidenceReplay.writes
            assertTrue("overflow must write a control frame", writes.isNotEmpty())
            assertEquals(
                "the last frame must be a RST|ACK so the phone drops the half-open service",
                0x14,
                writes.last()[29].toInt() and 0xff,
            )
        } finally { host.close() }
    }

    @Test fun normalSmallPacketsAreDeliveredWithoutLoss() {
        UsbEvidenceReplay.transfers.add(versionReply)
        val host = Iap2UsbMuxHost.open(pipe())
        try {
            val connection = Iap2UsbMuxTcpConnection(host, 1, 2)
            val payloadSize = 8 * 1024
            // More than MAX_RECEIVED_BYTES cumulative, but drained every time: recv() must
            // decrement the byte counter so a healthy consumer never trips the cap.
            repeat(MAX_RECEIVED_BYTES / payloadSize + 1) { i ->
                val packet = ByteArray(payloadSize) { (i + it).toByte() }
                connection.onPacket(TCP_ACK_FLAG, i, packet)
                assertArrayEquals("packet $i was lost", packet, connection.recv(payloadSize, 1_000L)!!)
            }
            // A partial read splits one packet; the byte accounting must stay exact.
            val packet = ByteArray(payloadSize) { 0x5a.toByte() }
            connection.onPacket(TCP_ACK_FLAG, 0, packet)
            assertArrayEquals(packet.copyOf(100), connection.recv(100, 1_000L)!!)
            assertArrayEquals(packet.copyOfRange(100, payloadSize), connection.recv(payloadSize, 1_000L)!!)
            assertEquals(0, ReflectionHelpers.getField<Int>(connection, "receivedBytes"))
        } finally { host.close() }
    }

    private fun lines(prefix: String) =
        ShadowLog.getLogsForTag("xcertplay-usb").count { it.msg.startsWith(prefix) }

    private fun awaitReads(count: Int) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (UsbEvidenceReplay.completedReads < count && System.nanoTime() < deadline) Thread.sleep(1)
        assertEquals(count, UsbEvidenceReplay.completedReads)
    }

    // proto 6, length 36, magic, mux seq, mux ack; TCP: ports, seq, ack, offset 5, ACK flag, window.
    private fun pureAck(sequence: Int): ByteArray = hex(
        "00 00 00 06 00 00 00 24 fa ce fa ce 00 00 00 00 " +
            "f2 7e 00 01 00 00 00 00 00 00 00 01 50 10 02 00 00 00 00 00").also { it[15] = sequence.toByte() }

    private fun pipe(): Iap2UsbSession {
        val connection = ReflectionHelpers.callConstructor(UsbDeviceConnection::class.java,
            ClassParameter.from(UsbDevice::class.java, null))
        fun endpoint(address: Int): UsbEndpoint = ReflectionHelpers.callConstructor(
            UsbEndpoint::class.java, ClassParameter.from(Int::class.javaPrimitiveType, address),
            ClassParameter.from(Int::class.javaPrimitiveType, 2),
            ClassParameter.from(Int::class.javaPrimitiveType, 512),
            ClassParameter.from(Int::class.javaPrimitiveType, 0))
        return Iap2UsbSession(connection, endpoint(0x04), endpoint(0x85))
    }

    private fun hex(value: String) = value.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val FRAMES = 200
        const val MAX_LINES = 50
        const val TCP_ACK_FLAG = 0x10
        val versionReply = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 20, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0)
    }
}
