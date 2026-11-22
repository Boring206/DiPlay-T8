package com.shilapi.xcertplay.hud

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The arrival time in the windshield HUD's field 26 ([BydHudPayload.guidance]'s `eta`), Denza N9 only (#301).
 *
 * With the field empty, the N9 HUD shows "到" ("arrive") on its own. A photo of a stock N9 HUD shows
 * "13:18到", so the HUD seems to add the "到" itself and to want the bare clock time. That form is not
 * confirmed on the car: if it needs another one, such as AMap's "预计13:18到达", change [TEMPLATE].
 *
 * Every other car keeps the empty field 26 it has always had. Neither DiPlay nor DiAuto has sent another
 * car a non-empty field 26, so nothing shows how those HUDs would draw it.
 */
object BydHudArrival {
    /** What the HUD gets; `%s` is the arrival clock time ("14:36", or "2:36" on a 12-hour head unit). */
    internal const val TEMPLATE = "%s"

    /**
     * The same N9 firmware (OTA V1.8 and V1.9) as `DiLink51ClusterLayout.DENZA_N9_FINGERPRINTS` in :common;
     * a :common test keeps the two equal, so the HUD and cluster gates cannot drift apart. Exact builds
     * only: an N9 OTA that isn't listed here gets the empty field 26 again.
     */
    const val DENZA_N9_FINGERPRINT = "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260705.011226:user/release-keys" // OTA V1.8
    const val DENZA_N9_V19_FINGERPRINT = "BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20260902.221210:user/release-keys" // OTA V1.9
    val DENZA_N9_FINGERPRINTS = setOf(DENZA_N9_FINGERPRINT, DENZA_N9_V19_FINGERPRINT)

    internal fun enabled(fingerprint: String? = Build.FINGERPRINT): Boolean = fingerprint in DENZA_N9_FINGERPRINTS

    /**
     * The field text: the iPhone's arrival time when it sends one, otherwise the route time left added to
     * the time it was received ([remainingAtMillis], or [nowMillis] when unknown), otherwise empty.
     * Counting from the receive time keeps the shown time from creeping later between 0x06 updates.
     */
    internal fun text(
        nowMillis: Long,
        arrivalEpochSeconds: Long?,
        remainingSeconds: Long?,
        zone: TimeZone,
        use24Hour: Boolean,
        remainingAtMillis: Long? = null,
    ): String {
        val arrivalMillis = when {
            arrivalEpochSeconds != null && arrivalEpochSeconds > 0 -> arrivalEpochSeconds * 1000L
            remainingSeconds != null && remainingSeconds >= 0 -> (remainingAtMillis ?: nowMillis) + remainingSeconds * 1000L
            else -> return ""
        }
        val clock = SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm", Locale.US).apply { timeZone = zone }
        return String.format(Locale.US, TEMPLATE, clock.format(Date(arrivalMillis)))
    }

    /**
     * The guidance payload [BydHudBridge] sends. With [arrivalEnabled] false it is byte for byte the
     * 0.2.22 payload (empty field 26) and [BydHudOutgoing.arrival] is null; with it true, field 26 carries
     * [text] and [BydHudOutgoing.arrival] the same value for the report.
     */
    internal fun payloadFor(
        guidance: BydHudGuidance,
        icon: ByteArray?,
        arrivalEnabled: Boolean,
        nowMillis: Long,
        zone: TimeZone,
        use24Hour: Boolean,
    ): BydHudOutgoing {
        val arrival = if (arrivalEnabled) {
            text(nowMillis, guidance.arrivalEpochSeconds, guidance.remainingSeconds, zone, use24Hour, guidance.remainingAtMillis)
        } else {
            null
        }
        val payload = BydHudPayload.guidance(
            distanceMeters = guidance.distanceMeters,
            maneuver = guidance.maneuver,
            icon = icon,
            road = guidance.road,
            eta = arrival.orEmpty(),
        )
        return BydHudOutgoing(payload, arrival)
    }
}

/** A guidance payload and the field-26 text it carries; [arrival] is null where field 26 is not used. */
internal class BydHudOutgoing(val payload: ByteArray, val arrival: String?)

/**
 * When the windshield HUD writes its "accepted guidance" report line: at the first accepted payload after
 * each show, and once more on the N9 when field 26 first carries a time, because the first route update
 * may come without an arrival epoch or time left (#301).
 */
internal class BydHudGuidanceNote {
    private var noted = false
    private var arrivalNoted = false

    /** The line to write for an accepted payload carrying [arrival], or null when it would say nothing new. */
    fun accepted(arrival: String?): String? {
        val hasArrival = !arrival.isNullOrEmpty()
        if (noted && (arrivalNoted || !hasArrival)) return null
        noted = true
        arrivalNoted = hasArrival
        return NavigationOutputLines.hudGuidanceAccepted(arrival)
    }

    /** The HUD was cleared or lost its gateway; the next accepted payload is a new show. */
    fun reset() {
        noted = false
        arrivalNoted = false
    }
}
