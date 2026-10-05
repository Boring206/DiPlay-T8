package com.shilapi.xcertplay.network

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Use the same scoped, link-local path for manual APs as for Wi-Fi Direct, unless
 * [linkLocalUsable] says this device cannot answer on it and an IPv4 address exists.
 */
internal fun wirelessHostAddress(
    addresses: List<InetAddress>,
    interfaceIndex: Int,
    linkLocalUsable: (Inet6Address) -> Boolean = { true },
): InetAddress? {
    val ipv4 = addresses.firstOrNull {
        it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress &&
            !it.isAnyLocalAddress && !it.isMulticastAddress
    }
    if (interfaceIndex > 0) {
        addresses.filterIsInstance<Inet6Address>().firstOrNull { it.isLinkLocalAddress }?.let {
            val scoped = Inet6Address.getByAddress(null, it.address, interfaceIndex)
            // With no IPv4 address there is nothing better to offer than the link-local one.
            if (ipv4 == null || linkLocalUsable(scoped)) return scoped
        }
    }
    return ipv4
}

/**
 * Whether this device can send to link-local peers on the address's interface. Android 8 and 9
 * keep a hotspot interface's fe80::/64 route in a table no routing rule consults: the phone's
 * packets arrive and every reply is dropped as unreachable, so only IPv4 works there. connect()
 * on a datagram socket asks the kernel for the route without sending anything.
 */
internal fun linkLocalRoutable(scoped: Inet6Address): Boolean {
    val socket = try {
        Os.socket(OsConstants.AF_INET6, OsConstants.SOCK_DGRAM, 0)
    } catch (_: Exception) {
        return true
    }
    return try {
        Os.connect(socket, Inet6Address.getByAddress(null, LINK_LOCAL_PROBE_PEER, scoped.scopeId), LINK_LOCAL_PROBE_PORT)
        true
    } catch (failure: ErrnoException) {
        failure.errno != OsConstants.ENETUNREACH && failure.errno != OsConstants.EHOSTUNREACH
    } catch (_: Exception) {
        true
    } finally {
        runCatching { Os.close(socket) }
    }
}

// fe80::1: any link-local peer does, only the route lookup matters.
private val LINK_LOCAL_PROBE_PEER = ByteArray(16).also { it[0] = 0xfe.toByte(); it[1] = 0x80.toByte(); it[15] = 1 }
private const val LINK_LOCAL_PROBE_PORT = 9

/** Station LAN discovery must cover IPv4 multicast as well as scoped link-local IPv6. */
internal fun existingWifiHostAddresses(addresses: List<InetAddress>, interfaceIndex: Int): List<InetAddress> {
    val ipv4 = addresses.firstOrNull {
        it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress &&
            !it.isAnyLocalAddress && !it.isMulticastAddress
    }
    val ipv6 = wirelessHostAddress(addresses, interfaceIndex) as? Inet6Address
    return listOfNotNull(ipv4, ipv6)
}
