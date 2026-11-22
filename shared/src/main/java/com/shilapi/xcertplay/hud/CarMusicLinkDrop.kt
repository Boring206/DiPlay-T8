package com.shilapi.xcertplay.hud

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/** The car's Bluetooth music link (A2DP sink), as far as the drop needs it. */
internal interface CarMusicLink {
    /** Addresses whose music link is up, or null while the profile can't be read. */
    fun connected(): List<String>?
    /** true accepted, false refused, null when the platform has no such call. */
    fun disconnect(address: String): Boolean?
    fun connect(address: String): Boolean?
    /** Whether the device is still paired with the car, or null when that can't be read. */
    fun bonded(address: String): Boolean?
}

/** The device whose music link DiPlay dropped, kept across an app kill until it is restored. */
internal interface CarMusicLinkJournal {
    fun dropped(): String?
    fun write(address: String?): Boolean
}

/** What to do and what the report says; no Android calls, so every branch is testable. */
internal class CarMusicLinkGuard(private val journal: CarMusicLinkJournal) {

    /** Drops this session's link if it is up, and returns the report line. */
    fun drop(link: CarMusicLink, address: String): String {
        val up = link.connected() ?: return "car Bluetooth music drop skipped: the car's music profile can't be read"
        if (up.none { it.equals(address, ignoreCase = true) }) {
            return "car Bluetooth music not connected for this session's iPhone; nothing to drop yet"
        }
        // One note at a time: a link still noted for another iPhone is handed back before it is overwritten.
        val stale = runCatching { journal.dropped() }.getOrNull()
        val staleLine = if (stale != null && !stale.equals(address, ignoreCase = true)) restore(link) else null
        if (runCatching { journal.dropped() }.getOrNull() != null && staleLine != null) {
            return "$staleLine; car Bluetooth music drop skipped: another iPhone's link is still noted"
        }
        // commit() first: an app kill after the call must leave the note that brings the link back.
        if (!runCatching { journal.write(address) }.getOrDefault(false)) {
            return "car Bluetooth music drop skipped: the restore note could not be saved"
        }
        return when (link.disconnect(address)) {
            true -> "car Bluetooth music dropped for this session's iPhone until CarPlay ends"
            false -> {
                clear()
                "car Bluetooth music drop refused by the head unit; CarPlay music may pause"
            }
            null -> {
                clear()
                "car Bluetooth music drop unavailable on this head unit; CarPlay music may pause"
            }
        }
    }

    /** Brings back only a link DiPlay dropped. Returns the report line, or null when nothing was dropped. */
    fun restore(link: CarMusicLink): String? {
        val address = runCatching { journal.dropped() }.getOrNull() ?: return null
        when (link.bonded(address)) {
            false -> {
                clear()
                return "car Bluetooth music not restored: the iPhone is no longer paired with the car"
            }
            null -> return "car Bluetooth music restore postponed: Bluetooth can't be read; DiPlay retries when it opens"
            true -> Unit
        }
        val up = link.connected()
            ?: return "car Bluetooth music restore postponed: the car's music profile can't be read; DiPlay retries when it opens"
        if (up.any { it.equals(address, ignoreCase = true) }) {
            clear()
            return "car Bluetooth music back: the link is up again"
        }
        return when (link.connect(address)) {
            true -> {
                clear()
                "car Bluetooth music reconnect requested"
            }
            // Android 9 on an ECARX KX11 lets apps call disconnect() but not connect(): retrying can't help.
            null -> {
                clear()
                "car Bluetooth music can't be reconnected on this head unit; it comes back when the iPhone " +
                    "reconnects to the car's Bluetooth"
            }
            // The iPhone rarely brings back a link the car dropped, so a refused request keeps the note for a retry.
            false -> "car Bluetooth music reconnect refused; DiPlay tries again when it opens"
        }
    }

    private fun clear() {
        runCatching { journal.write(null) }
    }

    companion object {
        /** Why this session leaves the car's music link alone, or null to drop it. */
        fun skipReason(enabled: Boolean, carBluetoothAudio: Boolean, address: String?): String? = when {
            !enabled -> "off in Settings"
            carBluetoothAudio -> "car Bluetooth music left on: CarPlay audio plays over the car's Bluetooth"
            address == null -> "car Bluetooth music left on: no iPhone known for this session"
            else -> null
        }

        /** Whether a state change of [device] means the car's music link to [phone] is (coming) up. */
        fun linkUp(state: Int, device: String?, phone: String?): Boolean =
            phone != null && device.equals(phone, ignoreCase = true) &&
                (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING)
    }
}

/**
 * Optional: while a CarPlay session runs, drop the car's own Bluetooth music link (A2DP sink) to this
 * session's iPhone, and again each time the car brings it back.
 *
 * In the stock Android 9 sink, an A2DP stream that starts while the sink holds no audio focus gets an
 * AVRCP pause straight away, so the iPhone pauses CarPlay music (#877, #1343). Only the music link goes;
 * calls (HFP) and the pairing stay. Some seconds after the session ends, unless a new session took over,
 * only a link DiPlay dropped is connected again, if the iPhone is still paired. A note saved before each drop brings the link back after an app kill.
 * The calls are hidden platform methods; a head unit that refuses them is reported, not retried. Android 9
 * on an ECARX KX11 allows disconnect() but hides connect(), so there the link returns only when the iPhone
 * reconnects to the car's Bluetooth.
 */
object CarMusicLinkDrop {
    private const val TAG = "DiPlay-CarMusicLink"
    private const val PREFS = "diplay_car_music_link"
    private const val KEY_DROPPED = "dropped_address"
    /** BluetoothProfile.A2DP_SINK, hidden from the public SDK. */
    private const val PROFILE_A2DP_SINK = 11
    /** BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED, hidden from the public SDK. */
    private const val ACTION_STATE = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED"
    /** The car may report the link several times in a row; one try per burst. */
    private const val DEBOUNCE_MILLIS = 500L
    /**
     * A wireless session that reconnects comes back within seconds; restoring at once would hand the next
     * session a link that pauses its music before it can be dropped again.
     */
    private const val RESTORE_DELAY_MILLIS = 20_000L
    /** A profile proxy that never answers must not hold the queue. */
    private const val PROXY_TIMEOUT_MILLIS = 5_000L

    private val main by lazy { Handler(Looper.getMainLooper()) }

    // Main thread only.
    private var owner: Any? = null
    private var address: String? = null
    private var log: (String) -> Unit = {}
    private var proxy: BluetoothProfile? = null
    private var proxyRequested = false
    private val waiting = mutableListOf<(BluetoothProfile?) -> Unit>()
    private var receiver: BroadcastReceiver? = null
    private var unavailable = false // the platform has no disconnect(); not asked again in this process
    private var app: Context? = null
    private var restoreLog: (String) -> Unit = {}
    private var restorePending = false
    private val drop = Runnable { withProxy { dropWith(it) } }
    private val restoreLater = Runnable { restore(restoreLog) }
    // The request stays outstanding: a late proxy is kept for the next call instead of asking twice.
    private val proxyTimeout = Runnable { if (proxy == null && waiting.isNotEmpty()) flush(null) }

    /**
     * Drops the link for [owner]'s session if [enabled]. [address] is this session's iPhone: the device the
     * wireless iAP2 link was opened to, or the iPhone chosen in DiPlay for a wired session.
     */
    fun start(
        context: Context,
        owner: Any,
        enabled: Boolean,
        carBluetoothAudio: Boolean,
        address: String?,
        stillActive: () -> Boolean,
        log: (String) -> Unit,
    ) {
        val appContext = context.applicationContext
        main.post {
            app = appContext
            // A late start behind its own stop must not leave a drop that nothing ends.
            if (!stillActive()) return@post
            val skip = CarMusicLinkGuard.skipReason(enabled, carBluetoothAudio, address)
                ?: if (unavailable) "car Bluetooth music drop unavailable on this head unit; CarPlay music may pause" else null
            if (skip != null) {
                if (enabled) log(skip)
                // This session leaves the link alone: end any earlier drop, also one of a replaced controller,
                // and hand back a link a killed session left dropped.
                if (this.owner != null) end(log) else scheduleRestore(log)
                return@post
            }
            main.removeCallbacks(restoreLater)
            restorePending = false
            if (this.owner === owner && this.address.equals(address, ignoreCase = true)) {
                scheduleDrop()
                return@post
            }
            // A replacement controller takes over; the saved note stays, so its restore still runs at the end.
            unregister()
            this.owner = owner
            this.address = address
            this.log = log
            register(appContext)
            withProxy { if (this.owner === owner) dropWith(it) }
        }
    }

    /** Ends [owner]'s drop and later connects the link again if DiPlay dropped it; an older owner can't end a newer one. */
    fun stop(context: Context, owner: Any, log: (String) -> Unit) {
        val appContext = context.applicationContext
        main.post {
            if (this.owner !== owner) return@post
            app = appContext
            end(log)
        }
    }

    /** Brings back a link left dropped by a session that ended without a clean close (app kill, crash). */
    fun restoreIfNeeded(context: Context) {
        val appContext = context.applicationContext
        main.post {
            app = appContext
            // A restore already queued by a session end keeps its timer and its report logger.
            if (owner != null || restorePending) return@post
            scheduleRestore { Log.i(TAG, it) }
        }
    }

    private fun end(log: (String) -> Unit) {
        owner = null
        address = null
        this.log = {}
        unregister()
        restorePending = false
        scheduleRestore(log)
    }

    private fun scheduleRestore(log: (String) -> Unit) {
        val appContext = app ?: return
        if (restorePending || journal(appContext).dropped() == null) return
        restorePending = true
        restoreLog = log
        main.postDelayed(restoreLater, RESTORE_DELAY_MILLIS)
    }

    private fun restore(log: (String) -> Unit) {
        restorePending = false
        restoreLog = {} // drops the ended controller held by its logger
        val appContext = app ?: return
        if (owner != null) return // a new session took the link over meanwhile
        if (journal(appContext).dropped() == null) {
            closeProxyIfIdle()
            return
        }
        withProxy { open ->
            if (owner == null) CarMusicLinkGuard(journal(appContext)).restore(ProxyLink(open))?.let(log)
            closeProxyIfIdle()
        }
    }

    private fun dropWith(open: BluetoothProfile?) {
        val appContext = app ?: return
        val phone = address ?: return
        if (owner == null || unavailable) return
        // A null proxy reads as an unreadable profile, so the report says why nothing was dropped.
        val link = ProxyLink(open)
        val line = CarMusicLinkGuard(journal(appContext)).drop(link, phone)
        if (link.missing) unavailable = true
        log(line)
    }

    private fun scheduleDrop() {
        main.removeCallbacks(drop)
        main.postDelayed(drop, DEBOUNCE_MILLIS)
    }

    private fun register(appContext: Context) {
        val stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                if (owner != null && CarMusicLinkGuard.linkUp(state, device?.address, address)) scheduleDrop()
            }
        }
        // Exported: the Bluetooth service sends this system broadcast.
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(stateReceiver, IntentFilter(ACTION_STATE), Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(stateReceiver, IntentFilter(ACTION_STATE))
            }
        }.isSuccess
        if (registered) receiver = stateReceiver
    }

    private fun unregister() {
        main.removeCallbacks(drop)
        receiver?.let { registered -> runCatching { app?.unregisterReceiver(registered) } }
        receiver = null
    }

    /**
     * Runs [block] on the main thread once the profile proxy is there, or with null if it can't be had in
     * time. A proxy lost to a Bluetooth service restart is asked for again here.
     */
    private fun withProxy(block: (BluetoothProfile?) -> Unit) {
        proxy?.let { return block(it) }
        val appContext = app ?: return block(null)
        waiting += block
        main.removeCallbacks(proxyTimeout)
        main.postDelayed(proxyTimeout, PROXY_TIMEOUT_MILLIS)
        if (proxyRequested) return
        proxyRequested = true
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, connectedProxy: BluetoothProfile) {
                main.post {
                    main.removeCallbacks(proxyTimeout)
                    val held = proxy
                    if (held != null && held !== connectedProxy) {
                        runCatching { adapter?.closeProfileProxy(PROFILE_A2DP_SINK, connectedProxy) }
                        return@post
                    }
                    proxy = connectedProxy
                    flush(connectedProxy)
                    closeProxyIfIdle()
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                main.post {
                    proxy = null
                    proxyRequested = false
                }
            }
        }
        val asked = adapter != null && try {
            adapter.getProfileProxy(appContext, listener, PROFILE_A2DP_SINK)
        } catch (error: Exception) {
            Log.w(TAG, "car Bluetooth music profile not reachable", error)
            false
        }
        if (!asked) {
            main.removeCallbacks(proxyTimeout)
            proxyRequested = false
            flush(null)
        }
    }

    private fun flush(open: BluetoothProfile?) {
        val blocks = waiting.toList()
        waiting.clear()
        blocks.forEach { it(open) }
    }

    private fun closeProxyIfIdle() {
        if (owner != null || waiting.isNotEmpty()) return
        proxy?.let { open -> runCatching { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(PROFILE_A2DP_SINK, open) } }
        proxy = null
        proxyRequested = false
    }

    private fun journal(appContext: Context): CarMusicLinkJournal {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return object : CarMusicLinkJournal {
            override fun dropped(): String? = prefs.getString(KEY_DROPPED, null)
            override fun write(address: String?): Boolean = prefs.edit().putString(KEY_DROPPED, address).commit()
        }
    }

    /** The hidden A2DP-sink calls, by reflection. */
    private class ProxyLink(private val proxy: BluetoothProfile?) : CarMusicLink {
        /** The platform lacks the call; set by [call]. */
        var missing = false
            private set

        private val adapter get() = BluetoothAdapter.getDefaultAdapter()

        override fun connected(): List<String>? {
            val open = proxy ?: return null
            return runCatching { open.connectedDevices.orEmpty().map { it.address } }.getOrNull()
        }

        override fun disconnect(address: String): Boolean? = call("disconnect", address)
        override fun connect(address: String): Boolean? = call("connect", address)

        override fun bonded(address: String): Boolean? = runCatching {
            adapter?.bondedDevices?.any { it.address.equals(address, ignoreCase = true) }
        }.getOrNull()

        private fun call(name: String, address: String): Boolean? {
            val open = proxy ?: return false
            return try {
                val device = adapter?.getRemoteDevice(address) ?: return false
                open.javaClass.getMethod(name, BluetoothDevice::class.java).invoke(open, device) as? Boolean ?: false
            } catch (error: NoSuchMethodException) {
                missing = true
                Log.w(TAG, "car Bluetooth music: no $name() here", error)
                null
            } catch (error: Exception) {
                // A refused call (permission, Bluetooth service restart) is reported, not retried here.
                Log.w(TAG, "car Bluetooth music: $name() refused", error)
                false
            }
        }
    }
}
