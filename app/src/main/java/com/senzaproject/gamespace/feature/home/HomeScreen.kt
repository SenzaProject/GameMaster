package com.senzaproject.gamespace.feature.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.designsystem.GmCard
import com.senzaproject.gamespace.core.designsystem.GmScreen
import com.senzaproject.gamespace.core.designsystem.StatusChip
import com.senzaproject.gamespace.core.monitoring.DeviceMonitorViewModel
import com.senzaproject.gamespace.feature.devicemonitor.DeviceMetricRows
import com.senzaproject.gamespace.navigation.FeatureCatalog
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpen: (route: String) -> Unit,
    viewModel: DeviceMonitorViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
    }

    GmScreen(title = stringResource(R.string.screen_home)) {
        Text(
            text = stringResource(greeting),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.home_phase_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GmCard(onClick = { onOpen(com.senzaproject.gamespace.navigation.Routes.DeviceMonitor) }) {
            Text(stringResource(R.string.home_overview), style = MaterialTheme.typography.titleMedium)
            val current = snapshot
            if (current == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                DeviceMetricRows(current, detailed = false)
            }
        }
        Text(stringResource(R.string.home_all_screens), style = MaterialTheme.typography.titleMedium)
        FeatureCatalog.entries.forEach { entry ->
            GmCard(onClick = { onOpen(entry.route) }) {
                Text(stringResource(entry.titleRes), style = MaterialTheme.typography.titleSmall)
                StatusChip(entry.status, entry.phase)
            }
        }
    }
}
