package com.senzaproject.gamespace.feature.devicemonitor

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.common.FeatureStatus
import com.senzaproject.gamespace.core.common.percentOf
import com.senzaproject.gamespace.core.designsystem.GmCard
import com.senzaproject.gamespace.core.designsystem.GmMetricRow
import com.senzaproject.gamespace.core.designsystem.GmMeter
import com.senzaproject.gamespace.core.designsystem.GmScreen
import com.senzaproject.gamespace.core.designsystem.GmSparkline
import com.senzaproject.gamespace.core.designsystem.StatusChip
import com.senzaproject.gamespace.core.monitoring.CpuSnapshot
import com.senzaproject.gamespace.core.monitoring.DeviceMonitorViewModel
import com.senzaproject.gamespace.core.monitoring.DeviceSnapshot
import com.senzaproject.gamespace.core.monitoring.GpuInfo
import com.senzaproject.gamespace.core.monitoring.GpuLoad
import com.senzaproject.gamespace.core.monitoring.ThermalSnapshot
import com.senzaproject.gamespace.core.monitoring.isThermalWarning

@Composable
fun DeviceMonitorScreen(
    onBack: () -> Unit,
    viewModel: DeviceMonitorViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val cpu by viewModel.cpu.collectAsStateWithLifecycle()
    val gpuInfo by viewModel.gpuInfo.collectAsStateWithLifecycle()
    val gpuLoad by viewModel.gpuLoad.collectAsStateWithLifecycle()
    val thermal by viewModel.thermal.collectAsStateWithLifecycle()
    val memoryHistory by viewModel.memoryHistory.collectAsStateWithLifecycle()

    GmScreen(title = stringResource(R.string.screen_device_monitor), onBack = onBack) {
        StatusChip(FeatureStatus.PARTIAL)
        Text(
            text = snapshot?.model ?: "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        CpuCard(cpu)
        GpuCard(gpuInfo, gpuLoad)
        MemoryCard(snapshot, memoryHistory)
        StorageCard(snapshot)
        BatteryCard(snapshot)
        ThermalCard(thermal)
    }
}

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
private fun CpuCard(cpu: CpuSnapshot?) {
    val unavailable = stringResource(R.string.value_unavailable)
    GmCard {
        SectionTitle(R.string.dm_section_cpu)
        if (cpu == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@GmCard
        }
        GmMetricRow(stringResource(R.string.metric_cpu_hardware), cpu.hardware ?: unavailable)
        GmMetricRow(stringResource(R.string.metric_abi), cpu.abi ?: unavailable)
        GmMetricRow(stringResource(R.string.metric_cpu_cores), cpu.coreCount.toString())
        GmMetricRow(
            stringResource(R.string.metric_cpu_usage),
            cpu.devicePercent?.let { stringResource(R.string.value_percent, it) } ?: unavailable,
        )
        GmMetricRow(
            stringResource(R.string.metric_cpu_app_usage),
            cpu.appPercent?.let { stringResource(R.string.value_percent_decimal, it) } ?: unavailable,
        )
        if (cpu.coreFrequenciesMhz.all { it == null }) {
            GmMetricRow(stringResource(R.string.metric_cpu_freq), unavailable)
        } else {
            cpu.coreFrequenciesMhz.forEachIndexed { index, mhz ->
                GmMetricRow(
                    stringResource(R.string.metric_cpu_core, index),
                    mhz?.let { stringResource(R.string.value_mhz, it) } ?: unavailable,
                )
            }
        }
        Note(R.string.dm_cpu_note)
    }
}

@Composable
private fun GpuCard(info: GpuInfo?, load: GpuLoad?) {
    val unavailable = stringResource(R.string.value_unavailable)
    GmCard {
        SectionTitle(R.string.dm_section_gpu)
        if (info == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@GmCard
        }
        GmMetricRow(stringResource(R.string.metric_gpu_renderer), info.renderer ?: unavailable)
        GmMetricRow(stringResource(R.string.metric_gpu_vendor), info.vendor ?: unavailable)
        GmMetricRow(stringResource(R.string.metric_gl_version), info.glVersion ?: unavailable)
        GmMetricRow(stringResource(R.string.metric_gles_system), info.glesVersion ?: unavailable)
        GmMetricRow(stringResource(R.string.metric_vulkan), info.vulkanVersion ?: unavailable)
        GmMetricRow(
            stringResource(R.string.metric_gpu_load),
            load?.busyPercent?.let { stringResource(R.string.value_percent, it) } ?: unavailable,
        )
        GmMetricRow(
            stringResource(R.string.metric_gpu_freq),
            load?.frequencyMhz?.let { stringResource(R.string.value_mhz, it) } ?: unavailable,
        )
        Note(R.string.dm_gpu_note)
    }
}

@Composable
private fun MemoryCard(snapshot: DeviceSnapshot?, history: List<Float>) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.value_unavailable)
    GmCard {
        SectionTitle(R.string.dm_section_ram)
        val memory = snapshot?.memory
        if (snapshot == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (memory == null) {
            GmMetricRow(stringResource(R.string.metric_ram_used), unavailable)
        } else {
            val used = memory.totalBytes - memory.availableBytes
            val percent = percentOf(used, memory.totalBytes)
            GmMetricRow(
                stringResource(R.string.metric_ram_used),
                "${Formatter.formatFileSize(context, used)} (${stringResource(R.string.value_percent, percent)})",
            )
            GmMetricRow(stringResource(R.string.metric_ram_available), Formatter.formatFileSize(context, memory.availableBytes))
            GmMetricRow(stringResource(R.string.metric_ram_total), Formatter.formatFileSize(context, memory.totalBytes))
            GmMeter(percent)
            Note(R.string.dm_ram_history)
            GmSparkline(history, minValue = 0f, maxValue = 100f)
        }
    }
}

@Composable
private fun StorageCard(snapshot: DeviceSnapshot?) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.value_unavailable)
    GmCard {
        SectionTitle(R.string.dm_section_storage)
        val storage = snapshot?.storage
        if (snapshot == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (storage == null) {
            GmMetricRow(stringResource(R.string.metric_storage_available), unavailable)
        } else {
            GmMetricRow(stringResource(R.string.metric_storage_available), Formatter.formatFileSize(context, storage.availableBytes))
            GmMetricRow(stringResource(R.string.metric_storage_total), Formatter.formatFileSize(context, storage.totalBytes))
            GmMeter(percentOf(storage.totalBytes - storage.availableBytes, storage.totalBytes))
        }
    }
}

@Composable
private fun BatteryCard(snapshot: DeviceSnapshot?) {
    val unavailable = stringResource(R.string.value_unavailable)
    GmCard {
        SectionTitle(R.string.dm_section_battery)
        val battery = snapshot?.battery
        if (snapshot == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@GmCard
        }
        if (battery == null) {
            GmMetricRow(stringResource(R.string.metric_battery), unavailable)
            return@GmCard
        }
        GmMetricRow(
            stringResource(R.string.metric_battery),
            battery.percent?.let { stringResource(R.string.value_percent, it) } ?: unavailable,
        )
        GmMetricRow(
            stringResource(R.string.metric_charging),
            stringResource(if (battery.charging) R.string.value_yes else R.string.value_no),
        )
        GmMetricRow(stringResource(R.string.metric_power_source), stringResource(powerSourceLabel(battery.powerSource)))
        GmMetricRow(stringResource(R.string.metric_battery_health), stringResource(batteryHealthLabel(battery.health)))
        GmMetricRow(stringResource(R.string.metric_battery_tech), battery.technology ?: unavailable)
        GmMetricRow(
            stringResource(R.string.metric_battery_temp),
            battery.temperatureCelsius?.let { stringResource(R.string.value_celsius, it) } ?: unavailable,
        )
        GmMetricRow(
            stringResource(R.string.metric_voltage),
            battery.voltageMillivolts?.let { stringResource(R.string.value_millivolt, it) } ?: unavailable,
        )
        GmMetricRow(
            stringResource(R.string.metric_battery_current),
            battery.currentMicroAmps?.let { stringResource(R.string.value_milliamp, it / 1000) } ?: unavailable,
        )
    }
}

@Composable
private fun ThermalCard(thermal: ThermalSnapshot?) {
    val unavailable = stringResource(R.string.value_unavailable)
    GmCard {
        SectionTitle(R.string.dm_section_thermal)
        if (thermal == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@GmCard
        }
        GmMetricRow(stringResource(R.string.metric_thermal_status), stringResource(thermalLabel(thermal.level)))
        GmMetricRow(
            stringResource(R.string.metric_thermal_headroom),
            thermal.headroom?.let { stringResource(R.string.value_headroom, it) } ?: unavailable,
        )
        if (isThermalWarning(thermal.level)) {
            Text(
                text = stringResource(R.string.thermal_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
        }
        if (thermal.zones.isEmpty()) {
            Note(R.string.dm_thermal_zones_none)
        } else {
            thermal.zones.forEach { zone ->
                GmMetricRow(zone.name, stringResource(R.string.value_celsius, zone.celsius))
            }
            Note(R.string.dm_thermal_note)
        }
    }
}
