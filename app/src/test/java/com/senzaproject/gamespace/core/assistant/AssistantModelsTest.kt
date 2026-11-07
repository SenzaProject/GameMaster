package com.senzaproject.gamespace.core.assistant

import com.senzaproject.gamespace.core.database.GameProfileEntity
import com.senzaproject.gamespace.core.gaming.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantModelsTest {
    @Test fun brightnessConversionCoversTheWholeRangeAndNeverReturnsZero() {
        // 0 is treated as 1 percent, and 1 percent is value 3 of 255 (never the fully dark value 0).
        assertEquals(3, brightnessValueFromPercent(0))
        assertEquals(3, brightnessValueFromPercent(1))
        assertEquals(255, brightnessValueFromPercent(100))
        assertEquals(255, brightnessValueFromPercent(500))
        assertEquals(128, brightnessValueFromPercent(50))
    }

    @Test fun brightnessRoundTripStaysClose() {
        for (percent in 1..100) {
            assertEquals(percent.toDouble(), brightnessPercentFromValue(brightnessValueFromPercent(percent)).toDouble(), 1.0)
        }
        assertEquals(1, brightnessPercentFromValue(0))
        assertEquals(100, brightnessPercentFromValue(255))
    }

    @Test fun profileBrightnessOverridesTheMode() {
        val mode = ModeConfig(brightnessPercent = 40, dnd = false, keepScreenOn = false, volumePercent = null)
        val profile = GameProfileEntity("g", brightnessPercent = 80)
        assertEquals(80, resolveEffective(GameMode.BATTERY_SAVER, mode, profile).brightnessPercent)
        assertEquals(40, resolveEffective(GameMode.BATTERY_SAVER, mode, null).brightnessPercent)
    }

    @Test fun profileWithoutBrightnessKeepsTheModeValue() {
        val mode = ModeConfig(40, dnd = false, keepScreenOn = false, volumePercent = null)
        assertEquals(40, resolveEffective(GameMode.BALANCED, mode, GameProfileEntity("g")).brightnessPercent)
    }

    @Test fun noBrightnessAnywhereMeansNoChange() {
        val mode = ModeConfig(null, dnd = false, keepScreenOn = false, volumePercent = null)
        assertNull(resolveEffective(GameMode.BALANCED, mode, GameProfileEntity("g")).brightnessPercent)
    }

    @Test fun dndIsOnWhenEitherTheModeOrTheProfileAsksForIt() {
        val off = ModeConfig(null, dnd = false, keepScreenOn = false, volumePercent = null)
        val on = off.copy(dnd = true)
        assertFalse(resolveEffective(GameMode.BALANCED, off, GameProfileEntity("g")).dnd)
        assertTrue(resolveEffective(GameMode.BALANCED, off, GameProfileEntity("g", dndEnabled = true)).dnd)
        assertTrue(resolveEffective(GameMode.BALANCED, on, GameProfileEntity("g", dndEnabled = false)).dnd)
    }

    @Test fun outOfRangeValuesAreClamped() {
        val mode = ModeConfig(brightnessPercent = 400, dnd = false, keepScreenOn = false, volumePercent = 400)
        val effective = resolveEffective(GameMode.CUSTOM, mode, null)
        assertEquals(100, effective.brightnessPercent)
        assertEquals(100, effective.volumePercent)
    }

    @Test fun everyModeHasADefaultAndAnInterval() {
        GameMode.entries.forEach { mode ->
            ModeDefaults.config(mode)
            assertTrue(ModeDefaults.monitorIntervalMs(mode) in 1_000L..3_000L)
        }
        assertTrue(ModeDefaults.monitorIntervalMs(GameMode.BATTERY_SAVER) > ModeDefaults.monitorIntervalMs(GameMode.PERFORMANCE))
    }

    @Test fun powerSavingLowersBrightnessAndPerformanceDoesNot() {
        assertEquals(40, ModeDefaults.config(GameMode.BATTERY_SAVER).brightnessPercent)
        assertNull(ModeDefaults.config(GameMode.PERFORMANCE).brightnessPercent)
        assertNull(ModeDefaults.config(GameMode.BALANCED).brightnessPercent)
    }

    @Test fun panelShowsThreeProfiles() {
        assertEquals(listOf(GameMode.BATTERY_SAVER, GameMode.BALANCED, GameMode.PERFORMANCE), PANEL_PROFILES)
    }

    @Test fun enumListParsingDropsUnknownAndDuplicateNames() {
        val parsed = parseEnumList<MonitorWidget>("FPS,NOT_A_WIDGET,CPU,FPS")
        assertEquals(listOf(MonitorWidget.FPS, MonitorWidget.CPU), parsed)
        assertEquals(emptyList<MonitorWidget>(), parseEnumList<MonitorWidget>(null))
        assertEquals(emptyList<MonitorWidget>(), parseEnumList<MonitorWidget>(""))
    }

    @Test fun enumOrDefaultFallsBackOnBadInput() {
        assertEquals(HandleSide.RIGHT, enumOrDefault("LEFT_ISH", HandleSide.RIGHT))
        assertEquals(HandleSide.LEFT, enumOrDefault("LEFT", HandleSide.RIGHT))
        assertEquals(HandleSide.RIGHT, enumOrDefault(null, HandleSide.RIGHT))
    }

    @Test fun savedShortcutOrderKeepsItsOrderAndAppendsNewActions() {
        val saved = listOf(QuickAction.TOOLBOX, QuickAction.DND)
        val complete = completeQuickOrder(saved)
        assertEquals(QuickAction.TOOLBOX, complete[0])
        assertEquals(QuickAction.DND, complete[1])
        assertEquals(QuickAction.entries.size, complete.size)
        assertEquals(QuickAction.entries.toSet(), complete.toSet())
    }

    @Test fun hiddenShortcutsAreNotShown() {
        val config = OverlayConfig(hiddenQuickActions = setOf(QuickAction.DND, QuickAction.TOOLBOX))
        assertFalse(QuickAction.DND in config.shownQuickActions)
        assertEquals(QuickAction.entries.size - 2, config.shownQuickActions.size)
    }

    @Test fun snapshotIsEmptyOnlyWhenNothingWasChanged() {
        assertTrue(RestoreSnapshot(null, null, null, null).isEmpty)
        assertFalse(RestoreSnapshot(120, 0, null, null).isEmpty)
        assertFalse(RestoreSnapshot(null, null, 1, null).isEmpty)
        assertFalse(RestoreSnapshot(null, null, null, 0).isEmpty)
    }

    @Test fun recordingSizeIsEvenAndCapped() {
        assertEquals(Pair(1080, 1920), recordingSize(1080, 1920))
        val (w, h) = recordingSize(1440, 3200)
        assertTrue(w % 2 == 0 && h % 2 == 0)
        assertTrue(maxOf(w, h) <= 1920)
        assertEquals(Pair(720, 1280), recordingSize(721, 1281))
    }
}
