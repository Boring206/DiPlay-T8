package com.shilapi.xcertplay

import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class WiredTeardownGuardTest {
    @Test fun onlyStillRunningControllersBlockWiredStart() {
        val first = mock(CarPlayController::class.java)
        val second = mock(CarPlayController::class.java)
        `when`(first.diagnosticAttempt).thenReturn(10)
        `when`(second.diagnosticAttempt).thenReturn(20)
        WiredTeardownGuard.hold(first)
        WiredTeardownGuard.hold(second)
        try {
            assertEquals(listOf(10, 20), WiredTeardownGuard.pendingControllerAttempts())
            `when`(first.awaitClosed(0)).thenReturn(true)
            assertEquals(listOf(20), WiredTeardownGuard.pendingControllerAttempts())
        } finally {
            `when`(first.awaitClosed(0)).thenReturn(true)
            `when`(second.awaitClosed(0)).thenReturn(true)
            assertEquals(emptyList<Int>(), WiredTeardownGuard.pendingControllerAttempts())
        }
    }
}
