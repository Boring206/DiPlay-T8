package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.media.NavigationAudioGain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 29, 33])
class CallAudioAttenuationSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun defaultIsUnchangedAndSavedValuesPersistWithinBounds() {
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(0, AirPlayPersistence.loadCallAudioAttenuationDb(context))
        AirPlayPersistence.saveCallAudioAttenuationDb(context, -18)
        assertEquals(-18, AirPlayPersistence.loadCallAudioAttenuationDb(context))
        AirPlayPersistence.saveCallAudioAttenuationDb(context, -100)
        assertEquals(NavigationAudioGain.MIN_DB, AirPlayPersistence.loadCallAudioAttenuationDb(context))
        AirPlayPersistence.saveCallAudioAttenuationDb(context, 20)
        assertEquals(NavigationAudioGain.MAX_DB, AirPlayPersistence.loadCallAudioAttenuationDb(context))
    }

    @Test fun theCallAndNavigationLevelsAreStoredSeparately() {
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        AirPlayPersistence.saveNavigationAudioAttenuationDb(context, -7)
        AirPlayPersistence.saveCallAudioAttenuationDb(context, -18)
        assertEquals(-7, AirPlayPersistence.loadNavigationAudioAttenuationDb(context))
        assertEquals(-18, AirPlayPersistence.loadCallAudioAttenuationDb(context))
    }

    @Test fun theClearerCallVoicesSettingNoLongerChangesTheCallLevel() {
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        AirPlayPersistence.saveCallVoiceFilter(context, true)
        assertEquals(0, AirPlayPersistence.loadCallAudioAttenuationDb(context))
    }
}
