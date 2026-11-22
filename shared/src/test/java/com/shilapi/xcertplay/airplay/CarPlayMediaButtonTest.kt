package com.shilapi.xcertplay.airplay

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayMediaButtonTest {
    @Test
    fun steeringWheelKeysMapToCarPlayMediaPresses() {
        assertEquals(CarPlayMediaButton.NEXT, CarPlayMediaButton.forKeyCode(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(CarPlayMediaButton.PREVIOUS, CarPlayMediaButton.forKeyCode(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        // BYD rewrites its play/pause key into PLAY or PAUSE from the session state; both toggle.
        assertEquals(CarPlayMediaButton.PLAY_PAUSE, CarPlayMediaButton.forKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(CarPlayMediaButton.PLAY_PAUSE, CarPlayMediaButton.forKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(CarPlayMediaButton.PLAY_PAUSE, CarPlayMediaButton.forKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(CarPlayMediaButton.PLAY_PAUSE, CarPlayMediaButton.forKeyCode(KeyEvent.KEYCODE_HEADSETHOOK))
        assertEquals(CarPlayMediaButton.PLAY_PAUSE, CarPlayMediaButton.forKeyCode(353))
        // DiLink 3 (Han, GCC) sends KEYCODE_AUTO_MEDIA_PLAY_PAUSE unchanged.
        assertNull(CarPlayMediaButton.forKeyCode(331))
        assertEquals(CarPlayMediaButton.PLAY_PAUSE,
            CarPlayMediaButton.forKeyCode(331, experimentalDiLink3Keys = true))
    }

    @Test
    fun dilink3CarPlayVoiceKeysOpenSiriOnlyThroughTheWheelKeyService() {
        assertTrue(CarPlayMediaButton.opensSiriWhileCarPlay(327))
        assertTrue(CarPlayMediaButton.opensSiriWhileCarPlay(328))
        assertFalse(CarPlayMediaButton.opensSiriWhileCarPlay(304))
        assertFalse(CarPlayMediaButton.opensSiri(327))
    }

    @Test
    fun theWheelsShortVoiceKeysAreTheCarPlayAndGenericShortCodes() {
        assertTrue(CarPlayMediaButton.isShortVoiceKey(327))
        assertTrue(CarPlayMediaButton.isShortVoiceKey(304))
        assertFalse(CarPlayMediaButton.isShortVoiceKey(328))
        assertFalse(CarPlayMediaButton.isShortVoiceKey(312))
        assertFalse(CarPlayMediaButton.isShortVoiceKey(KeyEvent.KEYCODE_VOICE_ASSIST))
    }

    @Test
    fun theVoiceKeyOpensSiri() {
        // Recorded on DiLink 5.0: short press 304 (scan 290), long press 312 (scan 312).
        assertTrue(CarPlayMediaButton.opensSiri(304))
        assertTrue(CarPlayMediaButton.opensSiri(312))
        assertTrue(CarPlayMediaButton.opensSiri(KeyEvent.KEYCODE_VOICE_ASSIST))
        assertFalse(CarPlayMediaButton.opensSiri(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertNull(CarPlayMediaButton.forKeyCode(304))
    }

    @Test
    fun otherKeysAreLeftToTheSystem() {
        assertNull(CarPlayMediaButton.forKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(CarPlayMediaButton.forKeyCode(KeyEvent.KEYCODE_MEDIA_STOP))
    }

    @Test
    fun indicesMatchTheAdvertisedMediaHidReport() {
        // Media report usages: 0 none, 1 play, 2 pause, 3 play/pause, 4 next, 5 previous; with the seek keys
        // declared, 7 fast forward and 8 rewind.
        assertEquals(3, CarPlayMediaButton.PLAY_PAUSE)
        assertEquals(4, CarPlayMediaButton.NEXT)
        assertEquals(5, CarPlayMediaButton.PREVIOUS)
        assertEquals(7, CarPlayMediaButton.FAST_FORWARD)
        assertEquals(8, CarPlayMediaButton.REWIND)
    }

    // #1190: DiLink 3 names its long-press keys; the numbers come from the head unit's framework.
    private val dilink3 = mapOf("KEYCODE_AUTO_MEDIA_NEXT_LP" to 401, "KEYCODE_AUTO_MEDIA_PREVIOUS_LP" to 402)

    @Test
    fun dilink3LongPressNextAndPreviousSeek() {
        val keys = LongPressSeekKeys { dilink3[it] ?: KeyEvent.KEYCODE_UNKNOWN }
        assertTrue(keys.available)
        assertEquals(CarPlayMediaButton.FAST_FORWARD, keys.seekFor(401))
        assertEquals(CarPlayMediaButton.REWIND, keys.seekFor(402))
        // The tap keys keep their presses, and the long-press keys are no tap.
        assertNull(keys.seekFor(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertNull(keys.seekFor(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertNull(keys.seekFor(KeyEvent.KEYCODE_UNKNOWN))
        assertNull(CarPlayMediaButton.forKeyCode(401))
        assertNull(CarPlayMediaButton.forKeyCode(402, experimentalDiLink3Keys = true))
        assertNull(CarPlayMediaButton.forCarPlayWindow(401, sessionActive = true, inCall = false))
        assertEquals("next=401 previous=402", keys.describe())
    }

    @Test
    fun firmwareThatDoesNotNameTheLongPressKeysSeeksWithNothing() {
        val unnamed = LongPressSeekKeys { KeyEvent.KEYCODE_UNKNOWN }
        assertFalse(unnamed.available)
        for (code in listOf(KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 401)) assertNull(unnamed.seekFor(code))
        // The SDK's stub, or a firmware whose lookup fails, is the same as a name it does not know.
        val failing = LongPressSeekKeys { error("not available") }
        assertFalse(failing.available)
        assertNull(failing.seekFor(401))
        val onlyNext = LongPressSeekKeys { if (it == "KEYCODE_AUTO_MEDIA_NEXT_LP") 401 else KeyEvent.KEYCODE_UNKNOWN }
        assertTrue(onlyNext.available)
        assertEquals(CarPlayMediaButton.FAST_FORWARD, onlyNext.seekFor(401))
        assertNull(onlyNext.seekFor(KeyEvent.KEYCODE_UNKNOWN))
        assertEquals("not named by this firmware", unnamed.describe())
        assertEquals("next=401 previous=none", onlyNext.describe())
    }

    @Test
    fun eachNameIsLookedUpOnce() {
        val asked = mutableListOf<String>()
        val keys = LongPressSeekKeys { asked += it; dilink3[it] ?: KeyEvent.KEYCODE_UNKNOWN }
        assertTrue(asked.isEmpty())
        repeat(5) {
            keys.seekFor(401)
            keys.seekFor(KeyEvent.KEYCODE_MEDIA_NEXT)
            keys.available
        }
        assertEquals(listOf("KEYCODE_AUTO_MEDIA_NEXT_LP", "KEYCODE_AUTO_MEDIA_PREVIOUS_LP"), asked.sorted())
    }

    @Test
    fun theWindowClaimsTrackKeysOnlyWhileCarPlayIsRunning() {
        fun claim(keyCode: Int, sessionActive: Boolean = true, inCall: Boolean = false) =
            CarPlayMediaButton.forCarPlayWindow(keyCode, sessionActive, inCall)

        assertEquals(CarPlayMediaButton.NEXT, claim(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(CarPlayMediaButton.PREVIOUS, claim(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        // Without a session, or during a call, the wheel keeps the car's own action.
        assertNull(claim(KeyEvent.KEYCODE_MEDIA_NEXT, sessionActive = false))
        assertNull(claim(KeyEvent.KEYCODE_MEDIA_PREVIOUS, sessionActive = false))
        assertNull(claim(KeyEvent.KEYCODE_MEDIA_NEXT, inCall = true))
        assertNull(claim(KeyEvent.KEYCODE_MEDIA_PREVIOUS, inCall = true))
    }

    @Test
    fun theWindowLeavesPlayPauseAndUnrelatedKeysAlone() {
        // BYD acts on play/pause itself, and the wheel key service owns it for DiLink 3. Claiming it
        // in the window too would take it away from the car for every driver.
        for (keyCode in listOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, 353, 331,
            KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_VOLUME_UP)) {
            assertNull("keyCode=$keyCode", CarPlayMediaButton.forCarPlayWindow(keyCode, true, false))
        }
    }
}
