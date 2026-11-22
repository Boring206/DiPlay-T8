package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.adb.LocalAdb

/**
 * Whether the car is in P, read through the adb shell (autoservice binder) where apps would need a
 * BYD signature. The id is BYD SDK 1.0.5's gearbox device: 1 P, 2 R, 3 N, 4 D.
 */
internal object BydParkedState {
    const val GEAR = "service call autoservice 5 i32 1011 i32 555745336"
    private const val PARK = 1

    private val shell = BydAdbShell("DiPlay-BYD-Parked")

    /** Why the latest [parked] read returned null; null after a read that worked. */
    @Volatile var lastUnreadable: GearUnreadable? = null
        private set

    /** Null when the gear cannot be read (no ADB over network, or another car). Blocking. */
    fun parked(context: Context): Boolean? {
        val output = shell.run(context, BydWheelSpeed.gearCommand(context))
        return parked(output).also { parked ->
            lastUnreadable = if (parked != null) null else GearUnreadable(shell.lastAccess, replied = output != null)
        }
    }

    fun parked(output: String?): Boolean? = BydParcel.value(output)?.takeIf { it in 1..6 }?.let { it == PARK }
}

/**
 * Why the car's gear could not be read (#1180): DiPlay's ADB access on the head unit at the latest try,
 * and whether the gear command answered. Video while parked stays off while the gear is unreadable.
 */
data class GearUnreadable(val adb: LocalAdb.Access?, val replied: Boolean) {
    /** For the report, such as "adb=NOT_APPROVED" or "adb=READY reply=unreadable". */
    val report: String
        get() = when {
            adb == null -> "adb=not-tried"
            adb != LocalAdb.Access.READY -> "adb=$adb"
            replied -> "adb=READY reply=unreadable"
            else -> "adb=READY reply=none"
        }
}
