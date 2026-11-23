package com.shilapi.xcertplay.airplay

import android.content.Context

/** Separate opt-ins: the original solid album-color preference keeps its meaning. */
object ClusterSpeedometerArtworkSettings {
    private const val KEY = "cluster_speedometer_artwork_background"
    private const val MOTION_KEY = "cluster_speedometer_artwork_motion"
    private fun prefs(context: Context) = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean(KEY, false)
    fun save(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY, enabled).apply()
    }
    fun motion(context: Context) = prefs(context).getBoolean(MOTION_KEY, false)
    fun saveMotion(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(MOTION_KEY, enabled).apply()
    }
}
