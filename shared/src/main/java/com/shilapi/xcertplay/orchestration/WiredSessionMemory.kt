package com.shilapi.xcertplay.orchestration

/**
 * The USB enumeration (device node) of the iPhone on which DiPlay last started a wired CarPlay session.
 *
 * After DiPlay ends that session itself, as Save and reconnect does, the iPhone stays in CarPlay mode on the
 * same node. A new session there passes iAP2 and StartSession, but the iPhone opens no AirPlay connection
 * until it resets USB 30–150 s later; a fresh CarPlay mode request instead connected within seconds (#335).
 * The node changes whenever the iPhone re-enumerates, so a match means it is still in that session's mode.
 *
 * Some iPhones never answer that request on the node: each retry waited the full re-enumeration limit and then
 * used the mode it already offered (#1338: 22 of 22 requests, on three plug-ins). The node of such an unanswered
 * request is kept too, so later retries on it use that mode at once.
 */
internal object WiredSessionMemory {
    @Volatile private var startedOn: String? = null
    @Volatile private var transitionUnansweredOn: String? = null

    fun started(deviceName: String) {
        startedOn = deviceName
    }

    fun startedOn(deviceName: String): Boolean = startedOn == deviceName

    /** The iPhone was still listed on [deviceName] when the wait for its re-enumeration ended. */
    fun transitionUnanswered(deviceName: String) {
        transitionUnansweredOn = deviceName
    }

    fun transitionUnansweredOn(deviceName: String): Boolean = transitionUnansweredOn == deviceName

    internal fun clear() {
        startedOn = null
        transitionUnansweredOn = null
    }
}
