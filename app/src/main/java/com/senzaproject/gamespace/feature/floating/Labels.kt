package com.senzaproject.gamespace.feature.floating

import androidx.annotation.StringRes
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.assistant.HandleSide
import com.senzaproject.gamespace.core.assistant.HandleStyle
import com.senzaproject.gamespace.core.assistant.MonitorWidget
import com.senzaproject.gamespace.core.assistant.OverlayVisibility
import com.senzaproject.gamespace.core.assistant.QuickAction
import com.senzaproject.gamespace.core.assistant.SizePreset

@StringRes
fun widgetLabel(widget: MonitorWidget): Int = when (widget) {
    MonitorWidget.FPS -> R.string.ov_w_fps
    MonitorWidget.CPU -> R.string.ov_w_cpu
    MonitorWidget.GPU -> R.string.ov_w_gpu
    MonitorWidget.RAM -> R.string.ov_w_ram
    MonitorWidget.TEMPERATURE -> R.string.ov_w_temp
    MonitorWidget.BATTERY -> R.string.ov_w_battery
    MonitorWidget.THERMAL -> R.string.ov_w_thermal
    MonitorWidget.REFRESH_RATE -> R.string.ov_w_refresh
    MonitorWidget.NETWORK -> R.string.ov_w_network
    MonitorWidget.LATENCY -> R.string.ov_w_latency
}

@StringRes
fun quickActionLabel(action: QuickAction): Int = when (action) {
    QuickAction.WIFI_SETTINGS -> R.string.qa_wifi
    QuickAction.DND -> R.string.qa_dnd
    QuickAction.SCREENSHOT -> R.string.qa_screenshot
    QuickAction.SCREEN_RECORD -> R.string.qa_record
    QuickAction.DISPLAY_SETTINGS -> R.string.qa_display
    QuickAction.NETWORK_TEST -> R.string.qa_network_test
    QuickAction.GAME_MODE -> R.string.qa_game_mode
    QuickAction.TOOLBOX -> R.string.qa_toolbox
}

@StringRes
fun sideLabel(side: HandleSide): Int = if (side == HandleSide.LEFT) R.string.pe_side_left else R.string.pe_side_right

@StringRes
fun styleLabel(style: HandleStyle): Int = when (style) {
    HandleStyle.PILL -> R.string.pe_style_pill
    HandleStyle.BAR -> R.string.pe_style_bar
    HandleStyle.DOT -> R.string.pe_style_dot
}

@StringRes
fun sizeLabel(size: SizePreset): Int = when (size) {
    SizePreset.SMALL -> R.string.pe_size_small
    SizePreset.MEDIUM -> R.string.pe_size_medium
    SizePreset.LARGE -> R.string.pe_size_large
}

@StringRes
fun visibilityLabel(v: OverlayVisibility): Int =
    if (v == OverlayVisibility.ALWAYS) R.string.pe_vis_always else R.string.pe_vis_games
