package com.shilapi.xcertplay.transport

import com.shilapi.xcertplay.iap2.body.Iap2BodyReader
import com.shilapi.xcertplay.iap2.wire.Iap2ParameterList
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Iap2WiredControlClientTest {
    @Test
    fun carPlayStartSessionNestsAddressesInsideWiredAttributes() {
        val frame = Iap2WiredControlClient.carPlayStartSession(
            Iap2WiredCarPlayEndpoint(
                ipv6Addresses = listOf("fe80::2"),
                airPlayPort = 7000,
                deviceIdentifier = "02:00:00:00:00:02",
                publicKey = "abcd",
                sourceVersion = "1.0",
            ),
        )

        assertEquals(0x4301, frame.messageId)
        val outer = Iap2BodyReader.of(frame).list()
        val wired = outer.single { it.id == 0 }
        val addresses = Iap2ParameterList.parse(wired.payload).asList()
        assertEquals(1, addresses.size)
        assertEquals(0, addresses.single().id)
        assertArrayEquals("fe80::2\u0000".encodeToByteArray(), addresses.single().payload)
    }

    @Test
    fun powerSourceUpdateOffersTheConfiguredChargingCurrent() {
        listOf(2400, 1500, 500).forEach { milliAmps ->
            val frame = Iap2WiredControlClient.powerSourceUpdate(milliAmps)

            assertEquals(0xae03, frame.messageId)
            val current = Iap2BodyReader.of(frame).list().single { it.id == 0 }.payload
            assertArrayEquals(byteArrayOf((milliAmps shr 8).toByte(), milliAmps.toByte()), current)
        }
    }

    @Test fun availabilityReplyIsAllowedOnlyBelowTheCap() {
        val cap = Iap2WiredControlClient.MAX_CARPLAY_START_SESSION_SENDS
        assertTrue(carPlayAvailabilityReplyAllowed(0, cap))
        assertTrue(carPlayAvailabilityReplyAllowed(cap - 1, cap))
        assertFalse(carPlayAvailabilityReplyAllowed(cap, cap))
        assertFalse(carPlayAvailabilityReplyAllowed(cap + 1, cap))
    }

    @Test fun repeatedCarPlayAvailabilityIsCappedAtMaximumStartSessionSends() {
        val cap = Iap2WiredControlClient.MAX_CARPLAY_START_SESSION_SENDS
        val flood = cap * 4
        val traces = mutableListOf<String>()
        val progress = mutableListOf<String>()
        val session = Iap2ControlClientTestHarness.scriptedSession(flood) { traces += it }
        val result = Iap2WiredControlClient(session, Iap2ControlClientTestHarness.fakeMfi()).run(
            identification = identification(),
            endpoint = endpoint(),
            availableCurrentMilliAmps = 1500,
            timeoutMillis = 500L, // bounded, so the scripted loop exits via TIMED_OUT
            onProgress = { progress += it },
        )
        // No retry here (startSessionAgain defaults to DONE): only availability replies are counted.
        assertEquals(cap, traces.count { it.startsWith("IAP2 TX [test] 0x4301") })
        assertEquals(cap, result.carPlayStartSessionsSent)
        assertEquals(
            flood - cap,
            progress.count { "ignored: maximum CarPlay StartSession sends reached" in it },
        )
    }

    @Test fun startAgainDoesNotConsumeAnAvailabilityCapSlot() {
        val cap = Iap2WiredControlClient.MAX_CARPLAY_START_SESSION_SENDS
        val flood = cap * 4
        val traces = mutableListOf<String>()
        val progress = mutableListOf<String>()
        val session = Iap2ControlClientTestHarness.scriptedSession(flood) { traces += it }
        val result = Iap2WiredControlClient(session, Iap2ControlClientTestHarness.fakeMfi()).run(
            identification = identification(),
            endpoint = endpoint(),
            availableCurrentMilliAmps = 1500,
            timeoutMillis = 500L, // bounded, so the scripted loop exits via TIMED_OUT
            onProgress = { progress += it },
            startSessionAgain = { Iap2WiredControlClient.StartAgain.SEND },
        )
        // The retry is sent once, but availability replies are still answered up to cap (not cap - 1).
        assertEquals(1, progress.count { it == Iap2WiredControlClient.START_SESSION_AGAIN_PROGRESS })
        assertEquals(
            flood - cap,
            progress.count { "ignored: maximum CarPlay StartSession sends reached" in it },
        )
        // cap availability replies + one retry = cap + 1 total 0x4301 frames, all reported.
        assertEquals(cap + 1, traces.count { it.startsWith("IAP2 TX [test] 0x4301") })
        assertEquals(cap + 1, result.carPlayStartSessionsSent)
    }

    private fun identification(): Iap2IdentificationConfig = Iap2IdentificationConfig(
        name = "wired",
        modelIdentifier = "wired",
        manufacturer = "test",
        serialNumber = "1",
        firmwareVersion = "1.0",
        hardwareVersion = "1.0",
        carPlayUsbInterfaceNumber = 4,
    )

    private fun endpoint(): Iap2WiredCarPlayEndpoint = Iap2WiredCarPlayEndpoint(
        ipv6Addresses = listOf("fe80::2"),
        airPlayPort = 7000,
        deviceIdentifier = "02:00:00:00:00:02",
        publicKey = "abcd",
        sourceVersion = "1.0",
    )
}
