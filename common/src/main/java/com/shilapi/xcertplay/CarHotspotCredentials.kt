package com.shilapi.xcertplay

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager

/**
 * The car hotspot's own name and password, where the firmware still lets apps read them.
 * A typed name or password that is off by one character leaves the iPhone unable to join, with
 * nothing on the car side to show why.
 */
internal object CarHotspotCredentials {
    data class Credentials(val ssid: String, val passphrase: String)

    @Suppress("DEPRECATION")
    fun read(context: Context): Credentials? = runCatching {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val configuration = WifiManager::class.java.getMethod("getWifiApConfiguration").invoke(wifi) as? WifiConfiguration
        val ssid = configuration?.SSID?.removeSurrounding("\"")?.takeIf { it.isNotBlank() } ?: return null
        Credentials(ssid, configuration.preSharedKey?.removeSurrounding("\"").orEmpty())
    }.getOrNull()
}
