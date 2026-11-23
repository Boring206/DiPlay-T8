package com.shilapi.xcertplay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Queued entries retain only their exact file target and a redacted, bounded metadata string. */
internal object AsyncDiagnosticLog {
    private data class Entry(val target: SessionLogFile, val line: String, val afterClose: Boolean)
    private val writer = BoundedDiagnosticWriter<Entry> {
        if (it.afterClose) it.target.appendAfterClose(it.line) else it.target.appendNow(it.line)
    }

    /** [afterClose]: still written once the session's log is closed (see [SessionLogFile.appendAfterClose]). */
    fun append(
        target: SessionLogFile?,
        message: String,
        nowMillis: Long = System.currentTimeMillis(),
        afterClose: Boolean = false,
    ) {
        if (target == null) return
        val safe = DiagnosticRedactor.redact(message) ?: return
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(nowMillis))
        runCatching { writer.enqueue(Entry(target, "$timestamp  $safe", afterClose)) }
    }

    internal fun awaitIdle(timeoutMillis: Long): Boolean = writer.awaitIdle(timeoutMillis)
}
