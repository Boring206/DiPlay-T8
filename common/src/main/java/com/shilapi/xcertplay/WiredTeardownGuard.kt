package com.shilapi.xcertplay

import com.shilapi.xcertplay.orchestration.CarPlayController

/** Retains retiring wired controllers across Activities until teardown and their workers finish. */
internal object WiredTeardownGuard {
    private val pending = mutableSetOf<CarPlayController>()

    @Synchronized fun hold(controller: CarPlayController) {
        pending.add(controller)
    }

    @Synchronized fun pendingControllerAttempts(): List<Int> {
        pending.removeAll { it.awaitClosed(0) }
        return pending.map { it.diagnosticAttempt }
    }
}
