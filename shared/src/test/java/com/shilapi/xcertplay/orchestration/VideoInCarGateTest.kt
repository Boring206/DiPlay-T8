package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.airplay.VideoInCar
import com.shilapi.xcertplay.hud.GearUnreadable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VideoInCarGateTest {
    @After
    fun reset() {
        VideoInCar.allowed = false
    }

    @Test
    fun videoIsAllowedOnlyWhileTheGearReadsPark() {
        val changes = mutableListOf<Boolean>()
        val gate = VideoInCarGate({ null }, onChanged = { changes += it })

        gate.update(null) // no ADB: stays off
        gate.update(false)
        gate.update(true)
        gate.update(true)
        gate.update(null) // unreadable while parked: off again
        gate.update(false)

        assertEquals(listOf(true, false), changes)
        assertFalse(VideoInCar.allowed)
    }

    @Test
    fun closingTheGateTurnsVideoOff() {
        val gate = VideoInCarGate({ true }, onChanged = {})
        gate.update(true)
        gate.close()
        gate.update(true)

        assertFalse(VideoInCar.allowed)
    }

    @Test
    fun observationsDistinguishUnknownParkAndNotPark() {
        val observations = mutableListOf<Boolean?>()
        val gate = VideoInCarGate({ null }, {}, { parked, _ -> observations += parked })

        gate.update(null)
        gate.update(null)
        gate.update(false)
        gate.update(true)

        assertEquals(listOf(null, false, true), observations)
    }

    // #1180: "gear=unknown" alone did not say that DiPlay's ADB key was not approved.
    @Test
    fun anUnreadableGearSaysWhyAndANewReasonIsANewObservation() {
        val observations = mutableListOf<String>()
        val gate = VideoInCarGate({ null }, {}, { parked, reason -> observations += VideoInCarDiagnostics.gearLine(parked, reason) })

        gate.update(null, GearUnreadable(LocalAdb.Access.NOT_APPROVED, replied = false).report)
        gate.update(null, GearUnreadable(LocalAdb.Access.NOT_APPROVED, replied = false).report)
        gate.update(null, GearUnreadable(LocalAdb.Access.READY, replied = true).report)
        gate.update(true)

        assertEquals(listOf(
            "video in car gear=unknown adb=NOT_APPROVED; video stays off until the gear reads P",
            "video in car gear=unknown adb=READY reply=unreadable; video stays off until the gear reads P",
            "video in car gear=P",
        ), observations)
        assertEquals("video in car gear=not-P", VideoInCarDiagnostics.gearLine(false, null))
        assertEquals("video in car gear=unknown reason=unknown; video stays off until the gear reads P",
            VideoInCarDiagnostics.gearLine(null, null))
    }

    @Test
    fun eachAdbStateReadsAsItsOwnReason() {
        assertEquals("adb=not-tried", GearUnreadable(null, replied = false).report)
        assertEquals("adb=UNREACHABLE", GearUnreadable(LocalAdb.Access.UNREACHABLE, replied = false).report)
        assertEquals("adb=UNSUPPORTED", GearUnreadable(LocalAdb.Access.UNSUPPORTED, replied = false).report)
        assertEquals("adb=READY reply=none", GearUnreadable(LocalAdb.Access.READY, replied = false).report)
    }
}
