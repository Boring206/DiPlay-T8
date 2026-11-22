package com.shilapi.xcertplay

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.airplay.LongPressSeekKeys
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CarPlayMediaCallbackTest {
    private val sent = mutableListOf<Int>()
    private val callback = CarPlayMediaCallback { index, _ -> sent += index }

    @Test
    fun controllerPlayAndPauseAreExplicit() {
        callback.onPlay()
        callback.onPause()
        callback.onSkipToNext()
        callback.onSkipToPrevious()

        assertEquals(
            listOf(CarPlayMediaButton.PLAY, CarPlayMediaButton.PAUSE, CarPlayMediaButton.NEXT, CarPlayMediaButton.PREVIOUS),
            sent,
        )
    }

    @Test
    fun hardwarePlayAndPauseKeysToggle() {
        press(KeyEvent.KEYCODE_MEDIA_PLAY)
        press(KeyEvent.KEYCODE_MEDIA_PAUSE)
        press(CarPlayMediaButton.KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE)

        assertEquals(List(3) { CarPlayMediaButton.PLAY_PAUSE }, sent)
    }

    @Test fun experimentalPlayPauseKeyNeedsOptInAndStopsAfterDisable() {
        var enabled = false
        val experimental = CarPlayMediaCallback(experimentalDiLink3Keys = { enabled }) { index, _ -> sent += index }
        val key = button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, 331, 0))
        experimental.onMediaButtonEvent(key)
        assertEquals(emptyList<Int>(), sent)
        enabled = true
        experimental.onMediaButtonEvent(key)
        assertEquals(listOf(CarPlayMediaButton.PLAY_PAUSE), sent)
        enabled = false
        experimental.onMediaButtonEvent(key)
        assertEquals(listOf(CarPlayMediaButton.PLAY_PAUSE), sent)
    }

    // #610: a car's own code for a wheel key must show in the report, not look like no key at all.
    @Test fun aButtonCarPlayDoesNotUseIsNotedOncePerPress() {
        val notUsed = mutableListOf<String>()
        val callback = CarPlayMediaCallback(notUsed = { notUsed += it }) { index, _ -> sent += index }
        val skip = KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD
        callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, skip, 0)))
        callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, skip, 1)))
        callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_UP, skip, 0)))
        callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 0)))
        callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, 331, 0)))
        callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0)))

        assertEquals(listOf(skip, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 331).map(KeyEvent::keyCodeToString), notUsed)
        assertEquals(listOf(CarPlayMediaButton.NEXT), sent)
    }

    // #1190: whether a car sends a long press at its threshold or at the release.
    @Test fun anUnusedKeysReleaseSaysWhenItCameAfterItsDownTime() {
        val released = mutableListOf<Pair<String, String>>()
        val callback = CarPlayMediaCallback(notUsedReleased = { name, timing -> released += name to timing }) { index, _ -> sent += index }
        val skip = KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD
        val name = KeyEvent.keyCodeToString(skip)
        // An instant down/up pair.
        callback.onMediaButtonEvent(button(KeyEvent(1_000, 1_000, KeyEvent.ACTION_DOWN, skip, 0)))
        callback.onMediaButtonEvent(button(KeyEvent(1_000, 1_004, KeyEvent.ACTION_UP, skip, 0)))
        // Held for four seconds, repeating.
        callback.onMediaButtonEvent(button(KeyEvent(2_000, 2_000, KeyEvent.ACTION_DOWN, skip, 0)))
        callback.onMediaButtonEvent(button(KeyEvent(2_000, 2_500, KeyEvent.ACTION_DOWN, skip, 1)))
        callback.onMediaButtonEvent(button(KeyEvent(2_000, 2_600, KeyEvent.ACTION_DOWN, skip, 2)))
        callback.onMediaButtonEvent(button(KeyEvent(2_000, 6_200, KeyEvent.ACTION_UP, skip, 0)))
        // Sent at the threshold as a pair dated from the press.
        callback.onMediaButtonEvent(button(KeyEvent(7_000, 7_800, KeyEvent.ACTION_DOWN, skip, 0)))
        callback.onMediaButtonEvent(button(KeyEvent(7_000, 7_801, KeyEvent.ACTION_UP, skip, 0)))
        // A release with no press seen, and a used key's release, add nothing.
        callback.onMediaButtonEvent(button(KeyEvent(9_000, 9_000, KeyEvent.ACTION_UP, skip, 0)))
        callback.onMediaButtonEvent(button(KeyEvent(9_000, 9_000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0)))
        callback.onMediaButtonEvent(button(KeyEvent(9_000, 9_900, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0)))

        assertEquals(listOf(
            name to "; released at +0 s",
            name to "; released at +4.2 s, 2 repeats",
            name to "; released at +0.8 s, down event at +0.8 s",
        ), released)
        assertEquals(listOf(CarPlayMediaButton.NEXT), sent)
    }

    private val dilink3 = mapOf("KEYCODE_AUTO_MEDIA_NEXT_LP" to 401, "KEYCODE_AUTO_MEDIA_PREVIOUS_LP" to 402)

    @Test fun longPressKeysSeekOnlyWithTheSettingOn() {
        var enabled = false
        val seeks = mutableListOf<Triple<Int, Int, Int>>()
        val notUsed = mutableListOf<String>()
        val callback = CarPlayMediaCallback(
            longPressSeek = { enabled },
            seekKeys = LongPressSeekKeys { dilink3[it] ?: KeyEvent.KEYCODE_UNKNOWN },
            seek = { index, _, event -> seeks += Triple(index, event.action, event.repeatCount) },
            notUsed = { notUsed += it },
        ) { index, _ -> sent += index }
        fun longPress(code: Int) {
            callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0)))
            callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0)))
        }

        // Off, as in 0.2.22: the report names the key and nothing is sent.
        longPress(401)
        assertEquals(listOf(KeyEvent.keyCodeToString(401)), notUsed)
        assertEquals(emptyList<Triple<Int, Int, Int>>(), seeks)

        enabled = true
        longPress(401)
        longPress(402)
        callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, 401, 1)))
        assertEquals(listOf(
            Triple(CarPlayMediaButton.FAST_FORWARD, KeyEvent.ACTION_DOWN, 0),
            Triple(CarPlayMediaButton.FAST_FORWARD, KeyEvent.ACTION_UP, 0),
            Triple(CarPlayMediaButton.REWIND, KeyEvent.ACTION_DOWN, 0),
            Triple(CarPlayMediaButton.REWIND, KeyEvent.ACTION_UP, 0),
            Triple(CarPlayMediaButton.FAST_FORWARD, KeyEvent.ACTION_DOWN, 1),
        ), seeks)
        assertEquals(1, notUsed.size)
        // Next and previous are still taps.
        assertEquals(emptyList<Int>(), sent)
        callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0)))
        assertEquals(listOf(CarPlayMediaButton.NEXT), sent)
        assertEquals(5, seeks.size)
    }

    @Test fun onAHeadUnitThatDoesNotNameTheLongPressKeysTheSettingChangesNothing() {
        val seeks = mutableListOf<Int>()
        val callback = CarPlayMediaCallback(
            longPressSeek = { true },
            seekKeys = LongPressSeekKeys { KeyEvent.KEYCODE_UNKNOWN },
            seek = { index, _, _ -> seeks += index },
        ) { index, _ -> sent += index }
        for (code in listOf(KeyEvent.KEYCODE_UNKNOWN, 401, KeyEvent.KEYCODE_MEDIA_NEXT)) {
            callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0)))
        }
        assertEquals(emptyList<Int>(), seeks)
        assertEquals(listOf(CarPlayMediaButton.NEXT), sent)
    }

    @Test
    fun aHeldKeySendsOnePress() {
        press(KeyEvent.KEYCODE_MEDIA_NEXT, repeat = 1)
        callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0)))

        assertEquals(listOf(CarPlayMediaButton.NEXT), sent)
    }

    @Test
    fun nowPlayingFieldsBecomeAndroidMediaMetadata() {
        val artwork = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val metadata = CarPlayMediaKeys.androidMetadata(
            CarPlayNowPlaying(
                title = "Dreams",
                album = "Rumours",
                artist = "Fleetwood Mac",
                sourceApp = "Music",
                durationMillis = 257_000,
            ),
            artwork,
        )

        assertEquals("Dreams", metadata.getString(MediaMetadata.METADATA_KEY_TITLE))
        assertEquals("Dreams", metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
        assertEquals("Fleetwood Mac", metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))
        assertEquals("Fleetwood Mac", metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
        assertEquals("Rumours", metadata.getString(MediaMetadata.METADATA_KEY_ALBUM))
        assertEquals("Music", metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION))
        assertEquals(257_000, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION))
        assertEquals(artwork, metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
        assertEquals(artwork, metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))
    }

    @Test
    fun aPendingArtworkTransferKeepsThePreviousArt() {
        val previous = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val cached = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        assertSame(previous, CarPlayMediaKeys.nextArtwork(7, emptyMap(), previous))
        assertSame(cached, CarPlayMediaKeys.nextArtwork(7, mapOf(7 to cached), previous))
        assertNull(CarPlayMediaKeys.nextArtwork(7, mapOf(7 to null), previous))
        assertNull(CarPlayMediaKeys.nextArtwork(null, mapOf(7 to cached), previous))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun wideArtworkIsPaddedToASquareNotStretched() {
        val wide = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val square = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)

        val padded = CarPlayMediaKeys.squareArtwork(wide)

        assertEquals(384, padded.width)
        assertEquals(384, padded.height)
        assertEquals(Color.TRANSPARENT, padded.getPixel(192, 0))
        assertEquals(Color.RED, padded.getPixel(192, 192))
        assertEquals(Color.TRANSPARENT, padded.getPixel(192, 383))
        assertSame(square, CarPlayMediaKeys.squareArtwork(square))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun smallOrTallArtworkIsPaddedWithoutUpscaling() {
        val small = CarPlayMediaKeys.squareArtwork(Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888))
        val tall = CarPlayMediaKeys.squareArtwork(
            Bitmap.createBitmap(100, 1000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) },
        )

        assertEquals(200, small.width)
        assertEquals(200, small.height)
        assertEquals(384, tall.width)
        assertEquals(384, tall.height)
        assertEquals(Color.TRANSPARENT, tall.getPixel(0, 192))
        assertEquals(Color.RED, tall.getPixel(192, 192))
    }

    @Test
    fun thePlaceholderRastersAtArtworkSize() {
        val placeholder = CarPlayMediaKeys.placeholderArt(RuntimeEnvironment.getApplication())

        assertEquals(384, placeholder?.width)
        assertEquals(384, placeholder?.height)
    }

    private fun press(keyCode: Int, repeat: Int = 0) {
        for (count in 0..repeat) {
            callback.onMediaButtonEvent(button(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, count)))
        }
    }

    private fun button(event: KeyEvent) = Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, event)

    @Test
    fun positionAndPlayStateDoNotRepublishMetadata() {
        val song = CarPlayNowPlaying(title = "Song", artist = "Artist", artworkTransferId = 7, elapsedMillis = 1_000, playing = true)
        assertEquals(false, CarPlayMediaKeys.metadataChanged(song, song.copy(elapsedMillis = 1_450)))
        assertEquals(false, CarPlayMediaKeys.metadataChanged(song, song.copy(playing = false)))
        assertEquals(true, CarPlayMediaKeys.metadataChanged(song, song.copy(title = "Next")))
        assertEquals(true, CarPlayMediaKeys.metadataChanged(song, song.copy(artworkTransferId = 8)))
        assertEquals(true, CarPlayMediaKeys.metadataChanged(CarPlayNowPlaying(), song))
    }
}
