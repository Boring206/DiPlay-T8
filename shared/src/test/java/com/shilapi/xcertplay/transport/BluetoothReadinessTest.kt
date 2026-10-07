package com.shilapi.xcertplay.transport

import com.shilapi.xcertplay.network.WirelessStartupFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothReadinessTest {
    private class Clock { var now = 0L }

    private fun ensureOn(onAfterMillis: Long?, switchOn: (() -> Boolean)?, clock: Clock = Clock(), cancelled: () -> Boolean = { false }) =
        BluetoothReadiness.ensureOn(
            isOn = { onAfterMillis != null && clock.now >= onAfterMillis },
            switchOn = switchOn,
            isCancelled = cancelled,
            timeoutMillis = 10_000L,
            pause = { clock.now += it },
            nowMillis = { clock.now },
        )

    @Test fun bluetoothThatIsAlreadyOnIsLeftAlone() {
        assertTrue(ensureOn(onAfterMillis = 0L, switchOn = { throw AssertionError("must not be switched") }))
    }

    @Test fun bluetoothThatIsOffIsSwitchedOnAndWaitedFor() {
        val clock = Clock()
        var asked = 0
        assertTrue(ensureOn(onAfterMillis = 2_000L, switchOn = { asked++; true }, clock = clock))
        assertEquals(1, asked)
        assertEquals(2_000L, clock.now)
    }

    @Test fun staysOffWhereItCannotBeSwitchedOrNeverComesUp() {
        assertFalse(ensureOn(onAfterMillis = null, switchOn = null))
        assertFalse(ensureOn(onAfterMillis = null, switchOn = { false }))
        assertFalse(ensureOn(onAfterMillis = null, switchOn = { throw SecurityException() }))
        val clock = Clock()
        assertFalse(ensureOn(onAfterMillis = 60_000L, switchOn = { true }, clock = clock))
        assertEquals(10_000L, clock.now)
    }

    @Test fun cancelledWaitStopsAtOnce() {
        val clock = Clock()
        assertFalse(ensureOn(onAfterMillis = 5_000L, switchOn = { true }, clock = clock, cancelled = { true }))
        assertEquals(0L, clock.now)
    }

    @Test fun instantSilentConnectionLooksUnrealButASlowOrTalkingOneDoesNot() {
        // Seen on an Allwinner T8 head unit: eight "connections" in 4 to 17 ms, no byte in five minutes.
        for (millis in listOf(4L, 7L, 8L, 9L, 11L, 13L, 14L, 17L)) assertTrue(BluetoothReadiness.looksUnreal(millis, 0L))
        assertFalse(BluetoothReadiness.looksUnreal(650L, 0L))
        assertFalse(BluetoothReadiness.looksUnreal(9L, 6L))
    }

    @Test fun bluetoothAndConfigurationFaultsAreNotRetriedBlindly() {
        assertEquals(
            listOf(WirelessStartupFailure.HOTSPOT_NOT_READY, WirelessStartupFailure.FIRST_TCP_TIMEOUT),
            WirelessStartupFailure.values().filter { it.retryable },
        )
    }
}
