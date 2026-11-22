package com.shilapi.xcertplay

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.network.WirelessStartupException
import com.shilapi.xcertplay.network.WirelessStartupFailure
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.BluetoothLinkCheck
import com.shilapi.xcertplay.transport.BluetoothRfcommDuplexStream
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * The unreal-Bluetooth check as the controller runs it, with a control connect that "succeeds" at
 * once, as it did on the Allwinner T8 (PR 590) and on the XTRONS IX in the PR 1178 report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class BluetoothLinkCheckControllerTest {
    private lateinit var controller: CarPlayController
    private val logs = mutableListOf<String>()
    private val streams = mutableListOf<BluetoothRfcommDuplexStream>()

    @Before fun setUp() {
        val config = AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(800, 480))
        val identity = AirPlayIdentity(ByteArray(32), ByteArray(32), "test")
        val listener = object : AirPlaySessionListener {
            override fun onDebugLog(message: String) { synchronized(logs) { logs += message } }
        }
        controller = CarPlayController(object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = false
        }, CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL, transport = CarPlayTransport.WIRELESS,
            identification = Iap2IdentificationConfig(name = "test", modelIdentifier = "test", manufacturer = "test",
                serialNumber = "test", firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3)),
            config, identity, PairingStore(), listener, object : AirPlayMediaHandler {}, {})
    }

    @After fun tearDown() {
        streams.forEach { it.close() }
        controller.close()
        controller.awaitClosed(2_000)
    }

    @Test fun anAdapterThatNeverCarriedAByteStillStops() {
        val (failure, line) = checkInstant(newAdapter(), BluetoothLinkCheck.Trigger.SILENCE)
        assertTrue(line, line.contains("unreal=true adapterProven=false verdict=UNREAL"))
        assertTrue(failure is WirelessStartupException)
        assertEquals(WirelessStartupFailure.BLUETOOTH_UNUSABLE, (failure as WirelessStartupException).reason)
        assertTrue(failure.message!!.contains(BluetoothLinkCheck.UNREAL_MARK))
    }

    // PR 1178 report: 15:29:37, 17 s after this process had completed iAP2 over Bluetooth.
    @Test fun aSilentLinkOnAnAdapterThatCarriedDataIsRetried() {
        val adapter = newAdapter()
        recordProof(adapter)

        val (silence, line) = checkInstant(adapter, BluetoothLinkCheck.Trigger.SILENCE)
        assertTrue(line, line.contains("unreal=false adapterProven=true verdict=ADAPTER_PROVEN"))
        assertNotNull(silence)
        assertFalse(silence is WirelessStartupException)
        assertFalse(silence!!.message!!.contains(BluetoothLinkCheck.UNREAL_MARK))
        assertTrue(log("wireless Bluetooth link check trigger=silence:").contains("not reported unusable"))

        // A failed attempt keeps its own failure, which is retried.
        val (failure, failureLine) = checkInstant(adapter, BluetoothLinkCheck.Trigger.FAILURE)
        assertTrue(failureLine, failureLine.contains("verdict=ADAPTER_PROVEN"))
        assertNull(failure)
    }

    // Whatever the control connect took, a proven adapter is never reported as unusable.
    @Test fun aProvenAdapterIsNeverReportedUnusable() {
        val adapter = newAdapter().also(::recordProof)
        repeat(3) {
            for (trigger in BluetoothLinkCheck.Trigger.entries) {
                assertFalse(check(link(adapter), trigger) is WirelessStartupException)
                assertTrue(diagnostic(trigger).contains("adapterProven=true"))
            }
        }
    }

    @Test fun proofIsSavedAndASavedProofIsHonouredAfterARestart() {
        val adapter = newAdapter()
        recordProof(adapter)
        assertEquals(adapter, prefs().getString("proven_bluetooth_adapter", null))
        assertTrue(logs.any { it.contains("adapter carried RFCOMM data") })

        // As if DiPlay had been restarted: only the saved key knows.
        val saved = newAdapter()
        prefs().edit().putString("proven_bluetooth_adapter", saved).commit()
        assertFalse(check(link(saved), BluetoothLinkCheck.Trigger.SILENCE) is WirelessStartupException)
        assertTrue(diagnostic(BluetoothLinkCheck.Trigger.SILENCE).contains("adapterProven=true"))
    }

    // A real stack refuses a service no phone offers: the link passes, as it did before.
    @Test fun aRefusedControlConnectPassesOnAnyAdapter() {
        val proven = newAdapter().also(::recordProof)
        for (adapter in listOf(newAdapter(), proven)) {
            for (trigger in BluetoothLinkCheck.Trigger.entries) {
                assertNull(check(link(adapter, controlConnects = false), trigger))
            }
        }
        assertTrue(logs.filter { it.contains("Bluetooth link check trigger=") && it.contains("connectMs=") }.all { it.contains("verdict=PASSED") })
    }

    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences("diplay_wireless", Context.MODE_PRIVATE)

    /** Proof is kept for the process, so each test uses adapters no other test has seen. */
    private fun newAdapter() = UUID.randomUUID().toString()

    private fun log(prefix: String): String = synchronized(logs) { logs.last { it.contains(prefix) } }

    /**
     * Runs the check until its control connect is measured as instant, as it is on the head units;
     * the first mocked connect can take longer than [BluetoothLinkCheck.INSTANT_CONNECT_MILLIS].
     */
    private fun checkInstant(adapter: String, trigger: BluetoothLinkCheck.Trigger): Pair<IOException?, String> {
        repeat(10) {
            val failure = check(link(adapter), trigger)
            val line = diagnostic(trigger)
            val controlMillis = Regex("controlMs=(\\d+)").find(line)!!.groupValues[1].toLong()
            if (controlMillis < BluetoothLinkCheck.INSTANT_CONNECT_MILLIS) return failure to line
        }
        assumeTrue("the control connect was never instant on this machine", false)
        error("unreachable")
    }

    /** The report line of the last check that [trigger] set off. */
    private fun diagnostic(trigger: BluetoothLinkCheck.Trigger) = log("Bluetooth link check trigger=${trigger.label} connectMs=")

    private fun recordProof(adapter: String) {
        controller.javaClass.getDeclaredMethod("recordBluetoothAdapterProof", String::class.java)
            .apply { isAccessible = true }.invoke(controller, adapter)
    }

    private fun check(link: Any, trigger: BluetoothLinkCheck.Trigger): IOException? =
        controller.javaClass.getDeclaredMethod("unrealBluetoothFailure", link.javaClass,
            BluetoothLinkCheck.Trigger::class.java)
            .apply { isAccessible = true }.invoke(controller, link, trigger) as IOException?

    /** An instant, silent link to a phone whose head unit answers a control connect as told. */
    private fun link(adapter: String, controlConnects: Boolean = true): Any {
        val control = mock(BluetoothSocket::class.java)
        if (!controlConnects) doThrow(IOException("Service discovery failed")).`when`(control).connect()
        val device = mock(BluetoothDevice::class.java)
        doReturn("00:11:22:33:44:55").`when`(device).address
        doReturn(control).`when`(device).createRfcommSocketToServiceRecord(any())
        val input = SilentInput()
        val bootstrap = mock(BluetoothSocket::class.java)
        doReturn(input).`when`(bootstrap).inputStream
        doReturn(ByteArrayOutputStream()).`when`(bootstrap).outputStream
        doAnswer { input.close() }.`when`(bootstrap).close()
        val stream = BluetoothRfcommDuplexStream(bootstrap).also { streams += it }
        val type = Class.forName("com.shilapi.xcertplay.orchestration.CarPlayController\$InstantBluetoothLink")
        return type.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(0, device, stream, 4L, adapter)
    }

    private class SilentInput : InputStream() {
        private val closed = CountDownLatch(1)
        override fun read(): Int {
            closed.await()
            throw IOException("closed")
        }
        override fun close() = closed.countDown()
    }
}
