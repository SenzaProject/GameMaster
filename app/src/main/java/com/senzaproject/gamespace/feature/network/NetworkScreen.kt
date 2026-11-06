package com.senzaproject.gamespace.feature.network

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.senzaproject.gamespace.core.monitoring.NetworkSnapshot
import com.senzaproject.gamespace.core.monitoring.NetworkType
import com.senzaproject.gamespace.core.monitoring.PingRepository
import com.senzaproject.gamespace.feature.devicemonitor.networkTypeLabel

@Composable
fun NetworkScreen(
    onBack: () -> Unit,
    viewModel: NetworkViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val download by viewModel.downloadHistory.collectAsStateWithLifecycle()
    val upload by viewModel.uploadHistory.collectAsStateWithLifecycle()
    val ping by viewModel.ping.collectAsStateWithLifecycle()

    // Latency tests use the network, so they end when the app leaves the foreground or the screen closes.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    GmScreen(title = stringResource(R.string.screen_network), onBack = onBack) {
        StatusChip(FeatureStatus.PARTIAL)
        ConnectionCard(snapshot)
        TrafficCard(snapshot, download, upload)

        GmCard {
            SectionTitle(R.string.net_section_ping)
            Text(stringResource(R.string.net_ping_target), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.targets.forEach { target ->
                    FilterChip(
                        selected = ping.targetId == target.id,
                        onClick = { viewModel.selectTarget(target.id) },
                        label = { Text(stringResource(targetLabel(target.id))) },
                    )
                }
            }
            Button(
                onClick = if (ping.running) viewModel::stop else viewModel::start,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (ping.running) R.string.net_ping_stop else R.string.net_ping_start))
            }
            if (ping.resolveFailed) {
                Text(
                    stringResource(R.string.net_ping_resolve_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val stats = ping.stats
            if (stats != null) {
                GmMetricRow(stringResource(R.string.net_ping_latest), millisText(ping.samples.lastOrNull()))
                GmMetricRow(stringResource(R.string.net_ping_avg), millisText(stats.avgMs))
                GmMetricRow(stringResource(R.string.net_ping_min), millisText(stats.minMs))
                GmMetricRow(stringResource(R.string.net_ping_max), millisText(stats.maxMs))
                GmMetricRow(stringResource(R.string.net_ping_jitter), millisText(stats.jitterMs))
                GmMetricRow(
                    stringResource(R.string.net_ping_loss),
                    stringResource(R.string.net_ping_loss_value, stats.sent - stats.received, stats.sent, stats.lossPercent),
                )
                Text(stringResource(R.string.net_ping_history), style = MaterialTheme.typography.titleSmall)
                // Timed-out attempts are left out of the line; they are counted in "lost attempts" above.
                GmSparkline(ping.samples.filterNotNull().map { it.toFloat() }, minValue = 0f)
            }
            Note(R.string.net_ping_note)
        }
        Note(R.string.net_scope_note)
    }
}

@StringRes
private fun targetLabel(id: String): Int =
    if (id == PingRepository.TARGET_GOOGLE) R.string.net_target_google else R.string.net_target_cloudflare

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Note(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun millisText(value: Int?): String =
    value?.let { stringResource(R.string.value_ms_int, it) } ?: stringResource(R.string.value_unavailable)

@Composable
private fun rateText(bytesPerSecond: Long?): String {
    val context = LocalContext.current
    return bytesPerSecond?.let { stringResource(R.string.net_rate, Formatter.formatFileSize(context, it)) }
        ?: stringResource(R.string.value_unavailable)
}

@Composable
private fun ConnectionCard(snapshot: NetworkSnapshot?) {
    val unavailable = stringResource(R.string.value_unavailable)
    GmCard {
        SectionTitle(R.string.net_section_connection)
        if (snapshot == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@GmCard
        }
        GmMetricRow(stringResource(R.string.metric_net_type), stringResource(networkTypeLabel(snapshot.type)))
        if (snapshot.type == NetworkType.NONE) {
            Note(R.string.net_no_network)
            return@GmCard
        }
        GmMetricRow(
            stringResource(R.string.metric_net_internet),
            stringResource(if (snapshot.validated) R.string.value_yes else R.string.value_no),
        )
        GmMetricRow(
            stringResource(R.string.metric_net_metered),
            stringResource(if (snapshot.metered) R.string.value_yes else R.string.value_no),
        )
        GmMetricRow(
            stringResource(R.string.metric_net_signal),
            snapshot.signalDbm?.let { stringResource(R.string.value_dbm, it) } ?: unavailable,
        )
        if (snapshot.type == NetworkType.WIFI) {
            GmMetricRow(
                stringResource(R.string.metric_net_link),
                snapshot.wifiLinkMbps?.let { stringResource(R.string.value_mbps, it) } ?: unavailable,
            )
            GmMetricRow(
                stringResource(R.string.metric_net_freq),
                snapshot.wifiFrequencyMhz?.let { stringResource(R.string.value_mhz, it) } ?: unavailable,
            )
        }
        GmMetricRow(
            stringResource(R.string.metric_net_est_down),
            snapshot.downstreamKbps?.let { stringResource(R.string.value_mbps_decimal, it / 1000f) } ?: unavailable,
        )
        GmMetricRow(
            stringResource(R.string.metric_net_est_up),
            snapshot.upstreamKbps?.let { stringResource(R.string.value_mbps_decimal, it / 1000f) } ?: unavailable,
        )
        Note(R.string.net_estimate_note)
    }
}

@Composable
private fun TrafficCard(snapshot: NetworkSnapshot?, download: List<Float>, upload: List<Float>) {
    GmCard {
        SectionTitle(R.string.net_section_traffic)
        GmMetricRow(stringResource(R.string.metric_net_rx), rateText(snapshot?.rxBytesPerSecond))
        GmMetricRow(stringResource(R.string.metric_net_tx), rateText(snapshot?.txBytesPerSecond))
        Text(stringResource(R.string.net_history_down), style = MaterialTheme.typography.titleSmall)
        GmSparkline(download, minValue = 0f)
        Text(stringResource(R.string.net_history_up), style = MaterialTheme.typography.titleSmall)
        GmSparkline(upload, minValue = 0f, color = MaterialTheme.colorScheme.secondary)
        Note(R.string.net_traffic_note)
    }
}
