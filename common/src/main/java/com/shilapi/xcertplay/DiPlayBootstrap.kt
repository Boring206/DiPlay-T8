package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.orchestration.MfiTarget
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Installs the experimental identity from this build's assets, or copies it from an installed
 * DiPlay that carries one. It has no remote fallback.
 */
internal object DiPlayBootstrap {
    @Volatile private var ready = false
    private val IDENTITY_FILES = listOf("identity.pk8", "certificate.p7b")
    // A build published without the identity takes it from one of these on the same device.
    private val DONOR_PACKAGES = listOf("com.shihab.diplay", "com.shihab.diplay.hudtest")

    @Synchronized fun ensure(context: Context, mfiTarget: MfiTarget) {
        if (mfiTarget != MfiTarget.LOCAL) return
        if (ready) return
        val target = File(context.noBackupFilesDir, LocalMfiAuthenticationClient.DIRECTORY)
        if (!target.exists()) {
            val staging = File(context.noBackupFilesDir, "offline-mfi-staging")
            staging.deleteRecursively()
            check(staging.mkdirs()) { "Could not prepare local authentication" }
            staging.setReadable(false, false); staging.setReadable(true, true)
            staging.setExecutable(false, false); staging.setExecutable(true, true)
            try {
                for ((name, bytes) in identityFiles(context)) {
                    val file = File(staging, name)
                    file.writeBytes(bytes)
                    file.setReadable(false, false); file.setReadable(true, true)
                    file.setWritable(false, false); file.setWritable(true, true)
                }
                LocalMfiAuthenticationClient.load(staging)
                check(staging.renameTo(target)) { "Could not install local authentication" }
            } finally {
                staging.deleteRecursively()
            }
        }
        LocalMfiAuthenticationClient.load(target)
        AirPlayPersistence.saveDebugLogsEnabled(context, false)
        ready = true
    }

    /** Both files always come from one source: a key from one build never matches another's certificate. */
    private fun identityFiles(context: Context): Map<String, ByteArray> {
        runCatching {
            IDENTITY_FILES.associateWith { name -> context.assets.open("offline-mfi/$name").use { it.readBytes() } }
        }.getOrNull()?.let { return it }
        for (donor in DONOR_PACKAGES) {
            if (donor == context.packageName) continue
            runCatching {
                ZipFile(context.packageManager.getApplicationInfo(donor, 0).sourceDir).use { apk ->
                    IDENTITY_FILES.associateWith { name ->
                        apk.getInputStream(checkNotNull(apk.getEntry("assets/offline-mfi/$name"))).use { it.readBytes() }
                    }
                }
            }.getOrNull()?.let { return it }
        }
        error("No CarPlay identity in this build or in an installed DiPlay")
    }

    fun deviceId(identity: AirPlayIdentity): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(identity.publicKey).take(6).toByteArray()
        bytes[0] = ((bytes[0].toInt() and 0xfc) or 0x02).toByte()
        return bytes.joinToString(":") { "%02X".format(it.toInt() and 0xff) }
    }
}

internal object DiPlayPreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)
    fun phoneAddress(context: Context): String? = prefs(context).getString("phone_address", null)
    fun autoConnectOnBluetooth(context: Context) = prefs(context).getBoolean("auto_connect_on_bluetooth", false)
    fun saveAutoConnectOnBluetooth(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("auto_connect_on_bluetooth", value).apply()
    }
    fun carPlayWorked(context: Context) = prefs(context).getBoolean("carplay_worked", false)
    fun markCarPlayWorked(context: Context) {
        if (!carPlayWorked(context)) prefs(context).edit().putBoolean("carplay_worked", true).apply()
    }
    // Builds before t8.7 kept no such mark; the address family order is only ever saved by a
    // wireless session that showed a picture, so its presence says the same.
    fun wirelessWorked(context: Context) = prefs(context).run {
        getBoolean("wireless_worked", false) || contains("hotspot_prefers_ipv4")
    }
    fun markWirelessWorked(context: Context) {
        if (!wirelessWorked(context)) prefs(context).edit().putBoolean("wireless_worked", true).apply()
    }
    /**
     * True after a connection attempt found that this unit's Android Bluetooth "connects" to
     * services no phone offers. Kept so the home screen says so before anyone tries again; cleared
     * as soon as a phone really answers over Bluetooth.
     */
    fun bluetoothUnreal(context: Context) = prefs(context).getBoolean("bluetooth_unreal", false)
    fun saveBluetoothUnreal(context: Context, value: Boolean) {
        if (bluetoothUnreal(context) != value) prefs(context).edit().putBoolean("bluetooth_unreal", value).apply()
    }
    /**
     * The address family order that last led to a working wireless session. Until one has, Android
     * 8 and 9 start with IPv4: they never route a tethered hotspot's link-local prefix, and route
     * an app-owned hotspot's only the first time after boot or after tethering, so IPv4 is the one
     * family that is there on every attempt.
     */
    fun hotspotPrefersIpv4(context: Context) = prefs(context).getBoolean(
        "hotspot_prefers_ipv4", android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q)
    fun saveHotspotPrefersIpv4(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("hotspot_prefers_ipv4", value).apply()
    }
    fun handsFreeOffered(context: Context) = prefs(context).getBoolean("hands_free_offered", false)
    fun markHandsFreeOffered(context: Context) {
        prefs(context).edit().putBoolean("hands_free_offered", true).apply()
    }
    fun phoneName(context: Context): String = prefs(context).getString("phone_name", null) ?: "Your iPhone"
    fun savePhone(context: Context, address: String, name: String) {
        prefs(context).edit().putString("phone_address", address).putString("phone_name", name).apply()
    }
    fun autoConnect(context: Context) = prefs(context).getBoolean("auto_connect", false)
    fun saveAutoConnect(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("auto_connect", value).apply()
    }
}
