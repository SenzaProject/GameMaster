package com.senzaproject.gamespace.core.monitoring

import android.os.Build
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

data class CpuSnapshot(
    val hardware: String?,
    val coreCount: Int,
    val abi: String?,
    /** Whole-device load. Null on most phones: Android 8+ blocks apps from reading /proc/stat. */
    val devicePercent: Int?,
    /** Current frequency of each core in MHz. A null entry means that core's file is not readable. */
    val coreFrequenciesMhz: List<Int?>,
    /** CPU time used by Game Master itself as a percentage of all cores. Always measurable. */
    val appPercent: Float?,
)

/** CPU readings from documented APIs and files. Anything Android hides is null. */
@Singleton
class CpuRepository @Inject constructor() {
    private val hardware: String? by lazy { readHardware() }

    fun snapshots(intervalMs: Long = 2_000L): Flow<CpuSnapshot> = flow {
        val cores = Runtime.getRuntime().availableProcessors()
        var previousTimes: CpuTimes? = null
        var previousCpuMs = Process.getElapsedCpuTime()
        var previousWallMs = SystemClock.elapsedRealtime()
        while (true) {
            val times = readTextOrNull("/proc/stat")?.let(::parseProcStat)
            val before = previousTimes
            val devicePercent = if (before != null && times != null) cpuUsagePercent(before, times) else null
            previousTimes = times

            val cpuMs = Process.getElapsedCpuTime()
            val wallMs = SystemClock.elapsedRealtime()
            val appPercent = processCpuPercent(cpuMs - previousCpuMs, wallMs - previousWallMs, cores)
            previousCpuMs = cpuMs
            previousWallMs = wallMs

            emit(
                CpuSnapshot(
                    hardware = hardware,
                    coreCount = cores,
                    abi = Build.SUPPORTED_ABIS.firstOrNull(),
                    devicePercent = devicePercent,
                    coreFrequenciesMhz = (0 until cores).map { core ->
                        khzToMhz(readTextOrNull("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq"))
                    },
                    appPercent = appPercent,
                ),
            )
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun readHardware(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val soc = listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                .filter { it.isNotBlank() && it != Build.UNKNOWN }
                .joinToString(" ")
            if (soc.isNotEmpty()) return soc
        }
        return readTextOrNull("/proc/cpuinfo")?.let(::parseCpuHardware)
            ?: Build.HARDWARE.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
    }
}
