package com.senzaproject.gamespace.feature.floating

import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.assistant.DndFilter
import com.senzaproject.gamespace.core.assistant.HandleSide
import com.senzaproject.gamespace.core.assistant.HandleStyle
import com.senzaproject.gamespace.core.assistant.ModeDefaults
import com.senzaproject.gamespace.core.assistant.MonitorWidget
import com.senzaproject.gamespace.core.assistant.OverlayGeometry
import com.senzaproject.gamespace.core.assistant.PANEL_PROFILES
import com.senzaproject.gamespace.core.assistant.QuickAction
import com.senzaproject.gamespace.core.assistant.RecordingState
import com.senzaproject.gamespace.core.assistant.SkipReason
import com.senzaproject.gamespace.core.assistant.brightnessPercentFromValue
import com.senzaproject.gamespace.core.common.percentOf
import com.senzaproject.gamespace.core.designsystem.GameIcon
import com.senzaproject.gamespace.core.designsystem.GmMeter
import com.senzaproject.gamespace.core.designsystem.GmSparkline
import com.senzaproject.gamespace.core.gaming.GameMode
import com.senzaproject.gamespace.core.monitoring.GameFpsReading
import com.senzaproject.gamespace.core.monitoring.PingStats
import com.senzaproject.gamespace.core.monitoring.isThermalWarning
import com.senzaproject.gamespace.core.monitoring.summarizePing
import com.senzaproject.gamespace.core.permissions.AppPermission
import com.senzaproject.gamespace.core.database.DockAppEntity
import com.senzaproject.gamespace.feature.devicemonitor.networkTypeLabel
import com.senzaproject.gamespace.feature.devicemonitor.thermalLabel
import com.senzaproject.gamespace.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val HANDLE_TOUCH_WIDTH_DP = 26

@Composable
fun OverlayContent(state: OverlayUiState, deps: PanelDependencies, actions: PanelActions) {
    com.senzaproject.gamespace.core.designsystem.GameMasterTheme {
        val left = state.config.side == HandleSide.LEFT
        val origin = TransformOrigin(pivotFractionX = if (left) 0f else 1f, pivotFractionY = 0.5f)
        Box(contentAlignment = if (left) Alignment.TopStart else Alignment.TopEnd) {
            AnimatedVisibility(visible = !state.expanded, enter = fadeIn(tween(150)), exit = fadeOut(tween(100))) {
                HandleView(state, deps)
            }
            AnimatedVisibility(
                visible = state.expanded,
                enter = fadeIn(tween(160)) + scaleIn(tween(180), initialScale = 0.92f, transformOrigin = origin),
                exit = fadeOut(tween(120)) + scaleOut(tween(150), targetScale = 0.92f, transformOrigin = origin),
            ) {
                PanelView(state, deps, actions)
            }
        }
    }
}

// ---------------------------------------------------------------- handle

@Composable
private fun HandleView(state: OverlayUiState, deps: PanelDependencies) {
    val config = state.config
    val active = state.active
    // The FPS number on the handle is only sampled while the handle is on screen and the user asked for it.
    val fpsText by produceState<String?>(initialValue = null, config.fpsOnHandle, active?.packageName) {
        val pkg = active?.packageName
        if (!config.fpsOnHandle || pkg == null || !deps.fps.hasDumpPermission()) {
            value = null
            return@produceState
        }
        deps.fps.readings(pkg).collect { reading ->
            value = when (reading) {
                is GameFpsReading.Value -> reading.summary.current.roundToInt().toString()
                GameFpsReading.Idle -> "0"
                is GameFpsReading.Problem -> "-"
            }
        }
    }
    val height = OverlayGeometry.handleHeightDp(config.handleSize)
    val alpha = config.handleOpacityPercent / 100f
    val color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    val left = config.side == HandleSide.LEFT
    Box(
        modifier = Modifier.width(HANDLE_TOUCH_WIDTH_DP.dp).height(height.dp),
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        val label = fpsText
        if (label != null) {
            Box(
                Modifier.width(34.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).background(color),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
        } else {
            when (config.handleStyle) {
                HandleStyle.PILL -> Box(Modifier.width(8.dp).height((height * 0.75f).dp).clip(RoundedCornerShape(4.dp)).background(color))
                HandleStyle.BAR -> Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
                HandleStyle.DOT -> Box(Modifier.width(20.dp).height(20.dp).clip(CircleShape).background(color))
            }
        }
    }
}

// ---------------------------------------------------------------- panel

@Composable
private fun PanelView(state: OverlayUiState, deps: PanelDependencies, actions: PanelActions) {
    val config = state.config
    val width = OverlayGeometry.panelWidthDp(config.panelSize, state.screenWidthDp)
    val height = OverlayGeometry.panelHeightDp(config.panelSize, state.screenHeightDp)
    Surface(
        modifier = Modifier.width(width.dp).height(height.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = config.panelOpacityPercent / 100f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            PanelHeader(state, deps, actions)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PanelTab.entries.forEach { tab ->
                    val selected = state.tab == tab
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { state.tab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(tabLabel(tab)),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.tab) {
                    PanelTab.MONITOR -> MonitorTab(state, deps)
                    PanelTab.CONTROL -> ControlTab(state, deps, actions)
                    PanelTab.QUICK -> QuickTab(state, deps, actions)
                    PanelTab.DOCK -> DockTab(state, deps, actions)
                }
            }
        }
    }
}

private fun tabLabel(tab: PanelTab): Int = when (tab) {
    PanelTab.MONITOR -> R.string.ov_tab_monitor
    PanelTab.CONTROL -> R.string.ov_tab_control
    PanelTab.QUICK -> R.string.ov_tab_quick
    PanelTab.DOCK -> R.string.ov_tab_dock
}

@Composable
private fun PanelHeader(state: OverlayUiState, deps: PanelDependencies, actions: PanelActions) {
    val active = state.active
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (active != null) {
            GameIcon(active.packageName, active.label, deps.iconLoader, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(active.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(profileLabel(active.mode)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Text(
                stringResource(R.string.ov_no_game),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = actions::collapse) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ov_close))
        }
    }
}

fun profileLabel(mode: GameMode): Int = when (mode) {
    GameMode.BATTERY_SAVER -> R.string.profile_power_saving
    GameMode.BALANCED -> R.string.profile_balanced
    GameMode.PERFORMANCE -> R.string.profile_performance
    GameMode.CUSTOM -> R.string.mode_custom
}

// ---------------------------------------------------------------- monitor tab

@Composable
private fun PanelRow(label: String, value: String, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun MonitorTab(state: OverlayUiState, deps: PanelDependencies) {
    val metrics = remember { MutableStateFlow(PanelMetrics()) }
    val m by metrics.collectAsState()
    val widgets = state.config.visibleWidgets
    val gamePackage = state.active?.packageName
    val interval = state.active?.effective?.monitorIntervalMs ?: ModeDefaults.monitorIntervalMs(state.defaultMode)
    // Readers start here and stop when this tab or the panel closes.
    LaunchedEffect(widgets, gamePackage, interval) { collectPanelMetrics(deps, widgets, gamePackage, interval, metrics) }

    val context = LocalContext.current
    val unavailable = stringResource(R.string.value_unavailable)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MonitorWidget.entries.filter { it in widgets }.forEach { widget ->
            when (widget) {
                MonitorWidget.FPS -> {
                    val fps = m.fps
                    PanelRow(
                        stringResource(R.string.ov_w_fps),
                        when {
                            fps != null && m.fpsState == FpsPanelState.LIVE ->
                                stringResource(R.string.ov_fps_summary, fps.current, fps.average, fps.minimum)
                            m.fpsState == FpsPanelState.NO_GAME -> stringResource(R.string.ov_fps_no_game)
                            m.fpsState == FpsPanelState.NEEDS_PERMISSION -> stringResource(R.string.ov_fps_needs_adb)
                            m.fpsState == FpsPanelState.IDLE -> stringResource(R.string.ov_fps_idle)
                            m.fpsState == FpsPanelState.LAYER_NOT_FOUND -> stringResource(R.string.ov_fps_layer)
                            else -> stringResource(R.string.ov_fps_unavailable)
                        },
                    )
                    if (fps != null && m.fpsState == FpsPanelState.LIVE) GmSparkline(fps.history, minValue = 0f)
                }
                MonitorWidget.CPU -> {
                    val cpu = m.cpu
                    val topFrequency = cpu?.coreFrequenciesMhz?.filterNotNull()?.maxOrNull()
                    PanelRow(
                        stringResource(R.string.ov_w_cpu),
                        when {
                            cpu?.devicePercent != null -> stringResource(R.string.value_percent, cpu.devicePercent)
                            topFrequency != null -> stringResource(R.string.ov_cpu_freq_max, topFrequency)
                            else -> unavailable
                        },
                    )
                }
                MonitorWidget.GPU -> {
                    val name = m.gpuInfo?.renderer
                    val load = m.gpuLoad?.busyPercent
                    PanelRow(
                        stringResource(R.string.ov_w_gpu),
                        when {
                            name != null && load != null -> stringResource(R.string.ov_gpu_value, name, load)
                            name != null -> name
                            else -> unavailable
                        },
                    )
                }
                MonitorWidget.RAM -> {
                    val mem = m.device?.memory
                    if (mem != null) {
                        val used = mem.totalBytes - mem.availableBytes
                        PanelRow(
                            stringResource(R.string.ov_w_ram),
                            stringResource(
                                R.string.ov_ram_value,
                                Formatter.formatShortFileSize(context, used),
                                Formatter.formatShortFileSize(context, mem.totalBytes),
                            ),
                        )
                        GmMeter(percentOf(used, mem.totalBytes))
                    } else {
                        PanelRow(stringResource(R.string.ov_w_ram), unavailable)
                    }
                }
                MonitorWidget.TEMPERATURE -> {
                    val t = m.device?.battery?.temperatureCelsius
                    PanelRow(stringResource(R.string.ov_w_temp), if (t != null) stringResource(R.string.value_celsius, t) else unavailable)
                }
                MonitorWidget.BATTERY -> {
                    val b = m.device?.battery
                    val percent = b?.percent
                    PanelRow(
                        stringResource(R.string.ov_w_battery),
                        when {
                            percent == null -> unavailable
                            b?.charging == true -> stringResource(R.string.ov_battery_charging, percent)
                            else -> stringResource(R.string.value_percent, percent)
                        },
                    )
                }
                MonitorWidget.THERMAL -> {
                    val level = m.thermal?.level
                    PanelRow(
                        stringResource(R.string.ov_w_thermal),
                        if (level != null) stringResource(thermalLabel(level)) else unavailable,
                        warn = level != null && isThermalWarning(level),
                    )
                }
                MonitorWidget.REFRESH_RATE -> {
                    val hz = m.device?.refreshRateHz
                    PanelRow(stringResource(R.string.ov_w_refresh), if (hz != null) stringResource(R.string.value_hertz, hz) else unavailable)
                }
                MonitorWidget.NETWORK -> {
                    val n = m.network
                    PanelRow(
                        stringResource(R.string.ov_w_network),
                        when {
                            n == null -> unavailable
                            n.rxBytesPerSecond != null && n.txBytesPerSecond != null -> stringResource(
                                R.string.ov_net_value,
                                stringResource(networkTypeLabel(n.type)),
                                Formatter.formatShortFileSize(context, n.rxBytesPerSecond),
                                Formatter.formatShortFileSize(context, n.txBytesPerSecond),
                            )
                            else -> stringResource(networkTypeLabel(n.type))
                        },
                    )
                }
                MonitorWidget.LATENCY -> {
                    val latency = m.latencyMs
                    PanelRow(
                        stringResource(R.string.ov_w_latency),
                        when {
                            !m.latencyTried -> stringResource(R.string.ov_latency_measuring)
                            latency != null -> stringResource(R.string.value_ms_int, latency)
                            else -> stringResource(R.string.ov_latency_none)
                        },
                    )
                }
            }
        }
        if (widgets.isEmpty()) {
            Text(stringResource(R.string.ov_no_widgets), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.ov_latency_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Modifier.fillMaxSize(): Modifier = this.fillMaxWidth().fillMaxHeight()

// ---------------------------------------------------------------- control tab

private fun dndIsOn(deps: PanelDependencies): Boolean? = deps.system.readDndFilter()?.let { it != DndFilter.ALL }

/** Toggles Do Not Disturb. Without access it opens the system page that grants it instead. */
private fun toggleDnd(deps: PanelDependencies, actions: PanelActions) {
    if (!deps.system.hasDndAccess()) {
        actions.requestPermission(AppPermission.DND_ACCESS)
        return
    }
    val turnOn = deps.system.readDndFilter() == DndFilter.ALL
    if (!deps.system.writeDndFilter(if (turnOn) DndFilter.PRIORITY else DndFilter.ALL)) actions.toast(R.string.ov_toast_change_failed)
}

@Composable
private fun ControlTab(state: OverlayUiState, deps: PanelDependencies, actions: PanelActions) {
    val scope = rememberCoroutineScope()
    val selected = state.active?.mode ?: state.defaultMode
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.ov_profile_title), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PANEL_PROFILES.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { scope.launch { deps.sessions.switchMode(mode) } },
                    label = { Text(stringResource(profileLabel(mode)), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                )
            }
        }
        AppliedSummary(state)

        HorizontalDivider2()

        // Brightness
        var canWrite by remember { mutableStateOf(deps.system.canWriteSettings()) }
        var brightness by remember {
            mutableFloatStateOf(deps.system.readBrightnessValue()?.let { brightnessPercentFromValue(it) }?.toFloat() ?: 50f)
        }
        Text(stringResource(R.string.ov_brightness), style = MaterialTheme.typography.titleSmall)
        if (canWrite) {
            Slider(
                value = brightness,
                onValueChange = {
                    brightness = it
                    deps.system.writeBrightness(((it.roundToInt()) * 255 + 50) / 100)
                },
                valueRange = 1f..100f,
            )
        } else {
            TextButton(onClick = { actions.requestPermission(AppPermission.WRITE_SETTINGS); canWrite = deps.system.canWriteSettings() }) {
                Text(stringResource(R.string.ov_allow_brightness))
            }
        }

        // Volume
        var volume by remember { mutableFloatStateOf(deps.system.readMusicVolumePercent()?.toFloat() ?: 50f) }
        Text(stringResource(R.string.ov_volume), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = volume,
            onValueChange = { volume = it; deps.system.writeMusicVolumePercent(it.roundToInt()) },
            valueRange = 0f..100f,
        )

        // Do Not Disturb
        var dnd by remember { mutableStateOf(dndIsOn(deps)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.ov_dnd), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(
                checked = dnd == true,
                onCheckedChange = { toggleDnd(deps, actions); dnd = dndIsOn(deps) },
            )
        }
        if (dnd == null) {
            Text(stringResource(R.string.ov_dnd_needs_access), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HorizontalDivider2() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}

/** Plain list of what the profile really did, including what it could not do and why. */
@Composable
private fun AppliedSummary(state: OverlayUiState) {
    val active = state.active
    if (active == null) {
        Text(stringResource(R.string.ov_profile_next), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val e = active.effective
    val a = active.applied
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.ov_applied_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Line(if (a.brightness && e.brightnessPercent != null) stringResource(R.string.ov_brightness_set, e.brightnessPercent) else stringResource(R.string.ov_brightness_unchanged))
        Line(if (a.dnd) stringResource(R.string.ov_dnd_on) else stringResource(R.string.ov_dnd_unchanged))
        if (e.volumePercent != null) {
            Line(if (a.volume) stringResource(R.string.ov_volume_set, e.volumePercent) else stringResource(R.string.ov_volume_unchanged))
        }
        Line(if (e.keepScreenOn) stringResource(R.string.ov_keep_awake_on) else stringResource(R.string.ov_keep_awake_off))
        Line(stringResource(R.string.ov_interval, (e.monitorIntervalMs / 1000).toInt()))
        Line(stringResource(R.string.ov_cpu_gpu_note))
        a.skipped.forEach { reason ->
            Text(
                stringResource(
                    when (reason) {
                        SkipReason.NEEDS_WRITE_SETTINGS -> R.string.ov_skip_write_settings
                        SkipReason.NEEDS_DND_ACCESS -> R.string.ov_skip_dnd_access
                        SkipReason.ALREADY_RESTRICTED -> R.string.ov_skip_restricted
                        SkipReason.WRITE_FAILED -> R.string.ov_skip_failed
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------- quick tab

@Composable
private fun QuickTab(state: OverlayUiState, deps: PanelDependencies, actions: PanelActions) {
    val scope = rememberCoroutineScope()
    var dnd by remember { mutableStateOf(dndIsOn(deps)) }
    var testing by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<PingStats?>(null) }
    var testFailed by remember { mutableStateOf(false) }
    val recording = state.recording is RecordingState.Recording

    fun runNetworkTest() {
        if (testing) return
        scope.launch {
            testing = true
            testFailed = false
            val address = deps.ping.resolve("1.1.1.1")
            if (address == null) {
                testFailed = true
                stats = null
            } else {
                val samples = List(5) { deps.ping.connectTimeMs(address, 443).also { delay(150) } }
                stats = summarizePing(samples)
            }
            testing = false
        }
    }

    val dash = "-"
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.config.shownQuickActions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { action ->
                    val (title, subtitle) = when (action) {
                        QuickAction.WIFI_SETTINGS -> R.string.qa_wifi to null
                        QuickAction.DND -> R.string.qa_dnd to when (dnd) {
                            null -> stringResource(R.string.ov_dnd_needs_access)
                            true -> stringResource(R.string.ov_state_on)
                            false -> stringResource(R.string.ov_state_off)
                        }
                        QuickAction.SCREENSHOT -> R.string.qa_screenshot to null
                        QuickAction.SCREEN_RECORD -> (if (recording) R.string.qa_record_stop else R.string.qa_record) to null
                        QuickAction.DISPLAY_SETTINGS -> R.string.qa_display to null
                        QuickAction.NETWORK_TEST -> R.string.qa_network_test to when {
                            testing -> stringResource(R.string.qa_testing)
                            testFailed -> stringResource(R.string.ov_latency_none)
                            stats != null -> stringResource(
                                R.string.ov_ping_result,
                                stats?.avgMs?.toString() ?: dash,
                                stats?.jitterMs?.toString() ?: dash,
                                stats?.lossPercent ?: 0,
                            )
                            else -> null
                        }
                        QuickAction.GAME_MODE -> R.string.qa_game_mode to null
                        QuickAction.TOOLBOX -> R.string.qa_toolbox to null
                    }
                    QuickTile(
                        title = stringResource(title),
                        subtitle = subtitle,
                        modifier = Modifier.weight(1f),
                    ) {
                        when (action) {
                            QuickAction.WIFI_SETTINGS -> actions.openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
                            QuickAction.DND -> { toggleDnd(deps, actions); dnd = dndIsOn(deps) }
                            QuickAction.SCREENSHOT -> actions.screenshot()
                            QuickAction.SCREEN_RECORD -> actions.toggleRecording()
                            QuickAction.DISPLAY_SETTINGS -> actions.openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS)
                            QuickAction.NETWORK_TEST -> runNetworkTest()
                            QuickAction.GAME_MODE -> actions.openGameMasterScreen(Routes.GameMode)
                            QuickAction.TOOLBOX -> actions.openGameMasterScreen(Routes.Toolbox)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (state.config.shownQuickActions.isEmpty()) {
            Text(stringResource(R.string.ov_no_actions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickTile(title: String, subtitle: String?, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp).height(52.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------- dock tab

@Composable
private fun DockTab(state: OverlayUiState, deps: PanelDependencies, actions: PanelActions) {
    val apps by deps.dock.apps.collectAsState(initial = emptyList<DockAppEntity>())
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val active = state.active
        Text(
            if (active != null) stringResource(R.string.ov_dock_current, active.label) else stringResource(R.string.ov_dock_no_current),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (apps.isEmpty()) {
            Text(stringResource(R.string.ov_dock_empty), style = MaterialTheme.typography.bodySmall)
        }
        apps.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { app ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { actions.launchApp(app.packageName) }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        GameIcon(app.packageName, app.label, deps.iconLoader, size = 44.dp)
                        Text(app.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Button(onClick = { actions.openGameMasterScreen(Routes.DockEditor) }) { Text(stringResource(R.string.ov_dock_edit)) }
    }
}
