package com.shilapi.xcertplay

import android.app.Activity
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.View
import com.shilapi.xcertplay.airplay.ClusterSpeedometerArtworkSettings
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClusterSpeedometerArtworkTest {
    @Before fun reset() {
        RuntimeEnvironment.getApplication().getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        clearMusic()
    }

    @After fun clearMusic() {
        ClusterMusicState.publish(CarPlayNowPlaying(), null)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun motionIsIndependentFromPlayerPreference() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("xcertplay_airplay", 0)
        prefs.edit().putBoolean("cluster_background_motion", true).commit()
        assertFalse(ClusterSpeedometerArtworkSettings.motion(app))
        ClusterSpeedometerArtworkSettings.saveMotion(app, true)
        prefs.edit().putBoolean("cluster_background_motion", false).commit()
        assertTrue(ClusterSpeedometerArtworkSettings.motion(app))
        ClusterSpeedometerArtworkSettings.saveMotion(app, false)
        assertFalse(prefs.getBoolean("cluster_background_motion", true))
    }

    @Test fun motionClockOnlyAdvancesWhileItIsActive() {
        val clock = ClusterArtworkMotionClock()
        assertEquals(0, clock.time(100, true))
        assertEquals(200, clock.time(300, true))
        assertEquals(200, clock.time(300, false))
        assertEquals(200, clock.time(30_300, false))
        assertEquals(200, clock.time(30_300, true))
        assertEquals(500, clock.time(30_600, true))
    }

    @Test fun pausedMusicKeepsTheGradientStillWhenSpeedRefreshes() {
        val app = RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveClusterSpeedometerEnabled(app, true)
        ClusterSpeedometerArtworkSettings.save(app, true)
        ClusterSpeedometerArtworkSettings.saveMotion(app, true)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val view = ClusterSpeedometerView(activity.get()) { {} }
        activity.get().setContentView(view)
        val art = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(220, 70, 40))
            Canvas(this).drawRect(20f, 0f, 44f, 64f, Paint().apply { color = Color.rgb(35, 130, 220) })
        }
        fun frame(speed: Int): Bitmap {
            view.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1280, 480)
            view.setSpeedKmh(speed)
            return Bitmap.createBitmap(1280, 480, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        ClusterMusicState.publish(CarPlayNowPlaying(title = "Track", playing = true), art)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        frame(100).recycle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        ClusterMusicState.publish(CarPlayNowPlaying(title = "Track", playing = false), art)
        shadowOf(Looper.getMainLooper()).idle()
        val paused = frame(100)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        val afterSpeedRefresh = frame(101)
        val rect = ReflectionHelpers.getField<RectF>(view, "rect")
        val x = (rect.left + rect.width() * .12f).toInt()
        val y = (rect.top + rect.height() * .12f).toInt()
        assertEquals(paused.getPixel(x, y), afterSpeedRefresh.getPixel(x, y))
        activity.pause().stop().destroy()
        paused.recycle(); afterSpeedRefresh.recycle(); art.recycle()
    }

    @Test fun missingArtworkFadesToAndFromSolidAndUnitPaintHasContrast() {
        val app = RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveClusterSpeedometerEnabled(app, true)
        AirPlayPersistence.saveClusterSpeedometerOpacity(app, 100)
        ClusterSpeedometerArtworkSettings.save(app, true)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val view = ClusterSpeedometerView(activity.get()) { {} }
        activity.get().setContentView(view)
        val art = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        fun draw(): Bitmap {
            view.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1280, 480)
            view.setSpeedKmh(100)
            return Bitmap.createBitmap(1280, 480, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val empty = draw()
        ClusterMusicState.publish(CarPlayNowPlaying(title = "White", playing = true), art)
        shadowOf(Looper.getMainLooper()).idle()
        // Native drawing can advance Robolectric's frame clock. Pin the zero-progress sample.
        ReflectionHelpers.setField(view, "artworkChangedAt", android.os.SystemClock.elapsedRealtime() + 100)
        val start = draw()
        assertTrue(empty.sameAs(start))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        val middle = draw()
        assertFalse(empty.sameAs(middle))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        val full = draw()
        assertFalse(middle.sameAs(full))
        val unit = ReflectionHelpers.getField<Paint>(view, "unitPaint")
        assertEquals(Color.rgb(220, 220, 225), unit.color)
        val background = ClusterArtworkBackground().apply { setArtwork(art, 0) }
        val sample = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        for (time in 0L..45000L step 1500L) {
            background.drawWideCrop(Canvas(sample), 240f, 240f, time, true)
            for (y in 0 until 240 step 8) for (x in 0 until 240 step 8) {
                assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(unit.color, sample.getPixel(x, y)) >= 4.5)
            }
        }
        ClusterMusicState.publish(CarPlayNowPlaying(), null)
        shadowOf(Looper.getMainLooper()).idle()
        ReflectionHelpers.setField(view, "artworkChangedAt", android.os.SystemClock.elapsedRealtime() + 100)
        val exitStart = draw()
        assertTrue(full.sameAs(exitStart))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        val exitMiddle = draw()
        assertFalse(full.sameAs(exitMiddle))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        val end = draw()
        assertTrue(empty.sameAs(end))
        activity.pause().stop().destroy()
        clearMusic()
        listOf(empty, start, middle, full, sample, exitStart, exitMiddle, end, art).forEach { it.recycle() }
    }

    @Test fun newOptInPreservesSolidColorAndOpacityAndStopsMotionWhenOutputIsHidden() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        assertFalse(ClusterSpeedometerArtworkSettings.enabled(app))
        assertFalse(ClusterSpeedometerArtworkSettings.motion(app))
        AirPlayPersistence.saveClusterSpeedometerEnabled(app, true)
        AirPlayPersistence.saveClusterSpeedometerAlbumColor(app, true)
        AirPlayPersistence.saveClusterSpeedometerOpacity(app, 40)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val view = ClusterSpeedometerView(activity.get()) { {} }
        activity.get().setContentView(view)
        val art = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) }
        ClusterMusicState.publish(CarPlayNowPlaying(title = "Track", playing = true), art)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        view.layout(0, 0, 1280, 480)
        view.setSpeedKmh(100)
        val image = Bitmap.createBitmap(1280, 480, Bitmap.Config.ARGB_8888)
        fun draw() {
            view.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1280, 480)
            image.eraseColor(Color.TRANSPARENT)
            view.draw(Canvas(image))
        }
        draw()
        assertEquals(Color.BLACK, ReflectionHelpers.getField<Paint>(view, "speedPaint").color)
        val solid = image.copy(Bitmap.Config.ARGB_8888, false)
        ClusterSpeedometerArtworkSettings.save(app, true)
        shadowOf(Looper.getMainLooper()).idle()
        draw()
        val gradient = image.copy(Bitmap.Config.ARGB_8888, false)
        assertFalse(solid.sameAs(gradient))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        draw()
        assertTrue(gradient.sameAs(image))
        val rect = ReflectionHelpers.getField<RectF>(view, "rect")
        assertEquals(102, Color.alpha(image.getPixel((rect.left + rect.width() * .2f).toInt(), rect.centerY().toInt())))
        val handler = ReflectionHelpers.getField<Handler>(view, "main")
        val frame = ReflectionHelpers.getField<Runnable>(view, "frame")
        ClusterSpeedometerArtworkSettings.saveMotion(app, true)
        shadowOf(Looper.getMainLooper()).idle()
        draw()
        assertTrue(handler.hasCallbacks(frame))
        ClusterOutputVisibility.update(view, false)
        draw()
        assertFalse(handler.hasCallbacks(frame))
        ClusterOutputVisibility.update(view, true)
        draw()
        assertTrue(handler.hasCallbacks(frame))
        ClusterSpeedometerArtworkSettings.save(app, false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        draw()
        val directory = java.io.File("build/reports/cluster-speedometer-artwork").apply { mkdirs() }
        java.io.File(directory, "solid-before.png").outputStream().use { solid.compress(Bitmap.CompressFormat.PNG, 100, it) }
        java.io.File(directory, "solid-after.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(solid.sameAs(image))
        assertTrue(AirPlayPersistence.loadClusterSpeedometerAlbumColor(app))
        assertEquals(Color.BLACK, ReflectionHelpers.getField<Paint>(view, "speedPaint").color)
        activity.pause().stop().destroy()
        assertFalse(handler.hasCallbacks(frame))
        clearMusic()
        image.recycle()
        solid.recycle()
        gradient.recycle()
        art.recycle()
    }
}
