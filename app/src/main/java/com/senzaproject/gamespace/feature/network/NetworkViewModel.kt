package com.senzaproject.gamespace.feature.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.gamespace.core.monitoring.NetworkRepository
import com.senzaproject.gamespace.core.monitoring.NetworkSnapshot
import com.senzaproject.gamespace.core.monitoring.PingRepository
import com.senzaproject.gamespace.core.monitoring.PingStats
import com.senzaproject.gamespace.core.monitoring.PingTarget
import com.senzaproject.gamespace.core.monitoring.appendCapped
import com.senzaproject.gamespace.core.monitoring.historyOf
import com.senzaproject.gamespace.core.monitoring.summarizePing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PingUiState(
    val targetId: String,
    val running: Boolean = false,
    /** Newest last. A null entry is an attempt that opened no connection in time. */
    val samples: List<Int?> = emptyList(),
    val stats: PingStats? = null,
    val resolveFailed: Boolean = false,
)

@HiltViewModel
class NetworkViewModel @Inject constructor(
    networkRepository: NetworkRepository,
    private val pingRepository: PingRepository,
) : ViewModel() {
    val snapshot: StateFlow<NetworkSnapshot?> = networkRepository.snapshots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Bytes per second over the last two minutes, newest last. Kept in memory only. */
    val downloadHistory: StateFlow<List<Float>> = snapshot
        .historyOf(HISTORY_SIZE) { it?.rxBytesPerSecond?.toFloat() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uploadHistory: StateFlow<List<Float>> = snapshot
        .historyOf(HISTORY_SIZE) { it?.txBytesPerSecond?.toFloat() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val targets: List<PingTarget> = pingRepository.targets

    private val _ping = MutableStateFlow(PingUiState(targetId = targets.first().id))
    val ping: StateFlow<PingUiState> = _ping.asStateFlow()
    private var pingJob: Job? = null

    fun selectTarget(id: String) {
        stop()
        _ping.value = PingUiState(targetId = id)
    }

    fun start() {
        if (pingJob?.isActive == true) return
        val target = targets.firstOrNull { it.id == _ping.value.targetId } ?: return
        _ping.value = PingUiState(targetId = target.id, running = true)
        pingJob = launchPing(target)
    }

    fun stop() {
        pingJob?.cancel()
        pingJob = null
        _ping.update { it.copy(running = false) }
    }

    private fun launchPing(target: PingTarget): Job = viewModelScope.launch {
        val address = pingRepository.resolve(target.host)
        if (address == null) {
            _ping.update { it.copy(running = false, resolveFailed = true) }
            return@launch
        }
        while (isActive) {
            val ms = pingRepository.connectTimeMs(address, target.port)
            _ping.update { state ->
                val samples = appendCapped(state.samples, ms, PING_HISTORY)
                state.copy(samples = samples, stats = summarizePing(samples))
            }
            delay(PING_INTERVAL_MS)
        }
    }

    private companion object {
        const val HISTORY_SIZE = 120
        const val PING_HISTORY = 60
        const val PING_INTERVAL_MS = 1_000L
    }
}
