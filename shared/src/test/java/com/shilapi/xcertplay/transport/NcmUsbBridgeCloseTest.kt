package com.shilapi.xcertplay.transport

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** The connection must remain open until a blocking bulk OUT transfer finishes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, shadows = [BlockingNcmConnectionShadow::class])
class NcmUsbBridgeCloseTest {
    @Test fun closeWaitsForInFlightWriteAndRejectsLaterWrites() {
        BlockingNcmConnection.reset()
        val connection = ReflectionHelpers.callConstructor(UsbDeviceConnection::class.java,
            ClassParameter.from(UsbDevice::class.java, null))
        fun endpoint(address: Int): UsbEndpoint = ReflectionHelpers.callConstructor(UsbEndpoint::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, address),
            ClassParameter.from(Int::class.javaPrimitiveType, 2),
            ClassParameter.from(Int::class.javaPrimitiveType, 512),
            ClassParameter.from(Int::class.javaPrimitiveType, 0))
        val bridge = NcmUsbBridge(connection, endpoint(0x06), endpoint(0x88), null, emptyList(), null)
        val failure = AtomicReference<Throwable?>()
        val frame = byteArrayOf(0x33, 0x33, 0, 0, 0, 1, 0x86.toByte(), 0xdd.toByte())
        val writer = Thread { try { bridge.send(frame, 2_000) } catch (e: Throwable) { failure.set(e) } }
        val closerStarted = CountDownLatch(1)
        val closer = Thread {
            closerStarted.countDown()
            try { bridge.close() } catch (e: Throwable) { failure.set(e) }
        }
        try {
            writer.start()
            assertTrue(BlockingNcmConnection.transferStarted.await(2, TimeUnit.SECONDS))
            assertFalse("neighbor reply must fall back rather than wait behind a blocked writer", bridge.trySendAdvertisement(frame, 50))
            closer.start()
            assertTrue(closerStarted.await(2, TimeUnit.SECONDS))
            assertFalse("connection closed during bulk OUT", BlockingNcmConnection.closed.await(100, TimeUnit.MILLISECONDS))
        } finally {
            BlockingNcmConnection.releaseTransfer.countDown()
            writer.join(2_000)
            closer.join(2_000)
        }
        assertFalse(writer.isAlive)
        assertFalse(closer.isAlive)
        assertNull(failure.get())
        assertTrue(BlockingNcmConnection.closed.await(1, TimeUnit.SECONDS))
        val rejected = try { bridge.send(frame, 2_000); false } catch (_: IphoneUsbException.DeviceUnavailable) { true }
        assertTrue("write after close must be rejected", rejected)
    }
}

object BlockingNcmConnection {
    lateinit var transferStarted: CountDownLatch
    lateinit var releaseTransfer: CountDownLatch
    lateinit var closed: CountDownLatch
    fun reset() {
        transferStarted = CountDownLatch(1)
        releaseTransfer = CountDownLatch(1)
        closed = CountDownLatch(1)
    }
}

@Implements(UsbDeviceConnection::class)
class BlockingNcmConnectionShadow {
    @Implementation fun bulkTransfer(endpoint: UsbEndpoint, buffer: ByteArray, length: Int, timeout: Int): Int {
        BlockingNcmConnection.transferStarted.countDown()
        check(BlockingNcmConnection.releaseTransfer.await(3, TimeUnit.SECONDS))
        return length
    }
    @Implementation fun close() { BlockingNcmConnection.closed.countDown() }
}
