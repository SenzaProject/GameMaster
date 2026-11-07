package com.senzaproject.gamespace.feature.fps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.gamespace.core.database.GameEntity
import com.senzaproject.gamespace.core.gaming.GameRepository
import com.senzaproject.gamespace.core.monitoring.DisplayFrameRateSource
import com.senzaproject.gamespace.core.monitoring.FpsAccumulator
import com.senzaproject.gamespace.core.monitoring.FpsSummary
import com.senzaproject.gamespace.core.monitoring.SfFailure
import com.senzaproject.gamespace.core.monitoring.SfReading
import com.senzaproject.gamespace.core.monitoring.ShellRunner
import com.senzaproject.gamespace.core.monitoring.SurfaceFlingerSampler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FpsSourceKind { DISPLAY_TEST, GAME }

enum class FpsProblem { DUMP_NOT_GRANTED, LAYER_NOT_FOUND, COMMAND_FAILED, NO_DATA }

data class FpsUiState(
    val source: FpsSourceKind = FpsSourceKind.DISPLAY_TEST,
    val gamePackage: String? = null,
    val running: Boolean = false,
    val dumpGranted: Boolean = false,
    val summary: FpsSummary? = null,
    val problem: FpsProblem? = null,
)

@HiltViewModel
class FpsMonitorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displaySource: DisplayFrameRateSource,
    private val shell: ShellRunner,
    gameRepository: GameRepository,
) : ViewModel() {
    val games: StateFlow<List<GameEntity>> = gameRepository.visibleGames
        .map { list -> list.sortedBy { it.label.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(FpsUiState(dumpGranted = hasDumpPermission()))
    val state: StateFlow<FpsUiState> = _state.asStateFlow()

    /** The exact command the user runs once from a computer. The package name differs between debug and release. */
    val grantCommand: String get() = "adb shell pm grant ${context.packageName} android.permission.DUMP"

    private var job: Job? = null

    fun selectSource(kind: FpsSourceKind) {
        stop()
        _state.update { it.copy(source = kind, summary = null, problem = null) }
    }

    fun selectGame(packageName: String) {
        stop()
        _state.update { it.copy(gamePackage = packageName, summary = null, problem = null) }
    }

    fun refreshPermission() {
        _state.update { it.copy(dumpGranted = hasDumpPermission()) }
    }

    fun start() {
        if (job?.isActive == true) return
        val current = _state.value
        val gamePackage = current.gamePackage
        if (current.source == FpsSourceKind.GAME) {
            if (gamePackage == null) return
            if (!hasDumpPermission()) {
                _state.update { it.copy(dumpGranted = false, problem = FpsProblem.DUMP_NOT_GRANTED) }
                return
            }
        }
        val accumulator = FpsAccumulator()
        _state.update { it.copy(running = true, summary = null, problem = null, dumpGranted = hasDumpPermission()) }
        job = viewModelScope.launch {
            when (current.source) {
                FpsSourceKind.DISPLAY_TEST ->
                    displaySource.samples().collect { publish(accumulator.add(it.fps, it.frameTimesMs)) }
                FpsSourceKind.GAME -> measureGame(checkNotNull(gamePackage), accumulator)
            }
            // Only reached when the measurement ended by itself. stop() clears the flag when it cancels the job.
            _state.update { it.copy(running = false) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false) }
    }

    private suspend fun measureGame(packageName: String, accumulator: FpsAccumulator) {
        val sampler = SurfaceFlingerSampler(shell)
        while (true) {
            when (val reading = sampler.sample(packageName)) {
                is SfReading.Frames -> publish(accumulator.add(reading.sample.fps, reading.sample.frameTimesMs))
                SfReading.NoNewFrames -> publish(accumulator.add(0f, emptyList()))
                is SfReading.Unavailable -> {
                    val problem = when (reading.reason) {
                        SfFailure.PERMISSION_DENIED -> FpsProblem.DUMP_NOT_GRANTED
                        SfFailure.LAYER_NOT_FOUND -> FpsProblem.LAYER_NOT_FOUND
                        SfFailure.COMMAND_FAILED -> FpsProblem.COMMAND_FAILED
                        SfFailure.NO_DATA -> FpsProblem.NO_DATA
                    }
                    _state.update { it.copy(problem = problem) }
                    if (reading.reason == SfFailure.PERMISSION_DENIED) return
                }
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private fun publish(summary: FpsSummary) {
        _state.update { it.copy(summary = summary, problem = null) }
    }

    private fun hasDumpPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
    }
}
