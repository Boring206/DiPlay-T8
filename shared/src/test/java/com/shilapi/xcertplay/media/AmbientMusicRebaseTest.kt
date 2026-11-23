package com.shilapi.xcertplay.media

import android.content.Context
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** #1332: a lamp hold always ends with a new CarPlay session or a DiPlay Settings change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AmbientMusicRebaseTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun prefs() = context.getSharedPreferences("ambient_music", Context.MODE_PRIVATE)

    @Test fun newCarPlaySessionAndSettingsChangeRebaseTheLamp() {
        prefs().edit().clear().putBoolean("enabled", true).commit()
        val reasons = mutableListOf<String>()
        val real = AmbientMusicController.rebaseLamp
        AmbientMusicController.rebaseLamp = { reasons.add(it) }
        val sink = AmbientMusicController.openSink(context)
        try {
            settle()
            // The test needs no lamp updates; it only records what the controller asks of the lamp session.
            (field("ticking") as ScheduledFuture<*>?)?.cancel(false)
            assertEquals(listOf("new CarPlay session"), reasons)
            AmbientMusicController.settingsChanged(context)
            settle()
            assertEquals(listOf("new CarPlay session", "DiPlay settings changed"), reasons)
            // Turning ambient off restores the lamp instead; there is nothing to rebase.
            prefs().edit().putBoolean("enabled", false).commit()
            AmbientMusicController.settingsChanged(context)
            settle()
            assertEquals(2, reasons.size)
        } finally {
            AmbientMusicController.rebaseLamp = real
            AmbientMusicController.closeSink(sink)
            prefs().edit().clear().commit()
            AmbientMusicController.settingsChanged(context)
            settle()
        }
    }

    private fun settle() {
        (field("executor") as ExecutorService).submit {}.get(2, TimeUnit.SECONDS)
    }

    private fun field(name: String): Any? = AmbientMusicController::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(AmbientMusicController)
}
