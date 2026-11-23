package com.shilapi.xcertplay.airplay

import android.content.Context
import android.content.SharedPreferences

/** Independent from the existing map/content and overlay preferences; map remains the default. */
object ClusterPlayerSettings {
    private const val MODE = "cluster_player_enabled"
    private const val SPEED = "cluster_player_speed"
    private const val VEHICLE = "cluster_player_vehicle"
    private const val FIT_VISIBLE = "cluster_player_fit_visible"
    private fun prefs(context: Context) = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)
    fun selected(context: Context) = prefs(context).getBoolean(MODE, false)
    fun enabled(context: Context) = selected(context) && prefs(context).getBoolean("cluster_map_enabled", false)
    fun speed(context: Context) = prefs(context).getBoolean(SPEED, false)
    fun vehicle(context: Context) = prefs(context).getBoolean(VEHICLE, false)
    fun fitVisible(context: Context) = prefs(context).getBoolean(FIT_VISIBLE, true)
    fun save(context: Context, selected: Boolean) { prefs(context).edit().putBoolean(MODE, selected).apply() }
    fun saveSpeed(context: Context, show: Boolean) { prefs(context).edit().putBoolean(SPEED, show).apply() }
    fun saveVehicle(context: Context, show: Boolean) { prefs(context).edit().putBoolean(VEHICLE, show).apply() }
    fun saveFitVisible(context: Context, fit: Boolean) { prefs(context).edit().putBoolean(FIT_VISIBLE, fit).apply() }
    fun observe(context: Context, changed: () -> Unit): () -> Unit {
        val preferences = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("cluster_player_") || key == "cluster_map_enabled") changed()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
