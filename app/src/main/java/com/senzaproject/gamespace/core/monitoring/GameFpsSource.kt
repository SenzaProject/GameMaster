package com.senzaproject.gamespace.core.monitoring

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** What the floating panel can show for FPS. Never a made-up number. */
sealed interface GameFpsReading {
    data class Value(val summary: FpsSummary) : GameFpsReading
    /** The game's layer exists but drew no new frame during the last second. */
    data object Idle : GameFpsReading
    data class Problem(val reason: SfFailure) : GameFpsReading
}

/**
 * Per-game FPS from SurfaceFlinger, for the floating panel. Same experimental source as the FPS monitor screen:
 * it needs the DUMP permission granted once over ADB. A root-backed [ShellRunner] arrives in phase 6.
 */
@Singleton
class GameFpsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shell: ShellRunner,
) {
    fun hasDumpPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

    /** One reading per [intervalMs]. Stops by itself when Android denies access. Collect only while it is shown. */
    fun readings(packageName: String, intervalMs: Long = 1_000L): Flow<GameFpsReading> = flow {
        val sampler = SurfaceFlingerSampler(shell)
        val accumulator = FpsAccumulator()
        while (true) {
            when (val reading = sampler.sample(packageName)) {
                is SfReading.Frames -> emit(GameFpsReading.Value(accumulator.add(reading.sample.fps, reading.sample.frameTimesMs)))
                SfReading.NoNewFrames -> emit(GameFpsReading.Idle)
                is SfReading.Unavailable -> {
                    emit(GameFpsReading.Problem(reading.reason))
                    if (reading.reason == SfFailure.PERMISSION_DENIED) return@flow
                }
            }
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)
}
