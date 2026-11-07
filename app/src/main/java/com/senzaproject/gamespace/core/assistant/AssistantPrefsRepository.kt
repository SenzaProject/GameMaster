package com.senzaproject.gamespace.core.assistant

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.senzaproject.gamespace.core.gaming.GameMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.assistantDataStore by preferencesDataStore(name = "assistant")

/** Persistent settings of the in-game assistant. Everything survives app restarts and reboots. */
@Singleton
class AssistantPrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.assistantDataStore

    private val enabledKey = booleanPreferencesKey("assistant_enabled")
    private val bootKey = booleanPreferencesKey("start_on_boot")
    private val defaultModeKey = stringPreferencesKey("default_mode")
    private val timerEndKey = longPreferencesKey("timer_end_at")

    private val sideKey = stringPreferencesKey("ov_side")
    private val yKey = intPreferencesKey("ov_y_percent")
    private val styleKey = stringPreferencesKey("ov_handle_style")
    private val handleSizeKey = stringPreferencesKey("ov_handle_size")
    private val handleOpacityKey = intPreferencesKey("ov_handle_opacity")
    private val panelOpacityKey = intPreferencesKey("ov_panel_opacity")
    private val panelSizeKey = stringPreferencesKey("ov_panel_size")
    private val widgetsKey = stringPreferencesKey("ov_widgets")
    private val orderKey = stringPreferencesKey("ov_quick_order")
    private val hiddenKey = stringPreferencesKey("ov_quick_hidden")
    private val visibilityKey = stringPreferencesKey("ov_visibility")
    private val fpsHandleKey = booleanPreferencesKey("ov_fps_on_handle")
    private val collapseKey = booleanPreferencesKey("ov_collapse_after_action")

    private val snapValidKey = booleanPreferencesKey("snap_valid")
    private val snapBrightnessKey = intPreferencesKey("snap_brightness")
    private val snapBrightnessModeKey = intPreferencesKey("snap_brightness_mode")
    private val snapDndKey = intPreferencesKey("snap_dnd")
    private val snapVolumeKey = intPreferencesKey("snap_volume")

    val enabled: Flow<Boolean> = store.data.map { it[enabledKey] ?: false }
    val startOnBoot: Flow<Boolean> = store.data.map { it[bootKey] ?: false }
    val defaultMode: Flow<GameMode> = store.data.map { enumOrDefault(it[defaultModeKey], GameMode.BALANCED) }
    val overlayConfig: Flow<OverlayConfig> = store.data.map(::readOverlay)
    val modeConfigs: Flow<Map<GameMode, ModeConfig>> = store.data.map { prefs ->
        GameMode.entries.associateWith { readMode(prefs, it) }
    }
    val snapshot: Flow<RestoreSnapshot?> = store.data.map(::readSnapshot)
    val timerEndAt: Flow<Long?> = store.data.map { it[timerEndKey] }

    suspend fun setEnabled(value: Boolean) { store.edit { it[enabledKey] = value } }
    suspend fun setStartOnBoot(value: Boolean) { store.edit { it[bootKey] = value } }
    suspend fun setDefaultMode(mode: GameMode) { store.edit { it[defaultModeKey] = mode.name } }
    suspend fun setTimerEnd(epochMs: Long?) {
        store.edit { if (epochMs == null) it.remove(timerEndKey) else it[timerEndKey] = epochMs }
    }

    /** Read-modify-write inside one transaction so two quick edits never overwrite each other. */
    suspend fun updateOverlay(transform: (OverlayConfig) -> OverlayConfig) {
        store.edit { prefs -> writeOverlay(prefs, transform(readOverlay(prefs))) }
    }

    suspend fun updateMode(mode: GameMode, transform: (ModeConfig) -> ModeConfig) {
        store.edit { prefs -> writeMode(prefs, mode, transform(readMode(prefs, mode))) }
    }

    suspend fun resetMode(mode: GameMode) {
        store.edit { prefs ->
            prefs.remove(intPreferencesKey("mode_${mode.name}_brightness"))
            prefs.remove(booleanPreferencesKey("mode_${mode.name}_dnd"))
            prefs.remove(booleanPreferencesKey("mode_${mode.name}_keep_on"))
            prefs.remove(intPreferencesKey("mode_${mode.name}_volume"))
        }
    }

    suspend fun saveSnapshot(snapshot: RestoreSnapshot) {
        store.edit {
            it[snapValidKey] = true
            it[snapBrightnessKey] = snapshot.brightnessValue ?: NONE
            it[snapBrightnessModeKey] = snapshot.brightnessMode ?: NONE
            it[snapDndKey] = snapshot.dndFilter ?: NONE
            it[snapVolumeKey] = snapshot.volumePercent ?: NONE
        }
    }

    suspend fun clearSnapshot() {
        store.edit {
            it.remove(snapValidKey)
            it.remove(snapBrightnessKey)
            it.remove(snapBrightnessModeKey)
            it.remove(snapDndKey)
            it.remove(snapVolumeKey)
        }
    }

    private fun readSnapshot(prefs: Preferences): RestoreSnapshot? {
        if (prefs[snapValidKey] != true) return null
        fun value(key: Preferences.Key<Int>) = prefs[key]?.takeIf { it != NONE }
        return RestoreSnapshot(value(snapBrightnessKey), value(snapBrightnessModeKey), value(snapDndKey), value(snapVolumeKey))
    }

    // Brightness and volume use 0 for "do not change" because their valid values start at 1 and 0.
    // A missing key means "use the built-in default for this mode".
    private fun readMode(prefs: Preferences, mode: GameMode): ModeConfig {
        val default = ModeDefaults.config(mode)
        val brightness = prefs[intPreferencesKey("mode_${mode.name}_brightness")]
        val volume = prefs[intPreferencesKey("mode_${mode.name}_volume")]
        return ModeConfig(
            brightnessPercent = if (brightness == null) default.brightnessPercent else brightness.takeIf { it > 0 },
            dnd = prefs[booleanPreferencesKey("mode_${mode.name}_dnd")] ?: default.dnd,
            keepScreenOn = prefs[booleanPreferencesKey("mode_${mode.name}_keep_on")] ?: default.keepScreenOn,
            volumePercent = if (volume == null) default.volumePercent else volume.takeIf { it >= 0 && it != NO_VOLUME },
        )
    }

    private fun writeMode(prefs: MutablePreferences, mode: GameMode, config: ModeConfig) {
        prefs[intPreferencesKey("mode_${mode.name}_brightness")] = config.brightnessPercent?.coerceIn(1, 100) ?: 0
        prefs[booleanPreferencesKey("mode_${mode.name}_dnd")] = config.dnd
        prefs[booleanPreferencesKey("mode_${mode.name}_keep_on")] = config.keepScreenOn
        prefs[intPreferencesKey("mode_${mode.name}_volume")] = config.volumePercent?.coerceIn(0, 100) ?: NO_VOLUME
    }

    private fun readOverlay(prefs: Preferences): OverlayConfig {
        val d = OverlayConfig()
        return OverlayConfig(
            side = enumOrDefault(prefs[sideKey], d.side),
            verticalPercent = (prefs[yKey] ?: d.verticalPercent).coerceIn(0, 100),
            handleStyle = enumOrDefault(prefs[styleKey], d.handleStyle),
            handleSize = enumOrDefault(prefs[handleSizeKey], d.handleSize),
            handleOpacityPercent = (prefs[handleOpacityKey] ?: d.handleOpacityPercent)
                .coerceIn(OverlayConfig.MIN_HANDLE_OPACITY, 100),
            panelOpacityPercent = (prefs[panelOpacityKey] ?: d.panelOpacityPercent)
                .coerceIn(OverlayConfig.MIN_PANEL_OPACITY, 100),
            panelSize = enumOrDefault(prefs[panelSizeKey], d.panelSize),
            visibleWidgets = prefs[widgetsKey]?.let { parseEnumList<MonitorWidget>(it).toSet() } ?: d.visibleWidgets,
            quickActionOrder = completeQuickOrder(parseEnumList(prefs[orderKey])),
            hiddenQuickActions = parseEnumList<QuickAction>(prefs[hiddenKey]).toSet(),
            visibility = enumOrDefault(prefs[visibilityKey], d.visibility),
            fpsOnHandle = prefs[fpsHandleKey] ?: d.fpsOnHandle,
            collapseAfterAction = prefs[collapseKey] ?: d.collapseAfterAction,
        )
    }

    private fun writeOverlay(prefs: MutablePreferences, c: OverlayConfig) {
        prefs[sideKey] = c.side.name
        prefs[yKey] = c.verticalPercent.coerceIn(0, 100)
        prefs[styleKey] = c.handleStyle.name
        prefs[handleSizeKey] = c.handleSize.name
        prefs[handleOpacityKey] = c.handleOpacityPercent.coerceIn(OverlayConfig.MIN_HANDLE_OPACITY, 100)
        prefs[panelOpacityKey] = c.panelOpacityPercent.coerceIn(OverlayConfig.MIN_PANEL_OPACITY, 100)
        prefs[panelSizeKey] = c.panelSize.name
        prefs[widgetsKey] = c.visibleWidgets.joinToString(",") { it.name }
        prefs[orderKey] = c.quickActionOrder.joinToString(",") { it.name }
        prefs[hiddenKey] = c.hiddenQuickActions.joinToString(",") { it.name }
        prefs[visibilityKey] = c.visibility.name
        prefs[fpsHandleKey] = c.fpsOnHandle
        prefs[collapseKey] = c.collapseAfterAction
    }

    private companion object {
        const val NONE = -1
        const val NO_VOLUME = -1
    }
}
