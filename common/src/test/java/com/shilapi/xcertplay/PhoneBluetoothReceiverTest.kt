package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PhoneBluetoothReceiverTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val selected = "12:34:56:78:9A:BC"

    @Before fun reset() {
        DiPlayPreferences.saveConnectOnPhoneBluetooth(app, false)
        app.getSharedPreferences("diplay_phone_wake", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("diplay_phone_wake_diagnostics", Context.MODE_PRIVATE).edit().clear().commit()
        CarPlayBackgroundSession.clear()
        // Saved in lowercase on purpose: the receiver must match the (always uppercase) device
        // address case-insensitively; Robolectric refuses to build a lowercase remote device.
        DiPlayPreferences.savePhone(app, selected.lowercase(), "iPhone")
    }

    private fun event(address: String): Intent {
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
        return Intent(BluetoothDevice.ACTION_ACL_CONNECTED)
            .putExtra(BluetoothDevice.EXTRA_DEVICE, device)
    }

    @Test fun onlySelectedPhoneWithOptInLaunchesOnce() {
        val launches = mutableListOf<Intent>()
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { launches += intent }
        }
        val receiver = PhoneBluetoothReceiver()
        receiver.onReceive(context, event(selected))
        assertTrue(launches.isEmpty())
        DiPlayPreferences.saveConnectOnPhoneBluetooth(app, true)
        receiver.onReceive(context, event("AA:BB:CC:DD:EE:FF"))
        assertTrue(launches.isEmpty())
        receiver.onReceive(context, event(selected))
        receiver.onReceive(context, event(selected))
        assertEquals(1, launches.size)
        assertEquals(DiPlayActivity::class.java.name, launches.single().component?.className)
        assertEquals(PhoneBluetoothReceiver.launchToken, launches.single().getStringExtra(PhoneBluetoothReceiver.EXTRA_AUTO_CONNECT))
        assertTrue(PhoneWakeDiagnostics.report(app).contains("result=startActivity-returned"))
    }

    @Test fun savedElapsedTimeFromAnotherBootDoesNotSuppressTheFirstConnection() {
        DiPlayPreferences.saveConnectOnPhoneBluetooth(app, true)
        // This is indistinguishable from a recent launch if the receiver only compares clocks.
        app.getSharedPreferences("diplay_phone_wake", Context.MODE_PRIVATE).edit()
            .putLong("last_launch_elapsed", SystemClock.elapsedRealtime())
            .putString("launch_process", "previous-process")
            .commit()
        val launches = mutableListOf<Intent>()
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { launches += intent }
        }
        PhoneBluetoothReceiver().onReceive(context, event(selected))
        PhoneBluetoothReceiver().onReceive(context, event(selected))
        assertEquals(1, launches.size)
    }

    @Test fun failedLaunchAllowsTheNextMatchingEventToRetryImmediately() {
        DiPlayPreferences.saveConnectOnPhoneBluetooth(app, true)
        var attempts = 0
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) {
                if (++attempts == 1) throw SecurityException("Background launch rejected")
            }
        }
        PhoneBluetoothReceiver().onReceive(context, event(selected))
        assertTrue(PhoneWakeDiagnostics.report(app).contains("result=failed"))
        PhoneBluetoothReceiver().onReceive(context, event(selected))
        assertEquals(2, attempts)
        assertTrue(PhoneWakeDiagnostics.report(app).contains("result=startActivity-returned"))
    }
}
