package com.senzaproject.gamespace.feature.toolbox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.assistant.AssistantPrefsRepository
import com.senzaproject.gamespace.core.assistant.DndFilter
import com.senzaproject.gamespace.core.assistant.GameMasterAccessibilityService
import com.senzaproject.gamespace.core.assistant.GamingTimer
import com.senzaproject.gamespace.core.assistant.RecordingState
import com.senzaproject.gamespace.core.assistant.ScreenRecorder
import com.senzaproject.gamespace.core.assistant.ScreenRecordingState
import com.senzaproject.gamespace.core.assistant.SystemSettingsController
import com.senzaproject.gamespace.core.assistant.brightnessPercentFromValue
import com.senzaproject.gamespace.core.assistant.brightnessValueFromPercent
import com.senzaproject.gamespace.core.designsystem.CommitSlider
import com.senzaproject.gamespace.core.designsystem.GmCard
import com.senzaproject.gamespace.core.designsystem.GmScreen
import com.senzaproject.gamespace.core.designsystem.GmSwitchRow
import com.senzaproject.gamespace.core.permissions.AppPermission
import com.senzaproject.gamespace.core.permissions.PermissionRepository
import com.senzaproject.gamespace.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Live values of the controls, read from the system each time the screen resumes. */
data class ToolboxState(
    val canWriteSettings: Boolean = false,
    val hasDndAccess: Boolean = false,
    val brightnessPercent: Int = 50,
    val volumePercent: Int = 50,
    val dndOn: Boolean = false,
    val haptic: Boolean? = null,
)

@HiltViewModel
class ToolboxViewModel @Inject constructor(
    private val system: SystemSettingsController,
    private val permissions: PermissionRepository,
    private val timer: GamingTimer,
    prefs: AssistantPrefsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ToolboxState())
    val state: StateFlow<ToolboxState> = _state.asStateFlow()
    val recording: StateFlow<RecordingState> = ScreenRecordingState.state
    val timerEndAt: StateFlow<Long?> = prefs.timerEndAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        _state.value = ToolboxState(
            canWriteSettings = system.canWriteSettings(),
            hasDndAccess = system.hasDndAccess(),
            brightnessPercent = system.readBrightnessValue()?.let { brightnessPercentFromValue(it) } ?: 50,
            volumePercent = system.readMusicVolumePercent() ?: 50,
            dndOn = system.readDndFilter()?.let { it != DndFilter.ALL } ?: false,
            haptic = system.readHapticFeedback(),
        )
    }

    fun settingsIntent(permission: AppPermission): Intent = permissions.settingsIntent(permission)

    fun setBrightness(percent: Int) { system.writeBrightness(brightnessValueFromPercent(percent)); refresh() }
    fun setVolume(percent: Int) { system.writeMusicVolumePercent(percent); refresh() }
    fun setDnd(on: Boolean) { system.writeDndFilter(if (on) DndFilter.PRIORITY else DndFilter.ALL); refresh() }
    fun setHaptic(on: Boolean) { system.writeHapticFeedback(on); refresh() }
    fun startTimer(minutes: Int) { viewModelScope.launch { timer.start(minutes) } }
    fun cancelTimer() { viewModelScope.launch { timer.cancel() } }
}

@Composable
fun ToolboxScreen(onOpen: (String) -> Unit, viewModel: ToolboxViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val timerEnd by viewModel.timerEndAt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val percent = { v: Int -> context.getString(R.string.value_percent, v) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.perm_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    GmScreen(title = stringResource(R.string.screen_toolbox)) {
        Text(stringResource(R.string.tb_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        GmCard {
            Text(stringResource(R.string.tb_controls), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            CommitSlider(
                title = stringResource(R.string.ov_brightness),
                value = state.brightnessPercent,
                range = 1..100,
                valueText = percent,
                onCommit = viewModel::setBrightness,
                enabled = state.canWriteSettings,
            )
            if (!state.canWriteSettings) {
                OutlinedButton(onClick = { open(viewModel.settingsIntent(AppPermission.WRITE_SETTINGS)) }) { Text(stringResource(R.string.ov_allow_brightness)) }
            }
            CommitSlider(
                title = stringResource(R.string.ov_volume),
                value = state.volumePercent,
                range = 0..100,
                valueText = percent,
                onCommit = viewModel::setVolume,
            )
            GmSwitchRow(
                title = stringResource(R.string.ov_dnd),
                description = if (state.hasDndAccess) null else stringResource(R.string.ov_dnd_needs_access),
                checked = state.dndOn,
                onCheckedChange = { on ->
                    if (state.hasDndAccess) viewModel.setDnd(on) else open(viewModel.settingsIntent(AppPermission.DND_ACCESS))
                },
            )
            GmSwitchRow(
                title = stringResource(R.string.tb_haptic),
                description = stringResource(if (state.canWriteSettings) R.string.tb_haptic_desc else R.string.tb_haptic_needs_access),
                checked = state.haptic == true,
                onCheckedChange = { on ->
                    if (state.canWriteSettings) viewModel.setHaptic(on) else open(viewModel.settingsIntent(AppPermission.WRITE_SETTINGS))
                },
                enabled = state.haptic != null,
            )
        }

        GmCard {
            Text(stringResource(R.string.tb_capture), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = {
                    if (GameMasterAccessibilityService.isConnected) {
                        if (!GameMasterAccessibilityService.takeScreenshot()) {
                            Toast.makeText(context, R.string.ov_toast_screenshot_failed, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, R.string.ov_toast_screenshot_needs_service, Toast.LENGTH_LONG).show()
                        open(viewModel.settingsIntent(AppPermission.SCREENSHOT_SERVICE))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.qa_screenshot)) }
            val isRecording = recording is RecordingState.Recording
            OutlinedButton(onClick = { ScreenRecorder.toggle(context) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (isRecording) R.string.qa_record_stop else R.string.qa_record))
            }
            if (isRecording) Text(stringResource(R.string.tb_recording_now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            (recording as? RecordingState.Failed)?.let {
                Text(stringResource(R.string.rec_failed, it.message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.tb_capture_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        GmCard {
            Text(stringResource(R.string.tb_shortcuts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { open(Intent(Settings.ACTION_WIFI_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.qa_wifi)) }
            OutlinedButton(onClick = { open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.qa_display)) }
            OutlinedButton(onClick = { onOpen(Routes.Network) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.screen_network)) }
            OutlinedButton(onClick = { onOpen(Routes.FpsMonitor) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.screen_fps_monitor)) }
            OutlinedButton(onClick = { onOpen(Routes.DeviceMonitor) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.screen_device_monitor)) }
            OutlinedButton(onClick = { onOpen(Routes.GameMode) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.screen_game_mode)) }
            OutlinedButton(onClick = { onOpen(Routes.FloatingPanel) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.screen_floating_panel)) }
            OutlinedButton(onClick = { onOpen(Routes.Statistics) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.screen_statistics)) }
        }

        GmCard {
            Text(stringResource(R.string.tb_timer), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(timerEnd) {
                while (timerEnd != null) {
                    now = System.currentTimeMillis()
                    delay(1_000)
                }
            }
            val end = timerEnd
            if (end == null) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 90).forEach { minutes ->
                        OutlinedButton(onClick = { viewModel.startTimer(minutes) }) { Text(stringResource(R.string.tb_timer_minutes, minutes)) }
                    }
                }
                Text(stringResource(R.string.tb_timer_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val remaining = end - now
                Text(
                    if (remaining > 0) stringResource(R.string.tb_timer_left, formatClock(remaining)) else stringResource(R.string.tb_timer_finished),
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedButton(onClick = viewModel::cancelTimer) { Text(stringResource(if (remaining > 0) R.string.tb_timer_cancel else R.string.tb_timer_clear)) }
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
