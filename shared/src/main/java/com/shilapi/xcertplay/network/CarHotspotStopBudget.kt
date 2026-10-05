package com.shilapi.xcertplay.network

/**
 * Limits how often the car hotspot is turned off for an app-owned one. A hotspot that is back on
 * for attempt after attempt is being restored by the head unit, and switching it off again only
 * makes the connection drop in a loop.
 *
 * Retries are minutes apart once they back off, so the window has to be long; a session that
 * stays up clears the count instead, since it shows the last stop held.
 */
class CarHotspotStopBudget(private val limit: Int = 3, private val windowMillis: Long = 1_800_000L) {
    private val recent = mutableListOf<Long>()

    @Synchronized fun take(nowMillis: Long): Boolean {
        recent.removeAll { nowMillis - it >= windowMillis }
        if (recent.size >= limit) return false
        recent += nowMillis
        return true
    }

    @Synchronized fun reset() = recent.clear()

    companion object {
        /** One budget for the process: attempts and their controllers come and go. */
        val shared = CarHotspotStopBudget()
    }
}
