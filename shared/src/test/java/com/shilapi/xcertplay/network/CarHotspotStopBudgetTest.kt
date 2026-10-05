package com.shilapi.xcertplay.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarHotspotStopBudgetTest {
    @Test fun hotspotThatKeepsComingBackIsLeftAloneAfterAFewRounds() {
        val budget = CarHotspotStopBudget(limit = 3, windowMillis = 120_000L)
        assertTrue(budget.take(0L))
        assertTrue(budget.take(20_000L))
        assertTrue(budget.take(40_000L))
        assertFalse(budget.take(60_000L))
        // A refusal is not a stop, so it does not extend the wait.
        assertFalse(budget.take(119_999L))
    }

    @Test fun defaultsRunOutAtTheRetryPacesSeenOnAHeadUnit() {
        // Stops observed while a hotspot kept returning: on screen, then with DiPlay in the background.
        for (pace in listOf(listOf(0L, 33_000L, 68_000L, 137_000L), listOf(0L, 176_000L, 312_000L, 478_000L))) {
            val budget = CarHotspotStopBudget()
            assertTrue(pace.take(3).all(budget::take))
            assertFalse(budget.take(pace[3]))
        }
    }

    @Test fun sessionThatStayedUpClearsTheCount() {
        // Several short trips in a row each stop the hotspot once and each get a working session.
        val budget = CarHotspotStopBudget()
        repeat(10) { trip ->
            assertTrue(budget.take(trip * 300_000L))
            budget.reset()
        }
    }

    @Test fun oneStopPerDriveNeverRunsOut() {
        val budget = CarHotspotStopBudget()
        repeat(20) { drive -> assertTrue(budget.take(drive * 1_800_000L)) }
    }

    @Test fun stopsOlderThanTheWindowAreForgotten() {
        val budget = CarHotspotStopBudget(limit = 3, windowMillis = 120_000L)
        repeat(3) { assertTrue(budget.take(it * 1_000L)) }
        assertFalse(budget.take(100_000L))
        assertTrue(budget.take(120_000L))
    }
}
