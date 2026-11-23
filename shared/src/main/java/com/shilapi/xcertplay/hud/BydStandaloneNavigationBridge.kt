package com.shilapi.xcertplay.hud

import android.content.Context
import android.util.Log
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal object BydStandaloneNavigationBridge {
    private const val TAG = "DiPlay-Standalone"
    private val lock = Any()
    private val route = BydHudRouteState()
    private var context: Context? = null
    private var output: BydStandaloneHudOutput? = null
    private var started = false
    private var shown: String? = null
    private var failing = false

    fun initialize(appContext: Context) = synchronized(lock) {
        context = appContext.applicationContext
        if (output == null) {
            output = BydStandaloneHudOutput.create(appContext)
            if (output != null) {
                NavigationOutputLog.note(TAG, "standalone.receiver",
                    NavigationOutputLines.standaloneReady(BydStandaloneHudOutput.RECEIVER_PACKAGE))
            }
        }
        if (output != null && !started) {
            started = true
            Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "diplay-standalone-navi").apply { isDaemon = true }
            }.scheduleWithFixedDelay(::tick, 0, 250, TimeUnit.MILLISECONDS)
        }
    }

    fun onFrame(frame: Iap2Frame) = synchronized(lock) {
        route.accept(frame.messageId, frame.payload)
        Unit
    }

    fun clear() = synchronized(lock) {
        route.clear() // A keepalive must never resurrect ended guidance.
        output?.clear()
        Unit
    }

    private fun tick() = synchronized(lock) {
        if (BydStandaloneHudOutput.syntheticHold) return@synchronized
        try {
            val frame = if (context?.let(BydOutputSettings::enabled) == true)
                route.currentApple()?.let(BydClusterFrame::from) else null
            val line = context?.takeIf(BydOutputSettings::hudSong)
                ?.let { BydClusterSong.current() }?.takeIf { it.playing }?.line
            // Directions own the shared row. Text-only mode clears previous maneuver records first.
            val showing: String? = when {
                frame != null -> {
                    output?.update(frame.icon, frame.roundaboutExit, frame.distanceMeters, frame.road)
                    "guidance"
                }
                line != null -> {
                    output?.showText(line)
                    "song text"
                }
                else -> {
                    output?.clear()
                    null
                }
            }
            if (failing) {
                failing = false
                NavigationOutputLog.note(TAG, "standalone.error", NavigationOutputLines.standaloneRecovered())
            }
            if (output != null && showing != shown) {
                // Starts cleared; only what is shown or cleared afterwards is a change.
                NavigationOutputLog.note(TAG, "standalone.output",
                    showing?.let(NavigationOutputLines::standaloneShows) ?: NavigationOutputLines.standaloneCleared())
                shown = showing
            }
        } catch (error: Exception) {
            Log.w(TAG, "HUD update/cleanup will retry", error)
            failing = true
            NavigationOutputLog.note(TAG, "standalone.error", NavigationOutputLines.standaloneFailed(error), logcat = false)
        }
    }
}
