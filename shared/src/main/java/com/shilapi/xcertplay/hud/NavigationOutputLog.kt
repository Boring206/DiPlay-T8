package com.shilapi.xcertplay.hud

import android.util.Log

/**
 * Report lines for the BYD navigation outputs (#1196): the SOME/IP windshield HUD, the AMap cluster
 * adapter and the standalone HUD. They used to log to logcat only, so a saved report could not tell
 * whether the gateway bound or accepted guidance.
 *
 * Each output keeps its state under a key, and a line is written only when that state changes. A key
 * writes at most [MAX_LINES_PER_KEY] lines per attached session, so an output that flaps cannot flood
 * the log; logcat keeps every change. The outputs start with the app, before a session log exists, so
 * [attach] first writes the current state of each key.
 */
object NavigationOutputLog {
    const val PREFIX = "Navigation output: "
    const val CURRENT_PREFIX = "Navigation output (current): "
    const val MAX_LINES_PER_KEY = 12

    private val lock = Any()
    private val current = LinkedHashMap<String, String>()
    private val written = HashMap<String, Int>()
    private var owner: Any? = null
    private var sink: ((String) -> Unit)? = null

    /** [sink] receives the lines until [detach] by the same [owner] or a newer [attach]. */
    fun attach(owner: Any, sink: (String) -> Unit) {
        val state = synchronized(lock) {
            this.owner = owner
            this.sink = sink
            written.clear()
            current.values.toList()
        }
        state.forEach { deliver(sink, CURRENT_PREFIX + it) }
    }

    fun detach(owner: Any) = synchronized(lock) {
        if (this.owner !== owner) return@synchronized
        this.owner = null
        sink = null
    }

    /** Records the state of [key]; writes [message] when it differs from the last one for that key. */
    internal fun note(tag: String, key: String, message: String, warn: Boolean = false, logcat: Boolean = true) {
        val delivery = synchronized(lock) {
            if (current[key] == message) return
            current[key] = message
            val target = sink ?: return@synchronized null
            val count = (written[key] ?: 0) + 1
            written[key] = count
            when {
                count <= MAX_LINES_PER_KEY -> target to PREFIX + message
                count == MAX_LINES_PER_KEY + 1 -> target to PREFIX + NavigationOutputLines.capped(key)
                else -> null
            }
        }
        if (logcat) runCatching { if (warn) Log.w(tag, message) else Log.i(tag, message) }
        delivery?.let { (target, line) -> deliver(target, line) }
    }

    private fun deliver(target: (String) -> Unit, line: String) {
        // Optional evidence must never break an output.
        runCatching { target(line) }
    }

    /** Tests start from an empty state. */
    internal fun resetForTest() = synchronized(lock) {
        current.clear()
        written.clear()
        owner = null
        sink = null
    }
}

/**
 * The text of each navigation output line. Package names, result codes and exception types only:
 * never a road name, the song text or a device identity.
 */
object NavigationOutputLines {
    const val SOMEIP_PACKAGE = "com.ts.car.someip.service"

    fun hudBindRequested() = "HUD SOME/IP bind requested ($SOMEIP_PACKAGE)"
    fun hudBindRefused(maxRetrySeconds: Long) =
        "HUD SOME/IP bind refused; retrying with backoff up to ${maxRetrySeconds}s"
    fun hudBindFailed(error: Throwable) = "HUD SOME/IP bind failed ${error.javaClass.simpleName}; retrying with backoff"
    fun hudConnected(callback: Int, startResult: Int, started: Boolean) =
        "HUD SOME/IP connected callback=$callback startResult=$startResult started=$started"
    fun hudGatewayLost(how: String) = "HUD SOME/IP gateway $how"
    fun hudHalReady(ready: Boolean) = "HUD SOME/IP HAL ready=$ready"
    fun hudTransactionFailed(code: Int, error: Throwable) =
        "HUD SOME/IP transaction $code failed ${error.javaClass.simpleName}"
    fun hudTransactionsAnswer() = "HUD SOME/IP transactions answer again"
    fun hudFireEvent(result: Int) =
        "HUD fireEvent result=$result${if (BydHudProtocol.eventAccepted(result)) "" else " (not accepted)"}"
    /**
     * [arrival] is what the N9 HUD's field 26 carried (#301): null leaves it out (every other car sends the
     * field empty), and an empty value shows as "none".
     */
    fun hudGuidanceAccepted(arrival: String? = null) = "HUD gateway accepted guidance; rendering unconfirmed" +
        (arrival?.let { "; arrival field26=${it.ifEmpty { "none" }}" } ?: "")
    fun hudGuidanceCleared() = "HUD guidance cleared"

    fun clusterAdapter(packageName: String, simpleNavigationMode: Boolean) =
        "cluster AMap adapter $packageName simpleNavigationMode=$simpleNavigationMode"
    fun clusterFactoryTest() = "cluster factory test output"
    fun clusterGuidanceSent(target: String) = "cluster guidance dispatched to $target"
    fun clusterGuidanceEnded() = "cluster guidance ended"
    fun clusterBroadcastFailed(error: Throwable) = "cluster broadcast failed ${error.javaClass.simpleName}"
    fun clusterBroadcastRecovered() = "cluster broadcast dispatched again"

    fun standaloneReady(receiverPackage: String) = "standalone HUD receiver $receiverPackage ready"
    fun standaloneShows(what: String) = "standalone HUD shows $what"
    fun standaloneCleared() = "standalone HUD cleared"
    fun standaloneFailed(error: Throwable) = "standalone HUD update failed ${error.javaClass.simpleName}; retrying"
    fun standaloneRecovered() = "standalone HUD update works again"

    fun capped(key: String) = "$key changes again; further changes this session are in logcat only"

    /**
     * The route guidance the iPhone sent during one CarPlay session: RouteGuidanceUpdate (0x5201) and
     * RouteGuidanceManeuverUpdate (0x5202) counts, and the navigation apps it named. No frames at all
     * means the iPhone sent no guidance, which no output can fix.
     */
    fun routeGuidanceSummary(
        updates: Int,
        maneuvers: Int,
        sources: Collection<String>,
        navigationOutput: Boolean,
        receivers: List<String>,
    ): String = "Route guidance this session: 0x5201=$updates 0x5202=$maneuvers " +
        "source=${if (sources.isEmpty()) "none" else sources.joinToString(",") { "\"$it\"" }} " +
        "navigation_enabled=$navigationOutput receivers=${receivers.ifEmpty { listOf("none") }.joinToString(",")}"
}
