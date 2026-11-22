package com.shilapi.xcertplay.hud

import java.util.TimeZone
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BydHudArrivalTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val arrival = 1_794_407_760L // 2026-11-11 14:36:00 UTC
    private val now = 1_794_405_020_000L // 2026-11-11 13:50:20 UTC

    @Test fun onlyTheDenzaN9GetsAnArrivalTime() {
        // #301: no other HUD has been sent a non-empty field 26, so they keep it empty.
        for (fingerprint in listOf(
            "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260705.011226:user/release-keys", // N9 OTA V1.8
            "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260902.221210:user/release-keys", // N9 OTA V1.9
        )) {
            assertTrue("fingerprint=$fingerprint", BydHudArrival.enabled(fingerprint))
        }
        for (fingerprint in listOf(
            "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260722.221155:user/release-keys", // DiLink 5.1
            "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260705.021352:user/release-keys", // Leopard 5
            "BYD-AUTO/DiLink5.0/DiLink5.0:12/SKQ1.230128.001/eng.build.20251111.182747:user/release-keys",
            BydHudArrival.DENZA_N9_FINGERPRINT.replace("011226", "011227"),
            BydHudArrival.DENZA_N9_V19_FINGERPRINT.replace("221210", "221211"),
            "",
            null,
        )) {
            assertFalse("fingerprint=$fingerprint", BydHudArrival.enabled(fingerprint))
        }
    }

    @Test fun theIphoneArrivalTimeWinsOverTheTimeLeft() {
        assertEquals("14:36", BydHudArrival.text(now, arrival, remainingSeconds = 60, utc, use24Hour = true))
    }

    @Test fun withoutAnArrivalTimeItIsNowPlusTheTimeLeft() {
        assertEquals("14:20", BydHudArrival.text(now, null, remainingSeconds = 1_800, utc, use24Hour = true))
        assertEquals("13:50", BydHudArrival.text(now, 0, remainingSeconds = 0, utc, use24Hour = true))
    }

    @Test fun followsTheHeadUnitClockFormatAndTimeZone() {
        assertEquals("2:36", BydHudArrival.text(now, arrival, null, utc, use24Hour = false))
        assertEquals("18:36", BydHudArrival.text(now, arrival, null, TimeZone.getTimeZone("Asia/Dubai"), use24Hour = true))
        assertEquals("09:05", BydHudArrival.text(0, 9 * 3_600L + 5 * 60L, null, utc, use24Hour = true))
    }

    @Test fun nothingToShowLeavesTheFieldEmpty() {
        assertEquals("", BydHudArrival.text(now, null, null, utc, use24Hour = true))
        assertEquals("", BydHudArrival.text(now, 0, -1, utc, use24Hour = true))
    }

    @Test fun theHudGetsTheBareClockTime() {
        // A stock N9 HUD shows "13:18到", so the HUD seems to add "到" itself. Unconfirmed on the car.
        assertEquals("%s", BydHudArrival.TEMPLATE)
        assertEquals("14:36", BydHudArrival.text(now, arrival, null, utc, use24Hour = true))
        assertEquals("2:36", BydHudArrival.text(now, arrival, null, utc, use24Hour = false))
    }

    @Test fun theTimeLeftCountsFromWhenItArrivedSoTheArrivalDoesNotCreep() {
        // 0x06 said 30 min at 13:50:20; 59 s later, with no new 0x06, the arrival is still 14:20.
        val later = now + 59_000
        assertEquals("14:20", BydHudArrival.text(later, null, 1_800, utc, use24Hour = true, remainingAtMillis = now))
        assertEquals("14:21", BydHudArrival.text(later, null, 1_800, utc, use24Hour = true))
    }

    @Test fun theArrivalTimeTravelsInFieldTwentySix() {
        val eta = BydHudArrival.text(now, arrival, null, utc, use24Hour = true)
        val hex = BydHudPayload.guidance(distanceMeters = 120, maneuver = 2, eta = eta).joinToString("") { "%02x".format(it) }
        assertTrue(hex, hex.contains("d20105" + "31343a3336")) // field 26 = "14:36"
    }

    private val guidance = BydHudGuidance(
        distanceMeters = 120,
        maneuver = 2,
        road = "Main St",
        arrivalEpochSeconds = arrival,
        remainingSeconds = 1_800,
        remainingAtMillis = now,
    )
    private val icon = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)

    private fun payloadFor(guidance: BydHudGuidance, arrivalEnabled: Boolean, nowMillis: Long = now) =
        BydHudArrival.payloadFor(guidance, icon, arrivalEnabled, nowMillis, utc, use24Hour = true)

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    @Test fun offTheN9ThePayloadIsTheSameBytesAs0222() {
        val outgoing = payloadFor(guidance, arrivalEnabled = false)
        // The 0.2.22 call had no eta, so field 26 went out empty ("d20100").
        val v0222 = BydHudPayload.guidance(distanceMeters = 120, maneuver = 2, icon = icon, road = "Main St")
        assertArrayEquals(v0222, outgoing.payload)
        assertEquals(V0222_HEX, outgoing.payload.hex())
        assertTrue(outgoing.payload.hex().contains("d20100"))
        assertNull(outgoing.arrival)
    }

    @Test fun onTheN9FieldTwentySixCarriesTheArrivalTime() {
        val outgoing = payloadFor(guidance, arrivalEnabled = true)
        assertEquals("14:36", outgoing.arrival)
        assertArrayEquals(
            BydHudPayload.guidance(distanceMeters = 120, maneuver = 2, icon = icon, road = "Main St", eta = "14:36"),
            outgoing.payload,
        )
        assertTrue(outgoing.payload.hex(), outgoing.payload.hex().contains("d20105" + "31343a3336"))
    }

    @Test fun onTheN9TheTimeLeftFallbackDoesNotCreepBetweenUpdates() {
        val noEpoch = guidance.copy(arrivalEpochSeconds = null)
        assertEquals("14:20", payloadFor(noEpoch, arrivalEnabled = true, nowMillis = now + 59_000).arrival)
    }

    @Test fun onTheN9AnUnknownArrivalIsSentEmptyAndReportedAsNone() {
        val unknown = guidance.copy(arrivalEpochSeconds = null, remainingSeconds = null, remainingAtMillis = null)
        val outgoing = payloadFor(unknown, arrivalEnabled = true)
        assertEquals("", outgoing.arrival)
        assertArrayEquals(payloadFor(unknown, arrivalEnabled = false).payload, outgoing.payload)
        assertEquals(
            "HUD gateway accepted guidance; rendering unconfirmed; arrival field26=none",
            NavigationOutputLines.hudGuidanceAccepted(outgoing.arrival),
        )
    }

    @Test fun theReportLineNamesFieldTwentySixOnlyWhereItIsUsed() {
        assertEquals("HUD gateway accepted guidance; rendering unconfirmed", NavigationOutputLines.hudGuidanceAccepted())
        assertEquals("HUD gateway accepted guidance; rendering unconfirmed", NavigationOutputLines.hudGuidanceAccepted(null))
        assertEquals(
            "HUD gateway accepted guidance; rendering unconfirmed; arrival field26=14:36",
            NavigationOutputLines.hudGuidanceAccepted("14:36"),
        )
    }

    @Test fun offTheN9TheReportLineIsWrittenOncePerShow() {
        val note = BydHudGuidanceNote()
        assertEquals(NavigationOutputLines.hudGuidanceAccepted(), note.accepted(null))
        repeat(3) { assertNull(note.accepted(null)) }
        note.reset()
        assertEquals(NavigationOutputLines.hudGuidanceAccepted(), note.accepted(null))
    }

    @Test fun onTheN9TheReportLineIsWrittenAgainWhenTheArrivalTimeFirstAppears() {
        // The first route update may carry neither 0x05 nor 0x06; the report must still show the time later.
        val note = BydHudGuidanceNote()
        assertEquals(NavigationOutputLines.hudGuidanceAccepted(""), note.accepted(""))
        assertNull(note.accepted(""))
        assertEquals(NavigationOutputLines.hudGuidanceAccepted("14:36"), note.accepted("14:36"))
        // Minute changes are not written: one line per show is enough to see what field 26 carried.
        assertNull(note.accepted("14:37"))
        assertNull(note.accepted(""))
        note.reset()
        assertEquals(NavigationOutputLines.hudGuidanceAccepted("14:37"), note.accepted("14:37"))
        assertNull(note.accepted("14:38"))
    }

    private companion object {
        // BydHudPayload is unchanged since v0.2.22: field 2=2, 6=1, 7 empty, 8 icon, 9=120, 10 "Main St",
        // 16=2, 26 empty, 28=2.
        const val V0222_HEX = "0a20100230013a00420489504e47487852074d61696e205374800102d20100e00102"
    }
}
