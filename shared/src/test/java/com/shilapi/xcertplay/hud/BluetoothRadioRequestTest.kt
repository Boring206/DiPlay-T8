package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothRadioRequestTest {
    @Test
    fun adapterRequestIssuedSkipsShell() {
        val shellCalls = mutableListOf<Boolean>()
        assertTrue(BluetoothRadioRequest.request(false, { true }, { shellCalls += it }))
        assertTrue(shellCalls.isEmpty())
        assertTrue(BluetoothRadioRequest.request(true, { true }, { shellCalls += it }))
        assertTrue(shellCalls.isEmpty())
    }

    @Test
    fun adapterRefusalFallsBackToShell() {
        val shellCalls = mutableListOf<Boolean>()
        assertFalse(BluetoothRadioRequest.request(false, { false }, { shellCalls += it }))
        assertEquals(listOf(false), shellCalls)
        assertFalse(BluetoothRadioRequest.request(true, { false }, { shellCalls += it }))
        assertEquals(listOf(false, true), shellCalls)
    }

    @Test
    fun adapterFailureFallsBackToShell() {
        val shellCalls = mutableListOf<Boolean>()
        assertFalse(BluetoothRadioRequest.request(false, { throw SecurityException("denied") }, { shellCalls += it }))
        assertEquals(listOf(false), shellCalls)
    }

    @Test
    fun shellFailureDoesNotThrow() {
        assertFalse(BluetoothRadioRequest.request(false, { false }, { throw IllegalStateException("shell down") }))
    }
}
