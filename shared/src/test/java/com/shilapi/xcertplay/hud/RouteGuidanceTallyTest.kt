package com.shilapi.xcertplay.hud

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #1196: one line per session says what route guidance the iPhone sent and which app sent it. */
class RouteGuidanceTallyTest {
    private val update = BydHudRouteState.ROUTE_GUIDANCE_UPDATE
    private val maneuver = BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE

    @Test fun countsBothFrameKindsAndNamesTheNavigationApp() {
        val tally = RouteGuidanceTally()
        tally.accept(update, tlvs(tlv(0x01, byteArrayOf(1)), text(0x13, "Google Maps")))
        repeat(3) { tally.accept(maneuver, tlvs(tlv(0x01, byteArrayOf(0, 1)))) }
        tally.accept(update, tlvs(tlv(0x01, byteArrayOf(1)))) // a later update without the name keeps it
        tally.accept(update, tlvs(text(0x13, "Waze")))
        tally.accept(0x5000, ByteArray(0)) // other iAP2 messages are not route guidance

        assertEquals(
            "Route guidance this session: 0x5201=3 0x5202=3 source=\"Google Maps\",\"Waze\" " +
                "navigation_enabled=true receivers=com.ts.car.someip.service",
            tally.finish(navigationOutput = true, receivers = listOf("com.ts.car.someip.service")),
        )
    }

    @Test fun aSessionWithoutGuidanceSaysSoAndEachSessionStartsFromZero() {
        val tally = RouteGuidanceTally()
        tally.accept(update, tlvs(text(0x13, "Maps")))
        tally.finish(navigationOutput = true, receivers = emptyList())

        assertEquals(
            "Route guidance this session: 0x5201=0 0x5202=0 source=none navigation_enabled=false receivers=none",
            tally.finish(navigationOutput = false, receivers = emptyList()),
        )
    }

    @Test fun anEndedOrOddSourceCannotBreakTheLine() {
        val tally = RouteGuidanceTally()
        tally.accept(update, tlvs(text(0x13, ""))) // "" is how the iPhone says navigation ended
        tally.accept(update, tlvs(text(0x13, "Bad\"App\nname=x ${"y".repeat(60)}")))
        assertEquals(
            "Route guidance this session: 0x5201=2 0x5202=0 source=\"BadAppnamex ${"y".repeat(20)}\" " +
                "navigation_enabled=true receivers=none",
            tally.finish(navigationOutput = true, receivers = emptyList()),
        )
        assertNull(RouteGuidanceTally.sourceLabel(" \u0000 "))
    }

    private fun text(id: Int, value: String) = tlv(id, value.toByteArray(Charsets.UTF_8) + 0)

    private fun tlv(id: Int, value: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        val length = value.size + 4
        write(length ushr 8); write(length); write(id ushr 8); write(id); write(value)
    }.toByteArray()

    private fun tlvs(vararg values: ByteArray): ByteArray = ByteArrayOutputStream().apply { values.forEach(::write) }.toByteArray()
}
