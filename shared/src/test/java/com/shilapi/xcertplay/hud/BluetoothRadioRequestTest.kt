package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothRadioRequestTest {
    @Test
    fun shellReplySkipsAdapter() {
        val adapterCalls = mutableListOf<Boolean>()
        assertFalse(BluetoothRadioRequest.request(false, { adapterCalls += it; true }, { "OK" }))
        assertTrue(adapterCalls.isEmpty())
        assertFalse(BluetoothRadioRequest.request(true, { adapterCalls += it; true }, { "OK" }))
        assertTrue(adapterCalls.isEmpty())
    }

    @Test
    fun noShellReplyFallsBackToAdapter() {
        val shellCalls = mutableListOf<Boolean>()
        assertTrue(BluetoothRadioRequest.request(false, { true }, { shellCalls += it; null }))
        assertEquals(listOf(false), shellCalls)
        assertTrue(BluetoothRadioRequest.request(true, { true }, { shellCalls += it; null }))
        assertEquals(listOf(false, true), shellCalls)
    }

    @Test
    fun adapterRefusalAfterNoShellReplyReturnsFalse() {
        val shellCalls = mutableListOf<Boolean>()
        assertFalse(BluetoothRadioRequest.request(false, { false }, { shellCalls += it; null }))
        assertEquals(listOf(false), shellCalls)
    }

    @Test
    fun adapterFailureAfterNoShellReplyReturnsFalse() {
        assertFalse(BluetoothRadioRequest.request(false, { throw SecurityException("denied") }, { null }))
    }

    @Test
    fun shellFailureFallsBackToAdapter() {
        assertTrue(BluetoothRadioRequest.request(false, { true }, { throw IllegalStateException("shell down") }))
        assertFalse(BluetoothRadioRequest.request(false, { false }, { throw IllegalStateException("shell down") }))
    }
}
