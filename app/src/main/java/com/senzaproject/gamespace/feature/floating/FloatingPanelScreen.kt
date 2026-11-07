package com.senzaproject.gamespace.feature.floating

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.senzaproject.gamespace.core.assistant.ActiveSession
import com.senzaproject.gamespace.core.assistant.AssistantPrefsRepository
import com.senzaproject.gamespace.core.assistant.GameSessionManager
import com.senzaproject.gamespace.core.designsystem.GmCard
import com.senzaproject.gamespace.core.designsystem.GmScreen
import com.senzaproject.gamespace.core.designsystem.GmSwitchRow
import com.senzaproject.gamespace.core.permissions.AppPermission
import com.senzaproject.gamespace.core.permissions.PermissionRepository
import com.senzaproject.gamespace.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FloatingPanelViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AssistantPrefsRepository,
    private val permissions: PermissionRepository,
    sessions: GameSessionManager,
) : ViewModel() {
    val running: StateFlow<Boolean> = AssistantRuntime.running
    val active: StateFlow<ActiveSession?> = sessions.active
    val startOnBoot: StateFlow<Boolean> = prefs.startOnBoot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _granted = MutableStateFlow(readPermissions())
    val granted: StateFlow<Map<AppPermission, Boolean>> = _granted.asStateFlow()

    fun refresh() { _granted.value = readPermissions() }

    private fun readPermissions() = AppPermission.entries.associateWith { permissions.isGranted(it) }

    fun settingsIntent(permission: AppPermission): Intent = permissions.settingsIntent(permission)

    fun setRunning(on: Boolean) {
        viewModelScope.launch {
            prefs.setEnabled(on)
            if (on) FloatingPanelService.start(context) else FloatingPanelService.stop(context)
        }
    }

    fun setStartOnBoot(value: Boolean) {
        viewModelScope.launch { prefs.setStartOnBoot(value) }
    }
}

private data class PermissionRow(val permission: AppPermission, @StringRes val title: Int, @StringRes val why: Int)

private val permissionRows = listOf(
    PermissionRow(AppPermission.OVERLAY, R.string.perm_overlay_title, R.string.fp_why_overlay),
    PermissionRow(AppPermission.USAGE_ACCESS, R.string.perm_usage_title, R.string.fp_why_usage),
    PermissionRow(AppPermission.NOTIFICATIONS, R.string.perm_notif_title, R.string.fp_why_notif),
    PermissionRow(AppPermission.WRITE_SETTINGS, R.string.perm_write_title, R.string.fp_why_write),
    PermissionRow(AppPermission.DND_ACCESS, R.string.perm_dnd_title, R.string.fp_why_dnd),
    PermissionRow(AppPermission.SCREENSHOT_SERVICE, R.string.perm_a11y_title, R.string.fp_why_a11y),
)

@Composable
fun FloatingPanelScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: FloatingPanelViewModel = hiltViewModel(),
) {
    val running by viewModel.running.collectAsStateWithLifecycle()
    val granted by viewModel.granted.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val bootStart by viewModel.startOnBoot.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Special permissions change in system settings, so read them again when the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    fun grant(permission: AppPermission) {
        try {
            context.startActivity(viewModel.settingsIntent(permission))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.perm_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    val overlayGranted = granted[AppPermission.OVERLAY] == true
    val usageGranted = granted[AppPermission.USAGE_ACCESS] == true

    GmScreen(title = stringResource(R.string.screen_floating_panel), onBack = onBack) {
        GmCard {
            Text(
                stringResource(if (running) R.string.fp_status_running else R.string.fp_status_stopped),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GmSwitchRow(
                title = stringResource(R.string.fp_switch_title),
                description = stringResource(R.string.fp_switch_desc),
                checked = running,
                onCheckedChange = viewModel::setRunning,
                enabled = overlayGranted,
            )
            if (!overlayGranted) {
                Text(stringResource(R.string.fp_need_overlay), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Button(onClick = { grant(AppPermission.OVERLAY) }) { Text(stringResource(R.string.perm_grant)) }
            } else if (!usageGranted) {
                Text(stringResource(R.string.fp_no_usage_warning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.fp_games_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val session = active
            if (running && session != null) {
                Text(
                    stringResource(R.string.fp_active_game, session.label, stringResource(profileLabel(session.mode))),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        GmCard {
            Text(stringResource(R.string.fp_permissions_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            permissionRows.forEach { row ->
                val ok = granted[row.permission] == true
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(row.title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (ok) R.string.perm_granted else R.string.perm_not_granted),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(row.why), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!ok) OutlinedButton(onClick = { grant(row.permission) }) { Text(stringResource(R.string.perm_grant)) }
            }
            Text(stringResource(R.string.fp_restricted_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        GmCard {
            GmSwitchRow(
                title = stringResource(R.string.fp_boot_title),
                description = stringResource(R.string.fp_boot_desc),
                checked = bootStart,
                onCheckedChange = viewModel::setStartOnBoot,
            )
        }

        GmCard {
            Text(stringResource(R.string.fp_customize_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(onClick = { onOpen(Routes.PanelEditor) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fp_open_editor)) }
            OutlinedButton(onClick = { onOpen(Routes.DockEditor) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fp_open_dock)) }
            OutlinedButton(onClick = { onOpen(Routes.GameMode) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.screen_game_mode)) }
            OutlinedButton(onClick = { onOpen(Routes.Statistics) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.screen_statistics)) }
        }

        GmCard {
            Text(stringResource(R.string.fp_limits_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.fp_limits_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
