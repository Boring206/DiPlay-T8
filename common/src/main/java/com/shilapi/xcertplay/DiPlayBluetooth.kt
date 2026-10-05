package com.shilapi.xcertplay

import android.bluetooth.BluetoothManager
import android.content.Context
import android.provider.Settings
import com.shilapi.xcertplay.transport.BluetoothLocalAddress

internal object DiPlayBluetooth {
    fun localAddress(context: Context): String? {
        val bluetooth = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
        val adapter = runCatching { bluetooth?.address }.getOrNull()
        val setting = runCatching { Settings.Secure.getString(context.contentResolver, "bluetooth_address") }.getOrNull()
        return listOfNotNull(BluetoothLocalAddress.fromService(bluetooth), adapter, setting).firstOrNull {
            Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}").matches(it) &&
                !it.startsWith("02:00:00:00:00:") && it != "00:00:00:00:00:00"
        }
    }
}
