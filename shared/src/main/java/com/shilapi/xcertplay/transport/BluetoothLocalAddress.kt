package com.shilapi.xcertplay.transport

import android.bluetooth.BluetoothAdapter
import android.os.Build

/**
 * The adapter's own address, which is what the iPhone paired with. Android 6+ hands apps a
 * placeholder; through Android 8.1 the Bluetooth service itself still answers.
 */
object BluetoothLocalAddress {
    private val FORMAT = Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")

    fun fromService(adapter: BluetoothAdapter?): String? {
        if (adapter == null || Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) return null
        return runCatching {
            val service = BluetoothAdapter::class.java.getDeclaredField("mService")
                .apply { isAccessible = true }.get(adapter)
            service?.javaClass?.getMethod("getAddress")?.invoke(service) as? String
        }.getOrNull()?.takeIf(::usable)
    }

    fun usable(address: String): Boolean =
        FORMAT.matches(address) && !address.startsWith("02:00:00:00:00:") && address != "00:00:00:00:00:00"
}
