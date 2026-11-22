package com.shilapi.xcertplay.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import com.shilapi.xcertplay.orchestration.ConnectionFailureCause

/**
 * Whether the head unit's firmware has Wi-Fi Direct at all.
 *
 * Some firmware ships without the wifip2p service, for example the XPeng P7 E28 on Android 9 (#1256).
 * Android then has no [WifiP2pManager], and no retry can create a group.
 */
object WifiDirectSupport {
    fun available(context: Context): Boolean = available(
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT),
        context.getSystemService(WifiP2pManager::class.java) != null,
    )

    fun available(hasFeature: Boolean, hasService: Boolean): Boolean = hasFeature && hasService

    /** Not retried: the driver has to choose another connection in Connection setup. */
    fun unsupported(): WirelessStartupException = WirelessStartupException(
        WirelessStartupFailure.HOTSPOT_CONFIGURATION,
        "This head unit does not support Wi-Fi Direct. Choose Built-in car hotspot or Existing Wi-Fi / Same LAN in Connection setup.",
        ConnectionFailureCause.WIFI_DIRECT_UNSUPPORTED,
    )
}
