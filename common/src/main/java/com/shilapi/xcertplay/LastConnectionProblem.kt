package com.shilapi.xcertplay

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateUtils
import android.provider.Settings
import androidx.annotation.StringRes
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.network.WirelessStartupFailure
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import com.shilapi.xcertplay.orchestration.ConnectionFailureCause
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal sealed interface ConnectionProblem {
    data class Cause(val cause: ConnectionFailureCause) : ConnectionProblem
    data class Startup(val failure: WirelessStartupFailure) : ConnectionProblem
    data class UsbNotListed(val otherDevice: Boolean) : ConnectionProblem

    @StringRes
    fun line(): Int = when (this) {
        is Cause -> ConnectionFailureGuidance.stage(cause)
        is Startup -> when (failure) {
            WirelessStartupFailure.HOTSPOT_NOT_READY,
            WirelessStartupFailure.HOTSPOT_CONFIGURATION -> R.string.hotspot_network_not_ready
            WirelessStartupFailure.FIRST_TCP_TIMEOUT -> R.string.first_tcp_timeout
            WirelessStartupFailure.WIFI_DIRECT_BESIDE_HOTSPOT -> R.string.wifi_direct_refused_beside_hotspot
            WirelessStartupFailure.BLUETOOTH_UNUSABLE -> R.string.connection_help_bluetooth_unusable
        }
        is UsbNotListed -> if (otherDevice) R.string.car_sees_usb_device_not_iphone else R.string.car_sees_no_usb_device
    }

    val token: String get() = when (this) {
        is Cause -> "cause:${cause.name}"
        is Startup -> "startup:${failure.name}"
        is UsbNotListed -> if (otherDevice) "usb:other" else "usb:none"
    }
}

/**
 * Video while parked is on, but the gear cannot be read, so video stays off (#1180). Kept apart from
 * connection problems: it belongs to a running session, and neither record replaces the other. Each value is
 * what the driver can do about it, resolved once when recorded so a later report says what was true then.
 */
internal enum class VideoGearProblem {
    NOT_APPROVED, ADB_OFF, ADB_NETWORK_NOT_ANSWERING, PAIRING_ONLY, GEAR_UNREADABLE, NO_REPLY, NOT_CHECKED;

    @StringRes
    fun hint(): Int = when (this) {
        NOT_APPROVED -> R.string.adb_not_approved
        ADB_OFF -> R.string.adb_off
        ADB_NETWORK_NOT_ANSWERING -> R.string.adb_network_not_answering
        PAIRING_ONLY -> R.string.adb_pairing_only
        GEAR_UNREADABLE -> R.string.adb_vehicle_gear_unreadable
        // The gate's own gear read got no reply; the driver ran no check.
        NO_REPLY -> R.string.video_gear_no_reply
        NOT_CHECKED -> R.string.adb_not_checked
    }

    val token: String get() = "video:gear:$name"

    companion object {
        fun of(context: Context, adb: LocalAdb.Access?, replied: Boolean): VideoGearProblem = when (adb) {
            LocalAdb.Access.NOT_APPROVED -> NOT_APPROVED
            LocalAdb.Access.UNREACHABLE ->
                // #1002: the developer-options switch can stay on while nothing listens on 5555.
                if (runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) }
                        .getOrDefault(0) == 1) ADB_NETWORK_NOT_ANSWERING else ADB_OFF
            LocalAdb.Access.UNSUPPORTED -> PAIRING_ONLY
            LocalAdb.Access.READY -> if (replied) GEAR_UNREADABLE else NO_REPLY
            null -> NOT_CHECKED
        }

        fun parse(token: String): VideoGearProblem? =
            token.removePrefix("video:gear:").takeIf { it != token }?.let { name -> entries.firstOrNull { it.name == name } }
    }
}

internal object LastConnectionProblem {
    data class Record(val problem: ConnectionProblem, val atMillis: Long)
    data class VideoRecord(val problem: VideoGearProblem, val atMillis: Long)

    private const val PREFS = "diplay_last_problem"
    private const val KEY_PROBLEM = "problem"
    private const val KEY_AT = "at"
    // #1180: video that stays off has its own keys, so follow(), clear() and a connection record never touch it.
    private const val KEY_VIDEO = "video"
    private const val KEY_VIDEO_AT = "video_at"

    fun follow(context: Context, status: CarPlayStatus) {
        val problem = when (status) {
            is CarPlayStatus.Failed -> status.knownCause?.let(ConnectionProblem::Cause)
                ?: status.startupFailure?.let(ConnectionProblem::Startup)
                ?: return clear(context)
            is CarPlayStatus.IphoneNotListed -> ConnectionProblem.UsbNotListed(status.nonHubDeviceListed)
            else -> return
        }
        prefs(context).edit().putString(KEY_PROBLEM, problem.token).putLong(KEY_AT, System.currentTimeMillis()).apply()
    }

    fun clear(context: Context) = prefs(context).edit().remove(KEY_PROBLEM).remove(KEY_AT).apply()

    /** Video while parked stays off because the gear cannot be read (#1180). Gate thread; written on a change only. */
    fun videoGearUnreadable(context: Context, adb: LocalAdb.Access?, replied: Boolean) {
        val problem = VideoGearProblem.of(context, adb, replied)
        if (prefs(context).getString(KEY_VIDEO, null) == problem.token) return
        prefs(context).edit().putString(KEY_VIDEO, problem.token).putLong(KEY_VIDEO_AT, System.currentTimeMillis()).apply()
    }

    /** The gear reads again (in a session or in Vehicle → Check ADB access), or the setting was turned off. */
    fun clearVideoGear(context: Context) = prefs(context).edit().remove(KEY_VIDEO).remove(KEY_VIDEO_AT).apply()

    fun load(context: Context): Record? {
        val prefs = prefs(context)
        val token = prefs.getString(KEY_PROBLEM, null) ?: return null
        val problem = parse(token)
        val at = prefs.getLong(KEY_AT, -1)
        if (problem == null || at < 0) {
            clear(context)
            return null
        }
        return Record(problem, at)
    }

    /** Only while Video while parked runs; a record kept while the setting was off is forgotten, not shown later. */
    fun loadVideo(context: Context): VideoRecord? {
        val prefs = prefs(context)
        val token = prefs.getString(KEY_VIDEO, null) ?: return null
        val problem = VideoGearProblem.parse(token)
        val at = prefs.getLong(KEY_VIDEO_AT, -1)
        if (problem == null || at < 0 || !BydOutputSettings.videoWhileParked(context)) {
            clearVideoGear(context)
            return null
        }
        // A vehicle mode without a gear reading runs no gate; hide the record until the gate runs again.
        if (!BydOutputSettings.videoWhileParkedActive(context)) return null
        return VideoRecord(problem, at)
    }

    /**
     * While CarPlay runs only a problem of the running session shows: video that stays off. Otherwise the last
     * connection attempt and, with the time it was recorded, video that stayed off.
     */
    fun homeLine(context: Context, sessionActive: Boolean = false): String? {
        val video = loadVideo(context)
        val videoText = video?.let { context.getString(R.string.video_off_gear_unreadable) + " " + context.getString(it.problem.hint()) }
        if (sessionActive) return videoText
        val record = load(context)
        val lines = listOfNotNull(
            record?.let { context.getString(R.string.last_connection_attempt, shortTime(it.atMillis), context.getString(it.problem.line())) },
            video?.let { context.getString(R.string.last_carplay_connection_problem, shortTime(it.atMillis), videoText) },
        )
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun shortTime(atMillis: Long): String =
        DateUtils.formatSameDayTime(atMillis, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()

    fun report(context: Context): String {
        val english = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) },
        ).resources
        fun at(millis: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(millis))
        val record = load(context)
        val connection = if (record == null) "Last problem: none recorded"
            else "Last problem: ${english.getString(record.problem.line())} (${record.problem.token}) at ${at(record.atMillis)}"
        val video = loadVideo(context) ?: return connection
        return connection + "\nVideo in car problem: " + english.getString(R.string.video_off_gear_unreadable) + " " +
            english.getString(video.problem.hint()) + " (${video.problem.token}) at ${at(video.atMillis)}"
    }

    private fun parse(token: String): ConnectionProblem? {
        val value = token.substringAfter(':')
        return when (token.substringBefore(':')) {
            "cause" -> ConnectionFailureCause.entries.firstOrNull { it.name == value }?.let(ConnectionProblem::Cause)
            "startup" -> WirelessStartupFailure.entries.firstOrNull { it.name == value }?.let(ConnectionProblem::Startup)
            "usb" -> when (value) {
                "none" -> ConnectionProblem.UsbNotListed(false)
                "other" -> ConnectionProblem.UsbNotListed(true)
                else -> null
            }
            else -> null
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
