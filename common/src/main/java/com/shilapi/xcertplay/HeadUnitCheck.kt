package com.shilapi.xcertplay

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.MediaCodecList
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.network.CarHotspotStatus
import com.shilapi.xcertplay.network.CarHotspotTethering
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.io.File
import java.net.NetworkInterface
import java.util.Collections

/**
 * Checks, before any connection attempt, whether this head unit exposes what wireless CarPlay
 * needs. Aftermarket units often route Bluetooth through a vendor module that apps cannot use;
 * that is a hardware limit the connection flow would otherwise only show as an endless wait.
 */
internal object HeadUnitCheck {
    enum class Level { OK, WARNING, BLOCKED, INFO }
    enum class Action {
        BLUETOOTH_SETTINGS, HOTSPOT_SETTINGS, LOCATION_SETTINGS, CONNECTION_SETUP, CHOOSE_PHONE, CAR_HOTSPOT_CHOICE,
    }
    data class Item(val level: Level, val text: String, val action: Action? = null)

    fun run(context: Context): List<Item> = bluetooth(context) + wifi(context) + listOf(decoder(context))

    private fun bluetooth(context: Context): List<Item> {
        val adapter = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
            ?: return listOf(Item(Level.BLOCKED, context.getString(R.string.check_bt_missing)))
        if (runCatching { adapter.isEnabled }.getOrDefault(false).not()) {
            // Up to Android 11 DiPlay switches Bluetooth on itself when connecting.
            return listOf(
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) Item(Level.INFO, context.getString(R.string.check_bt_off_auto))
                else Item(Level.BLOCKED, context.getString(R.string.check_bt_off), Action.BLUETOOTH_SETTINGS),
            )
        }
        val bonded = runCatching { adapter.bondedDevices.orEmpty().toList() }.getOrNull()
            ?: return listOf(Item(Level.WARNING, context.getString(R.string.check_bt_permission), Action.CHOOSE_PHONE))
        val on = Item(Level.OK, context.getString(R.string.check_bt_on))
        if (bonded.isEmpty()) {
            return listOf(on, Item(Level.BLOCKED, context.getString(R.string.check_phone_none), Action.BLUETOOTH_SETTINGS))
        }
        val selected = DiPlayPreferences.phoneAddress(context)
        val phone = bonded.firstOrNull { it.address.equals(selected, ignoreCase = true) }
            ?: bonded.firstOrNull { runCatching { it.name }.getOrNull()?.contains("iPhone", ignoreCase = true) == true }
        return listOf(
            on,
            if (phone != null) {
                Item(Level.OK, context.getString(R.string.check_phone_ok, runCatching { phone.name }.getOrNull() ?: "iPhone"))
            } else {
                Item(Level.WARNING, context.getString(R.string.check_phone_choose, bonded.size), Action.CHOOSE_PHONE)
            },
        )
    }

    private fun wifi(context: Context): List<Item> {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return listOf(Item(Level.BLOCKED, context.getString(R.string.check_wifi_missing)))
        val items = mutableListOf<Item>()
        if (AirPlayPersistence.loadWirelessHotspotMode(context) == WirelessHotspotMode.MANUAL) {
            val savedSsid = AirPlayPersistence.loadManualHotspotSsid(context)
            val fromCar = CarHotspotCredentials.read(context)
            if (savedSsid.isNullOrBlank()) {
                items += Item(Level.BLOCKED, context.getString(R.string.check_hotspot_not_saved), Action.CONNECTION_SETUP)
            } else if (fromCar != null && (fromCar.ssid != savedSsid ||
                    fromCar.passphrase != AirPlayPersistence.loadManualHotspotPassphrase(context).orEmpty())
            ) {
                items += Item(Level.BLOCKED, context.getString(R.string.check_hotspot_mismatch, fromCar.ssid), Action.CONNECTION_SETUP)
            }
            items += when (CarHotspotStatus.isEnabled(context)) {
                true -> Item(Level.OK, context.getString(R.string.check_hotspot_on))
                false -> if (CarHotspotSettings.enabled(context) && CarHotspotTethering.permitted(context)) {
                    Item(Level.INFO, context.getString(R.string.check_hotspot_auto))
                } else {
                    Item(Level.BLOCKED, context.getString(R.string.check_hotspot_off), Action.HOTSPOT_SETTINGS)
                }
                null -> Item(Level.INFO, context.getString(R.string.check_hotspot_unknown), Action.HOTSPOT_SETTINGS)
            }
        }
        val mode = AirPlayPersistence.loadWirelessHotspotMode(context)
        // The iPhone can either share its own hotspot or join the car's, never both.
        if ((mode == WirelessHotspotMode.MANUAL || mode == WirelessHotspotMode.LOCAL_ONLY_HOTSPOT) && joinedPhoneHotspot(wifi)) {
            items += Item(Level.BLOCKED, context.getString(R.string.check_joined_phone_hotspot))
        }
        if (mode == WirelessHotspotMode.LOCAL_ONLY_HOTSPOT) {
            // The platform refuses an app-owned hotspot while location is off or tethering is on.
            if (!locationOn(context)) items += Item(Level.BLOCKED, context.getString(R.string.check_location_off), Action.LOCATION_SETTINGS)
            if (tetheredHotspotOn(context)) {
                items += if (CarHotspotSettings.mayStopForAppHotspot(context)) {
                    Item(Level.INFO, context.getString(R.string.check_app_hotspot_stops_car))
                } else if (carHotspotSaved(context)) {
                    Item(Level.INFO, context.getString(R.string.check_app_hotspot_uses_car))
                } else {
                    Item(Level.BLOCKED, context.getString(R.string.check_app_hotspot_conflict), Action.CAR_HOTSPOT_CHOICE)
                }
            }
        }
        items += when (runCatching { wifi.is5GHzBandSupported }.getOrNull()) {
            true -> Item(Level.OK, context.getString(R.string.check_wifi_5ghz))
            false -> Item(Level.INFO, context.getString(R.string.check_wifi_24ghz))
            null -> Item(Level.INFO, context.getString(R.string.check_wifi_band_unknown))
        }
        return items
    }

    /** While DiPlay holds a session the running AP is its own, whatever the firmware reports. */
    private fun tetheredHotspotOn(context: Context): Boolean =
        !CarPlayBackgroundSession.hasSession() && CarHotspotStatus.tetheredOn(context)

    fun carHotspotSaved(context: Context): Boolean = ManualHotspotValidation.error(
        AirPlayPersistence.loadManualHotspotSsid(context).orEmpty(),
        AirPlayPersistence.loadManualHotspotPassphrase(context).orEmpty(),
    ) == null

    @Suppress("DEPRECATION")
    private fun locationOn(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE) != Settings.Secure.LOCATION_MODE_OFF
    }.getOrDefault(true)

    /** True while the head unit's Wi-Fi is a client of an iPhone's Personal Hotspot. */
    fun joinedPhoneHotspot(context: Context): Boolean =
        context.applicationContext.getSystemService(WifiManager::class.java)?.let(::joinedPhoneHotspot) ?: false

    /** iOS Personal Hotspot always hands out 172.20.10.0/28 with the phone at .1. */
    @Suppress("DEPRECATION")
    private fun joinedPhoneHotspot(wifi: WifiManager): Boolean = runCatching {
        wifi.connectionInfo?.supplicantState == SupplicantState.COMPLETED &&
            wifi.dhcpInfo?.gateway == IOS_HOTSPOT_GATEWAY
    }.getOrDefault(false)

    @Volatile private var avcDecoderFound: Boolean? = null

    private fun decoder(context: Context): Item = runCatching {
        val found = avcDecoderFound ?: MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .any { info -> !info.isEncoder && info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) } }
            .also { avcDecoderFound = it }
        if (found) Item(Level.OK, context.getString(R.string.check_decoder_ok))
        else Item(Level.BLOCKED, context.getString(R.string.check_decoder_missing))
    }.getOrElse { Item(Level.INFO, context.getString(R.string.check_decoder_unknown)) }

    /** Untranslated facts for the diagnostic report. Each probe may throw on vendor firmware. */
    fun reportLines(context: Context): List<String> {
        val lines = mutableListOf<String>()
        fun probe(label: String, read: () -> Any?) {
            lines += "$label: " + runCatching { read()?.toString() ?: "unavailable" }
                .getOrElse { "failed (${it.javaClass.simpleName})" }
        }
        probe("ABIs") { Build.SUPPORTED_ABIS.joinToString() }
        probe("Processor") {
            File("/proc/cpuinfo").readLines()
                .filter { it.startsWith("Hardware") || it.startsWith("model name") || it.startsWith("Processor") }
                .map { it.substringAfter(':').trim() }.distinct().joinToString("; ")
                .ifEmpty { "not reported" } + "; cores=${Runtime.getRuntime().availableProcessors()}"
        }
        probe("Memory") {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            "${info.totalMem / 1_048_576} MB total, ${info.availMem / 1_048_576} MB free"
        }
        probe("Display") {
            val metrics = context.resources.displayMetrics
            "${metrics.widthPixels}x${metrics.heightPixels} density=${metrics.densityDpi}"
        }
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        probe("Wi-Fi") {
            wifi ?: return@probe "no WifiManager"
            "enabled=${wifi.isWifiEnabled} fiveGhz=${wifi.is5GHzBandSupported} p2p=${wifi.isP2pSupported} " +
                "joinedPhoneHotspot=${joinedPhoneHotspot(wifi)}"
        }
        probe("Car hotspot") {
            "enabled=${CarHotspotStatus.isEnabled(context)} mode=${AirPlayPersistence.loadWirelessHotspotMode(context)} " +
                "configReadable=${CarHotspotCredentials.read(context) != null}"
        }
        probe("Bluetooth") {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: return@probe "no Android Bluetooth adapter"
            val bonded = adapter.bondedDevices.orEmpty()
            "enabled=${adapter.isEnabled} state=${adapter.state} bonded=${bonded.size} " +
                "bondedIPhones=${bonded.count { it.name?.contains("iPhone", true) == true }} " +
                "phoneSelected=${DiPlayPreferences.phoneAddress(context) != null}"
        }
        bluetoothHardwareLines(context, ::probe)
        probe("Interfaces") {
            Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback }
                .joinToString { iface ->
                    val families = Collections.list(iface.inetAddresses)
                        .map { if (it is java.net.Inet6Address) "v6" else "v4" }.distinct().sorted().joinToString("+")
                    "${iface.name}($families)"
                }
        }
        probe("File picker") {
            Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE)
                .resolveActivity(context.packageManager)?.packageName ?: "none"
        }
        probe("Video decoders") {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
                .filter { info -> info.supportedTypes.any { it == "video/avc" || it == "video/hevc" } }
                .joinToString { info -> "${info.name}[${info.supportedTypes.joinToString("+") { it.substringAfter('/') }}]" }
        }
        return lines
    }

    /**
     * What stands behind the Android Bluetooth API. Many aftermarket units keep calls and music on
     * a vendor module with its own app, and leave Android an adapter that pairs and "connects"
     * without reaching the phone; only the firmware's own files and services tell the two apart.
     */
    private fun bluetoothHardwareLines(context: Context, probe: (String, () -> Any?) -> Unit) {
        val adapter = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
        probe("Bluetooth adapter") {
            adapter ?: return@probe "none"
            // The first three bytes name the chip maker; the rest would identify the unit.
            val maker = com.shilapi.xcertplay.transport.BluetoothLocalAddress.fromService(adapter)?.take(8)
            "state=${adapter.state} scanMode=${adapter.scanMode} discovering=${adapter.isDiscovering} " +
                "nameLength=${adapter.name?.length} addressPrefix=${maker ?: "hidden"}"
        }
        probe("Bluetooth devices") {
            adapter ?: return@probe "none"
            val selected = DiPlayPreferences.phoneAddress(context)
            val iap2 = java.util.UUID.fromString("00000000-deca-fade-deca-deafdecacafe")
            adapter.bondedDevices.orEmpty().joinToString("; ") { device ->
                "bond=${device.bondState} type=${device.type} class=${device.bluetoothClass?.majorDeviceClass} " +
                    "services=${device.uuids?.size ?: "none"} iap2=${device.uuids?.any { it.uuid == iap2 } ?: "unknown"} " +
                    "iPhone=${device.name?.contains("iPhone", true)} selected=${device.address.equals(selected, true)}"
            }.ifEmpty { "none bonded" }
        }
        probe("Bluetooth service") {
            val packages = context.packageManager
            val hosts = packages.queryIntentServices(Intent("android.bluetooth.IBluetooth"), 0)
                .joinToString { "${it.serviceInfo.packageName}/${it.serviceInfo.name.substringAfterLast('.')}" }
            val stock = runCatching { packages.getPackageInfo("com.android.bluetooth", 0) }.getOrNull()
            "hosts=[$hosts] stockApp=${stock?.versionName ?: "absent"} " +
                "stockLibs=${stock?.applicationInfo?.nativeLibraryDir?.let { File(it).list()?.joinToString() } ?: "none"}"
        }
        probe("Bluetooth kernel") {
            val rfkill = File("/sys/class/rfkill").listFiles().orEmpty().joinToString { node ->
                "${runCatching { File(node, "name").readText().trim() }.getOrDefault("?")}:" +
                    runCatching { File(node, "type").readText().trim() }.getOrDefault("?")
            }
            "hci=[${File("/sys/class/bluetooth").list()?.joinToString() ?: "unreadable"}] rfkill=[$rfkill] " +
                "wifiDriver=${runCatching { File("/sys/class/net/wlan0/device/driver").canonicalFile.name }.getOrDefault("?")}"
        }
        probe("Bluetooth files") {
            val radio = Regex("(?i)bt|blue|rtl|8723|8189|8821|bcm|ap6|xr8|nvram|hcd")
            listOf(
                "/system/lib/hw", "/vendor/lib/hw", "/system/lib", "/vendor/lib", "/system/etc/bluetooth",
                "/system/etc/firmware", "/vendor/etc/firmware", "/vendor/firmware", "/system/vendor/modules", "/vendor/modules",
            ).mapNotNull { directory ->
                File(directory).list()?.filter { radio.containsMatchIn(it) && !it.startsWith("libc") }
                    ?.takeIf { it.isNotEmpty() }?.let { "$directory: ${it.take(14).joinToString(",")}" }
            }.joinToString(" | ").take(640).ifEmpty { "nothing readable" }
        }
        probe("Radio properties") {
            val wanted = Regex("(?i)bluetooth|\\.bt|bt\\.|wifi|wlan|module|ro\\.board|ro\\.hardware|ro\\.product\\.(model|device|name)")
            Runtime.getRuntime().exec("getprop").inputStream.bufferedReader().useLines { lines ->
                lines.filter { wanted.containsMatchIn(it.substringBefore("]:")) }.take(40)
                    .joinToString(" ") { it.replace(" ", "") }.take(640)
            }
        }
        probe("Kernel modules") {
            File("/proc/modules").readLines().take(40).joinToString(",") { it.substringBefore(' ') }.ifEmpty { "none listed" }
        }
        // Vendor phone, Bluetooth and phone-mirroring apps show which stack the unit really uses.
        val foreign = runCatching {
            context.packageManager.getInstalledPackages(0).map { it.packageName }
                .filterNot { it == "android" || it.startsWith("com.android.") || it.startsWith("com.google.") }.sorted()
        }.getOrDefault(emptyList())
        foreign.chunked(18).forEachIndexed { index, chunk ->
            probe("Installed apps ${index + 1}") { chunk.joinToString(" ") }
        }
    }

    // 172.20.10.1 as DhcpInfo's little-endian int.
    private const val IOS_HOTSPOT_GATEWAY = 0x010A14AC
}
