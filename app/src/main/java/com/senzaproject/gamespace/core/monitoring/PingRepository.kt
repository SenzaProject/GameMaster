package com.senzaproject.gamespace.core.monitoring

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

data class PingTarget(val id: String, val host: String, val port: Int)

/**
 * Measures how long a TCP connection to a public server takes to open.
 * Without root an app cannot send ICMP pings, so this is the honest alternative. It is a network
 * latency measurement, not the ping a game would show.
 */
@Singleton
class PingRepository @Inject constructor() {
    val targets: List<PingTarget> = listOf(
        PingTarget(TARGET_CLOUDFLARE, "1.1.1.1", 443),
        PingTarget(TARGET_GOOGLE, "8.8.8.8", 443),
    )

    /** Resolves [host] once per session so DNS time never ends up in the latency figure. Null when it fails. */
    suspend fun resolve(host: String): InetAddress? = withContext(Dispatchers.IO) {
        try {
            InetAddress.getByName(host)
        } catch (e: IOException) {
            null
        }
    }

    /** Milliseconds for the TCP handshake, or null when no connection opened within [timeoutMs]. */
    suspend fun connectTimeMs(address: InetAddress, port: Int, timeoutMs: Int = TIMEOUT_MS): Int? =
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                try {
                    val start = System.nanoTime()
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                    ((System.nanoTime() - start) / 1_000_000L).toInt()
                } catch (e: IOException) {
                    null
                }
            }
        }

    companion object {
        const val TARGET_CLOUDFLARE = "cloudflare"
        const val TARGET_GOOGLE = "google"
        const val TIMEOUT_MS = 2_000
    }
}
