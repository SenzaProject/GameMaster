package com.senzaproject.gamespace.core.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.gamespace.core.common.percentOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Every reading is its own flow, so a screen only pays for what it shows.
 * Sampling stops 5 seconds after the last collector leaves the foreground.
 */
@HiltViewModel
class DeviceMonitorViewModel @Inject constructor(
    repository: DeviceInfoRepository,
    cpuRepository: CpuRepository,
    gpuRepository: GpuRepository,
    thermalRepository: ThermalRepository,
) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<T>.shared(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val snapshot: StateFlow<DeviceSnapshot?> = repository.snapshots().shared(null)
    val cpu: StateFlow<CpuSnapshot?> = cpuRepository.snapshots().shared(null)
    val gpuLoad: StateFlow<GpuLoad?> = gpuRepository.loads().shared(null)
    val thermal: StateFlow<ThermalSnapshot?> = thermalRepository.snapshots().shared(null)

    /** GPU model and API support do not change, so they are read once. */
    val gpuInfo: StateFlow<GpuInfo?> = flow { emit(gpuRepository.info()) }.shared(null)

    /** RAM use in percent, newest last. At the 2 second sampling interval this covers about three minutes. */
    val memoryHistory: StateFlow<List<Float>> = snapshot
        .historyOf(HISTORY_SIZE) { value ->
            value?.memory?.let { percentOf(it.totalBytes - it.availableBytes, it.totalBytes).toFloat() }
        }
        .shared(emptyList())

    private companion object {
        const val HISTORY_SIZE = 90
    }
}
