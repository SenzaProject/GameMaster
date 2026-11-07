package com.senzaproject.gamespace.core.assistant

import com.senzaproject.gamespace.core.database.GameProfileEntity
import com.senzaproject.gamespace.core.gaming.GameMode

// Pure data and rules for the in-game assistant. No Android dependencies, so everything here is unit-tested on the JVM.

enum class HandleSide { LEFT, RIGHT }
enum class HandleStyle { PILL, BAR, DOT }
enum class SizePreset { SMALL, MEDIUM, LARGE }

/** ALWAYS keeps the handle on screen. ONLY_IN_GAMES shows it while a library game is in the foreground. */
enum class OverlayVisibility { ALWAYS, ONLY_IN_GAMES }

enum class MonitorWidget { FPS, CPU, GPU, RAM, TEMPERATURE, BATTERY, THERMAL, REFRESH_RATE, NETWORK, LATENCY }

enum class QuickAction { WIFI_SETTINGS, DND, SCREENSHOT, SCREEN_RECORD, DISPLAY_SETTINGS, NETWORK_TEST, GAME_MODE, TOOLBOX }

/** Everything the user can customise about the floating handle and panel. All of it is persisted. */
data class OverlayConfig(
    val side: HandleSide = HandleSide.RIGHT,
    /** 0 = top of the screen, 100 = bottom. Stored as a percentage so it survives rotation. */
    val verticalPercent: Int = 35,
    val handleStyle: HandleStyle = HandleStyle.PILL,
    val handleSize: SizePreset = SizePreset.MEDIUM,
    val handleOpacityPercent: Int = 70,
    val panelOpacityPercent: Int = 88,
    val panelSize: SizePreset = SizePreset.MEDIUM,
    val visibleWidgets: Set<MonitorWidget> = DEFAULT_WIDGETS,
    /** Every action, in display order. Hidden ones are listed in [hiddenQuickActions]. */
    val quickActionOrder: List<QuickAction> = QuickAction.entries,
    val hiddenQuickActions: Set<QuickAction> = emptySet(),
    val visibility: OverlayVisibility = OverlayVisibility.ONLY_IN_GAMES,
    /** Shows the live FPS number on the collapsed handle. Needs the same FPS source as the panel. */
    val fpsOnHandle: Boolean = false,
    /** Collapse the panel after launching an app or a shortcut so it does not cover the game. */
    val collapseAfterAction: Boolean = true,
) {
    val shownQuickActions: List<QuickAction> get() = quickActionOrder.filter { it !in hiddenQuickActions }

    companion object {
        val DEFAULT_WIDGETS: Set<MonitorWidget> = setOf(
            MonitorWidget.FPS, MonitorWidget.CPU, MonitorWidget.RAM, MonitorWidget.TEMPERATURE,
            MonitorWidget.BATTERY, MonitorWidget.THERMAL, MonitorWidget.LATENCY,
        )
        const val MIN_HANDLE_OPACITY = 25
        const val MIN_PANEL_OPACITY = 40
    }
}

/**
 * What a game mode changes while a game runs. Null means "do not touch that setting".
 * Only settings an ordinary app is allowed to change are here: there is deliberately no CPU or GPU field.
 */
data class ModeConfig(
    val brightnessPercent: Int?,
    val dnd: Boolean,
    val keepScreenOn: Boolean,
    val volumePercent: Int?,
)

/** The mode config merged with the game's own profile. This is what is actually applied. */
data class EffectiveConfig(
    val brightnessPercent: Int?,
    val dnd: Boolean,
    val keepScreenOn: Boolean,
    val volumePercent: Int?,
    /** How often the panel refreshes its readings while it is open. Slower saves battery. */
    val monitorIntervalMs: Long,
)

object ModeDefaults {
    fun config(mode: GameMode): ModeConfig = when (mode) {
        GameMode.BATTERY_SAVER -> ModeConfig(brightnessPercent = 40, dnd = true, keepScreenOn = false, volumePercent = null)
        GameMode.BALANCED -> ModeConfig(brightnessPercent = null, dnd = false, keepScreenOn = false, volumePercent = null)
        GameMode.PERFORMANCE -> ModeConfig(brightnessPercent = null, dnd = true, keepScreenOn = true, volumePercent = null)
        GameMode.CUSTOM -> ModeConfig(brightnessPercent = null, dnd = false, keepScreenOn = false, volumePercent = null)
    }

    fun monitorIntervalMs(mode: GameMode): Long = when (mode) {
        GameMode.BATTERY_SAVER -> 3_000L
        GameMode.BALANCED -> 2_000L
        GameMode.PERFORMANCE -> 1_000L
        GameMode.CUSTOM -> 2_000L
    }
}

/** The three profiles shown in the panel map onto the stored game modes. CUSTOM is edited in the Game mode screen. */
val PANEL_PROFILES: List<GameMode> = listOf(GameMode.BATTERY_SAVER, GameMode.BALANCED, GameMode.PERFORMANCE)

/** A per-game profile wins over the mode: its brightness replaces the mode's, and DND is on if either asks for it. */
fun resolveEffective(mode: GameMode, config: ModeConfig, profile: GameProfileEntity?): EffectiveConfig =
    EffectiveConfig(
        brightnessPercent = (profile?.brightnessPercent ?: config.brightnessPercent)?.coerceIn(1, 100),
        dnd = config.dnd || profile?.dndEnabled == true,
        keepScreenOn = config.keepScreenOn,
        volumePercent = config.volumePercent?.coerceIn(0, 100),
        monitorIntervalMs = ModeDefaults.monitorIntervalMs(mode),
    )

/** Percent (1..100) to Android's 0..255 brightness scale. Never 0, because 0 can turn the screen fully dark. */
fun brightnessValueFromPercent(percent: Int): Int = ((percent.coerceIn(1, 100) * 255 + 50) / 100).coerceIn(1, 255)

fun brightnessPercentFromValue(value: Int): Int = ((value.coerceIn(0, 255) * 100 + 127) / 255).coerceIn(1, 100)

// ---- Enum persistence helpers ----

inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

/** Parses "A,B,C" into enum values. Unknown names are dropped so an old file never crashes a newer app. */
inline fun <reified T : Enum<T>> parseEnumList(text: String?): List<T> =
    text?.split(',')?.mapNotNull { name -> enumValues<T>().firstOrNull { it.name == name } }?.distinct().orEmpty()

/** Saved order plus any action added in a newer version, appended at the end. */
fun completeQuickOrder(saved: List<QuickAction>): List<QuickAction> =
    saved + QuickAction.entries.filter { it !in saved }

/** Originals of the settings Game Master changed, so they can be put back. Null means "was not changed". */
data class RestoreSnapshot(
    val brightnessValue: Int?,
    val brightnessMode: Int?,
    val dndFilter: Int?,
    val volumePercent: Int?,
) {
    val isEmpty: Boolean get() = brightnessValue == null && dndFilter == null && volumePercent == null
}
