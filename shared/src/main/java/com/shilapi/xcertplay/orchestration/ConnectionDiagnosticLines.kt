package com.shilapi.xcertplay.orchestration

import android.bluetooth.BluetoothDevice

/**
 * Connection report lines that [CarPlayController] writes, built here so that DiagnosticRedactorTest
 * checks the exact text a saved report receives. They carry counts, states and Android's numeric USB
 * device id only: never a device name, a Bluetooth address or a URL.
 *
 * No field may end in `name`: the redactor drops a whole line with `name=` or `name:` in it, which is
 * how the Bluetooth snapshot went missing from reports (#1151).
 */
object ConnectionDiagnosticLines {
    /** The cached Bluetooth state of the iPhone DiPlay connects to; read-only, no discovery. */
    fun bluetoothSnapshot(
        point: String,
        adapterEnabled: Boolean?,
        bondState: Int,
        cachedServicesReadable: Boolean,
        cachedServiceCount: Int?,
        cachedIap2Service: Boolean?,
    ): String =
        "Bluetooth snapshot point=$point enabled=$adapterEnabled " +
            "bondState=$bondState bond=${bond(bondState)} cachedServicesReadable=$cachedServicesReadable " +
            "cachedServiceCount=${cachedServiceCount ?: "unknown"} " +
            "cachedIap2Service=${cachedIap2Service ?: "unknown"}"

    /** The iPhone DiPlay chose in Settings: whether it is still paired. */
    fun savedBluetoothIphone(bonded: Int, stillPaired: Boolean): String =
        "wireless Bluetooth iPhone choice=saved bonded=$bonded savedStillPaired=$stillPaired"

    /**
     * How many paired iPhones DiPlay saw and how many were connected when it picked one. [connected]
     * counts the ones a Bluetooth profile reports when none answered directly.
     */
    fun automaticBluetoothIphone(bonded: Int, bondedIphones: Int, directlyConnected: Int, connected: Int): String =
        "wireless Bluetooth iPhone choice=automatic bonded=$bonded bondedIPhones=$bondedIphones " +
            "directlyConnected=$directlyConnected connected=$connected"

    /**
     * A USB attach or detach of an iPhone. Android gives each enumeration a new [deviceId], so a cable
     * that keeps dropping shows as a run of attaches with rising ids (#1216).
     */
    fun usbIphone(attached: Boolean, deviceId: Int, attaches: Int, detaches: Int, permissionRequested: Boolean? = null): String =
        "USB iPhone ${if (attached) "attached" else "detached"} deviceId=$deviceId " +
            "attaches=$attaches detaches=$detaches" +
            (permissionRequested?.let { " permissionRequested=$it" } ?: "")

    fun bond(bondState: Int): String = when (bondState) {
        BluetoothDevice.BOND_NONE -> "NONE"
        BluetoothDevice.BOND_BONDING -> "BONDING"
        BluetoothDevice.BOND_BONDED -> "BONDED"
        else -> "UNKNOWN"
    }
}
