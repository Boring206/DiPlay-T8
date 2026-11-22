package com.shilapi.xcertplay.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import com.shilapi.xcertplay.orchestration.ConnectionFailureCause

/**
 * Whether the head unit's firmware has Wi-Fi Direct at all.
 *
 * Some firmware ships without the wifip2p service, for example the XPeng P7 E28 on Android 9 (#1256).
 * Android then has no [WifiP2pManager], and no retry can create a group. Where a manager exists, the
 * framework's own P2P_UNSUPPORTED answer to createGroup says the same thing (see [unsupported]).
 * The android.hardware.wifi.direct feature alone does not decide it: nothing shows that every head
 * unit where Wi-Fi Direct works declares it, so it is only logged ([diagnostic]).
 */
object WifiDirectSupport {
    fun available(context: Context): Boolean = hasManager(context)

    /** One line for the connection log, so reports show whether the feature flag matches the service. */
    fun diagnostic(context: Context): String = "wifiDirect feature=${hasFeature(context)} manager=${hasManager(context)}"

    private fun hasManager(context: Context): Boolean = context.getSystemService(WifiP2pManager::class.java) != null

    private fun hasFeature(context: Context): Boolean =
        runCatching { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) }.getOrDefault(false)

    /** Not retried: the driver has to choose another connection in Connection setup. */
    fun unsupported(cause: Throwable? = null): WirelessStartupException = WirelessStartupException(
        WirelessStartupFailure.HOTSPOT_CONFIGURATION,
        "This head unit does not support Wi-Fi Direct. Choose Built-in car hotspot or Existing Wi-Fi / Same LAN in Connection setup.",
        ConnectionFailureCause.WIFI_DIRECT_UNSUPPORTED,
    ).apply { if (cause != null) initCause(cause) }
}
