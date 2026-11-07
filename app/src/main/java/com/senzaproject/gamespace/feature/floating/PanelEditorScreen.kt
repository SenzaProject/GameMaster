package com.senzaproject.gamespace.feature.floating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.assistant.AssistantPrefsRepository
import com.senzaproject.gamespace.core.assistant.HandleSide
import com.senzaproject.gamespace.core.assistant.HandleStyle
import com.senzaproject.gamespace.core.assistant.MonitorWidget
import com.senzaproject.gamespace.core.assistant.OverlayConfig
import com.senzaproject.gamespace.core.assistant.OverlayVisibility
import com.senzaproject.gamespace.core.assistant.QuickAction
import com.senzaproject.gamespace.core.assistant.SizePreset
import com.senzaproject.gamespace.core.assistant.moveItem
import com.senzaproject.gamespace.core.designsystem.ChoiceChips
import com.senzaproject.gamespace.core.designsystem.CommitSlider
import com.senzaproject.gamespace.core.designsystem.GmCard
import com.senzaproject.gamespace.core.designsystem.GmScreen
import com.senzaproject.gamespace.core.designsystem.GmSwitchRow
import com.senzaproject.gamespace.core.designsystem.ReorderableColumn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PanelEditorViewModel @Inject constructor(
    private val prefs: AssistantPrefsRepository,
) : ViewModel() {
    val config: StateFlow<OverlayConfig> =
        prefs.overlayConfig.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverlayConfig())

    /** Every change is saved at once. A running panel picks it up immediately. */
    fun update(transform: (OverlayConfig) -> OverlayConfig) {
        viewModelScope.launch { prefs.updateOverlay(transform) }
    }
}

@Composable
fun PanelEditorScreen(onBack: () -> Unit, viewModel: PanelEditorViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val percent = { v: Int -> context.getString(R.string.value_percent, v) }

    GmScreen(title = stringResource(R.string.screen_panel_editor), onBack = onBack) {
        Text(stringResource(R.string.pe_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        GmCard {
            Section(R.string.pe_section_position)
            ChoiceChips(HandleSide.entries, config.side, { stringResource(sideLabel(it)) }, { v -> viewModel.update { it.copy(side = v) } })
            CommitSlider(
                title = stringResource(R.string.pe_vertical),
                value = config.verticalPercent,
                range = 0..100,
                valueText = percent,
                onCommit = { v -> viewModel.update { it.copy(verticalPercent = v) } },
            )
            Text(stringResource(R.string.pe_drag_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        GmCard {
            Section(R.string.pe_section_handle)
            ChoiceChips(HandleStyle.entries, config.handleStyle, { stringResource(styleLabel(it)) }, { v -> viewModel.update { it.copy(handleStyle = v) } })
            ChoiceChips(SizePreset.entries, config.handleSize, { stringResource(sizeLabel(it)) }, { v -> viewModel.update { it.copy(handleSize = v) } })
            CommitSlider(
                title = stringResource(R.string.pe_opacity),
                value = config.handleOpacityPercent,
                range = OverlayConfig.MIN_HANDLE_OPACITY..100,
                valueText = percent,
                onCommit = { v -> viewModel.update { it.copy(handleOpacityPercent = v) } },
            )
            GmSwitchRow(
                title = stringResource(R.string.pe_fps_handle),
                description = stringResource(R.string.pe_fps_handle_desc),
                checked = config.fpsOnHandle,
                onCheckedChange = { v -> viewModel.update { it.copy(fpsOnHandle = v) } },
            )
        }

        GmCard {
            Section(R.string.pe_section_panel)
            ChoiceChips(SizePreset.entries, config.panelSize, { stringResource(sizeLabel(it)) }, { v -> viewModel.update { it.copy(panelSize = v) } })
            CommitSlider(
                title = stringResource(R.string.pe_opacity),
                value = config.panelOpacityPercent,
                range = OverlayConfig.MIN_PANEL_OPACITY..100,
                valueText = percent,
                onCommit = { v -> viewModel.update { it.copy(panelOpacityPercent = v) } },
            )
        }

        GmCard {
            Section(R.string.pe_section_widgets)
            MonitorWidget.entries.forEach { widget ->
                GmSwitchRow(
                    title = stringResource(widgetLabel(widget)),
                    description = null,
                    checked = widget in config.visibleWidgets,
                    onCheckedChange = { on ->
                        viewModel.update { c -> c.copy(visibleWidgets = if (on) c.visibleWidgets + widget else c.visibleWidgets - widget) }
                    },
                )
            }
        }

        GmCard {
            Section(R.string.pe_section_shortcuts)
            Text(stringResource(R.string.pe_shortcuts_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Local copy while dragging; saved once when the finger lifts.
            var order by remember(config.quickActionOrder) { mutableStateOf(config.quickActionOrder) }
            ReorderableColumn(
                items = order,
                itemKey = { it },
                rowHeight = 56.dp,
                onMove = { from, to -> order = moveItem(order, from, to) },
                onDragEnd = { viewModel.update { it.copy(quickActionOrder = order) } },
            ) { action: QuickAction, handle ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.pe_drag_handle),
                        modifier = handle.padding(end = 12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(quickActionLabel(action)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(
                        checked = action !in config.hiddenQuickActions,
                        onCheckedChange = { on ->
                            viewModel.update { c -> c.copy(hiddenQuickActions = if (on) c.hiddenQuickActions - action else c.hiddenQuickActions + action) }
                        },
                    )
                }
            }
        }

        GmCard {
            Section(R.string.pe_section_behavior)
            Text(stringResource(R.string.pe_visibility), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(OverlayVisibility.entries, config.visibility, { stringResource(visibilityLabel(it)) }, { v -> viewModel.update { it.copy(visibility = v) } })
            GmSwitchRow(
                title = stringResource(R.string.pe_collapse_after),
                description = stringResource(R.string.pe_collapse_after_desc),
                checked = config.collapseAfterAction,
                onCheckedChange = { v -> viewModel.update { it.copy(collapseAfterAction = v) } },
            )
        }
    }
}

@Composable
private fun Section(titleRes: Int) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}
