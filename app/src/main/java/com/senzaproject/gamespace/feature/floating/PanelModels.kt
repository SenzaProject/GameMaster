package com.senzaproject.gamespace.feature.floating

import androidx.annotation.StringRes
import com.senzaproject.gamespace.core.assistant.DockRepository
import com.senzaproject.gamespace.core.assistant.GameSessionManager
import com.senzaproject.gamespace.core.assistant.MonitorWidget
import com.senzaproject.gamespace.core.assistant.SystemSettingsController
import com.senzaproject.gamespace.core.gaming.GameIconLoader
import com.senzaproject.gamespace.core.monitoring.CpuRepository
import com.senzaproject.gamespace.core.monitoring.CpuSnapshot
import com.senzaproject.gamespace.core.monitoring.DeviceInfoRepository
import com.senzaproject.gamespace.core.monitoring.DeviceSnapshot
import com.senzaproject.gamespace.core.monitoring.FpsSummary
import com.senzaproject.gamespace.core.monitoring.GameFpsReading
import com.senzaproject.gamespace.core.monitoring.GameFpsSource
import com.senzaproject.gamespace.core.monitoring.GpuInfo
import com.senzaproject.gamespace.core.monitoring.GpuLoad
import com.senzaproject.gamespace.core.monitoring.GpuRepository
import com.senzaproject.gamespace.core.monitoring.NetworkRepository
import com.senzaproject.gamespace.core.monitoring.NetworkSnapshot
import com.senzaproject.gamespace.core.monitoring.PingRepository
import com.senzaproject.gamespace.core.monitoring.SfFailure
import com.senzaproject.gamespace.core.monitoring.ThermalRepository
import com.senzaproject.gamespace.core.monitoring.ThermalSnapshot
import com.senzaproject.gamespace.core.permissions.AppPermission
import com.senzaproject.gamespace.core.permissions.PermissionRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the overlay UI needs, injected once. The overlay lives in a Service, so it cannot use ViewModels. */
@Singleton
class PanelDependencies @Inject constructor(
    val cpu: CpuRepository,
    val gpu: GpuRepository,
    val device: DeviceInfoRepository,
    val thermal: ThermalRepository,
    val network: NetworkRepository,
    val ping: PingRepository,
    val fps: GameFpsSource,
    val system: SystemSettingsController,
    val dock: DockRepository,
    val iconLoader: GameIconLoader,
    val sessions: GameSessionManager,
    val permissions: PermissionRepository,
)

/** What the panel may ask the host service to do. */
interface PanelActions {
    fun collapse()
    fun launchApp(packageName: String)
    fun openSystemSettings(action: String)
    fun openGameMasterScreen(route: String)
    fun screenshot()
    fun toggleRecording()
    fun requestPermission(permission: AppPermission)
    fun toast(@StringRes message: Int)
}

enum class FpsPanelState { NO_GAME, NEEDS_PERMISSION, LIVE, IDLE, LAYER_NOT_FOUND, UNAVAILABLE }

/** Live readings shown in the panel. A null field means "no reading", never a guess. */
data class PanelMetrics(
    val cpu: CpuSnapshot? = null,
    val gpuInfo: GpuInfo? = null,
    val gpuLoad: GpuLoad? = null,
    val device: DeviceSnapshot? = null,
    val thermal: ThermalSnapshot? = null,
    val network: NetworkSnapshot? = null,
    val latencyMs: Int? = null,
    val latencyTried: Boolean = false,
    val fps: FpsSummary? = null,
    val fpsState: FpsPanelState = FpsPanelState.NO_GAME,
)

private const val LATENCY_HOST = "1.1.1.1"
private const val LATENCY_PORT = 443
private const val LATENCY_EVERY_MS = 5_000L

/**
 * Starts a reader only for each widget the user switched on, and runs until cancelled.
 * Call it from a LaunchedEffect: when the panel closes the effect is cancelled and every reader stops with it.
 */
suspend fun collectPanelMetrics(
    deps: PanelDependencies,
    widgets: Set<MonitorWidget>,
    gamePackage: String?,
    intervalMs: Long,
    out: MutableStateFlow<PanelMetrics>,
) = coroutineScope {
    if (widgets.any { it in DEVICE_WIDGETS }) {
        launch { deps.device.snapshots(intervalMs).collect { s -> out.update { it.copy(device = s) } } }
    }
    if (MonitorWidget.CPU in widgets) {
        launch { deps.cpu.snapshots(intervalMs).collect { s -> out.update { it.copy(cpu = s) } } }
    }
    if (MonitorWidget.GPU in widgets) {
        launch {
            val info = deps.gpu.info()
            out.update { it.copy(gpuInfo = info) }
            deps.gpu.loads(intervalMs).collect { s -> out.update { it.copy(gpuLoad = s) } }
        }
    }
    if (MonitorWidget.THERMAL in widgets) {
        launch { deps.thermal.snapshots(maxOf(intervalMs, 2_000L)).collect { s -> out.update { it.copy(thermal = s) } } }
    }
    if (MonitorWidget.NETWORK in widgets) {
        launch { deps.network.snapshots(maxOf(intervalMs, 1_000L)).collect { s -> out.update { it.copy(network = s) } } }
    }
    if (MonitorWidget.LATENCY in widgets) {
        launch {
            // Resolved once: an IP literal needs no DNS, so the number is only the TCP handshake time.
            val address = deps.ping.resolve(LATENCY_HOST)
            while (true) {
                val ms = address?.let { deps.ping.connectTimeMs(it, LATENCY_PORT) }
                out.update { it.copy(latencyMs = ms, latencyTried = true) }
                delay(LATENCY_EVERY_MS)
            }
        }
    }
    if (MonitorWidget.FPS in widgets) {
        launch {
            when {
                gamePackage == null -> out.update { it.copy(fps = null, fpsState = FpsPanelState.NO_GAME) }
                !deps.fps.hasDumpPermission() -> out.update { it.copy(fps = null, fpsState = FpsPanelState.NEEDS_PERMISSION) }
                else -> deps.fps.readings(gamePackage).collect { reading ->
                    out.update {
                        when (reading) {
                            is GameFpsReading.Value -> it.copy(fps = reading.summary, fpsState = FpsPanelState.LIVE)
                            GameFpsReading.Idle -> it.copy(fpsState = FpsPanelState.IDLE)
                            is GameFpsReading.Problem -> it.copy(
                                fps = null,
                                fpsState = when (reading.reason) {
                                    SfFailure.PERMISSION_DENIED -> FpsPanelState.NEEDS_PERMISSION
                                    SfFailure.LAYER_NOT_FOUND -> FpsPanelState.LAYER_NOT_FOUND
                                    else -> FpsPanelState.UNAVAILABLE
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val DEVICE_WIDGETS = setOf(
    MonitorWidget.RAM, MonitorWidget.BATTERY, MonitorWidget.TEMPERATURE, MonitorWidget.REFRESH_RATE,
)
