package com.senzaproject.gamespace.feature.devicemonitor

import androidx.annotation.StringRes
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.monitoring.BatteryHealth
import com.senzaproject.gamespace.core.monitoring.NetworkType
import com.senzaproject.gamespace.core.monitoring.PowerSource
import com.senzaproject.gamespace.core.monitoring.ThermalLevel

@StringRes
fun thermalLabel(level: ThermalLevel): Int = when (level) {
    ThermalLevel.NONE -> R.string.thermal_none
    ThermalLevel.LIGHT -> R.string.thermal_light
    ThermalLevel.MODERATE -> R.string.thermal_moderate
    ThermalLevel.SEVERE -> R.string.thermal_severe
    ThermalLevel.CRITICAL -> R.string.thermal_critical
    ThermalLevel.EMERGENCY -> R.string.thermal_emergency
    ThermalLevel.SHUTDOWN -> R.string.thermal_shutdown
    ThermalLevel.UNKNOWN -> R.string.value_unknown
}

@StringRes
fun powerSourceLabel(source: PowerSource): Int = when (source) {
    PowerSource.NONE -> R.string.source_none
    PowerSource.AC -> R.string.source_ac
    PowerSource.USB -> R.string.source_usb
    PowerSource.WIRELESS -> R.string.source_wireless
    PowerSource.DOCK -> R.string.source_dock
    PowerSource.UNKNOWN -> R.string.value_unknown
}

@StringRes
fun batteryHealthLabel(health: BatteryHealth): Int = when (health) {
    BatteryHealth.GOOD -> R.string.health_good
    BatteryHealth.OVERHEAT -> R.string.health_overheat
    BatteryHealth.DEAD -> R.string.health_dead
    BatteryHealth.OVER_VOLTAGE -> R.string.health_over_voltage
    BatteryHealth.FAILURE -> R.string.health_failure
    BatteryHealth.COLD -> R.string.health_cold
    BatteryHealth.UNKNOWN -> R.string.value_unknown
}

@StringRes
fun networkTypeLabel(type: NetworkType): Int = when (type) {
    NetworkType.NONE -> R.string.net_type_none
    NetworkType.WIFI -> R.string.net_type_wifi
    NetworkType.CELLULAR -> R.string.net_type_cellular
    NetworkType.ETHERNET -> R.string.net_type_ethernet
    NetworkType.BLUETOOTH -> R.string.net_type_bluetooth
    NetworkType.VPN -> R.string.net_type_vpn
    NetworkType.OTHER -> R.string.net_type_other
}
