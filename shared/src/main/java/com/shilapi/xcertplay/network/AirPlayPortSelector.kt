package com.shilapi.xcertplay.network

import java.net.BindException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException

/**
 * Binds the AirPlay control listener, falling back when the preferred port is already taken.
 *
 * Some head units ship a factory CarPlay daemon that permanently listens on the default AirPlay
 * port (7000) on every interface, so binding DiPlay's listener fails with EADDRINUSE. The bound
 * port is advertised to the iPhone through Bonjour and iAP2, so any free port works.
 */
object AirPlayPortSelector {
    /** Ports tried, in order, after the preferred port; an ephemeral port is the last resort. */
    val FALLBACK_PORTS: IntRange = 7001..7010

    /** The listeners [bindAll] opened, in address order, and the addresses it had to leave out. */
    class BoundListeners(
        val servers: List<ServerSocket>,
        /** The requested address behind each entry of [servers]. */
        val addresses: List<InetAddress>,
        val dropped: List<AddressBindFailure>,
    )

    /**
     * Why one listener address was not served. The message names the family and the platform's
     * error, never the address, so it can go into diagnostic reports as it is.
     */
    class AddressBindFailure(
        val address: InetAddress,
        val error: SocketException,
        /** True when the address itself cannot be bound; false when only the ports were taken. */
        val addressUnusable: Boolean,
    ) : BindException(
        "${familyOf(address)} ${if (addressUnusable) "address unusable" else "ports busy"}: " +
            "${error.javaClass.simpleName}: ${error.message?.let(::withoutAddress) ?: "no message"}",
    ) {
        init {
            initCause(error)
        }

        val family: String get() = familyOf(address)
    }

    /**
     * Specific per-family listeners avoid relying on a platform's IPV6_V6ONLY default.
     *
     * Every served address shares one port. An address the kernel refuses whatever the port
     * (EADDRNOTAVAIL for a tentative, DAD-failed or vanished IPv6 address, EINVAL, or a failure
     * even on an ephemeral port) is dropped so the other family is still served (#1356); the
     * caller must then stop publishing it. When nothing can be served the thrown exception
     * carries one suppressed [AddressBindFailure] per address.
     */
    fun bindAll(
        addresses: List<InetAddress>,
        preferredPort: Int,
        fallbackPorts: Iterable<Int> = FALLBACK_PORTS,
        /** Opens one listener; tests replace it. Kept before [onFallback] for trailing lambdas. */
        binder: (InetAddress, Int) -> ServerSocket = ::bindPort,
        onFallback: (Int, Int) -> Unit = { _, _ -> },
    ): BoundListeners {
        require(addresses.isNotEmpty()) { "At least one listener address is required" }
        val candidates = listOf(preferredPort) + fallbackPorts.filter { it != preferredPort } +
            List(EPHEMERAL_ATTEMPTS) { 0 }
        val remaining = addresses.distinct().toMutableList()
        val dropped = mutableListOf<AddressBindFailure>()
        val busy = LinkedHashMap<InetAddress, AddressBindFailure>()
        var index = 0
        while (remaining.isNotEmpty() && index < candidates.size) {
            when (val attempt = bindTogether(remaining, candidates[index], binder)) {
                is Attempt.Bound -> {
                    val servers = attempt.servers
                    try {
                        val port = servers.first().localPort
                        if (preferredPort != 0 && port != preferredPort) onFallback(preferredPort, port)
                        return BoundListeners(servers, remaining.toList(), dropped.toList())
                    } catch (error: Throwable) {
                        servers.forEach { closeAfterFailure(it, error) }
                        throw error
                    }
                }
                // Ports skipped because of the dropped address may suit the others: start over.
                is Attempt.Failed -> if (attempt.failure.addressUnusable) {
                    remaining.remove(attempt.failure.address)
                    busy.remove(attempt.failure.address)
                    dropped += attempt.failure
                    index = 0
                } else {
                    busy[attempt.failure.address] = attempt.failure
                    index++
                }
            }
        }
        val failure = BindException(
            if (remaining.isEmpty()) "No AirPlay listener address could be bound"
            else "No common AirPlay port available for the selected interface addresses",
        )
        addresses.distinct().forEach { address ->
            (dropped.firstOrNull { it.address == address } ?: busy[address])?.let(failure::addSuppressed)
        }
        throw failure
    }

    private sealed class Attempt {
        class Bound(val servers: List<ServerSocket>) : Attempt()
        class Failed(val failure: AddressBindFailure) : Attempt()
    }

    /** The first address picks the port; the others must follow it. */
    private fun bindTogether(
        addresses: List<InetAddress>,
        candidate: Int,
        binder: (InetAddress, Int) -> ServerSocket,
    ): Attempt {
        val servers = mutableListOf<ServerSocket>()
        for (address in addresses) {
            val port = servers.firstOrNull()?.localPort ?: candidate
            try {
                servers.add(binder(address, port))
            } catch (error: Throwable) {
                servers.forEach { closeAfterFailure(it, error) }
                if (error !is SocketException) throw error
                val unusable = port == 0 || addressUnusable(error)
                if (!unusable && error !is BindException) throw error
                return Attempt.Failed(AddressBindFailure(address, error, unusable))
            }
        }
        return Attempt.Bound(servers)
    }

    fun bind(
        address: InetAddress,
        preferredPort: Int,
        fallbackPorts: Iterable<Int> = FALLBACK_PORTS,
        onFallback: (busyPort: Int, boundPort: Int) -> Unit = { _, _ -> },
    ): ServerSocket {
        tryBind(address, preferredPort)?.let { return it }
        for (port in fallbackPorts) {
            if (port == preferredPort) continue
            tryBind(address, port)?.let { server ->
                return reportFallback(server, preferredPort, onFallback)
            }
        }
        return reportFallback(bindPort(address, 0), preferredPort, onFallback)
    }

    private fun tryBind(address: InetAddress, port: Int): ServerSocket? = try {
        bindPort(address, port)
    } catch (_: BindException) {
        null
    }

    private fun bindPort(address: InetAddress, port: Int): ServerSocket {
        val server = ServerSocket()
        return try {
            server.bind(InetSocketAddress(address, port))
            server
        } catch (error: Throwable) {
            closeAfterFailure(server, error)
            throw error
        }
    }

    private fun reportFallback(
        server: ServerSocket,
        preferredPort: Int,
        onFallback: (Int, Int) -> Unit,
    ): ServerSocket = try {
        onFallback(preferredPort, server.localPort)
        server
    } catch (error: Throwable) {
        // Ownership transfers to the caller only after notification succeeds.
        closeAfterFailure(server, error)
        throw error
    }

    /**
     * Errors that no other port would fix. Android's IoBridge reports the errno name and its text
     * ("bind failed: EADDRNOTAVAIL (Cannot assign requested address)"), a JVM only the text.
     */
    internal fun addressUnusable(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(4).any { failure ->
            val message = failure.message.orEmpty()
            ADDRESS_ERRORS.any { message.contains(it, ignoreCase = true) }
        }

    // Android's "Can't bind to a link-local address without a scope id: <address>" is the one
    // bind error that names the address.
    private fun withoutAddress(message: String): String = message.substringBefore(" id: ").let {
        if (it.length == message.length) message else "$it id"
    }

    internal fun familyOf(address: InetAddress): String = if (address is Inet6Address) "IPv6" else "IPv4"

    private val ADDRESS_ERRORS = listOf(
        // Linux and Android say "Cannot", macOS "Can't".
        "EADDRNOTAVAIL", "assign requested address",
        "EINVAL", "Invalid argument",
        "EAFNOSUPPORT", "Address family not supported",
        "without a scope id",
    )

    private const val EPHEMERAL_ATTEMPTS = 4

    private fun closeAfterFailure(server: ServerSocket, error: Throwable) {
        try {
            server.close()
        } catch (closeError: Throwable) {
            error.addSuppressed(closeError)
        }
    }
}
