package com.senzaproject.gamespace.core.monitoring

import kotlin.math.abs
import kotlin.math.roundToInt

// Pure helpers with no Android dependencies, so they are unit-tested on the JVM.

/** Cumulative CPU time counters from the aggregate line of /proc/stat, in clock ticks. */
data class CpuTimes(val idle: Long, val total: Long)

/**
 * Parses the aggregate "cpu" line of /proc/stat.
 * Fields: user nice system idle iowait irq softirq steal. Idle and iowait both count as idle time.
 * Returns null when the text is not in the expected format.
 */
fun parseProcStat(text: String): CpuTimes? {
    val line = text.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
    val fields = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
    if (fields.size < 4) return null
    val idle = fields[3] + (fields.getOrNull(4) ?: 0L)
    val total = fields.take(8).sum()
    return CpuTimes(idle = idle, total = total)
}

/** CPU utilisation between two samples as 0..100, or null when the counters did not advance. */
fun cpuUsagePercent(previous: CpuTimes, current: CpuTimes): Int? {
    val totalDelta = current.total - previous.total
    val idleDelta = current.idle - previous.idle
    if (totalDelta <= 0L || idleDelta < 0L) return null
    return (((totalDelta - idleDelta) * 100L) / totalDelta).toInt().coerceIn(0, 100)
}

/** How busy this process kept the CPU during one interval, as a percentage of all cores. */
fun processCpuPercent(cpuTimeDeltaMs: Long, wallDeltaMs: Long, cores: Int): Float? {
    if (wallDeltaMs <= 0L || cores <= 0 || cpuTimeDeltaMs < 0L) return null
    return (cpuTimeDeltaMs * 100f / (wallDeltaMs * cores)).coerceIn(0f, 100f)
}

/** Hardware line from /proc/cpuinfo: ARM devices use "Hardware", x86 devices use "model name". */
fun parseCpuHardware(cpuInfo: String): String? {
    for (key in listOf("hardware", "model name")) {
        val value = cpuInfo.lineSequence()
            .map { it.split(":", limit = 2) }
            .firstOrNull { it.size == 2 && it[0].trim().equals(key, ignoreCase = true) }
            ?.get(1)
            ?.trim()
        if (!value.isNullOrEmpty()) return value
    }
    return null
}

/** Converts a cpufreq value in kHz to whole MHz. Returns null for missing or non-positive values. */
fun khzToMhz(text: String?): Int? =
    text?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.let { (it / 1000L).toInt() }

/** First integer found in [text], e.g. "12 %" -> 12. */
fun parseLeadingInt(text: String?): Int? =
    text?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

/** Thermal zones report milli-degrees on most kernels and whole degrees on a few. Returns null if implausible. */
fun normalizeZoneTemperature(raw: Long): Float? {
    val celsius = if (abs(raw) >= 1000L) raw / 1000f else raw.toFloat()
    return celsius.takeIf { it in -30f..150f }
}

/** Decodes a Vulkan version packed as (major << 22) | (minor << 12) | patch. */
fun vulkanVersionString(encoded: Int): String? =
    if (encoded <= 0) null else "${encoded ushr 22}.${(encoded ushr 12) and 0x3ff}"

/** Keeps the newest [capacity] values. */
fun <T> appendCapped(list: List<T>, value: T, capacity: Int): List<T> =
    (list + value).takeLast(capacity)

// ---- Battery ----

enum class PowerSource { NONE, AC, USB, WIRELESS, DOCK, UNKNOWN }

/** Maps the BatteryManager.EXTRA_PLUGGED bit flags. */
fun powerSourceOf(plugged: Int): PowerSource = when {
    plugged == 0 -> PowerSource.NONE
    plugged and 4 != 0 -> PowerSource.WIRELESS
    plugged and 1 != 0 -> PowerSource.AC
    plugged and 2 != 0 -> PowerSource.USB
    plugged and 8 != 0 -> PowerSource.DOCK
    else -> PowerSource.UNKNOWN
}

enum class BatteryHealth { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, FAILURE, COLD, UNKNOWN }

/** Maps the BatteryManager.EXTRA_HEALTH constants. */
fun batteryHealthOf(code: Int): BatteryHealth = when (code) {
    2 -> BatteryHealth.GOOD
    3 -> BatteryHealth.OVERHEAT
    4 -> BatteryHealth.DEAD
    5 -> BatteryHealth.OVER_VOLTAGE
    6 -> BatteryHealth.FAILURE
    7 -> BatteryHealth.COLD
    else -> BatteryHealth.UNKNOWN
}

// ---- Thermal ----

enum class ThermalLevel { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNKNOWN }

/** Maps PowerManager.THERMAL_STATUS_* (0..6). */
fun thermalLevelOf(androidStatus: Int): ThermalLevel = when (androidStatus) {
    0 -> ThermalLevel.NONE
    1 -> ThermalLevel.LIGHT
    2 -> ThermalLevel.MODERATE
    3 -> ThermalLevel.SEVERE
    4 -> ThermalLevel.CRITICAL
    5 -> ThermalLevel.EMERGENCY
    6 -> ThermalLevel.SHUTDOWN
    else -> ThermalLevel.UNKNOWN
}

/** The warning indicator turns on from MODERATE upward, when Android starts limiting performance. */
fun isThermalWarning(level: ThermalLevel): Boolean =
    level.ordinal >= ThermalLevel.MODERATE.ordinal && level != ThermalLevel.UNKNOWN

// ---- Network ----

enum class NetworkType { NONE, WIFI, CELLULAR, ETHERNET, BLUETOOTH, VPN, OTHER }

fun networkTypeOf(wifi: Boolean, cellular: Boolean, ethernet: Boolean, bluetooth: Boolean, vpn: Boolean): NetworkType = when {
    wifi -> NetworkType.WIFI
    cellular -> NetworkType.CELLULAR
    ethernet -> NetworkType.ETHERNET
    bluetooth -> NetworkType.BLUETOOTH
    vpn -> NetworkType.VPN
    else -> NetworkType.OTHER
}

/** Bytes per second between two counter reads. Null when a counter is unsupported, reset or the interval is empty. */
fun bytesPerSecond(previous: Long, current: Long, elapsedMs: Long): Long? {
    if (previous < 0L || current < 0L || elapsedMs <= 0L || current < previous) return null
    return (current - previous) * 1000L / elapsedMs
}

/** Summary of a latency run. A null sample is an attempt that got no connection in time. */
data class PingStats(
    val sent: Int,
    val received: Int,
    val lossPercent: Int,
    val minMs: Int?,
    val avgMs: Int?,
    val maxMs: Int?,
    val jitterMs: Int?,
)

fun summarizePing(samples: List<Int?>): PingStats {
    val replies = samples.filterNotNull()
    val sent = samples.size
    return PingStats(
        sent = sent,
        received = replies.size,
        lossPercent = if (sent == 0) 0 else ((sent - replies.size) * 100) / sent,
        minMs = replies.minOrNull(),
        avgMs = if (replies.isEmpty()) null else replies.average().roundToInt(),
        maxMs = replies.maxOrNull(),
        // Jitter: mean absolute difference between consecutive successful samples.
        jitterMs = if (replies.size < 2) null else replies.zipWithNext { a, b -> abs(b - a) }.average().roundToInt(),
    )
}
