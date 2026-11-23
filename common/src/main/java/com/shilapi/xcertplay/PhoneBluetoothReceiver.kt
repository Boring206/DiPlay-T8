package com.shilapi.xcertplay

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log

/** Optional Bluetooth wake-up path for the iPhone the user selected in connection setup. */
class PhoneBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED ||
            !DiPlayPreferences.connectOnPhoneBluetooth(context) ||
            CarPlayBackgroundSession.hasSession()) return
        val selected = DiPlayPreferences.phoneAddress(context) ?: return
        if (Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        } ?: return
        val address = runCatching { device.address }.getOrNull() ?: return
        if (!address.equals(selected, ignoreCase = true)) return

        // The car can send several ACL events during one wake-up. Commit before launching so
        // another receiver instance cannot open a second connection from the same event burst.
        val prefs = context.getSharedPreferences("diplay_phone_wake", Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val previous = prefs.getLong("last_launch_elapsed", 0)
        // elapsedRealtime restarts at boot. Never compare a saved timestamp from another
        // process/boot with this boot's clock, even when the two values happen to be close.
        if (prefs.getString("launch_process", null) == deduplicationProcess &&
            prefs.contains("last_launch_elapsed") && now >= previous && now - previous < 15_000) return
        prefs.edit().putLong("last_launch_elapsed", now)
            .putString("launch_process", deduplicationProcess).commit()
        PhoneWakeDiagnostics.record(context, "matched")
        val launch = Intent(context, DiPlayActivity::class.java).apply {
            putExtra(EXTRA_AUTO_CONNECT, launchToken)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { context.startActivity(launch) }
            .onSuccess { PhoneWakeDiagnostics.record(context, "startActivity-returned") }
            .onFailure { error ->
                // A rejected activity launch must not suppress the next real ACL event.
                prefs.edit().remove("last_launch_elapsed").remove("launch_process").commit()
                PhoneWakeDiagnostics.record(context, "failed", error)
                Log.w("DiPlayPhoneWake", "Bluetooth wake launch failed: ${error.javaClass.simpleName}")
            }
    }

    companion object {
        const val EXTRA_AUTO_CONNECT = "com.shihab.diplay.AUTO_CONNECT_SELECTED_PHONE"
        private val deduplicationProcess: String = java.util.UUID.randomUUID().toString()

        /** DiPlayActivity is exported. Only this process knows the token, so only its own launch can connect. */
        internal val launchToken: String = java.util.UUID.randomUUID().toString()
    }
}

/** Only a timestamp and fixed outcome; the Bluetooth address and broadcast extras stay out of reports. */
internal object PhoneWakeDiagnostics {
    private const val PREFS = "diplay_phone_wake_diagnostics"

    fun record(context: Context, result: String, error: Throwable? = null) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("at", System.currentTimeMillis())
            .putString("result", result)
            .putString("failureClass", error?.javaClass?.simpleName ?: "none")
            .apply()
    }

    fun report(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong("at", 0)
        return if (at == 0L) "Phone Bluetooth wake: no matching event recorded"
        else "Phone Bluetooth wake: matchedAtEpochMs=$at result=${prefs.getString("result", "unknown")} " +
            "failureClass=${prefs.getString("failureClass", "none")}; " +
            "startActivity-returned does not prove the car displayed DiPlay."
    }
}
