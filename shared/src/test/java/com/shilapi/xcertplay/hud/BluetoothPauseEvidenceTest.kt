package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #1021: why `svc bluetooth disable` left the radio on, without the head unit's name or address. */
class BluetoothPauseEvidenceTest {
    private val prefix = "car Bluetooth pause evidence:"

    @Test fun theCommandLineSaysWhetherItRanAndWhatItPrinted() {
        assertEquals("$prefix svc bluetooth disable not sent", BluetoothPauseEvidence.commandLine(false, "ignored"))
        assertEquals("$prefix svc bluetooth disable got no ADB reply", BluetoothPauseEvidence.commandLine(true, null))
        assertEquals("$prefix svc bluetooth disable printed nothing", BluetoothPauseEvidence.commandLine(true, " \n"))
        assertEquals("$prefix svc bluetooth disable printed 'svc: unknown command bluetooth'",
            BluetoothPauseEvidence.commandLine(true, "svc: unknown command 'bluetooth'\n"))
        // Android 8.1 prints the usage text: one line, plain characters, bounded.
        val usage = BluetoothPauseEvidence.commandLine(true,
            "Control the Bluetooth manager\n\nusage: svc bluetooth [enable|disable]\n" + "x".repeat(400))
        assertTrue(usage, usage.startsWith("$prefix svc bluetooth disable printed 'Control the Bluetooth manager usage: svc bluetooth enable disable "))
        assertFalse(usage, usage.contains('\n') || usage.contains('[') || usage.contains('|'))
        assertTrue(usage, usage.length < prefix.length + 200)
    }

    @Test fun theManagerLineKeepsOnlyEnabledAndState() {
        val dump = """
            Bluetooth Status
              enabled: true
              state: ON
              address: C0:A6:00:29:58:0A
              name: Jane's Car
              time since enabled: 00:10:00.000

            Bluetooth Service state: BLE_ON
        """.trimIndent()
        val line = BluetoothPauseEvidence.managerLine(dump)
        assertEquals("$prefix bluetooth_manager after DiPlay's restore step enabled=true state=ON", line)
        assertEquals("$prefix bluetooth_manager unavailable", BluetoothPauseEvidence.managerLine(null))
        assertEquals("$prefix bluetooth_manager after DiPlay's restore step enabled=unknown state=unknown",
            BluetoothPauseEvidence.managerLine("Can't find service: bluetooth_manager"))
        assertEquals("$prefix bluetooth_manager after DiPlay's restore step enabled=false state=TURNING_OFF",
            BluetoothPauseEvidence.managerLine("  enabled: false\n  state: TURNING_OFF (12)\n  name: x"))
    }

    @Test fun theWaitLineIsLeftOutWhenNoDisableWasAwaited() {
        assertEquals(listOf("$prefix svc bluetooth disable not sent", "$prefix bluetooth_manager unavailable"),
            BluetoothPauseEvidence.lines(false, null, null, null))
        assertEquals("$prefix Android Bluetooth was OFF when the wait for the disable ended",
            BluetoothPauseEvidence.lines(true, "", "OFF", null)[1])
    }

    @Test fun aFailedPauseIsExactlyTheOutcomeThatSaysFailed() {
        for (paused in listOf(true, false)) for (before in listOf(true, false, null)) for (current in listOf(true, false)) {
            for (access in listOf(null, "READY", "NOT_APPROVED")) {
                val outcome = BluetoothPauseOutcome.describe(paused, before, paused || current, access)
                assertEquals(outcome, outcome.startsWith("car Bluetooth pause failed"),
                    BluetoothPauseOutcome.failed(paused, before, paused || current))
            }
        }
    }
}
