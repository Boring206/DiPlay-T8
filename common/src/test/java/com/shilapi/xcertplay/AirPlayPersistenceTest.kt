package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AirPlayPersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    /** AUDIO_CHANNELS (0..20), the range load/saveNavigationStreamType() validate against. Despite
     * the legacy name, these are not live UI choices: since #64 (v0.2.8) routing uses
     * navigation_audio_channel and DiPlayActivity.channelSelector() is never called. This key is
     * only inherited by loadNavigationAudioChannel(), which clamps out-of-range imports to 0. The
     * two readers fall back differently (14 here, 0 there); save* clamps invalid values to 0 to
     * match the inheritance path. */
    private val uiStreamTypes = 0..20
    private val defaultStreamType = 14

    @Before fun clearPreferences() {
        prefs.edit().clear().apply()
    }

    @Test fun freshInstallUsesDefaultStreamType() {
        assertEquals(defaultStreamType, AirPlayPersistence.loadNavigationStreamType(context))
    }

    /** A backup import writes straight into prefs; an out-of-range value must self-heal on read. */
    @Test fun importedInvalidStreamTypeFallsBackToDefault() {
        for (invalid in listOf(-1, 21, 99999, Int.MIN_VALUE, Int.MAX_VALUE)) {
            prefs.edit().putInt("navigation_stream_type", invalid).apply()
            assertEquals(
                "invalid $invalid should heal to the default",
                defaultStreamType,
                AirPlayPersistence.loadNavigationStreamType(context),
            )
        }
    }

    /** The read-side check must not reject values within AUDIO_CHANNELS. */
    @Test fun importedValidStreamTypeIsPreserved() {
        for (value in uiStreamTypes) {
            prefs.edit().putInt("navigation_stream_type", value).apply()
            assertEquals(value, AirPlayPersistence.loadNavigationStreamType(context))
        }
    }

    @Test fun everyUiStreamTypeRoundTripsThroughSaveAndLoad() {
        for (value in uiStreamTypes) {
            AirPlayPersistence.saveNavigationStreamType(context, value)
            assertEquals(value, AirPlayPersistence.loadNavigationStreamType(context))
            assertEquals(value, prefs.getInt("navigation_stream_type", -1))
        }
    }

    @Test fun saveDoesNotCorruptPreferencesForInvalidStreamType() {
        AirPlayPersistence.saveNavigationStreamType(context, defaultStreamType)
        for (invalid in listOf(-1, 21, 99999)) {
            AirPlayPersistence.saveNavigationStreamType(context, invalid)
            val stored = prefs.getInt("navigation_stream_type", -1)
            assertTrue(
                "stored $stored for invalid $invalid must stay within AUDIO_CHANNELS",
                stored in AirPlayPersistence.AUDIO_CHANNELS,
            )
            assertEquals(0, stored)
        }
    }
}
