package com.shilapi.xcertplay

/** Diagnostics describe state transitions; protocol payloads and credentials are never exported. */
internal object DiagnosticRedactor {
    private val secret = Regex("(?i)(pass(word|phrase)?|token|private.?key|certificate|pair.?record|ssid|body=|payload=|hex=)")
    private val mac = Regex("(?i)(?<![0-9a-f])(?:[0-9a-f]{2}:){5}[0-9a-f]{2}(?![0-9a-f])")
    private val identifier = Regex("(?i)\\b[0-9a-f]{24,}\\b|\\b[0-9a-f]{8}-[0-9a-f-]{27,}\\b|\\b[0-9a-f]{8}-[0-9a-f]{16}\\b")
    private val address = Regex("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])")
    private val namedDevice = Regex("(?i)(phone|device|peer|host)?name[=:]")
    private val ipv6 = Regex("(?i)(?:[0-9a-f]{1,4}:)*[0-9a-f]{0,4}::[0-9a-f:]*(?:%[a-z0-9_.-]+)?|(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}")
    // logCarBluetoothAudio() logs List.toString() of BluetoothDevice.getName(); keep "connected", drop the names.
    // It never logs MAC lists, so no hex exception is needed: redact every non-empty list, leave
    // "connected=[]" as is; ".+" reaches the last "]" so a "]" in a name cannot expose the rest.
    private val connectedDevices = Regex("(?i)connected=\\[.+\\]")
    /** Cheap check for lines [redact] always rejects, so a queue can refuse them before storing. */
    fun neverExported(line: String): Boolean =
        line.contains("TRACE ") || line.contains("PHONE ") || line.contains('\n') || line.contains('\r')

    fun redact(line: String): String? {
        if (neverExported(line) || secret.containsMatchIn(line) || namedDevice.containsMatchIn(line)) return null
        return line.replace(connectedDevices, "connected=[redacted]")
            .replace(mac, "[address]").replace(identifier, "[identifier]")
            .replace(address, "[ip]").replace(ipv6, "[ip]").take(700)
    }
}
