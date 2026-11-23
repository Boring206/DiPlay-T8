package com.shilapi.xcertplay.hud

/**
 * Chooses how a Bluetooth radio on/off request is issued.
 *
 * The ADB shell (`svc bluetooth ...`) runs first: every unit where the pause works
 * today uses it, and the adapter path was only tested on one Android 8.1 unit.
 * The app-level BluetoothAdapter control (BLUETOOTH_ADMIN) is only the fallback,
 * tried when the shell gave no reply (no ADB link, or still inside its 30 s retry
 * wait) — and only where it can work at all. Returns the path the request took,
 * so the pause outcome can name it. Whether the radio actually changed state is
 * verified separately by awaiting the adapter state.
 */
internal object BluetoothRadioRequest {
    /** How a radio request was issued, for the pause outcome and evidence lines. */
    enum class Path { SHELL, ADAPTER_SENT, ADAPTER_REFUSED, ADAPTER_NOT_ALLOWED }

    fun request(
        enabled: Boolean,
        adapterAllowed: Boolean,
        adapterRequest: (Boolean) -> Boolean,
        shellRequest: (Boolean) -> String?,
    ): Path {
        val shellReply = runCatching { shellRequest(enabled) }.getOrNull()
        if (shellReply != null) return Path.SHELL
        if (!adapterAllowed) return Path.ADAPTER_NOT_ALLOWED
        return if (runCatching { adapterRequest(enabled) }.getOrDefault(false)) Path.ADAPTER_SENT
        else Path.ADAPTER_REFUSED
    }

    /**
     * Whether the app-level adapter request may be tried: never on Android 13+
     * (with targetSdk 37 it always returns false there); never on ROMs that
     * require wireless consent (`config_permissionReviewRequired` on Android 8.x/9,
     * renamed `config_wirelessConsentRequired` in Android 10), where it pops a
     * system dialog instead of changing the radio; and never on Android 12/12L
     * without the BLUETOOTH_CONNECT (Nearby devices) permission.
     * Pure in its inputs so JVM tests can cover both cases; the caller reads the
     * SDK level, the consent flag and the permission.
     */
    fun adapterAllowed(sdkInt: Int, consentRequired: Boolean, bluetoothConnectGranted: Boolean): Boolean {
        if (sdkInt >= 33) return false
        if (consentRequired) return false
        if (sdkInt in 31..32 && !bluetoothConnectGranted) return false
        return true
    }
}
