package com.shilapi.xcertplay.hud

import android.bluetooth.BluetoothProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarMusicLinkGuardTest {
    private val phone = "44:A7:F4:3D:E9:17"

    private class FakeJournal(var address: String? = null, var writable: Boolean = true) : CarMusicLinkJournal {
        override fun dropped(): String? = address
        override fun write(address: String?): Boolean {
            if (!writable) return false
            this.address = address
            return true
        }
    }

    private class FakeLink(
        var up: List<String>? = emptyList(),
        var disconnectResult: Boolean? = true,
        var connectResult: Boolean? = true,
        var paired: Boolean? = true,
    ) : CarMusicLink {
        val disconnected = mutableListOf<String>()
        val connectedAgain = mutableListOf<String>()
        override fun connected(): List<String>? = up
        override fun disconnect(address: String): Boolean? {
            disconnected += address
            return disconnectResult
        }
        override fun connect(address: String): Boolean? {
            connectedAgain += address
            return connectResult
        }
        override fun bonded(address: String): Boolean? = paired
    }

    @Test
    fun onlyThisSessionsIphoneCountsAsItsLinkComingUp() {
        assertTrue(CarMusicLinkGuard.linkUp(BluetoothProfile.STATE_CONNECTED, phone, phone))
        assertTrue(CarMusicLinkGuard.linkUp(BluetoothProfile.STATE_CONNECTING, phone.lowercase(), phone))
        assertFalse(CarMusicLinkGuard.linkUp(BluetoothProfile.STATE_CONNECTED, "11:22:33:44:55:66", phone))
        assertFalse(CarMusicLinkGuard.linkUp(BluetoothProfile.STATE_DISCONNECTED, phone, phone))
        assertFalse(CarMusicLinkGuard.linkUp(BluetoothProfile.STATE_CONNECTED, null, phone))
        assertFalse(CarMusicLinkGuard.linkUp(BluetoothProfile.STATE_CONNECTED, phone, null))
    }

    @Test
    fun theSwitchOffCarBluetoothAudioAndAnUnknownIphoneLeaveTheLinkAlone() {
        assertEquals("off in Settings", CarMusicLinkGuard.skipReason(enabled = false, carBluetoothAudio = false, address = phone))
        // With CarPlay audio over the car's Bluetooth, the music plays over this very link.
        assertTrue(CarMusicLinkGuard.skipReason(enabled = true, carBluetoothAudio = true, address = phone)!!
            .contains("CarPlay audio plays over the car's Bluetooth"))
        assertTrue(CarMusicLinkGuard.skipReason(enabled = true, carBluetoothAudio = false, address = null)!!
            .contains("no iPhone known"))
        assertNull(CarMusicLinkGuard.skipReason(enabled = true, carBluetoothAudio = false, address = phone))
    }

    @Test
    fun anUpLinkIsDroppedAfterTheRestoreNoteIsSaved() {
        val journal = FakeJournal()
        val link = FakeLink(up = listOf(phone.lowercase()))
        val line = CarMusicLinkGuard(journal).drop(link, phone)
        assertEquals(listOf(phone), link.disconnected)
        assertEquals(phone, journal.address)
        assertTrue(line.startsWith("car Bluetooth music dropped"))
    }

    @Test
    fun anotherDevicesLinkIsLeftAlone() {
        val journal = FakeJournal()
        val link = FakeLink(up = listOf("11:22:33:44:55:66"))
        assertTrue(CarMusicLinkGuard(journal).drop(link, phone).contains("nothing to drop"))
        assertTrue(link.disconnected.isEmpty())
        assertNull(journal.address)
    }

    @Test
    fun aRefusedOrMissingCallIsReportedAndLeavesNothingToRestore() {
        val refused = FakeJournal()
        val refusedLine = CarMusicLinkGuard(refused).drop(FakeLink(up = listOf(phone), disconnectResult = false), phone)
        assertTrue(refusedLine.contains("refused"))
        assertNull(refused.address)

        val missing = FakeJournal()
        val missingLine = CarMusicLinkGuard(missing).drop(FakeLink(up = listOf(phone), disconnectResult = null), phone)
        assertTrue(missingLine.contains("unavailable"))
        assertNull(missing.address)
    }

    @Test
    fun noDropWithoutTheRestoreNote() {
        val link = FakeLink(up = listOf(phone))
        val line = CarMusicLinkGuard(FakeJournal(writable = false)).drop(link, phone)
        assertTrue(link.disconnected.isEmpty())
        assertTrue(line.contains("restore note could not be saved"))
    }

    @Test
    fun theRestoreConnectsOnlyTheDroppedLinkOfAStillPairedIphone() {
        val journal = FakeJournal(address = phone)
        val link = FakeLink(up = emptyList())
        val line = CarMusicLinkGuard(journal).restore(link)
        assertEquals(listOf(phone), link.connectedAgain)
        assertNull(journal.address)
        assertEquals("car Bluetooth music reconnect requested", line)

        // Nothing dropped: nothing to do and nothing reported.
        val untouched = FakeLink()
        assertNull(CarMusicLinkGuard(FakeJournal()).restore(untouched))
        assertTrue(untouched.connectedAgain.isEmpty())
    }

    @Test
    fun theRestoreSkipsALinkAlreadyBackOrAnIphoneNoLongerPaired() {
        val back = FakeLink(up = listOf(phone))
        val backJournal = FakeJournal(address = phone)
        assertTrue(CarMusicLinkGuard(backJournal).restore(back)!!.contains("up again"))
        assertTrue(back.connectedAgain.isEmpty())
        assertNull(backJournal.address)

        val unpaired = FakeLink(paired = false)
        val unpairedJournal = FakeJournal(address = phone)
        assertTrue(CarMusicLinkGuard(unpairedJournal).restore(unpaired)!!.contains("no longer paired"))
        assertTrue(unpaired.connectedAgain.isEmpty())
        assertNull(unpairedJournal.address)
    }

    @Test
    fun anUnreadableStateOrARefusedReconnectKeepsTheNoteForTheNextAppOpen() {
        val unknown = FakeJournal(address = phone)
        assertTrue(CarMusicLinkGuard(unknown).restore(FakeLink(paired = null))!!.contains("postponed"))
        assertEquals(phone, unknown.address)

        val noProfile = FakeJournal(address = phone)
        assertTrue(CarMusicLinkGuard(noProfile).restore(FakeLink(up = null))!!.contains("postponed"))
        assertEquals(phone, noProfile.address)

        val refused = FakeJournal(address = phone)
        val line = CarMusicLinkGuard(refused).restore(FakeLink(up = emptyList(), connectResult = false))
        assertTrue(line!!.contains("reconnect refused"))
        // Kept: the iPhone rarely brings the link back by itself, so the next app open tries again.
        assertEquals(phone, refused.address)
    }

    @Test
    fun aMissingConnectCallClearsTheNoteInsteadOfRetryingForever() {
        // Android 9 on an ECARX KX11: disconnect() is callable, connect() is hidden from apps.
        val journal = FakeJournal(address = phone)
        val link = FakeLink(up = emptyList(), connectResult = null)
        val line = CarMusicLinkGuard(journal).restore(link)
        assertTrue(line!!.contains("can't be reconnected on this head unit"))
        assertNull(journal.address)
    }

    @Test
    fun aNoteLeftForAnotherIphoneIsHandedBackBeforeTheNewDrop() {
        val other = "11:22:33:44:55:66"
        val journal = FakeJournal(address = other)
        val link = FakeLink(up = listOf(phone))
        val line = CarMusicLinkGuard(journal).drop(link, phone)
        assertEquals(listOf(other), link.connectedAgain)
        assertEquals(listOf(phone), link.disconnected)
        assertEquals(phone, journal.address)
        assertTrue(line.startsWith("car Bluetooth music dropped"))

        // The old note can't be handed back yet: it stays, and this session leaves the link alone.
        val stuck = FakeJournal(address = other)
        val refusing = FakeLink(up = listOf(phone), connectResult = false)
        assertTrue(CarMusicLinkGuard(stuck).drop(refusing, phone).contains("another iPhone's link is still noted"))
        assertTrue(refusing.disconnected.isEmpty())
        assertEquals(other, stuck.address)
    }
}
