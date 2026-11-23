package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.transport.BlockingDuplexByteStream

/**
 * Copies the iPhone's accessory and authentication messages (accessoryd, ACCCarPlayService, iap2d, CarPlay) from
 * `com.apple.syslog_relay` into logcat as `PHONE …` lines for the first two minutes of a wired session.
 *
 * Only for triage with `setprop log.tag.DiPlayTrace VERBOSE`, like the AirPlay hex dumps (#1131). Each line carries
 * the iPhone's name, one wired test logged 6,579 of them, and the diagnostic report drops them anyway.
 */
internal object PhoneSyslogCapture {
    private val RELEVANT = Regex(" (accessoryd|ACCCarPlayService|iap2d|CarPlay)([\\[(])", RegexOption.IGNORE_CASE)
    private const val CAPTURE_NANOS = 120_000_000_000L

    fun start(wanted: Boolean, open: () -> BlockingDuplexByteStream, isClosed: () -> Boolean, log: (String) -> Unit) {
        if (!wanted) return
        val relay = try {
            open()
        } catch (error: Exception) {
            log("phone authentication diagnostics unavailable: ${error.message}")
            return
        }
        Thread({
            try {
                copy(relay, isClosed, log)
                log("phone authentication diagnostic capture ended")
            } catch (error: Exception) {
                log("phone authentication diagnostic capture ended: ${error.javaClass.simpleName}")
            }
        }, "carplay-lab-phone-diagnostics").apply { isDaemon = true; start() }
        log("phone authentication diagnostic capture started")
    }

    internal fun copy(relay: BlockingDuplexByteStream, isClosed: () -> Boolean, log: (String) -> Unit) {
        relay.use {
            val deadline = System.nanoTime() + CAPTURE_NANOS
            val pending = StringBuilder()
            while (!isClosed() && System.nanoTime() < deadline) {
                val bytes = relay.recv(8192, 1000) ?: continue
                if (bytes.isEmpty()) break
                pending.append(bytes.toString(Charsets.UTF_8).replace('\u0000', '\n'))
                while (true) {
                    val end = pending.indexOf("\n")
                    if (end < 0) break
                    val line = pending.substring(0, end)
                    pending.delete(0, end + 1)
                    if (RELEVANT.containsMatchIn(line)) log("PHONE ${line.take(2000)}")
                }
                if (pending.length > 65536) pending.clear()
            }
        }
    }
}
