package com.shilapi.xcertplay.network

import android.content.Context
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

object CarHotspotSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("diplay_car_hotspot", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("auto_enable", false)

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("auto_enable", enabled).apply()

    /** The user's standing request to turn the car hotspot off when an app-owned one is needed. */
    fun stopForAppHotspot(context: Context): Boolean = prefs(context).getBoolean("stop_for_app_hotspot", false)

    fun setStopForAppHotspot(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("stop_for_app_hotspot", enabled).apply()

    /** True when that request can be carried out now. */
    fun mayStopForAppHotspot(context: Context): Boolean =
        stopForAppHotspot(context) && CarHotspotTethering.permitted(context)

    // Visibility is independent of the saved choice: ADB is only needed to grant permission.
    fun visible(bydAvailable: Boolean, access: LocalAdb.Access): Boolean =
        bydAvailable && (access == LocalAdb.Access.READY || access == LocalAdb.Access.NOT_APPROVED)

    fun shouldEnable(context: Context, wireless: Boolean, mode: WirelessHotspotMode): Boolean =
        enabled(context) && wireless && mode == WirelessHotspotMode.MANUAL
}
