package com.senzaproject.gamespace.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.assistant.SessionSpan
import com.senzaproject.gamespace.core.assistant.StatsRange
import com.senzaproject.gamespace.core.assistant.StatsSummary
import com.senzaproject.gamespace.core.assistant.formatDuration
import com.senzaproject.gamespace.core.assistant.summarizeSessions
import com.senzaproject.gamespace.core.database.SessionDao
import com.senzaproject.gamespace.core.designsystem.ChoiceChips
import com.senzaproject.gamespace.core.designsystem.GmBarChart
import com.senzaproject.gamespace.core.designsystem.GmCard
import com.senzaproject.gamespace.core.designsystem.GmScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class StatsUiState(val range: StatsRange, val summary: StatsSummary?)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val sessionDao: SessionDao,
) : ViewModel() {
    private val range = MutableStateFlow(StatsRange.WEEK)

    val state: StateFlow<StatsUiState> = combine(sessionDao.observeAll(), range) { rows, selected ->
        // A session that is still open counts up to the last time it was seen.
        val spans = rows.map { SessionSpan(it.packageName, it.label, it.startedAt, it.endedAt ?: it.lastSeenAt) }
        StatsUiState(
            range = selected,
            summary = summarizeSessions(
                spans = spans,
                nowMs = System.currentTimeMillis(),
                rangeDays = selected.days,
                chartDays = if (selected == StatsRange.WEEK) 7 else 30,
                zone = ZoneId.systemDefault(),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState(StatsRange.WEEK, null))

    fun setRange(value: StatsRange) { range.value = value }
    fun reset() { viewModelScope.launch { sessionDao.deleteAll() } }
}

@Composable
fun StatisticsScreen(onBack: () -> Unit, viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val summary = state.summary

    GmScreen(title = stringResource(R.string.screen_statistics), onBack = onBack) {
        ChoiceChips(
            options = StatsRange.entries,
            selected = state.range,
            label = {
                stringResource(
                    when (it) {
                        StatsRange.WEEK -> R.string.st_range_week
                        StatsRange.MONTH -> R.string.st_range_month
                        StatsRange.ALL -> R.string.st_range_all
                    },
                )
            },
            onSelect = viewModel::setRange,
        )
        if (summary == null || summary.sessionCount == 0 && summary.totalMs == 0L) {
            GmCard {
                Text(stringResource(R.string.st_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.st_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            GmCard {
                SummaryRow(R.string.st_total, formatDuration(summary.totalMs))
                SummaryRow(R.string.st_sessions, summary.sessionCount.toString())
                SummaryRow(R.string.st_average, formatDuration(summary.averageMs))
            }
            GmCard {
                Text(
                    stringResource(if (state.range == StatsRange.WEEK) R.string.st_chart_week else R.string.st_chart_month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                GmBarChart(summary.dailyMs)
            }
            GmCard {
                Text(stringResource(R.string.st_per_game), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                summary.perGame.forEach { game ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(game.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.st_game_value, formatDuration(game.totalMs), game.sessions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Text(stringResource(R.string.st_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.st_reset)) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.st_reset_title)) },
            text = { Text(stringResource(R.string.st_reset_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.reset(); confirmReset = false }) { Text(stringResource(R.string.st_reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SummaryRow(labelRes: Int, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
