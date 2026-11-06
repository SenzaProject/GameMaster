package com.senzaproject.gamespace.core.monitoring

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkSnapshot(
    val type: NetworkType,
    /** True when Android itself has verified that the connection reaches the internet. */
    val validated: Boolean,
    val metered: Boolean,
    /** Android's own estimate of link capacity. It is an estimate, not a measured speed. */
    val downstreamKbps: Int?,
    val upstreamKbps: Int?,
    val signalDbm: Int?,
    val wifiLinkMbps: Int?,
    val wifiFrequencyMhz: Int?,
    /** Whole-device traffic since the previous reading. Null when the counters are unsupported. */
    val rxBytesPerSecond: Long?,
    val txBytesPerSecond: Long?,
)

@Singleton
class NetworkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun snapshots(intervalMs: Long = 1_000L): Flow<NetworkSnapshot> = flow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val wifi = context.getSystemService(WifiManager::class.java)
        var previousRx = TrafficStats.getTotalRxBytes()
        var previousTx = TrafficStats.getTotalTxBytes()
        var previousAt = SystemClock.elapsedRealtime()
        emit(read(connectivity, wifi, null, null))
        while (true) {
            delay(intervalMs)
            val now = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val elapsed = now - previousAt
            emit(read(connectivity, wifi, bytesPerSecond(previousRx, rx, elapsed), bytesPerSecond(previousTx, tx, elapsed)))
            previousRx = rx
            previousTx = tx
            previousAt = now
        }
    }.flowOn(Dispatchers.IO)

    @Suppress("DEPRECATION") // WifiManager.connectionInfo still returns link speed and frequency; SSID is not needed.
    private fun read(
        connectivity: ConnectivityManager,
        wifi: WifiManager?,
        rxRate: Long?,
        txRate: Long?,
    ): NetworkSnapshot {
        val network = connectivity.activeNetwork
        val caps = network?.let { connectivity.getNetworkCapabilities(it) }
            ?: return NetworkSnapshot(NetworkType.NONE, false, false, null, null, null, null, null, rxRate, txRate)

        val type = networkTypeOf(
            wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            bluetooth = caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH),
            vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        )
        val wifiInfo = if (type == NetworkType.WIFI) runCatching { wifi?.connectionInfo }.getOrNull() else null
        val signal = caps.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
            ?: wifiInfo?.rssi?.takeIf { it > INVALID_RSSI }
        return NetworkSnapshot(
            type = type,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            downstreamKbps = caps.linkDownstreamBandwidthKbps.takeIf { it > 0 },
            upstreamKbps = caps.linkUpstreamBandwidthKbps.takeIf { it > 0 },
            signalDbm = signal,
            wifiLinkMbps = wifiInfo?.linkSpeed?.takeIf { it > 0 },
            wifiFrequencyMhz = wifiInfo?.frequency?.takeIf { it > 0 },
            rxBytesPerSecond = rxRate,
            txBytesPerSecond = txRate,
        )
    }

    private companion object {
        const val INVALID_RSSI = -127
    }
}
