package com.shilapi.xcertplay

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PhoneBluetoothReceiverTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val address = "AA:BB:CC:DD:EE:FF"

    @After fun tearDown() = CarPlayBackgroundSession.clear()

    private fun phoneConnects() {
        val phone = context.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(address)
        PhoneBluetoothReceiver().onReceive(context,
            Intent(BluetoothDevice.ACTION_ACL_CONNECTED).putExtra(BluetoothDevice.EXTRA_DEVICE, phone))
    }

    @Test fun theChosenPhoneOpensDiPlayToConnect() {
        DiPlayPreferences.savePhone(context, address, "iPhone")
        DiPlayPreferences.saveAutoConnectOnBluetooth(context, true)

        phoneConnects()

        val started = shadowOf(context).nextStartedActivity
        assertEquals(DiPlayActivity::class.java.name, started.component?.className)
        assertTrue(started.getBooleanExtra(DiPlayActivity.EXTRA_AUTO_CONNECT, false))
    }

    // Every attempt restarts the hotspot before Bluetooth can fail again.
    @Test fun aUnitWhoseBluetoothReachesNoPhoneIsLeftAlone() {
        DiPlayPreferences.savePhone(context, address, "iPhone")
        DiPlayPreferences.saveAutoConnectOnBluetooth(context, true)
        DiPlayPreferences.saveBluetoothUnreal(context, true)

        phoneConnects()

        assertNull(shadowOf(context).nextStartedActivity)
    }
}
