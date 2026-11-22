package com.shilapi.xcertplay.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import java.io.Closeable
import java.net.Inet4Address

/**
 * Android's own Wi-Fi station during a wired CarPlay session (#1233): when the head unit joins or leaves
 * a Wi-Fi network, such as the iPhone's Personal Hotspot, and whether Android validated it or treats it
 * as metered. The iPhone's hotspot hands out addresses in 172.20.10.0/28, so a network in that range is
 * flagged. No network name, BSSID or address is logged; a network is numbered in the order it appeared.
 */
class WifiStationWatch(context: Context, private val log: (String) -> Unit) : Closeable {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val state = WifiStationLog(log)
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() = synchronized(this) {
        if (callback != null) return@synchronized
        val manager = connectivity ?: return@synchronized log(WifiStationLines.unavailable("no ConnectivityManager"))
        val next = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = state.available(network)

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = state.flags(
                network,
                validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                internet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            )

            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) =
                state.iphoneHotspotRange(network, properties.linkAddresses.any { iphoneHotspotRange(it.address) })

            override fun onLost(network: Network) = state.lost(network)
        }
        try {
            manager.registerNetworkCallback(wifiRequest(), next)
            callback = next
            log(WifiStationLines.watching())
        } catch (error: RuntimeException) {
            log(WifiStationLines.unavailable(error.javaClass.simpleName))
        }
    }

    override fun close() = synchronized(this) {
        val registered = callback ?: return@synchronized
        callback = null
        runCatching { connectivity?.unregisterNetworkCallback(registered) }
        Unit
    }

    // Every Wi-Fi network, with or without internet. NetworkRequest.Builder.clearCapabilities needs API 30.
    private fun wifiRequest(): NetworkRequest {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            NetworkRequest.Builder().clearCapabilities()
        } else {
            NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
        }
        return builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
    }

    companion object {
        /** The iPhone's Personal Hotspot always uses 172.20.10.0/28. */
        fun iphoneHotspotRange(address: java.net.InetAddress): Boolean {
            if (address !is Inet4Address) return false
            val bytes = address.address
            return (bytes[0].toInt() and 0xff) == 172 && (bytes[1].toInt() and 0xff) == 20 &&
                (bytes[2].toInt() and 0xff) == 10 && (bytes[3].toInt() and 0xff) < 16
        }
    }
}

/**
 * The state behind [WifiStationWatch]: one line when a station network appears or goes, and one when
 * its flags change. Keyed by any object so tests need no [Network].
 */
internal class WifiStationLog(
    private val log: (String) -> Unit,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Station(val number: Int, val sinceMillis: Long) {
        var validated: Boolean? = null
        var metered: Boolean? = null
        var internet: Boolean? = null
        var iphoneHotspotRange: Boolean? = null
        var lastFlags: String? = null
    }

    private val stations = HashMap<Any, Station>()
    private var appeared = 0

    fun available(key: Any) {
        val line = synchronized(this) {
            if (stations.containsKey(key)) return
            val station = Station(++appeared, nowMillis())
            stations[key] = station
            WifiStationLines.connected(station.number, stations.size)
        }
        log(line)
    }

    fun flags(key: Any, validated: Boolean, metered: Boolean, internet: Boolean) = update(key) {
        it.validated = validated
        it.metered = metered
        it.internet = internet
    }

    fun iphoneHotspotRange(key: Any, inRange: Boolean) = update(key) { it.iphoneHotspotRange = inRange }

    fun lost(key: Any) {
        val line = synchronized(this) {
            val station = stations.remove(key) ?: return
            WifiStationLines.disconnected(station.number, (nowMillis() - station.sinceMillis) / 1_000, stations.size)
        }
        log(line)
    }

    private fun update(key: Any, change: (Station) -> Unit) {
        val line = synchronized(this) {
            val station = stations[key] ?: return
            change(station)
            val flags = WifiStationLines.flags(
                station.number, station.validated, station.metered, station.internet, station.iphoneHotspotRange,
            )
            if (flags == station.lastFlags) return
            station.lastFlags = flags
            flags
        }
        log(line)
    }
}

/** The text of each Wi-Fi station line: counts and flags only, never a network name or address. */
object WifiStationLines {
    fun watching() = "Android Wi-Fi station watch on (wired session)"
    fun unavailable(reason: String) = "Android Wi-Fi station watch unavailable $reason"
    fun connected(number: Int, connectedNow: Int) = "Android Wi-Fi station #$number connected stations=$connectedNow"
    fun disconnected(number: Int, afterSeconds: Long, connectedNow: Int) =
        "Android Wi-Fi station #$number disconnected after ${afterSeconds}s stations=$connectedNow"
    fun flags(number: Int, validated: Boolean?, metered: Boolean?, internet: Boolean?, iphoneHotspotRange: Boolean?) =
        "Android Wi-Fi station #$number validated=${validated.text()} metered=${metered.text()} " +
            "internet=${internet.text()} iPhoneHotspotRange=${iphoneHotspotRange.text()}"

    private fun Boolean?.text() = this?.toString() ?: "unknown"
}
