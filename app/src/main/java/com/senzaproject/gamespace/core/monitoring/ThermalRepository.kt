package com.senzaproject.gamespace.core.monitoring

import android.content.Context
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ThermalZone(val name: String, val celsius: Float)

data class ThermalSnapshot(
    /** Android's own thermal status (PowerManager, API 29+). */
    val level: ThermalLevel,
    /** 1.0 means Android is about to start throttling. Null before Android 11 or when the system declines to estimate. */
    val headroom: Float?,
    /** Sensors under /sys/class/thermal that this app may read. Often empty on newer Android versions. */
    val zones: List<ThermalZone>,
)

@Singleton
class ThermalRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun snapshots(intervalMs: Long = 3_000L): Flow<ThermalSnapshot> = flow {
        val power = context.getSystemService(PowerManager::class.java)
        while (true) {
            val level = thermalLevelOf(power.currentThermalStatus)
            val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                power.getThermalHeadroom(HEADROOM_FORECAST_SECONDS).takeIf { !it.isNaN() }
            } else {
                null
            }
            emit(ThermalSnapshot(level, headroom, readZones()))
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun readZones(): List<ThermalZone> {
        val directories = File("/sys/class/thermal").listFiles { file -> file.name.startsWith("thermal_zone") }
            ?: return emptyList()
        return directories.sortedBy { it.name }.mapNotNull { dir ->
            val celsius = readTextOrNull("${dir.path}/temp")?.trim()?.toLongOrNull()
                ?.let(::normalizeZoneTemperature) ?: return@mapNotNull null
            val type = readTextOrNull("${dir.path}/type")?.trim().orEmpty().ifEmpty { dir.name }
            ThermalZone(type, celsius)
        }.take(MAX_ZONES)
    }

    private companion object {
        const val HEADROOM_FORECAST_SECONDS = 10
        const val MAX_ZONES = 12
    }
}
