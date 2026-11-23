package com.shilapi.xcertplay.network

import android.os.ParcelFileDescriptor
import android.util.Log
import com.shilapi.xcertplay.transport.EthernetIpv6Codec
import com.shilapi.xcertplay.transport.NcmUsbBridge
import com.shilapi.xcertplay.transport.UsbNeighborDiscovery
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * Moves IPv6 packets between an Android VpnService tun and the iPhone NCM Ethernet link.
 *
 * NCM carries Ethernet frames while the tun is a layer-3 device, so this bridge strips and
 * restores the Ethernet II header. Solicitations for our USB address are answered on the
 * Ethernet side. On one MT6580/Android 8.1 unit, multicast routing through Wi-Fi coincided
 * with failed USB neighbor discovery.
 */
internal interface Ipv6NcmTransport : Closeable {
    fun recv(timeoutMillis: Long): ByteArray?
    fun send(frame: ByteArray, timeoutMillis: Int): Boolean
    fun trySendAdvertisement(frame: ByteArray, timeoutMillis: Int): Boolean
}

private class UsbIpv6NcmTransport(private val bridge: NcmUsbBridge) : Ipv6NcmTransport {
    override fun recv(timeoutMillis: Long) = bridge.recv(timeoutMillis)
    override fun send(frame: ByteArray, timeoutMillis: Int) = bridge.send(frame, timeoutMillis)
    override fun trySendAdvertisement(frame: ByteArray, timeoutMillis: Int) =
        bridge.trySendAdvertisement(frame, timeoutMillis)
    override fun close() = bridge.close()
}

internal interface Ipv6Tun : Closeable {
    fun input(): InputStream
    fun output(): OutputStream
}

private class DescriptorIpv6Tun(private val descriptor: ParcelFileDescriptor) : Ipv6Tun {
    override fun input(): InputStream = FileInputStream(descriptor.fileDescriptor)
    override fun output(): OutputStream = FileOutputStream(descriptor.fileDescriptor)
    override fun close() = descriptor.close()
}

internal class Ipv6NcmBridge(
    private val ncm: Ipv6NcmTransport,
    private val tun: Ipv6Tun,
    private val hostMac: ByteArray,
    private val hostAddress: ByteArray,
    private val onError: (Throwable) -> Unit,
) : Closeable {
    constructor(
        ncm: NcmUsbBridge,
        tun: ParcelFileDescriptor,
        hostMac: ByteArray,
        hostAddress: ByteArray,
        onError: (Throwable) -> Unit,
    ) : this(UsbIpv6NcmTransport(ncm), DescriptorIpv6Tun(tun), hostMac, hostAddress, onError)

    init {
        require(hostMac.size == EthernetIpv6Codec.MAC_BYTES) { "hostMac must be 6 bytes" }
        require(hostAddress.size == 16) { "hostAddress must be IPv6" }
    }

    @Volatile
    private var peerMac: ByteArray? = null
    private var loggedInbound = false
    private var loggedOutbound = false
    private var loggedWaitingForPeer = false
    private var inboundLogBudget = 16
    private var outboundLogBudget = 24
    private val running = AtomicBoolean(false)
    private lateinit var ncmToTunThread: Thread
    private lateinit var tunToNcmThread: Thread

    fun start() {
        check(running.compareAndSet(false, true)) { "bridge is already started" }
        ncmToTunThread = Thread(::runNcmToTun, "ncm-ipv6-in").apply {
            isDaemon = true
            start()
        }
        tunToNcmThread = Thread(::runTunToNcm, "ncm-ipv6-out").apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        // Stop TUN writes before closing NCM. One MT6580 unit panicked during USB teardown;
        // the capture suggests a race but does not establish its complete cause.
        try {
            tun.close()
        } finally {
            join(tunToNcmThread)
            try {
                ncm.close()
            } finally {
                join(ncmToTunThread)
            }
        }
    }

    private fun runNcmToTun() {
        val output = tun.output()
        try {
            while (running.get()) {
                val frame = ncm.recv(READ_TIMEOUT_MILLIS) ?: continue
                val ipv6 = EthernetIpv6Codec.parseIpv6View(frame) ?: continue
                peerMac = ipv6.sourceMac
                if (!loggedInbound) {
                    loggedInbound = true
                    Log.i(
                        TAG,
                        "ncm first inbound ipv6 bytes=${ipv6.payloadLength} " +
                            "peer=${ipv6.sourceMac.macString()}",
                    )
                }
                if (inboundLogBudget > 0) {
                    inboundLogBudget--
                    Log.i(TAG, "ncm inbound ${frame.summary(ipv6.payloadOffset)}")
                }
                // Avoid allocating an extra packet copy for video/audio/TCP traffic.
                val advertisement = if (ipv6.payloadLength >= 64 &&
                    frame[ipv6.payloadOffset + 6].toInt() == 58 &&
                    (frame[ipv6.payloadOffset + 40].toInt() and 0xff) == 135
                ) {
                    UsbNeighborDiscovery.advertisement(
                        frame.copyOfRange(ipv6.payloadOffset, ipv6.payloadOffset + ipv6.payloadLength),
                        hostAddress,
                        hostMac,
                    )
                } else null
                if (advertisement != null && ncm.trySendAdvertisement(
                        EthernetIpv6Codec.build(hostMac, ipv6.sourceMac, advertisement), ADVERTISEMENT_WRITE_TIMEOUT_MILLIS,
                    )
                ) {
                    Log.i(TAG, "ncm answered USB neighbor solicitation directly")
                    continue
                }
                output.write(frame, ipv6.payloadOffset, ipv6.payloadLength)
            }
        } catch (error: IOException) {
            if (running.get()) onError(error)
        } catch (error: RuntimeException) {
            if (running.get()) onError(error)
        }
    }

    private fun runTunToNcm() {
        val input = tun.input()
        val buffer = ByteArray(TUN_READ_BYTES)
        try {
            while (running.get()) {
                val length = input.read(buffer)
                if (length == -1) {
                    if (running.get()) onError(IOException("NCM IPv6 tunnel closed"))
                    return
                }
                // Android's TUN fd may transiently report a zero-byte read while its network is
                // being registered. It is neither EOF (-1) nor an IPv6 packet.
                if (length == 0) {
                    LockSupport.parkNanos(ZERO_READ_BACKOFF_NANOS)
                    continue
                }
                val tunPacket = buffer.copyOf(length)
                val ipv6 = EthernetIpv6Codec.addNeighborAdvertisementTargetMac(tunPacket, hostMac)
                if (ipv6.size != tunPacket.size) {
                    Log.i(TAG, "ncm added target-link-layer option to neighbor advertisement")
                }
                if (outboundLogBudget > 0) {
                    outboundLogBudget--
                    Log.i(TAG, "ncm outbound ${ipv6.summary(0)}")
                }
                val multicastMac = EthernetIpv6Codec.multicastDestinationMac(ipv6)
                val mac = multicastMac ?: peerMac
                if (mac == null) {
                    if (!loggedWaitingForPeer) {
                        loggedWaitingForPeer = true
                        Log.i(TAG, "ncm deferred outbound unicast bytes=$length until peer MAC is learned")
                    }
                    continue
                }
                if (!loggedOutbound) {
                    loggedOutbound = true
                    Log.i(
                        TAG,
                        "ncm first outbound ipv6 bytes=$length destination=${mac.macString()} multicast=${multicastMac != null}",
                    )
                }
                val frame = EthernetIpv6Codec.build(hostMac, mac, ipv6)
                ncm.send(frame, WRITE_TIMEOUT_MILLIS)
            }
        } catch (error: IOException) {
            if (running.get()) onError(error)
        } catch (error: RuntimeException) {
            if (running.get()) onError(error)
        }
    }

    private fun join(thread: Thread) {
        if (thread === Thread.currentThread()) return
        try {
            thread.join(JOIN_TIMEOUT_MILLIS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (thread.isAlive) thread.interrupt()
    }

    private fun ByteArray.macString(): String =
        joinToString(":") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun ByteArray.summary(offset: Int): String {
        val payloadBytes = size - offset
        if (payloadBytes < 40) return "truncated bytes=$payloadBytes"
        val source = InetAddress.getByAddress(copyOfRange(offset + 8, offset + 24)).hostAddress
        val destination = InetAddress.getByAddress(copyOfRange(offset + 24, offset + 40)).hostAddress
        val nextHeader = this[offset + 6].toInt() and 0xff
        val detail = when {
            nextHeader == 6 && payloadBytes >= 44 -> " tcp=${u16(offset + 40)}->${u16(offset + 42)}"
            nextHeader == 17 && payloadBytes >= 44 -> " udp=${u16(offset + 40)}->${u16(offset + 42)}"
            nextHeader == 58 && payloadBytes >= 41 -> " icmp6=${this[offset + 40].toInt() and 0xff}"
            else -> ""
        }
        return "bytes=$payloadBytes src=$source dst=$destination next=$nextHeader$detail"
    }

    private fun ByteArray.u16(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)

    private companion object {
        const val TAG = "xcertplay-usb"
        const val READ_TIMEOUT_MILLIS = 1_000L
        const val WRITE_TIMEOUT_MILLIS = 2_000
        const val ADVERTISEMENT_WRITE_TIMEOUT_MILLIS = 50
        const val TUN_READ_BYTES = 4_096
        const val ZERO_READ_BACKOFF_NANOS = 1_000_000L
        const val JOIN_TIMEOUT_MILLIS = 2_000L
    }
}
