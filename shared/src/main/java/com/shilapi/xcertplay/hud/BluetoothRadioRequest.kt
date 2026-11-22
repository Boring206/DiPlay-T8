package com.shilapi.xcertplay.hud

import android.content.res.Resources
import android.os.Build

/**
 * Chooses how a Bluetooth radio on/off request is issued.
 *
 * The ADB shell (`svc bluetooth ...`) runs first: every unit where the pause works
 * today uses it, and the adapter path was only tested on one Android 8.1 unit.
 * The app-level BluetoothAdapter control (BLUETOOTH_ADMIN) is only the fallback,
 * tried when the shell gave no reply (no ADB link, or still inside its 30 s retry
 * wait) — and only where it can work at all. Returns true when the adapter issued
 * the request. Whether the radio actually changed state is verified separately by
 * awaiting the adapter state.
 */
internal object BluetoothRadioRequest {
    fun request(
        enabled: Boolean,
        adapterRequest: (Boolean) -> Boolean,
        shellRequest: (Boolean) -> String?,
    ): Boolean {
        val shellReply = runCatching { shellRequest(enabled) }.getOrNull()
        if (shellReply != null) return false
        if (!adapterAllowed()) return false
        return runCatching { adapterRequest(enabled) }.getOrDefault(false)
    }

    /**
     * Whether the app-level adapter request may be tried: never on Android 13+
     * (with targetSdk 37 it always returns false there), and never on ROMs that
     * set `config_wirelessConsentRequired`, where it pops a system dialog instead
     * of changing the radio. The resource lookup is guarded so plain JVM unit
     * tests, where Android framework calls are stubs, still run.
     */
    internal fun adapterAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33) return false
        val id = runCatching {
            Resources.getSystem().getIdentifier("config_wirelessConsentRequired", "bool", "android")
        }.getOrDefault(0)
        if (id == 0) return true
        return runCatching { !Resources.getSystem().getBoolean(id) }.getOrDefault(true)
    }
}
