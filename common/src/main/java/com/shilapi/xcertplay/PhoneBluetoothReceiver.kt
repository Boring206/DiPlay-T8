package com.shilapi.xcertplay

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Opens DiPlay and connects when the chosen iPhone's Bluetooth link comes up, if the user asked
 * for that. Head units often sleep instead of rebooting, so boot start alone never fires.
 */
class PhoneBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        if (!DiPlayPreferences.autoConnectOnBluetooth(context) || CarPlayBackgroundSession.hasSession()) return
        // Not for a unit whose Bluetooth was found to reach no phone: see DiPlayActivity.autoConnectHeld.
        if (DiPlayPreferences.bluetoothUnreal(context)) return
        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        if (!device.address.equals(DiPlayPreferences.phoneAddress(context), ignoreCase = true)) return
        val launch = Intent(context, DiPlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(DiPlayActivity.EXTRA_AUTO_CONNECT, true)
        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "Could not open DiPlay for the connected iPhone", it) }
    }

    private companion object {
        const val TAG = "xcertplay-bluetooth"
    }
}
