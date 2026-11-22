package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothAdapterProofTest {
    private class MemoryStore(var saved: String? = null) : BluetoothAdapterProof.Store {
        var writes = 0
        override fun read(): String? = saved
        override fun write(adapter: String) {
            writes++
            saved = adapter
        }
    }

    private val unit = BluetoothAdapterProof.key("board-a", "00:11:22:33:44:51")

    @Test fun anAdapterIsUnprovenUntilALinkOnItCarriesAByte() {
        val proof = BluetoothAdapterProof()
        val store = MemoryStore()
        assertFalse(proof.proven(unit, store))
        assertTrue(proof.record(unit, store))
        assertTrue(proof.proven(unit, store))
    }

    // A new controller in the same process, or the next start of DiPlay, still knows.
    @Test fun proofOutlivesTheProcessThroughTheStore() {
        val store = MemoryStore()
        BluetoothAdapterProof().record(unit, store)
        assertEquals(unit, store.saved)
        assertTrue(BluetoothAdapterProof().proven(unit, store))
    }

    @Test fun proofIsKeptForTheProcessWithoutAStore() {
        val proof = BluetoothAdapterProof()
        proof.record(unit, store = null)
        assertTrue(proof.proven(unit, store = null))
        assertTrue(proof.proven(unit, MemoryStore()))
    }

    @Test fun eachLinkAfterTheFirstDoesNotWriteAgain() {
        val proof = BluetoothAdapterProof()
        val store = MemoryStore()
        assertTrue(proof.record(unit, store))
        assertFalse(proof.record(unit, store))
        assertFalse(proof.record(unit, store))
        assertEquals(1, store.writes)
    }

    // A saved key from another head unit, or another adapter, vouches for nothing here.
    @Test fun proofNeverVouchesForAnotherBoardOrAdapter() {
        val store = MemoryStore()
        BluetoothAdapterProof().record(unit, store)
        val fresh = BluetoothAdapterProof()
        assertFalse(fresh.proven(BluetoothAdapterProof.key("board-b", "00:11:22:33:44:51"), store))
        assertFalse(fresh.proven(BluetoothAdapterProof.key("board-a", "00:11:22:33:44:52"), store))
        assertFalse(fresh.proven(BluetoothAdapterProof.key(null, "00:11:22:33:44:51"), store))
    }

    @Test fun aFailingStoreLeavesTheProcessMemoryWorking() {
        val broken = object : BluetoothAdapterProof.Store {
            override fun read(): String? = throw IllegalStateException("prefs unavailable")
            override fun write(adapter: String) = throw IllegalStateException("prefs unavailable")
        }
        val proof = BluetoothAdapterProof()
        assertFalse(proof.proven(unit, broken))
        assertTrue(proof.record(unit, broken))
        assertTrue(proof.proven(unit, broken))
    }

    @Test fun theKeyIsStableAndDoesNotContainTheAddress() {
        assertEquals(unit, BluetoothAdapterProof.key("board-a", "00:11:22:33:44:51"))
        assertEquals(BluetoothAdapterProof.key("board-a", "AA:BB:CC:DD:EE:FF"),
            BluetoothAdapterProof.key("board-a", "aa:bb:cc:dd:ee:ff"))
        assertEquals(32, unit.length)
        assertTrue(unit.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(unit.contains("001122", ignoreCase = true))
        assertNotEquals(unit, BluetoothAdapterProof.key("board-a", "00:11:22:33:44:52"))
    }
}
