package com.senzaproject.gamespace.feature.gamemode

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.assistant.ActiveSession
import com.senzaproject.gamespace.core.assistant.AssistantPrefsRepository
import com.senzaproject.gamespace.core.assistant.GameSessionManager
import com.senzaproject.gamespace.core.assistant.ModeConfig
import com.senzaproject.gamespace.core.assistant.ModeDefaults
import com.senzaproject.gamespace.core.designsystem.ChoiceChips
import com.senzaproject.gamespace.core.designsystem.CommitSlider
import com.senzaproject.gamespace.core.designsystem.GmCard
import com.senzaproject.gamespace.core.designsystem.GmScreen
import com.senzaproject.gamespace.core.designsystem.GmSwitchRow
import com.senzaproject.gamespace.core.gaming.GameMode
import com.senzaproject.gamespace.core.permissions.AppPermission
import com.senzaproject.gamespace.core.permissions.PermissionRepository
import com.senzaproject.gamespace.feature.floating.profileLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameModeViewModel @Inject constructor(
    private val prefs: AssistantPrefsRepository,
    private val permissions: PermissionRepository,
    sessions: GameSessionManager,
) : ViewModel() {
    val configs: StateFlow<Map<GameMode, ModeConfig>> = prefs.modeConfigs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), GameMode.entries.associateWith { ModeDefaults.config(it) },
    )
    val defaultMode: StateFlow<GameMode> = prefs.defaultMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameMode.BALANCED)
    val active: StateFlow<ActiveSession?> = sessions.active

    private val _granted = MutableStateFlow(read())
    val granted: StateFlow<Map<AppPermission, Boolean>> = _granted.asStateFlow()

    private fun read() = mapOf(
        AppPermission.WRITE_SETTINGS to permissions.isGranted(AppPermission.WRITE_SETTINGS),
        AppPermission.DND_ACCESS to permissions.isGranted(AppPermission.DND_ACCESS),
    )

    fun refresh() { _granted.value = read() }
    fun settingsIntent(permission: AppPermission): Intent = permissions.settingsIntent(permission)
    fun setDefault(mode: GameMode) { viewModelScope.launch { prefs.setDefaultMode(mode) } }
    fun update(mode: GameMode, transform: (ModeConfig) -> ModeConfig) { viewModelScope.launch { prefs.updateMode(mode, transform) } }
    fun reset(mode: GameMode) { viewModelScope.launch { prefs.resetMode(mode) } }
}

@Composable
fun modeName(mode: GameMode): String = stringResource(profileLabel(mode))

@Composable
fun GameModeScreen(onBack: () -> Unit, viewModel: GameModeViewModel = hiltViewModel()) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val defaultMode by viewModel.defaultMode.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val granted by viewModel.granted.collectAsStateWithLifecycle()
    var editingName by rememberSaveable { mutableStateOf(GameMode.BALANCED.name) }
    val editing = GameMode.valueOf(editingName)
    val config = configs.getValue(editing)
    val context = LocalContext.current
    val percent = { v: Int -> context.getString(R.string.value_percent, v) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    fun grant(permission: AppPermission) {
        try {
            context.startActivity(viewModel.settingsIntent(permission))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.perm_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    GmScreen(title = stringResource(R.string.screen_game_mode), onBack = onBack) {
        Text(stringResource(R.string.gm_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val session = active
        if (session != null) {
            GmCard {
                Text(stringResource(R.string.gm_active_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.fp_active_game, session.label, modeName(session.mode)), style = MaterialTheme.typography.bodyMedium)
            }
        }

        GmCard {
            Text(stringResource(R.string.gm_default_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.gm_default_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceChips(GameMode.entries, defaultMode, { modeName(it) }, viewModel::setDefault)
        }

        GmCard {
            Text(stringResource(R.string.gm_edit_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ChoiceChips(GameMode.entries, editing, { modeName(it) }, { editingName = it.name })

            GmSwitchRow(
                title = stringResource(R.string.gm_change_brightness),
                description = stringResource(R.string.gm_change_brightness_desc),
                checked = config.brightnessPercent != null,
                onCheckedChange = { on -> viewModel.update(editing) { it.copy(brightnessPercent = if (on) 50 else null) } },
            )
            config.brightnessPercent?.let { value ->
                CommitSlider(
                    title = stringResource(R.string.ov_brightness),
                    value = value,
                    range = 1..100,
                    valueText = percent,
                    onCommit = { v -> viewModel.update(editing) { it.copy(brightnessPercent = v) } },
                )
            }
            GmSwitchRow(
                title = stringResource(R.string.gm_dnd),
                description = stringResource(R.string.gm_dnd_desc),
                checked = config.dnd,
                onCheckedChange = { on -> viewModel.update(editing) { it.copy(dnd = on) } },
            )
            GmSwitchRow(
                title = stringResource(R.string.gm_keep_awake),
                description = stringResource(R.string.gm_keep_awake_desc),
                checked = config.keepScreenOn,
                onCheckedChange = { on -> viewModel.update(editing) { it.copy(keepScreenOn = on) } },
            )
            GmSwitchRow(
                title = stringResource(R.string.gm_change_volume),
                description = stringResource(R.string.gm_change_volume_desc),
                checked = config.volumePercent != null,
                onCheckedChange = { on -> viewModel.update(editing) { it.copy(volumePercent = if (on) 70 else null) } },
            )
            config.volumePercent?.let { value ->
                CommitSlider(
                    title = stringResource(R.string.ov_volume),
                    value = value,
                    range = 0..100,
                    valueText = percent,
                    onCommit = { v -> viewModel.update(editing) { it.copy(volumePercent = v) } },
                )
            }
            Text(
                stringResource(R.string.gm_interval, (ModeDefaults.monitorIntervalMs(editing) / 1000).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { viewModel.reset(editing) }) { Text(stringResource(R.string.gm_reset)) }
        }

        GmCard {
            Text(stringResource(R.string.gm_access_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            AccessRow(R.string.perm_write_title, granted[AppPermission.WRITE_SETTINGS] == true) { grant(AppPermission.WRITE_SETTINGS) }
            AccessRow(R.string.perm_dnd_title, granted[AppPermission.DND_ACCESS] == true) { grant(AppPermission.DND_ACCESS) }
        }

        GmCard {
            Text(stringResource(R.string.gm_limits_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.gm_limits_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccessRow(titleRes: Int, ok: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
        if (ok) {
            Text(stringResource(R.string.perm_granted), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        } else {
            OutlinedButton(onClick = onGrant) { Text(stringResource(R.string.perm_grant)) }
        }
    }
}
