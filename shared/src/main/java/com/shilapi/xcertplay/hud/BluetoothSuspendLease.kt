package com.shilapi.xcertplay.hud

/** A durable promise to restore a radio observed ON before our disable command. */
internal interface BluetoothRestoreJournal {
    fun pending(): Boolean
    fun write(pending: Boolean): Boolean
}

/** Cancellation stays fast even when an already-issued radio command is blocked. */
internal class BluetoothSuspendLease(
    private val journal: BluetoothRestoreJournal,
    private val readEnabled: () -> Boolean?,
    private val requestEnabled: (Boolean) -> Unit,
    private val awaitEnabled: (Boolean) -> Boolean,
) {
    private val stateLock = Any()
    private val commandLock = Any()
    private var owner: Any? = null
    private var generation = 0L

    /**
     * For the report (#1021): the radio as a disable that did not take effect in time left it, read before
     * [restoreOwned] could turn it back on ("ON", "OFF" or "unknown"). Null when the last pause never
     * waited for a disable.
     */
    @Volatile var radioWhenDisableWaitEnded: String? = null
        private set

    fun begin(requester: Any): Long? = synchronized(stateLock) {
        if (owner === requester) return null
        owner = requester
        ++generation
    }

    fun end(requester: Any): Long? = synchronized(stateLock) {
        if (owner !== requester) return null
        owner = null
        ++generation
    }

    /** Opening settings during a live session must not release that session's lease. */
    fun recoveryOnAppOpen(): Long? = synchronized(stateLock) {
        if (owner != null) return null
        // Non-owning cleanup has the same recovery goal as an already queued handshake.
        // Reuse its ticket so a late old-controller close cannot make that handshake fail.
        generation
    }

    /** A new wireless handshake also retires an older pending disable. */
    fun beforeHandshake(): Long = synchronized(stateLock) {
        owner = null
        ++generation
    }

    fun isSuspended(): Boolean = pending() == true && enabled() == false

    /** For the report: the radio state the pause starts from, and whether [ticket] still owns it. */
    fun radioEnabled(): Boolean? = enabled()
    fun isCurrent(ticket: Long): Boolean = current(ticket)

    fun suspend(ticket: Long): Boolean = synchronized(commandLock) {
        radioWhenDisableWaitEnded = null
        if (!current(ticket)) return false
        // Reconcile an interrupted older lease before observing this session's original state.
        if (!restoreOwned()) return false
        if (!current(ticket) || enabled() != true) return false
        // commit(), not apply(): process death after the command must leave recovery evidence.
        if (!runCatching { journal.write(true) }.getOrDefault(false)) return false
        if (!current(ticket)) {
            restoreOwned()
            return false
        }
        runCatching { requestEnabled(false) }
        val disabled = runCatching { awaitEnabled(false) }.getOrDefault(false)
        if (!disabled) {
            radioWhenDisableWaitEnded = when (enabled()) {
                true -> "ON"
                false -> "OFF"
                null -> "unknown"
            }
        }
        if (!disabled || !current(ticket)) {
            // A cancelled command may already have executed. Compensate while still serialized.
            restoreOwned()
            return false
        }
        true
    }

    fun restore(ticket: Long): Boolean = synchronized(commandLock) {
        if (!currentRecovery(ticket)) return false
        restoreOwned()
    }

    private fun current(ticket: Long): Boolean = synchronized(stateLock) {
        generation == ticket && owner != null
    }

    private fun currentRecovery(ticket: Long): Boolean = synchronized(stateLock) {
        generation == ticket && owner == null
    }

    private fun pending(): Boolean? = runCatching { journal.pending() }.getOrNull()
    private fun enabled(): Boolean? = runCatching { readEnabled() }.getOrNull()

    private fun restoreOwned(): Boolean {
        val owned = pending() ?: return false
        if (!owned) return true
        when (enabled()) {
            true -> Unit // It never turned off, or somebody already restored the original state.
            false -> {
                runCatching { requestEnabled(true) }
                if (!runCatching { awaitEnabled(true) }.getOrDefault(false)) return false
            }
            null -> return false // Unknown state is not permission to enable a user's radio.
        }
        return runCatching { journal.write(false) }.getOrDefault(false)
    }
}

/** One report line for each pause attempt, so a report shows why calls still ring on the car (#18). */
internal object BluetoothPauseOutcome {
    fun describe(paused: Boolean, radioBefore: Boolean?, stillCurrent: Boolean, adbAccess: String?): String = when {
        paused -> "car Bluetooth paused: Android Bluetooth is off until CarPlay ends"
        !stillCurrent -> "car Bluetooth pause cancelled: CarPlay ended first"
        radioBefore == false -> "car Bluetooth pause skipped: Android Bluetooth was already off"
        radioBefore == null -> "car Bluetooth pause skipped: Android Bluetooth state unknown"
        adbAccess != null && adbAccess != "READY" -> "car Bluetooth pause failed: ADB access $adbAccess"
        else -> "car Bluetooth pause failed: Android Bluetooth stayed on"
    }

    /** True exactly when [describe] reports "pause failed". */
    fun failed(paused: Boolean, radioBefore: Boolean?, stillCurrent: Boolean): Boolean =
        !paused && stillCurrent && radioBefore == true
}

/**
 * What a failed pause adds to the report (#1021): what `svc bluetooth disable` printed, and the
 * `enabled:` and `state:` fields of `dumpsys bluetooth_manager`. The dump's name and address lines
 * never leave [managerLine], and the command's reply keeps plain characters only.
 */
object BluetoothPauseEvidence {
    private const val PREFIX = "car Bluetooth pause evidence:"
    private const val MAX_REPLY_CHARS = 160
    private val unsafeCharacters = Regex("[^A-Za-z0-9 _.,:;()/+-]")
    private val spaces = Regex("\\s+")

    /**
     * [radioWhenWaitEnded] is the radio as the wait for the disable ended, before DiPlay's restore could
     * turn it back on; null when no wait ran. [managerDump] is read after that restore step.
     */
    fun lines(disableSent: Boolean, disableReply: String?, radioWhenWaitEnded: String?, managerDump: String?): List<String> =
        listOfNotNull(commandLine(disableSent, disableReply), radioWhenWaitEnded?.let(::waitLine), managerLine(managerDump))

    /** OFF here with ON in [managerLine]: the disable worked, too late, and DiPlay turned the radio back on. */
    fun waitLine(radio: String): String = "$PREFIX Android Bluetooth was $radio when the wait for the disable ended"

    /** [sent] false: the command never ran; [reply] null: ADB returned nothing at all. */
    fun commandLine(sent: Boolean, reply: String?): String = when {
        !sent -> "$PREFIX svc bluetooth disable not sent"
        reply == null -> "$PREFIX svc bluetooth disable got no ADB reply"
        reply.isBlank() -> "$PREFIX svc bluetooth disable printed nothing"
        else -> "$PREFIX svc bluetooth disable printed '${clean(reply)}'"
    }

    fun managerLine(dump: String?): String {
        if (dump == null) return "$PREFIX bluetooth_manager unavailable"
        return "$PREFIX bluetooth_manager after DiPlay's restore step enabled=${field(dump, "enabled") ?: "unknown"} " +
            "state=${field(dump, "state") ?: "unknown"}"
    }

    private fun field(dump: String, key: String): String? = dump.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key:", ignoreCase = true) }
        ?.substringAfter(':')?.trim()
        ?.takeWhile { it.isLetterOrDigit() || it == '_' }
        ?.take(24)
        ?.ifEmpty { null }

    private fun clean(reply: String): String =
        reply.replace(unsafeCharacters, " ").replace(spaces, " ").trim().take(MAX_REPLY_CHARS)
}
