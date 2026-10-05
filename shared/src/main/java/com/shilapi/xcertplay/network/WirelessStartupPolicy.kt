package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.io.IOException

object WirelessStartupPolicy {
    const val HOTSPOT_READY_MILLIS = 15_000L
    const val INTERFACE_POLL_MILLIS = 250L
    const val STABLE_SAMPLES = 3
    const val FIRST_TCP_MILLIS = 30_000L
    const val MAX_STARTUP_RETRIES = 5
    const val STABLE_SESSION_MILLIS = 60_000L
}

/**
 * The hotspot an attempt should use. An app-owned hotspot cannot start beside the car's own one;
 * with that hotspot's name and password saved it serves just as well, otherwise only the user can
 * choose, and null says so.
 */
fun resolveHotspotMode(
    requested: WirelessHotspotMode,
    carHotspotOn: Boolean,
    carHotspotSaved: Boolean,
): WirelessHotspotMode? = when {
    requested != WirelessHotspotMode.LOCAL_ONLY_HOTSPOT || !carHotspotOn -> requested
    carHotspotSaved -> WirelessHotspotMode.MANUAL
    else -> null
}

enum class WirelessStartupFailure { HOTSPOT_NOT_READY, FIRST_TCP_TIMEOUT, HOTSPOT_CONFIGURATION }

class WirelessStartupException(val reason: WirelessStartupFailure, message: String) : IOException(message)
