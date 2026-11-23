package com.shilapi.xcertplay

import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.airplay.AirPlayPeerDiagnostics
import com.shilapi.xcertplay.hud.GearUnreadable
import com.shilapi.xcertplay.hud.NavigationOutputLines
import com.shilapi.xcertplay.hud.NavigationOutputLog
import com.shilapi.xcertplay.network.WifiStationLines
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.VideoInCarDiagnostics
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lines added for #1222, #1196, #1180 and #1233 reach a saved report unchanged: the redactor drops a
 * whole line that contains "ssid", "token", "payload=" or a field ending in "name", so each line is built
 * by the code that writes it and checked here.
 */
class ReportEvidenceRedactionTest {
    private val error = IllegalStateException("private detail")

    private val lines: List<String> = buildList {
        // #1222
        add("Car Bluetooth audio: start A2DP-sink state=connected devices=1")
        add("Car Bluetooth audio: after30s HFP-client state=connected no answer within 3s")
        add("Car Bluetooth audio: start HFP-client state=disconnected")
        add("Car Bluetooth audio: start A2DP-sink state unreadable SecurityException")
        // #1196
        with(NavigationOutputLines) {
            listOf(
                hudBindRequested(), hudBindRefused(30), hudBindFailed(error), hudConnected(0, 13, true),
                hudGatewayLost("binding died"), hudHalReady(true), hudTransactionFailed(6, error), hudTransactionsAnswer(),
                hudFireEvent(0), hudFireEvent(-1), hudGuidanceAccepted(), hudGuidanceCleared(),
                clusterAdapter("com.example.amapservice", true), clusterFactoryTest(),
                clusterGuidanceSent("com.byd.amapservice"), clusterGuidanceEnded(), clusterBroadcastFailed(error),
                clusterBroadcastRecovered(), standaloneReady("com.byd.clusterdebug"), standaloneShows("guidance"),
                standaloneShows("song text"), standaloneCleared(), standaloneFailed(error), standaloneRecovered(),
                capped("hud.send"),
            ).forEach {
                add(NavigationOutputLog.PREFIX + it)
                add(NavigationOutputLog.CURRENT_PREFIX + it)
            }
            add(routeGuidanceSummary(42, 120, listOf("Google Maps", "Waze"), true,
                listOf("com.byd.amapservice", "com.ts.car.someip.service")))
            add(routeGuidanceSummary(0, 0, emptyList(), false, emptyList()))
        }
        add(AirPlayPeerDiagnostics.summary("iPhone15,2", "", "870.14.1", "22G86"))
        // #1180
        for (adb in listOf(null) + LocalAdb.Access.entries) {
            for (replied in listOf(false, true)) add(VideoInCarDiagnostics.gearLine(null, GearUnreadable(adb, replied).report))
        }
        add(VideoInCarDiagnostics.gearLine(null, "read failed IllegalStateException"))
        add(VideoInCarDiagnostics.gearLine(true, null))
        // #1233, written as connection diagnostics
        with(WifiStationLines) {
            listOf(watching(), unavailable("SecurityException"), connected(1, 1), disconnected(1, 95, 0),
                flags(1, true, true, true, true), flags(2, null, null, null, null))
                .forEach { add("${CarPlayController.CONNECTION_DIAGNOSTIC_PREFIX} attempt=3 run=1 phase=CONTROL $it") }
        }
    }

    @Test fun everyNewLineSurvivesTheRedactorUnchanged() {
        for (line in lines) {
            assertTrue("New lines must fit the export cap: $line", line.length < 700)
            assertEquals(line, DiagnosticRedactor.redact(line))
            assertFalse(line, line.contains(error.message!!))
        }
    }

    @Test fun theLinesReachASavedSessionLog() {
        val folder = Files.createTempDirectory("diplay-report-evidence").toFile()
        try {
            val file = folder.resolve("diplay.log")
            SessionLogFile(file).use { log ->
                log.reset("started")
                lines.forEach(log::append)
            }
            val saved = file.readText()
            lines.forEach { assertTrue(it, saved.contains(it)) }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun theNamesTheRedactorWouldDropAreNotUsed() {
        // Why the summary says "source=" and the probe counts devices instead of naming them.
        assertNull(DiagnosticRedactor.redact("Route guidance this session: sourceName=Maps"))
        assertNull(DiagnosticRedactor.redact("Android Wi-Fi station ssid=Car"))
        assertFalse(lines.any { Regex("(?i)name[=:]|ssid").containsMatchIn(it) })
    }
}
