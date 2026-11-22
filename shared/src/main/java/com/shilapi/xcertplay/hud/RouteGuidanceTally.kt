package com.shilapi.xcertplay.hud

/**
 * Counts the route guidance the iPhone sends during one CarPlay session (#1196), for one summary line
 * when the session ends: RouteGuidanceUpdate (0x5201) and RouteGuidanceManeuverUpdate (0x5202) frames,
 * and the navigation apps named in the updates' source field, which [FceNavigationState] parses.
 */
internal class RouteGuidanceTally {
    private val lock = Any()
    private val navigation = FceNavigationState()
    private val sources = LinkedHashSet<String>()
    private var updates = 0
    private var maneuvers = 0

    fun accept(messageId: Int, payload: ByteArray) = synchronized(lock) {
        when (messageId) {
            BydHudRouteState.ROUTE_GUIDANCE_UPDATE -> {
                updates++
                navigation.accept(messageId, payload)
                navigation.source?.let(::sourceLabel)?.let { if (sources.size < MAX_SOURCES) sources += it }
            }
            BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE -> maneuvers++
        }
    }

    /** The line for the session that ended; the next session counts from zero. */
    fun finish(navigationOutput: Boolean, receivers: List<String>): String = synchronized(lock) {
        val line = NavigationOutputLines.routeGuidanceSummary(updates, maneuvers, sources, navigationOutput, receivers)
        updates = 0
        maneuvers = 0
        sources.clear()
        navigation.clear()
        line
    }

    companion object {
        private const val MAX_SOURCES = 4
        private const val MAX_SOURCE_LENGTH = 32

        /** An app's display name, such as "Google Maps", kept to plain characters so it cannot break the line. */
        fun sourceLabel(raw: String): String? = raw
            .filter { it.isLetterOrDigit() || it in " .&+-_'" }
            .trim()
            .take(MAX_SOURCE_LENGTH)
            .takeIf { it.isNotEmpty() }
    }
}
