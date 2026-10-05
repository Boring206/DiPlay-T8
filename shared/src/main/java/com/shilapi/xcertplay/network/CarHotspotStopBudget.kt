package com.shilapi.xcertplay.network

/**
 * Limits how often the car hotspot is turned off for an app-owned one. A hotspot that is back on
 * for attempt after attempt is being restored by the head unit, and switching it off again only
 * makes the connection drop in a loop.
 */
class CarHotspotStopBudget(private val limit: Int = 3, private val windowMillis: Long = 120_000L) {
    private val recent = mutableListOf<Long>()

    @Synchronized fun take(nowMillis: Long): Boolean {
        recent.removeAll { nowMillis - it >= windowMillis }
        if (recent.size >= limit) return false
        recent += nowMillis
        return true
    }

    companion object {
        /** One budget for the process: attempts and their controllers come and go. */
        val shared = CarHotspotStopBudget()
    }
}
