package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.network.WirelessStartupPolicy
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessHandoffTest {
    @Test
    fun activeTunnelKeepsHandoffAliveAfterBluetoothBootstrapCloses() {
        assertTrue(
            isWirelessHandoffInProgress(
                handoffRequested = false,
                tunnelActive = true,
                sessionActive = true,
            ),
        )
    }

    @Test
    fun ordinaryBootstrapLossStillFailsWithoutHandoffState() {
        assertFalse(
            isWirelessHandoffInProgress(
                handoffRequested = false,
                tunnelActive = false,
                sessionActive = false,
            ),
        )
    }

    @Test
    fun rfcommEofAfterSessionOrRenderedFrameKeepsTheTunnel() {
        val eof = IOException("iAP2 link ended")
        assertTrue(isRfcommEofAfterAirPlaySession(eof, sessionActive = true, frameRendered = false))
        assertTrue(isRfcommEofAfterAirPlaySession(eof, sessionActive = false, frameRendered = true))
        assertTrue(isRfcommEofAfterAirPlaySession(eof, sessionActive = true, frameRendered = true))
    }

    @Test
    fun earlyRfcommEofWithNoSessionStillFailsBringUp() {
        // The bring-up catch calls fail + closeWirelessStack unless this is true.
        assertFalse(
            isRfcommEofAfterAirPlaySession(
                IOException("iAP2 link ended"),
                sessionActive = false,
                frameRendered = false,
            ),
        )
    }

    @Test
    fun otherBringUpFailuresStayFatalAfterASessionExists() {
        assertFalse(
            isRfcommEofAfterAirPlaySession(
                IOException("Wireless CarPlay control channel closed before tunnel iAP2 ready"),
                sessionActive = false,
                frameRendered = false,
                airPlaySetupReached = true,
            ),
        )
        assertFalse(
            isRfcommEofAfterAirPlaySession(
                IOException("iAP2 inbound frame exceeds 65535 bytes"),
                sessionActive = true,
                frameRendered = false,
            ),
        )
        assertFalse(
            isRfcommEofAfterAirPlaySession(
                IllegalStateException("iAP2 link ended"),
                sessionActive = true,
                frameRendered = true,
                airPlaySetupReached = true,
            ),
        )
    }

    @Test
    fun sessionSetupAfterStartSessionAndTcpKeepsTheTunnel() {
        val handshake = reachedSetup(generation = 3)
        val eof = IOException("iAP2 link ended")
        assertTrue(
            isRfcommEofAfterAirPlaySession(
                eof,
                sessionActive = false,
                frameRendered = false,
                airPlaySetupReached = handshake.reachedSessionSetup(3),
            ),
        )
    }

    @Test
    fun eofBeforeTcpOrSetupStillFailsBringUp() {
        val handshake = WirelessAirPlayHandshake()
        handshake.begin(1)
        handshake.noteStartSession(1)
        assertFalse(handshake.reachedSessionSetup(1))
        assertFalse(eofKept(handshake, 1))
    }

    @Test
    fun tcpAcceptedWithoutSessionSetupStillFailsBringUp() {
        val handshake = WirelessAirPlayHandshake()
        handshake.begin(1)
        handshake.noteStartSession(1)
        handshake.noteTcpAccepted(1)
        assertFalse(handshake.reachedSessionSetup(1))
        assertFalse(eofKept(handshake, 1))
    }

    @Test
    fun setupBeforeThePhoneTcpDoesNotCount() {
        val handshake = WirelessAirPlayHandshake()
        handshake.begin(1)
        handshake.noteStartSession(1)
        handshake.noteSessionSetup(1)
        handshake.noteTcpAccepted(1)
        assertFalse(handshake.reachedSessionSetup(1))
        handshake.noteSessionSetup(1)
        assertTrue(handshake.reachedSessionSetup(1))
    }

    @Test
    fun handshakeFromAnotherGenerationDoesNotKeepTheTunnel() {
        val handshake = reachedSetup(generation = 1)
        handshake.begin(2)
        assertFalse(handshake.reachedSessionSetup(1))
        assertFalse(handshake.reachedSessionSetup(2))
        handshake.noteStartSession(1)
        handshake.noteTcpAccepted(1)
        handshake.noteSessionSetup(1)
        assertFalse(eofKept(handshake, 2))
    }

    @Test
    fun setupKeepAliveFailsWhenNoSessionArrives() {
        val watch = SetupKeepAliveWatch(4)
        watch.arm()
        assertTrue(watch.onDeadline(currentGeneration = 4, sessionActive = false, frameRendered = false))
        assertFalse(watch.onDeadline(currentGeneration = 4, sessionActive = false, frameRendered = false))
    }

    @Test
    fun setupKeepAliveIsCancelledWhenTheSessionBecomesActive() {
        val watch = SetupKeepAliveWatch(4)
        watch.arm()
        assertTrue(watch.cancel())
        assertFalse(watch.onDeadline(currentGeneration = 4, sessionActive = false, frameRendered = false))
        watch.arm()
        assertFalse(watch.onDeadline(currentGeneration = 4, sessionActive = true, frameRendered = false))
        assertFalse(watch.onControlLost(currentGeneration = 4, sessionActive = false))
    }

    @Test
    fun setupKeepAliveIgnoresAStaleGeneration() {
        val watch = SetupKeepAliveWatch(4)
        watch.arm()
        assertFalse(watch.onDeadline(currentGeneration = 5, sessionActive = false, frameRendered = false))
        assertFalse(watch.onControlLost(currentGeneration = 5, sessionActive = false))
        assertTrue(watch.onDeadline(currentGeneration = 4, sessionActive = false, frameRendered = false))
    }

    @Test
    fun controlLossBeforeTheSessionFailsTheKeptTunnel() {
        val watch = SetupKeepAliveWatch(4)
        watch.arm()
        assertTrue(watch.onControlLost(currentGeneration = 4, sessionActive = false))
        assertFalse(watch.onDeadline(currentGeneration = 4, sessionActive = false, frameRendered = false))
    }

    @Test
    fun setupSessionWaitIsFiveSeconds() {
        assertEquals(5_000L, WirelessStartupPolicy.SETUP_SESSION_WAIT_MILLIS)
        assertTrue(WirelessStartupPolicy.SETUP_SESSION_WAIT_MILLIS < WirelessStartupPolicy.FIRST_TCP_MILLIS)
    }

    private fun reachedSetup(generation: Int): WirelessAirPlayHandshake =
        WirelessAirPlayHandshake().also {
            it.begin(generation)
            it.noteStartSession(generation)
            it.noteTcpAccepted(generation)
            it.noteSessionSetup(generation)
        }

    private fun eofKept(handshake: WirelessAirPlayHandshake, generation: Int): Boolean =
        isRfcommEofAfterAirPlaySession(
            IOException("iAP2 link ended"),
            sessionActive = false,
            frameRendered = false,
            airPlaySetupReached = handshake.reachedSessionSetup(generation),
        )
}
