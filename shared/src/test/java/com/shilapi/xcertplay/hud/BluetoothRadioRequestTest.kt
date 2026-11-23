package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.hud.BluetoothRadioRequest.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothRadioRequestTest {
    @Test fun shellReplySkipsAdapter() {
        val adapterCalls = mutableListOf<Boolean>()
        assertEquals(Path.SHELL, BluetoothRadioRequest.request(false, true, { adapterCalls += it; true }, { "OK" }))
        assertTrue(adapterCalls.isEmpty())
        assertEquals(Path.SHELL, BluetoothRadioRequest.request(true, true, { adapterCalls += it; true }, { "OK" }))
        assertTrue(adapterCalls.isEmpty())
    }

    @Test fun noShellReplyFallsBackToAdapter() {
        val shellCalls = mutableListOf<Boolean>()
        assertEquals(Path.ADAPTER_SENT, BluetoothRadioRequest.request(false, true, { true }, { shellCalls += it; null }))
        assertEquals(listOf(false), shellCalls)
        assertEquals(Path.ADAPTER_SENT, BluetoothRadioRequest.request(true, true, { true }, { shellCalls += it; null }))
        assertEquals(listOf(false, true), shellCalls)
    }

    @Test fun adapterRefusalAfterNoShellReplyReturnsRefused() {
        val shellCalls = mutableListOf<Boolean>()
        assertEquals(Path.ADAPTER_REFUSED, BluetoothRadioRequest.request(false, true, { false }, { shellCalls += it; null }))
        assertEquals(listOf(false), shellCalls)
    }

    @Test fun adapterFailureAfterNoShellReplyReturnsRefused() {
        assertEquals(Path.ADAPTER_REFUSED,
            BluetoothRadioRequest.request(false, true, { throw SecurityException("denied") }, { null }))
    }

    @Test fun shellFailureFallsBackToAdapter() {
        assertEquals(Path.ADAPTER_SENT,
            BluetoothRadioRequest.request(false, true, { true }, { throw IllegalStateException("shell down") }))
        assertEquals(Path.ADAPTER_REFUSED,
            BluetoothRadioRequest.request(false, true, { false }, { throw IllegalStateException("shell down") }))
    }

    @Test fun adapterNotAllowedSkipsAdapter() {
        val adapterCalls = mutableListOf<Boolean>()
        assertEquals(Path.ADAPTER_NOT_ALLOWED,
            BluetoothRadioRequest.request(false, false, { adapterCalls += it; true }, { null }))
        assertTrue(adapterCalls.isEmpty())
    }

    @Test fun adapterAllowedRules() {
        // Android 13+: never, consent or not.
        assertFalse(BluetoothRadioRequest.adapterAllowed(33, consentRequired = false, bluetoothConnectGranted = true))
        assertFalse(BluetoothRadioRequest.adapterAllowed(34, consentRequired = false, bluetoothConnectGranted = true))
        // Consent ROMs: never, on any level.
        assertFalse(BluetoothRadioRequest.adapterAllowed(27, consentRequired = true, bluetoothConnectGranted = true))
        assertFalse(BluetoothRadioRequest.adapterAllowed(30, consentRequired = true, bluetoothConnectGranted = true))
        // Android 12/12L need the Nearby devices permission.
        assertFalse(BluetoothRadioRequest.adapterAllowed(31, consentRequired = false, bluetoothConnectGranted = false))
        assertFalse(BluetoothRadioRequest.adapterAllowed(32, consentRequired = false, bluetoothConnectGranted = false))
        assertTrue(BluetoothRadioRequest.adapterAllowed(31, consentRequired = false, bluetoothConnectGranted = true))
        assertTrue(BluetoothRadioRequest.adapterAllowed(32, consentRequired = false, bluetoothConnectGranted = true))
        // Older units without consent: allowed, permission or not.
        assertTrue(BluetoothRadioRequest.adapterAllowed(27, consentRequired = false, bluetoothConnectGranted = false))
        assertTrue(BluetoothRadioRequest.adapterAllowed(30, consentRequired = false, bluetoothConnectGranted = false))
    }
}
