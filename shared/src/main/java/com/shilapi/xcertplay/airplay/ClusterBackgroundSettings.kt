package com.shilapi.xcertplay.airplay

import android.content.Context
import android.content.SharedPreferences

/** Continuous drift is opt-in; track-change crossfades are independent. */
object ClusterBackgroundSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)
    fun motion(context: Context) = prefs(context).getBoolean("cluster_background_motion", false)
    fun saveMotion(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("cluster_background_motion", enabled).apply() }
    fun observe(context: Context, changed: () -> Unit): () -> Unit {
        val preferences = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "cluster_background_motion") changed()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
