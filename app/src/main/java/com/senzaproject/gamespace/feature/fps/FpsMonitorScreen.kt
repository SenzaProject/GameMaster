package com.senzaproject.gamespace.feature.fps

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.common.FeatureStatus
import com.senzaproject.gamespace.core.designsystem.GmCard
import com.senzaproject.gamespace.core.designsystem.GmMetricRow
import com.senzaproject.gamespace.core.designsystem.GmScreen
import com.senzaproject.gamespace.core.designsystem.GmSparkline
import com.senzaproject.gamespace.core.designsystem.StatusChip

@Composable
fun FpsMonitorScreen(
    onBack: () -> Unit,
    viewModel: FpsMonitorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    // The display test keeps the screen redrawing, so measuring always ends when the screen is left.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    GmScreen(title = stringResource(R.string.screen_fps_monitor), onBack = onBack) {
        StatusChip(FeatureStatus.PARTIAL)

        GmCard {
            Text(stringResource(R.string.fps_source_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.source == FpsSourceKind.DISPLAY_TEST,
                    onClick = { viewModel.selectSource(FpsSourceKind.DISPLAY_TEST) },
                    label = { Text(stringResource(R.string.fps_source_display)) },
                )
                FilterChip(
                    selected = state.source == FpsSourceKind.GAME,
                    onClick = { viewModel.selectSource(FpsSourceKind.GAME) },
                    label = { Text(stringResource(R.string.fps_source_game)) },
                )
            }
            Body(if (state.source == FpsSourceKind.DISPLAY_TEST) R.string.fps_display_explain else R.string.fps_game_explain)
        }

        if (state.source == FpsSourceKind.GAME) {
            GmCard {
                Text(stringResource(R.string.fps_adb_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Body(R.string.fps_adb_body)
                Text(
                    text = viewModel.grantCommand,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(viewModel.grantCommand)) }) {
                        Text(stringResource(R.string.fps_adb_copy))
                    }
                    OutlinedButton(onClick = viewModel::refreshPermission) {
                        Text(stringResource(R.string.fps_adb_check))
                    }
                }
                Text(
                    text = stringResource(if (state.dumpGranted) R.string.fps_dump_granted else R.string.fps_dump_missing),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.dumpGranted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                )
            }
            GmCard {
                Text(stringResource(R.string.fps_game_pick), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (games.isEmpty()) {
                    Body(R.string.fps_game_none)
                }
                games.forEach { game ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.selectGame(game.packageName) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.gamePackage == game.packageName, onClick = { viewModel.selectGame(game.packageName) })
                        Text(game.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        GmCard {
            val canStart = state.source == FpsSourceKind.DISPLAY_TEST || state.gamePackage != null
            Button(
                onClick = if (state.running) viewModel::stop else viewModel::start,
                enabled = state.running || canStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (state.running) R.string.fps_stop else R.string.fps_start))
            }
            state.problem?.let { problem ->
                Text(
                    text = stringResource(problemText(problem)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val summary = state.summary
            if (summary == null) {
                if (state.problem == null) Body(R.string.fps_no_data_yet)
            } else {
                GmMetricRow(stringResource(R.string.fps_current), stringResource(R.string.value_fps, summary.current))
                GmMetricRow(stringResource(R.string.fps_average), stringResource(R.string.value_fps, summary.average))
                GmMetricRow(stringResource(R.string.fps_min), stringResource(R.string.value_fps, summary.minimum))
                GmMetricRow(stringResource(R.string.fps_max), stringResource(R.string.value_fps, summary.maximum))
                GmMetricRow(
                    stringResource(R.string.fps_frame_time),
                    summary.worstFrameMs?.let { stringResource(R.string.value_ms_decimal, it) }
                        ?: stringResource(R.string.value_unavailable),
                )
                GmMetricRow(
                    stringResource(R.string.fps_stability),
                    summary.stabilityPercent?.let { stringResource(R.string.value_percent, it) }
                        ?: stringResource(R.string.value_unavailable),
                )
                Body(R.string.fps_stability_note)
                Text(stringResource(R.string.fps_history), style = MaterialTheme.typography.titleSmall)
                GmSparkline(summary.history, minValue = 0f)
            }
        }
        Body(R.string.fps_overlay_note)
    }
}

@StringRes
private fun problemText(problem: FpsProblem): Int = when (problem) {
    FpsProblem.DUMP_NOT_GRANTED -> R.string.fps_problem_dump
    FpsProblem.LAYER_NOT_FOUND -> R.string.fps_problem_layer
    FpsProblem.COMMAND_FAILED -> R.string.fps_problem_command
    FpsProblem.NO_DATA -> R.string.fps_problem_nodata
}

@Composable
private fun Body(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
