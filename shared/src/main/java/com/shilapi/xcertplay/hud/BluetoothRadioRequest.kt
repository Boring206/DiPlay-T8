package com.shilapi.xcertplay.hud

/**
 * Chooses how a Bluetooth radio on/off request is issued.
 *
 * The app-level BluetoothAdapter control (BLUETOOTH_ADMIN) works without ADB, so it is tried
 * first; the ADB shell (`svc bluetooth ...`) stays as the fallback for adapters the app API
 * cannot drive. Returns true when the adapter issued the request. Whether the radio actually
 * changed state is verified separately by awaiting the adapter state.
 */
internal object BluetoothRadioRequest {
    fun request(
        enabled: Boolean,
        adapterRequest: (Boolean) -> Boolean,
        shellRequest: (Boolean) -> Unit,
    ): Boolean {
        val issued = runCatching { adapterRequest(enabled) }.getOrDefault(false)
        if (issued) return true
        runCatching { shellRequest(enabled) }
        return false
    }
}
