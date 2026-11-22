package com.shilapi.xcertplay.transport

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers the Bluetooth adapters on which an RFCOMM link has carried a byte from a phone.
 *
 * [BluetoothLinkCheck] calls a link unreal when it connected at once, stayed silent, and a connect
 * to a service no phone offers also succeeded at once. A real stack can do all three: on an XTRONS
 * IX (SPRD, Android 12) every RFCOMM connect to the iPhone returns in 1 to 5 ms, those that carry
 * whole iAP2 sessions included, and the control connect "succeeds" in 4 to 5 ms (#1178). The
 * adapters the check was written for never carry a byte, so one that has is real, and a silent
 * link on it is a phone that did not answer this time.
 *
 * Proof is kept for the process and, through a [Store], across restarts. It is keyed by board and
 * adapter address, so it never vouches for another head unit.
 */
class BluetoothAdapterProof {
    /** Where proof outlives the process. A head unit has one adapter, so one key is enough. */
    interface Store {
        fun read(): String?
        fun write(adapter: String)
    }

    /** Where the proof that an adapter is real came from. */
    enum class Source(val label: String) {
        /** A link in this process carried a byte. */
        MEMORY("memory"),

        /** Only the [Store] knows: a link carried a byte in an earlier run. */
        STORE("store"),
    }

    private val proven = ConcurrentHashMap<String, Source>()

    /** Notes that a link on [adapter] has carried a byte; true the first time in this process. */
    fun record(adapter: String, store: Store?): Boolean {
        val previous = proven.put(adapter, Source.MEMORY)
        if (previous == Source.MEMORY) return false
        if (previous == null) runCatching { store?.write(adapter) }
        return true
    }

    /** True when a link on [adapter] has carried a byte, in this process or, per [store], before. */
    fun proven(adapter: String, store: Store?): Boolean = source(adapter, store) != null

    /**
     * Where the proof for [adapter] came from: [Source.MEMORY] once a link in this process has
     * carried a byte, [Source.STORE] when only an earlier run's saved key vouches for it, or null.
     */
    fun source(adapter: String, store: Store?): Source? {
        proven[adapter]?.let { return it }
        val saved = runCatching { store?.read() }.getOrNull() == adapter
        if (!saved) return null
        return proven.putIfAbsent(adapter, Source.STORE) ?: Source.STORE
    }

    companion object {
        /** The key for the adapter at [address] on [board]: a digest, so the address is not stored. */
        fun key(board: String?, address: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest("${board.orEmpty()}|${address.uppercase()}".toByteArray(Charsets.UTF_8))
                .take(KEY_BYTES)
                .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

        private const val KEY_BYTES = 16
    }
}
