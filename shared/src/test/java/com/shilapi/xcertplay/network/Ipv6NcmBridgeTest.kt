package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.transport.EthernetIpv6Codec
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Ipv6NcmBridgeTest {
    private val host = InetAddress.getByName("fe80::2").address
    private val peer = InetAddress.getByName("fe80::1234").address
    private val hostMac = byteArrayOf(2, 0, 0, 0, 0, 2)
    private val peerMac = byteArrayOf(2, 0, 0, 0, 0, 3)

    @Test fun solicitationIsAnsweredOnUsbWithoutForwardingToTun() {
        val transport = FakeTransport(true)
        withBridge(transport) { tun ->
            transport.inbound.put(frame())
            assertTrue(transport.advertised.await(2, TimeUnit.SECONDS))
            val response = transport.advertisement!!
            assertArrayEquals(peerMac, response.copyOfRange(0, 6))
            assertEquals(136, response[14 + 40].toInt() and 255)
            assertEquals(50, transport.advertisementTimeout)
            assertEquals(0, tun.fromBridge.size())
        }
    }

    @Test fun unacceptedAdvertisementForwardsOriginalSolicitationToTun() {
        val transport = FakeTransport(false)
        withBridge(transport) { tun ->
            val solicitation = solicitation()
            transport.inbound.put(EthernetIpv6Codec.build(peerMac, hostMac, solicitation))
            assertTrue(transport.advertised.await(2, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (tun.fromBridge.size() < solicitation.size && System.nanoTime() < deadline) Thread.sleep(5)
            assertArrayEquals(solicitation, tun.fromBridge.toByteArray())
        }
    }

    @Test fun closeWaitsForActiveTunWriterBeforeClosingNcm() {
        val transport = FakeTransport(true, blockWriter = true)
        withBridge(transport) { tun ->
            tun.toBridge.write(solicitation())
            assertTrue(transport.writerStarted.await(2, TimeUnit.SECONDS))
            // withBridge closes after this callback; a helper releases the writer shortly after.
            Thread {
                Thread.sleep(100)
                transport.closedWhileWriter = transport.closed
                transport.releaseWriter.countDown()
            }.start()
        }
        assertFalse("NCM must remain open while TUN writer is in send", transport.closedWhileWriter)
        assertTrue(transport.closed)
        assertTrue(transport.writerFinished)
    }

    private fun withBridge(transport: FakeTransport, block: (FakeTun) -> Unit) {
        val tun = FakeTun()
        val errors = mutableListOf<Throwable>()
        val bridge = Ipv6NcmBridge(transport, tun, hostMac, host) { synchronized(errors) { errors.add(it) } }
        bridge.start()
        try { block(tun) } finally { bridge.close() }
        assertTrue("unexpected bridge errors: $errors", errors.isEmpty())
    }

    private class FakeTun : Ipv6Tun {
        private val input = PipedInputStream()
        val toBridge = PipedOutputStream(input)
        val fromBridge = ByteArrayOutputStream()
        override fun input() = input
        override fun output() = fromBridge
        override fun close() { toBridge.close(); input.close() }
    }

    private fun frame() = EthernetIpv6Codec.build(peerMac, hostMac, solicitation())

    private fun solicitation(): ByteArray = ByteArray(64).also { packet ->
        packet[0] = 0x60; packet[5] = 24; packet[6] = 58; packet[7] = 255.toByte()
        peer.copyInto(packet, 8)
        InetAddress.getByName("ff02::1:ff00:2").address.copyInto(packet, 24)
        packet[40] = 135.toByte(); host.copyInto(packet, 48)
        val pseudo = packet.copyOfRange(8, 40) + byteArrayOf(0, 0, 0, 24, 0, 0, 0, 58) + packet.copyOfRange(40, 64)
        var sum = 0L
        for (i in pseudo.indices step 2) sum += (pseudo[i].toInt() and 255) * 256 + (pseudo[i + 1].toInt() and 255)
        while (sum > 65535) sum = (sum and 65535) + (sum ushr 16)
        val checksum = sum.toInt().inv() and 65535
        packet[42] = (checksum ushr 8).toByte(); packet[43] = checksum.toByte()
    }

    private class FakeTransport(private val accept: Boolean, private val blockWriter: Boolean = false) : Ipv6NcmTransport {
        val inbound = LinkedBlockingQueue<ByteArray>()
        val advertised = CountDownLatch(1)
        val writerStarted = CountDownLatch(1)
        val releaseWriter = CountDownLatch(1)
        @Volatile var advertisement: ByteArray? = null
        @Volatile var advertisementTimeout = 0
        @Volatile var closed = false
        @Volatile var writerFinished = false
        @Volatile var closedWhileWriter = false
        override fun recv(timeoutMillis: Long) = inbound.poll(timeoutMillis, TimeUnit.MILLISECONDS)
        override fun trySendAdvertisement(frame: ByteArray, timeoutMillis: Int): Boolean {
            advertisement = frame; advertisementTimeout = timeoutMillis; advertised.countDown(); return accept
        }
        override fun send(frame: ByteArray, timeoutMillis: Int): Boolean {
            writerStarted.countDown()
            if (blockWriter) check(releaseWriter.await(2, TimeUnit.SECONDS))
            writerFinished = true
            return true
        }
        override fun close() { closed = true }
    }
}
