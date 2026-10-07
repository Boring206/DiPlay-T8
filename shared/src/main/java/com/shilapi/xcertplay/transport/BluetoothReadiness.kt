package com.shilapi.xcertplay.transport

/** Getting the head unit's Bluetooth ready for a connection, and judging whether a link is real. */
object BluetoothReadiness {
    /**
     * True once Bluetooth is on, switching it on first where [switchOn] is available. Some head
     * units start every drive with Bluetooth off, and sending the driver to find that switch each
     * time defeats an automatic connection.
     */
    fun ensureOn(
        isOn: () -> Boolean,
        switchOn: (() -> Boolean)?,
        isCancelled: () -> Boolean,
        timeoutMillis: Long,
        pause: (Long) -> Unit = { Thread.sleep(it) },
        nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    ): Boolean {
        if (isOn()) return true
        if (switchOn == null || !runCatching(switchOn).getOrDefault(false)) return false
        val deadline = nowMillis() + timeoutMillis
        while (!isCancelled() && nowMillis() < deadline) {
            if (isOn()) return true
            pause(POLL_MILLIS)
        }
        return isOn()
    }

    /**
     * A real RFCOMM connection to a phone needs a page, a service search and a channel setup: at
     * least hundreds of milliseconds. One that "connects" at once and then never delivers a byte
     * is the mark of a head unit whose Android Bluetooth fronts a vendor module apps cannot use.
     */
    fun looksUnreal(connectMillis: Long, bytesReceived: Long): Boolean =
        connectMillis < INSTANT_CONNECT_MILLIS && bytesReceived == 0L

    const val INSTANT_CONNECT_MILLIS = 100L
    private const val POLL_MILLIS = 250L
}
