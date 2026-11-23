package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * #1222: the probe asked for the phone-side A2DP and HEADSET profiles, which a head unit does not run, so
 * no line ever followed "Car Bluetooth audio: on". It now asks for A2DP_SINK and HEADSET_CLIENT.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CarBluetoothAudioProbeTest {
    private val lines = mutableListOf<String>()
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private val states = mutableMapOf<Int, Int>()
    private val listeners = mutableMapOf<Int, BluetoothProfile.ServiceListener>()
    private val requested = mutableListOf<Int>()
    private val closed = mutableListOf<Int>()

    private fun probe(request: (Int, BluetoothProfile.ServiceListener) -> Boolean = { profile, listener ->
        requested += profile
        listeners[profile] = listener
        true
    }) = CarBluetoothAudioProbe(
        connectionState = { states[it] ?: BluetoothProfile.STATE_DISCONNECTED },
        requestProxy = request,
        closeProxy = { profile, _ -> closed += profile },
        after = { delay, action -> timers += delay to action },
        log = lines::add,
    )

    private fun proxy(devices: List<BluetoothDevice>) = object : BluetoothProfile {
        override fun getConnectedDevices(): List<BluetoothDevice> = devices
        override fun getDevicesMatchingConnectionStates(states: IntArray?): List<BluetoothDevice> = devices
        override fun getConnectionState(device: BluetoothDevice?): Int = BluetoothProfile.STATE_CONNECTED
    }

    @Test fun aProfileThatIsNotConnectedIsNotAskedForAProxy() {
        states[CarBluetoothAudioProbe.HEADSET_CLIENT] = BluetoothProfile.STATE_CONNECTING
        probe().probe("start")

        assertEquals(listOf(
            "Car Bluetooth audio: start A2DP-sink state=disconnected",
            "Car Bluetooth audio: start HFP-client state=connecting",
        ), lines)
        assertTrue(requested.isEmpty())
    }

    @Test fun aConnectedProfileCountsItsDevicesAndOneThatNeverAnswersStillWritesALine() {
        states[CarBluetoothAudioProbe.A2DP_SINK] = BluetoothProfile.STATE_CONNECTED
        states[CarBluetoothAudioProbe.HEADSET_CLIENT] = BluetoothProfile.STATE_CONNECTED
        probe().probe("after30s")
        val a2dpSink = listeners.getValue(CarBluetoothAudioProbe.A2DP_SINK)
        a2dpSink.onServiceConnected(CarBluetoothAudioProbe.A2DP_SINK, proxy(listOf(device("Jane's iPhone"))))
        timers.forEach { (delay, action) -> assertEquals(3_000L, delay); action() }
        // An answer after the timeout writes nothing more, but its proxy is still closed.
        listeners.getValue(CarBluetoothAudioProbe.HEADSET_CLIENT)
            .onServiceConnected(CarBluetoothAudioProbe.HEADSET_CLIENT, proxy(emptyList()))

        assertEquals(listOf(CarBluetoothAudioProbe.A2DP_SINK, CarBluetoothAudioProbe.HEADSET_CLIENT), requested)
        assertEquals(listOf(
            "Car Bluetooth audio: after30s A2DP-sink state=connected devices=1",
            "Car Bluetooth audio: after30s HFP-client state=connected no answer within 3s",
        ), lines)
        assertEquals(listOf(CarBluetoothAudioProbe.A2DP_SINK, CarBluetoothAudioProbe.HEADSET_CLIENT), closed)
        lines.forEach { assertEquals(it, DiagnosticRedactor.redact(it)) }
    }

    @Test fun aRefusedOrFailingRequestSaysSo() {
        states[CarBluetoothAudioProbe.A2DP_SINK] = BluetoothProfile.STATE_CONNECTED
        states[CarBluetoothAudioProbe.HEADSET_CLIENT] = BluetoothProfile.STATE_CONNECTED
        probe { profile, _ -> if (profile == CarBluetoothAudioProbe.A2DP_SINK) false else throw SecurityException("no") }
            .probe("start")
        CarBluetoothAudioProbe(
            connectionState = { throw SecurityException("BLUETOOTH_CONNECT") },
            requestProxy = { _, _ -> true }, closeProxy = { _, _ -> }, after = { _, _ -> }, log = lines::add,
        ).probe("start")

        assertEquals(listOf(
            "Car Bluetooth audio: start A2DP-sink state=connected proxy refused",
            "Car Bluetooth audio: start HFP-client state=connected proxy failed SecurityException",
            "Car Bluetooth audio: start A2DP-sink state unreadable SecurityException",
            "Car Bluetooth audio: start HFP-client state unreadable SecurityException",
        ), lines)
        assertTrue(timers.isEmpty())
    }

    /** The activity wires the probe to the adapter like this; device names must not reach the line. */
    @Test fun throughTheAdapterTheSinkSideProfilesAreTheOnesAsked() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        shadowOf(adapter).setProfileConnectionState(CarBluetoothAudioProbe.A2DP_SINK, BluetoothProfile.STATE_CONNECTED)
        shadowOf(adapter).setProfileProxy(CarBluetoothAudioProbe.A2DP_SINK,
            proxy(listOf(device("Jane's iPhone"), device("Car kit"))))
        CarBluetoothAudioProbe(
            connectionState = adapter::getProfileConnectionState,
            requestProxy = { profile, listener ->
                adapter.getProfileProxy(RuntimeEnvironment.getApplication(), listener, profile)
            },
            closeProxy = adapter::closeProfileProxy,
            after = { delay, action -> timers += delay to action },
            log = lines::add,
        ).probe("start")
        timers.forEach { it.second() }

        assertEquals(listOf(
            "Car Bluetooth audio: start A2DP-sink state=connected devices=2",
            "Car Bluetooth audio: start HFP-client state=disconnected",
        ), lines)
        assertFalse(lines.any { it.contains("Jane") || it.contains("connected=[") })
    }

    private fun device(name: String): BluetoothDevice =
        BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:${"%02X".format(name.length)}")
            .also { shadowOf(it).setName(name) }
}
