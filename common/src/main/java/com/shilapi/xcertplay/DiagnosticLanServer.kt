package com.shilapi.xcertplay

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

/**
 * Serves the redacted diagnostic report to a browser on the same Wi-Fi, for head units that have
 * neither a file picker nor a reachable ADB port. It lives for the process and holds no Activity.
 */
internal object DiagnosticLanServer {
    const val PORT = 8765
    @Volatile private var server: ServerSocket? = null

    @Synchronized fun start(report: () -> String) {
        if (server != null) return
        val socket = runCatching { ServerSocket(PORT) }.getOrNull() ?: return
        server = socket
        Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                Thread({ serve(client, report) }, "diplay-report-client").apply { isDaemon = true }.start()
            }
        }, "diplay-report-server").apply { isDaemon = true }.start()
    }

    fun addresses(): List<String> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .map { "http://${it.hostAddress}:$PORT/" }
    }.getOrDefault(emptyList())

    private fun serve(client: Socket, report: () -> String) {
        runCatching {
            client.use { socket ->
                socket.soTimeout = REQUEST_TIMEOUT_MILLIS
                val reader = socket.getInputStream().bufferedReader()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val body = runCatching(report).getOrElse { "Report failed: $it" }.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write(
                        ("HTTP/1.0 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII),
                    )
                    write(body)
                    flush()
                }
            }
        }
    }

    private const val REQUEST_TIMEOUT_MILLIS = 5_000
}
