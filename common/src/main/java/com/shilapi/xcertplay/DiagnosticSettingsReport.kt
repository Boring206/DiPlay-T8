package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.hud.BydCallPopupGuard
import com.shilapi.xcertplay.hud.BydOutputSettings

/**
 * Settings lines for the diagnostic report header that issues needed and reports lacked: call audio
 * and the BYD call popup (#1237), multi-window resolution (#1219) and the dashboard song (#1110).
 * Settings appear under their preference keys, so a report line maps straight to a backup entry.
 * Values after "since app start:" live in memory only: a report saved after a restart shows them reset.
 */
internal object DiagnosticSettingsReport {
    fun lines(context: Context): List<String> = listOf(
        callSettings(context),
        "Multi-window resolution: adapt_pip_resolution=${AirPlayPersistence.loadAdaptPipResolution(context)}",
        BydOutputSettings.clusterSongDiagnosticReport(context),
    )

    fun callSettings(context: Context): String =
        "Call settings: call_echo_cancellation=${AirPlayPersistence.loadCallEchoCancellation(context)} " +
            "phone_audio_route=${BydOutputSettings.phoneAudioRoute(context)} " +
            "call_speakerphone=${AirPlayPersistence.loadCallSpeakerphone(context)} " +
            "hide_byd_call_popup=${AirPlayPersistence.loadHideBydCallPopup(context)} " +
            "bt_suspend_during_carplay=${AirPlayPersistence.loadBtSuspendDuringCarplay(context)} " +
            "bt_suspend_delay_s=${AirPlayPersistence.loadBtSuspendDelaySeconds(context)}; " +
            "since app start: callPopupStatus=${BydCallPopupGuard.status}"
}
