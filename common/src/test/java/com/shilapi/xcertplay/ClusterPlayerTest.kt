package com.shilapi.xcertplay

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.os.Looper
import android.os.SystemClock
import android.text.StaticLayout
import android.view.View
import com.shilapi.xcertplay.airplay.ClusterPlayerSettings
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.hud.ClusterVehicleReading
import java.io.File
import java.time.Duration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
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
class ClusterPlayerTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    @Before fun clear() {
        context.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        context.getSharedPreferences("diplay", 0).edit().clear().commit()
        ClusterMusicState.publish(CarPlayNowPlaying(), null); idle()
    }
    @Test fun switchingWholePagesKeepsExistingMapPreferencesAndReleasesSpeedReader() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val map = View(activity.get())
        val dashboard = ClusterDashboardView(activity.get(), map)
        activity.get().setContentView(dashboard)
        ClusterMusicSettings.saveEnabled(context, true)
        AirPlayPersistence.saveClusterSpeedometerEnabled(context, true)
        AirPlayPersistence.saveClusterMapEnabled(context, true)
        assertFalse(ClusterPlayerSettings.selected(context))
        assertFalse(ClusterPlayerSettings.speed(context))
        assertFalse(ClusterPlayerSettings.vehicle(context))
        ClusterPlayerSettings.save(context, true); idle()
        assertEquals(View.GONE, map.visibility)
        assertEquals(View.VISIBLE, dashboard.getChildAt(1).visibility)
        assertTrue(ClusterMusicSettings.enabled(context))
        assertTrue(AirPlayPersistence.loadClusterSpeedometerEnabled(context))
        ClusterPlayerSettings.save(context, false); idle()
        assertEquals(View.VISIBLE, map.visibility)
        assertEquals(View.GONE, dashboard.getChildAt(1).visibility)
        activity.pause().stop().destroy()
    }
    @Test fun speedExpiresToUnknownAndCallbacksCannotReviveAfterDetach() {
        ClusterPlayerSettings.saveSpeed(context, true)
        AirPlayPersistence.saveClusterMapEnabled(context, true)
        ClusterPlayerSettings.save(context, true)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        var callback: ((Int?) -> Unit)? = null
        var stopped = 0
        val view = ClusterPlayerView(activity.get()) { listener -> callback = listener; { stopped++ } }
        activity.get().setContentView(view); idle()
        callback!!(72); idle()
        assertEquals(72, ReflectionHelpers.getField<Int>(view, "speedKmh"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertNull(ReflectionHelpers.getField<Int?>(view, "speedKmh"))
        val old = callback!!
        ClusterPlayerSettings.saveSpeed(context, false); idle()
        old(99); idle()
        assertNull(ReflectionHelpers.getField<Int?>(view, "speedKmh"))
        assertEquals(1, stopped)
        ClusterPlayerSettings.saveSpeed(context, true); idle()
        val last = callback!!
        activity.pause().stop().destroy(); last(88); idle()
        assertNull(ReflectionHelpers.getField<Int?>(view, "speedKmh"))
        assertEquals(2, stopped)
    }
    @Test fun legacyPlayerStaysInsideExistingHalfWidthProjectionBounds() {
        AirPlayPersistence.saveClusterMapEnabled(context,true)
        ClusterPlayerSettings.save(context,true)
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val dashboard=ClusterDashboardView(activity.get(),View(activity.get()),legacyBounds=true)
        activity.get().setContentView(dashboard)
        dashboard.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY))
        dashboard.layout(0,0,1920,720)
        val params=dashboard.getChildAt(1).layoutParams as android.widget.FrameLayout.LayoutParams
        assertEquals(960,params.width); assertEquals(360,params.height)
        assertEquals(480,params.leftMargin); assertEquals(180,params.topMargin)
        activity.pause().stop().destroy()
    }

    @Test fun hiddenProjectionLayerStopsSpeedReaderAndResumesWhenShown() {
        ClusterPlayerSettings.saveSpeed(context, true)
        AirPlayPersistence.saveClusterMapEnabled(context,true)
        ClusterPlayerSettings.save(context,true)
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        var starts=0; var stops=0
        val parent=android.widget.FrameLayout(activity.get())
        val player=ClusterPlayerView(activity.get()) { starts++; { stops++ } }
        parent.addView(player)
        activity.get().setContentView(parent); idle()
        assertEquals(1,starts)
        parent.visibility=View.INVISIBLE; idle()
        assertEquals(1,stops)
        parent.visibility=View.VISIBLE; idle()
        assertEquals(2,starts)
        activity.pause().stop().destroy()
        assertEquals(2,stops)
    }

    @Test fun progressSurvivesArtworkUpdatesStopsOnPauseAndClampsToDuration() {
        var state = ClusterMusicState.Snapshot()
        val stop = ClusterMusicState.observe { state = it }
        val song = CarPlayNowPlaying(title = "Song", elapsedMillis = 30_000, durationMillis = 90_000, playing = true)
        ClusterMusicState.publish(song, null); idle()
        val start = state.positionAt
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        ClusterMusicState.publish(song, Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)); idle()
        assertEquals(start, state.positionAt)
        assertEquals(35_000L, ClusterPlayerView.elapsed(state, SystemClock.elapsedRealtime()))
        ClusterMusicState.publish(song.copy(elapsedMillis = 35_000, playing = false), null); idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(35_000L, ClusterPlayerView.elapsed(state, SystemClock.elapsedRealtime()))
        ClusterMusicState.publish(song.copy(elapsedMillis = 88_000), null); idle()
        assertEquals(90_000L, ClusterPlayerView.elapsed(state, state.positionAt + 10_000))
        ClusterMusicState.publish(CarPlayNowPlaying(), null); idle()
        assertNull(ClusterPlayerView.elapsed(state, SystemClock.elapsedRealtime()))
        assertNull(state.artwork)
        stop()
    }
    @Test fun realPlayerCapturesWithSpeedAndMissingMetadata() {
        ClusterPlayerSettings.saveSpeed(context, true)
        AirPlayPersistence.saveClusterMapEnabled(context, true)
        ClusterPlayerSettings.save(context, true)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        var speed: ((Int?) -> Unit)? = null
        val player = ClusterPlayerView(activity.get()) { listener -> speed = listener; {} }
        activity.get().setContentView(player)
        val art = Bitmap.createBitmap(384,384,Bitmap.Config.ARGB_8888)
        Canvas(art).apply {
            val paint = Paint().apply { shader = LinearGradient(0f,0f,384f,384f,
                intArrayOf(Color.rgb(244,131,89),Color.rgb(157,63,116),Color.rgb(33,63,97)),null,Shader.TileMode.CLAMP) }
            drawPaint(paint)
            paint.shader = null; paint.color = Color.WHITE; paint.textSize = 66f
            drawText("DI / PLAY",32f,207f,paint)
        }
        ClusterMusicState.publish(CarPlayNowPlaying(title="Una canción para el camino", artist="Artista de muestra",
            album="Colores en movimiento", elapsedMillis=82_000, durationMillis=245_000, playing=true),art); idle()
        speed!!(72); idle()
        capture(player,1280,480,"player-1280x480")
        capture(player,1920,720,"player-1920x720")
        capture(player,600,720,"player-side-600x720")
        val config=Configuration(player.resources.configuration).apply { fontScale=1.5f }
        @Suppress("DEPRECATION") player.resources.updateConfiguration(config,player.resources.displayMetrics)
        capture(player,1280,480,"player-large-font")
        ClusterMusicState.publish(CarPlayNowPlaying(),null); idle()
        capture(player,1280,480,"player-idle")
        activity.pause().stop().destroy()
    }
    @Test fun paleCoverHasVisibleMotionAndFullCanvasLongTitle() {
        ClusterPlayerSettings.saveSpeed(context, true)
        com.shilapi.xcertplay.airplay.ClusterBackgroundSettings.saveMotion(context,true)
        ClusterPlayerSettings.saveFitVisible(context,false)
        AirPlayPersistence.saveClusterMapEnabled(context,true)
        ClusterPlayerSettings.save(context,true)
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val player=ClusterPlayerView(activity.get()) { listener -> listener(0); {} }
        activity.get().setContentView(player)
        val art=Bitmap.createBitmap(384,384,Bitmap.Config.ARGB_8888)
        Canvas(art).apply {
            drawColor(Color.rgb(238,229,204))
            val ink=Paint().apply { color=Color.rgb(178,53,42); textSize=29f }
            drawRect(140f,125f,245f,250f,Paint().apply { color=Color.rgb(175,68,48) })
            drawText("PALOMINO",102f,290f,ink)
        }
        ClusterMusicState.publish(CarPlayNowPlaying(title="Palomino (From the Netflix Series Nobody Wants This)",
            artist="FINNEAS",album="Palomino — Single",elapsedMillis=112000,durationMillis=190000,playing=true),art);idle()
        capture(player,1280,480,"player-v2-fullscreen")
        val frames=File("build/reports/cluster-player-ui/motion").apply { mkdirs() }
        for (index in 0 until 24) {
            capture(player,640,240,"motion/frame-${index.toString().padStart(2,'0')}")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        }
        val first=android.graphics.BitmapFactory.decodeFile(File(frames,"frame-00.png").path)
        val later=android.graphics.BitmapFactory.decodeFile(File(frames,"frame-12.png").path)
        assertTrue("Background, outside all content, must move", (0 until 640 step 20).any { x -> first.getPixel(x,12) != later.getPixel(x,12) })
        first.recycle();later.recycle()
        activity.pause().stop().destroy()
    }

    @Test fun virtualOnlyRouteDoesNotOfferPlayer() {
        AirPlayPersistence.saveClusterMapEnabled(context,true)
        val activity=Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(context,DiPlayActivity::class.java).putExtra("page","settings")).setup().visible()
        ReflectionHelpers.setField(activity.get(),"settingsCategory",SettingsCategory.ADVANCED)
        ReflectionHelpers.setField(activity.get(),"setupError",null)
        ReflectionHelpers.callInstanceMethod<Unit>(activity.get(),"render"); idle()
        assertNull(findText(activity.get().window.decorView,activity.get().getString(com.shilapi.xcertplay.host.R.string.settings_cluster_player_mode)))
        // A restored/saved selection must always offer an escape back to Map.
        ClusterPlayerSettings.save(context, true)
        ReflectionHelpers.callInstanceMethod<Unit>(activity.get(), "render"); idle()
        val selector = findText(activity.get().window.decorView,
            activity.get().getString(com.shilapi.xcertplay.host.R.string.settings_cluster_player_mode))!!
        assertTrue((selector as android.widget.TextView).text.toString().contains(
            activity.get().getString(com.shilapi.xcertplay.host.R.string.settings_cluster_player)))
        selector.performClick(); idle()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        dialog.listView.performItemClick(null, 0, 0)
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick(); idle()
        assertFalse(ClusterPlayerSettings.selected(context))
        assertFalse(ClusterPlayerSettings.enabled(context))
        assertNull(findText(activity.get().window.decorView,
            activity.get().getString(com.shilapi.xcertplay.host.R.string.settings_cluster_player_mode)))
        activity.pause().stop().destroy()
    }

    @Test fun staticBackgroundIsDefaultAndTitleLayoutIsReusedDuringProgressFrames() {
        assertFalse(com.shilapi.xcertplay.airplay.ClusterBackgroundSettings.motion(context))
        AirPlayPersistence.saveClusterMapEnabled(context,true)
        ClusterPlayerSettings.save(context,true)
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        val player=ClusterPlayerView(activity.get(),observeSpeed={ {} })
        activity.get().setContentView(player)
        val art=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        ClusterMusicState.publish(CarPlayNowPlaying(title="Static",playing=true,elapsedMillis=0,durationMillis=100000),art);idle()
        capture(player,1280,480,"player-static")
        val layout=ReflectionHelpers.getField<StaticLayout>(player,"cachedTitle")
        val first=Bitmap.createBitmap(1280,480,Bitmap.Config.ARGB_8888).also { player.draw(Canvas(it)) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        val later=Bitmap.createBitmap(1280,480,Bitmap.Config.ARGB_8888).also { player.draw(Canvas(it)) }
        assertSame(layout,ReflectionHelpers.getField<StaticLayout>(player,"cachedTitle"))
        for(x in 0 until 1280 step 16) assertEquals(first.getPixel(x,10),later.getPixel(x,10))
        activity.pause().stop().destroy();first.recycle();later.recycle();art.recycle()
    }

    @Test fun diLink3OffersPlayerBeforeItsProjectionDisplayExists() {
        AirPlayPersistence.saveClusterMapEnabled(context,true)
        com.shilapi.xcertplay.setup.DiLinkGeneration.save(context,com.shilapi.xcertplay.setup.DiLinkGeneration.DILINK_3)
        val activity=Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(context,DiPlayActivity::class.java).putExtra("page","settings")).setup().visible()
        ReflectionHelpers.setField(activity.get(),"settingsCategory",SettingsCategory.ADVANCED)
        ReflectionHelpers.setField(activity.get(),"setupError",null)
        ReflectionHelpers.callInstanceMethod<Unit>(activity.get(),"render");idle()
        assertNotNull(findText(activity.get().window.decorView,activity.get().getString(com.shilapi.xcertplay.host.R.string.settings_cluster_player_mode)))
        activity.pause().stop().destroy()
    }

    @Test fun settingsCapturesSpanishCompactFullAndArabic() {
        AirPlayPersistence.saveAdbClusterEnabled(context,true)
        AirPlayPersistence.saveClusterMapEnabled(context,true)
        ClusterPlayerSettings.save(context,true)
        for ((language,width) in listOf("es" to 800,"es" to 1280,"ar" to 1280)) {
            RuntimeEnvironment.setQualifiers("$language-${if(language=="ar") "ldrtl-" else ""}w${width}dp-h1200dp-mdpi")
            val activity=Robolectric.buildActivity(DiPlayActivity::class.java,
                Intent(context,DiPlayActivity::class.java).putExtra("page","settings")).setup().visible()
            ReflectionHelpers.setField(activity.get(),"settingsCategory",SettingsCategory.ADVANCED)
            ReflectionHelpers.setField(activity.get(),"setupError",null)
            val config=Configuration(activity.get().resources.configuration).apply { fontScale=1.5f }
            @Suppress("DEPRECATION") activity.get().resources.updateConfiguration(config,activity.get().resources.displayMetrics)
            ReflectionHelpers.callInstanceMethod<Unit>(activity.get(),"render"); idle()
            val decor = activity.get().window.decorView
            decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1200,View.MeasureSpec.EXACTLY))
            decor.layout(0,0,width,1200)
            val target = findText(decor,activity.get().getString(com.shilapi.xcertplay.host.R.string.settings_cluster_player_mode))
            assertNotNull("Mode selector must be present",target)
            target!!.requestRectangleOnScreen(Rect(0,-80,target.width,650),true)
            idle()
            capture(decor,width,1200,"settings-$language-$width")
            activity.pause().stop().destroy()
        }
    }
    @Test fun vehicleReadoutStopsWhenHiddenExpiresAndIgnoresLateCallbacks() {
        AirPlayPersistence.saveClusterMapEnabled(context,true)
        ClusterPlayerSettings.save(context,true)
        ClusterPlayerSettings.saveVehicle(context,true)
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        var callback: ((ClusterVehicleReading) -> Unit)? = null
        var starts=0;var stops=0
        val player=ClusterPlayerView(activity.get(), observeVehicle={ listener ->
            callback=listener;starts++; { stops++ }
        }, observeSpeed={ {} })
        val parent=android.widget.FrameLayout(activity.get()).apply { addView(player) }
        activity.get().setContentView(parent);idle()
        val art=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888)
        Canvas(art).apply {
            drawColor(Color.rgb(45,90,110))
            drawCircle(160f,160f,110f,Paint().apply { color=Color.rgb(225,132,86) })
            drawText("DI / PLAY",42f,171f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=42f })
        }
        ClusterMusicState.publish(CarPlayNowPlaying(title="Una canción para el camino",artist="Artista de muestra",
            album="Colores en movimiento",elapsedMillis=82_000,durationMillis=245_000,playing=true),art);idle()
        fun reading()=ClusterVehicleReading("P",39.0,15,SystemClock.elapsedRealtime(),SystemClock.elapsedRealtime())
        callback!!(reading());idle()
        assertEquals("P",ReflectionHelpers.getField<ClusterVehicleReading>(player,"vehicle").gear)
        capture(player,1280,480,"player-v3-vehicle")
        val config=Configuration(player.resources.configuration).apply { fontScale=1.5f }
        @Suppress("DEPRECATION") player.resources.updateConfiguration(config,player.resources.displayMetrics)
        capture(player,600,720,"player-v3-vehicle-compact-large-font")
        val rtl=Configuration(player.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag("ar")) }
        @Suppress("DEPRECATION") player.resources.updateConfiguration(rtl,player.resources.displayMetrics)
        capture(player,1280,480,"player-v3-vehicle-arabic-large-font")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertNull(ReflectionHelpers.getField<ClusterVehicleReading>(player,"vehicle").batteryPercent)
        val old=callback!!
        parent.visibility=View.INVISIBLE;idle();old(reading());idle()
        assertNull(ReflectionHelpers.getField<ClusterVehicleReading>(player,"vehicle").gear)
        assertEquals(1,stops)
        parent.visibility=View.VISIBLE;idle();assertEquals(2,starts)
        ClusterPlayerSettings.saveVehicle(context,false);idle()
        callback!!(reading());idle()
        assertNull(ReflectionHelpers.getField<ClusterVehicleReading>(player,"vehicle").gear)
        assertEquals(2,stops)
        activity.pause().stop().destroy()
    }
    @Test fun fittedPlayerKeepsChangingReadoutsOutOfTheNativeStripsAndCanReturnToFullCanvas() {
        ClusterPlayerSettings.saveSpeed(context, true)
        RuntimeEnvironment.setQualifiers("es-w1280dp-h480dp-mdpi")
        AirPlayPersistence.saveClusterMapEnabled(context,true)
        ClusterPlayerSettings.save(context,true)
        ClusterPlayerSettings.saveVehicle(context,true)
        assertTrue(ClusterPlayerSettings.fitVisible(context))
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        var speed: ((Int?) -> Unit)?=null
        var vehicle: ((ClusterVehicleReading) -> Unit)?=null
        val player=ClusterPlayerView(activity.get(),observeVehicle={ vehicle=it;{} },observeSpeed={ speed=it;{} })
        activity.get().setContentView(player)
        val art=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(35,100,128)) }
        val song=CarPlayNowPlaying(title="30",artist="Bo Burnham",album="INSIDE (DELUXE)",
            elapsedMillis=82_000,durationMillis=245_000,playing=false)
        ClusterMusicState.publish(song,art);idle()
        fun render():Bitmap {
            player.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY))
            player.layout(0,0,1280,480)
            return Bitmap.createBitmap(1280,480,Bitmap.Config.ARGB_8888).also { player.draw(Canvas(it)) }
        }
        val before=render()
        ClusterMusicState.publish(song.copy(title="Una canción para el camino",artist="Artista de muestra"),art)
        speed!!(88)
        vehicle!!(ClusterVehicleReading("P",38.0,15,SystemClock.elapsedRealtime(),SystemClock.elapsedRealtime()));idle()
        val after=render()
        var insideChanges=0
        var outsideDelta=0
        for(y in 0 until 480) for(x in 0 until 1280) {
            if(y<86 || y>=365) {
                val a=before.getPixel(x,y);val b=after.getPixel(x,y)
                outsideDelta=maxOf(outsideDelta,kotlin.math.abs(Color.red(a)-Color.red(b)),
                    kotlin.math.abs(Color.green(a)-Color.green(b)),kotlin.math.abs(Color.blue(a)-Color.blue(b)))
            }
            else if(before.getPixel(x,y)!=after.getPixel(x,y)) insideChanges++
        }
        // The gradient may advance a few milliseconds; bright readout pixels must not appear.
        assertTrue("Only the moving background may change behind native strips: delta=$outsideDelta",outsideDelta<=4)
        assertTrue(insideChanges>1000)
        capture(player,1280,480,"player-v4-fit-1280x480")
        ClusterPlayerSettings.saveFitVisible(context,false);idle()
        capture(player,1280,480,"player-v4-full-canvas-option")
        val full=render()
        assertTrue((0 until 1280).any { x -> (400 until 479).any { y -> full.getPixel(x,y)!=after.getPixel(x,y) } })
        before.recycle();after.recycle();full.recycle()
        ClusterPlayerSettings.saveFitVisible(context,true);idle()
        val config=Configuration(player.resources.configuration).apply { fontScale=1.5f }
        @Suppress("DEPRECATION") player.resources.updateConfiguration(config,player.resources.displayMetrics)
        capture(player,1280,480,"player-v4-fit-large-font")
        capture(player,600,720,"player-v4-fit-compact-large-font")
        activity.pause().stop().destroy()
    }
    @Test fun artworkCrossfadesWhilePausedAndReleasesPreviousCover() {
        AirPlayPersistence.saveClusterMapEnabled(context, true)
        ClusterPlayerSettings.save(context, true)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val player = ClusterPlayerView(activity.get(), observeSpeed = { {} })
        activity.get().setContentView(player)
        val red = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val blue = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val song = CarPlayNowPlaying(title = "First", playing = false)
        ClusterMusicState.publish(song,red); idle()
        fun pixel(): Int {
            player.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY))
            player.layout(0,0,1280,480)
            val bitmap = Bitmap.createBitmap(1280,480,Bitmap.Config.ARGB_8888)
            player.draw(Canvas(bitmap))
            val color = bitmap.getPixel(200,200); bitmap.recycle(); return color
        }
        assertEquals(Color.RED,pixel())
        ClusterMusicState.publish(song.copy(title = "Next"),blue); idle()
        val start = pixel() // The UI loop may already have advanced one animation frame.
        assertTrue(Color.red(start) > 235 && Color.blue(start) < 20)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        val middle = pixel()
        assertTrue(Color.red(middle) in 100..155 && Color.blue(middle) in 100..155)
        capture(player,1280,480,"player-v12-cover-transition")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(Color.BLUE,pixel())
        assertNull(ReflectionHelpers.getField<Bitmap?>(player,"previousCover"))
        capture(player,1280,480,"player-v12-settled")
        activity.pause().stop().destroy(); red.recycle(); blue.recycle()
    }

    @Test fun longTitlesDoNotPaintOverThreeDigitSpeed() {
        ClusterPlayerSettings.saveSpeed(context, true)
        RuntimeEnvironment.setQualifiers("es-w1280dp-h480dp-mdpi")
        AirPlayPersistence.saveClusterMapEnabled(context,true)
        ClusterPlayerSettings.save(context,true)
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible()
        var updateSpeed: ((Int?) -> Unit)? = null
        val player=ClusterPlayerView(activity.get()) { listener -> updateSpeed=listener; {} }
        activity.get().setContentView(player)
        val art=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(50,100,160)) }
        val song=CarPlayNowPlaying(title="Breve",artist="Artista",album="Álbum",playing=false)
        ClusterMusicState.publish(song,art);idle();updateSpeed!!(100);idle()
        fun render():Bitmap {
            player.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY))
            player.layout(0,0,1280,480)
            return Bitmap.createBitmap(1280,480,Bitmap.Config.ARGB_8888).also { player.draw(Canvas(it)) }
        }
        val before=render()
        ClusterMusicState.publish(song.copy(title="Un título muy largo que debe ocupar dos líneas y nunca invadir la velocidad",artist="Un artista con un nombre largo"),art);idle()
        val after=render()
        var maxDelta=0
        for(y in 90 until 175) for(x in 1060 until 1200) {
            val a=before.getPixel(x,y);val b=after.getPixel(x,y)
            maxDelta=maxOf(maxDelta,kotlin.math.abs(Color.red(a)-Color.red(b)),kotlin.math.abs(Color.green(a)-Color.green(b)),kotlin.math.abs(Color.blue(a)-Color.blue(b)))
        }
        assertTrue("Metadata must stay outside speed column: $maxDelta",maxDelta<=4)
        capture(player,1280,480,"player-v10-long-title-speed")
        val config=Configuration(player.resources.configuration).apply { fontScale=1.5f }
        @Suppress("DEPRECATION") player.resources.updateConfiguration(config,player.resources.displayMetrics)
        capture(player,1280,480,"player-v10-large-font")
        capture(player,600,720,"player-v10-compact-large-font")
        before.recycle();after.recycle();activity.pause().stop().destroy()
    }
    @Test fun artworkAreaWinsOverSmallSaturatedDetailsAndNeutralsStayNeutral() {
        fun dominant(background: Int, detail: Int): FloatArray {
            val art=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888)
            art.eraseColor(background)
            Canvas(art).drawRect(0f,0f,20f,100f,Paint().apply { color=detail })
            return FloatArray(3).also { Color.colorToHSV(ClusterPlayerView.palette(art)[0],it); art.recycle() }
        }
        val blue=dominant(Color.rgb(90,140,180),Color.RED)
        assertTrue(blue[0] in 195f..215f)
        val gold=dominant(Color.rgb(220,165,45),Color.rgb(80,45,160))
        assertTrue(gold[0] in 35f..50f)
        val gray=dominant(Color.LTGRAY,Color.LTGRAY)
        assertTrue(gray[1]<.01f)
    }
    @Test fun animatedLayersKeepDistinctArtworkHuesInsteadOfThreeNearbyShades() {
        val art=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(art)
        val hues=floatArrayOf(205f,220f,235f,35f)
        hues.forEachIndexed { index, hue ->
            canvas.drawRect(index*30f,0f,(index+1)*30f,120f,Paint().apply {
                color=Color.HSVToColor(floatArrayOf(hue,.65f,.75f))
            })
        }
        val palette=ClusterPlayerView.palette(art)
        assertTrue(palette.any { color ->
            val hsv=FloatArray(3);Color.colorToHSV(color,hsv);hsv[0] in 30f..40f
        })
        art.recycle()
    }
    private fun findText(view:View,value:String):View? {
        if (view is android.widget.TextView && view.text.toString().startsWith(value)) return view
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
            findText(view.getChildAt(index),value)?.let { return it }
        }
        return null
    }
    private fun capture(view: View,w:Int,h:Int,name:String) {
        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
        view.layout(0,0,w,h)
        val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val directory=File("build/reports/cluster-player-ui").apply { mkdirs() }
        File(directory,"$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it)) }
        image.recycle()
    }
}
