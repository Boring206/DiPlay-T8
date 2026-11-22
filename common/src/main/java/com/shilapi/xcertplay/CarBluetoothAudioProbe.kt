package com.shilapi.xcertplay

import android.bluetooth.BluetoothProfile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Car Bluetooth audio (#1222): whether the iPhone keeps its Bluetooth audio and call links with the car.
 *
 * A head unit is the sink side of a phone, so it runs A2DP_SINK (11) and HEADSET_CLIENT (16), not the
 * phone-side A2DP and HEADSET. A profile service that is absent never answers a proxy request, so, as
 * CarPlayController.connectedBluetoothDevices does, the connection state is read first and a proxy is
 * asked for only while the profile is connected. A request that gets no answer still writes a line.
 * Lines carry counts and states only: device names and addresses never reach the report.
 */
internal class CarBluetoothAudioProbe(
    private val connectionState: (profile: Int) -> Int,
    private val requestProxy: (profile: Int, listener: BluetoothProfile.ServiceListener) -> Boolean,
    private val closeProxy: (profile: Int, proxy: BluetoothProfile) -> Unit,
    private val after: (delayMillis: Long, action: () -> Unit) -> Unit,
    private val log: (String) -> Unit,
) {
    /** One line per profile for [moment], such as "start" or "after30s". */
    fun probe(moment: String) {
        for ((label, profile) in PROFILES) probe(moment, label, profile)
    }

    private fun probe(moment: String, label: String, profile: Int) {
        val prefix = "Car Bluetooth audio: $moment $label"
        val state = try {
            connectionState(profile)
        } catch (error: RuntimeException) {
            return log("$prefix state unreadable ${error.javaClass.simpleName}")
        }
        if (state != BluetoothProfile.STATE_CONNECTED) return log("$prefix state=${stateName(state)}")
        val answered = AtomicBoolean(false)
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(id: Int, proxy: BluetoothProfile) {
                if (answered.compareAndSet(false, true)) {
                    val count = runCatching { proxy.connectedDevices.orEmpty().size }
                    log(count.fold(
                        { "$prefix state=connected devices=$it" },
                        { "$prefix state=connected devices unreadable ${it.javaClass.simpleName}" },
                    ))
                }
                runCatching { closeProxy(id, proxy) }
            }

            override fun onServiceDisconnected(id: Int) = Unit
        }
        val requested = try {
            requestProxy(profile, listener)
        } catch (error: RuntimeException) {
            return log("$prefix state=connected proxy failed ${error.javaClass.simpleName}")
        }
        if (!requested) return log("$prefix state=connected proxy refused")
        after(ANSWER_TIMEOUT_MILLIS) {
            if (answered.compareAndSet(false, true)) {
                log("$prefix state=connected no answer within ${ANSWER_TIMEOUT_MILLIS / 1_000}s")
            }
        }
    }

    companion object {
        /** Hidden in the public SDK: BluetoothProfile.A2DP_SINK and BluetoothProfile.HEADSET_CLIENT. */
        const val A2DP_SINK = 11
        const val HEADSET_CLIENT = 16
        const val ANSWER_TIMEOUT_MILLIS = 3_000L
        val PROFILES = listOf("A2DP-sink" to A2DP_SINK, "HFP-client" to HEADSET_CLIENT)

        fun stateName(state: Int): String = when (state) {
            BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
            BluetoothProfile.STATE_CONNECTING -> "connecting"
            BluetoothProfile.STATE_CONNECTED -> "connected"
            BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
            else -> "unknown($state)"
        }
    }
}
