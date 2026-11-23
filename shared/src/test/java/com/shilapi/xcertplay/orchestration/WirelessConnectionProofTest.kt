package com.shilapi.xcertplay.orchestration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessConnectionProofTest {

    @Test fun activeSessionEvidenceIsScopedToGenerationAndClearedOnEnd() {
        val session = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) {}
        proof.activate(1, session)
        assertTrue(proof.hasActiveSession(1))
        assertFalse(proof.hasActiveSession(2))
        proof.end(1, session)
        assertFalse(proof.hasActiveSession(1))
    }
    @Test fun authenticationWithoutVideoDoesNotConfirm() {
        var saves = 0
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) { saves++ }
        proof.activate(1, Any())
        proof.authenticated(1)
        assertEquals(0, saves)
    }

    @Test fun bothEventsAreRequiredInEitherOrderAndOnlySaveOnce() {
        for (videoFirst in listOf(true, false)) {
            var saves = 0
            val session = Any()
            val proof = WirelessConnectionProof<Any>()
            proof.begin(1) { saves++ }
            proof.activate(1, session)
            if (videoFirst) proof.rendered(1, session) else proof.authenticated(1)
            assertEquals(0, saves)
            if (videoFirst) proof.authenticated(1) else proof.rendered(1, session)
            proof.rendered(1, session)
            proof.authenticated(1)
            assertEquals(1, saves)
        }
    }

    @Test fun oldGenerationAndOldSessionCannotConfirmANewConnection() {
        var saves = 0
        val old = Any()
        val current = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) { saves++ }
        proof.activate(1, old)
        proof.authenticated(1)
        proof.begin(2) { saves++ }
        proof.activate(2, current)
        proof.rendered(1, old)
        proof.authenticated(1)
        proof.rendered(2, old)
        proof.authenticated(2)
        assertEquals(0, saves)
        proof.rendered(2, current)
        assertEquals(1, saves)
    }

    @Test fun endedOrClearedSessionCannotBeLearnedByLateCallbacks() {
        val session = Any()
        var saves = 0
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) { saves++ }
        proof.activate(1, session)
        proof.rendered(1, session)
        proof.end(1, session)
        proof.authenticated(1)
        proof.clear()
        proof.activate(1, session)
        proof.authenticated(1)
        proof.rendered(1, session)
        assertEquals(0, saves)
    }

    @Test fun replacementSessionNeedsItsOwnAuthenticationAndFrame() {
        val first = Any()
        val second = Any()
        var saves = 0
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) { saves++ }
        proof.activate(1, first)
        proof.authenticated(1)
        proof.activate(1, second)
        proof.rendered(1, second)
        assertEquals(0, saves)
        proof.authenticated(1)
        assertEquals(1, saves)
    }

    @Test fun audioProvesAHiddenSessionButNotOneBehindAScreen() {
        val behindScreen = Any()
        val hidden = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) {}
        proof.activate(1, behindScreen)
        assertFalse(proof.isUsable(1, behindScreen, audioReceived = true, screen = SHOWN))
        proof.activate(1, hidden)
        assertTrue(proof.isUsable(1, hidden, audioReceived = true, screen = HIDDEN))
    }

    @Test fun aSessionWithoutFrameOrAudioIsNeverUsable() {
        val session = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) {}
        proof.activate(1, session)
        proof.authenticated(1)
        assertFalse(proof.isUsable(1, session, audioReceived = false, screen = HIDDEN))
        assertFalse(proof.isUsable(1, session, audioReceived = false, screen = SHOWN))
    }

    @Test fun aRenderedFrameIsEnoughWithOrWithoutAScreen() {
        val session = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) {}
        proof.activate(1, session)
        proof.rendered(1, session)
        assertTrue(proof.isUsable(1, session, audioReceived = false, screen = SHOWN))
        assertTrue(proof.isUsable(1, session, audioReceived = false, screen = HIDDEN))
    }

    @Test fun anAcceptedSessionStaysAcceptedWhenItsScreenComesBack() {
        val session = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) {}
        proof.activate(1, session)
        assertTrue(proof.isUsable(1, session, audioReceived = true, screen = HIDDEN))
        assertTrue(proof.isUsable(1, session, audioReceived = true, screen = SHOWN))
    }

    @Test fun acceptanceBelongsToOneSessionAndGeneration() {
        val first = Any()
        val second = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) {}
        proof.activate(1, first)
        assertFalse(proof.isUsable(2, first, audioReceived = true, screen = HIDDEN))
        assertFalse(proof.isUsable(1, second, audioReceived = true, screen = HIDDEN))
        assertTrue(proof.isUsable(1, first, audioReceived = true, screen = HIDDEN))
        proof.activate(1, second)
        assertFalse(proof.isUsable(1, second, audioReceived = false, screen = SHOWN))
        proof.end(1, second)
        assertFalse(proof.isUsable(1, second, audioReceived = true, screen = HIDDEN))
        proof.begin(2) {}
        assertFalse(proof.isUsable(1, first, audioReceived = true, screen = HIDDEN))
    }

    @Test fun audioProvesASessionHiddenAllAlong() {
        val session = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) {}
        proof.activate(1, session)
        proof.observeScreen(1, session, HIDDEN)
        assertFalse(proof.isUsable(1, session, audioReceived = false, screen = HIDDEN))
        assertTrue(proof.isUsable(1, session, audioReceived = true, screen = HIDDEN))
        assertFalse(proof.screenShown(1))
    }

    @Test fun audioCannotProveASessionThatShowedABlackScreenBeforeBeingHidden() {
        // CarPlay open at the start, no frame, then the user went to another app before the check.
        val session = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) {}
        proof.activate(1, session)
        proof.observeScreen(1, session, SHOWN)
        assertFalse(proof.isUsable(1, session, audioReceived = true, screen = HIDDEN))
        assertTrue(proof.screenShown(1))
    }

    @Test fun aScreenAttachedAndLeftBetweenChecksStillCounts() {
        // Hidden at the start and at the check, but CarPlay was opened (and left) in between.
        val session = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) {}
        proof.activate(1, session)
        proof.observeScreen(1, session, HIDDEN)
        assertFalse(proof.isUsable(1, session, audioReceived = true, screen = MainScreenOutput(false, 1)))
    }

    @Test fun aRenderedFrameStillProvesASessionWhoseScreenWasLeft() {
        val session = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) {}
        proof.activate(1, session)
        proof.observeScreen(1, session, SHOWN)
        proof.rendered(1, session)
        assertTrue(proof.isUsable(1, session, audioReceived = false, screen = HIDDEN))
    }

    @Test fun aNewSessionStartsWithoutTheOldOnesScreen() {
        val first = Any()
        val second = Any()
        val proof = WirelessConnectionProof<Any>()
        proof.begin(1) {}
        proof.activate(1, first)
        proof.observeScreen(1, first, SHOWN)
        proof.activate(1, second)
        proof.observeScreen(1, second, MainScreenOutput(false, 3))
        assertTrue(proof.isUsable(1, second, audioReceived = true, screen = MainScreenOutput(false, 3)))
    }

    private companion object {
        val SHOWN = MainScreenOutput(attached = true, attachments = 0)
        val HIDDEN = MainScreenOutput(attached = false, attachments = 0)
    }
}
