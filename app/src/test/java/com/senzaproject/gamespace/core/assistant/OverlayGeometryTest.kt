package com.senzaproject.gamespace.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGeometryTest {
    private val screenH = 2000
    private val handleH = 200
    private val margin = 20

    @Test fun handleStaysInsideTheMargins() {
        assertEquals(margin, OverlayGeometry.handleY(0, screenH, handleH, margin))
        assertEquals(screenH - handleH - margin, OverlayGeometry.handleY(100, screenH, handleH, margin))
        assertEquals(margin, OverlayGeometry.handleY(-30, screenH, handleH, margin))
        assertEquals(screenH - handleH - margin, OverlayGeometry.handleY(300, screenH, handleH, margin))
    }

    @Test fun positionSurvivesAConversionRoundTrip() {
        for (percent in 0..100 step 5) {
            val y = OverlayGeometry.handleY(percent, screenH, handleH, margin)
            assertEquals(percent.toDouble(), OverlayGeometry.percentFromY(y, screenH, handleH, margin).toDouble(), 1.0)
        }
    }

    @Test fun tinyScreenDoesNotCrash() {
        assertEquals(margin, OverlayGeometry.handleY(50, 100, 200, margin))
        assertEquals(50, OverlayGeometry.percentFromY(10, 100, 200, margin))
    }

    @Test fun openPanelNeverLeavesTheScreen() {
        val panelH = 800
        val top = OverlayGeometry.expandedY(handleY = 1700, panelHeight = panelH, screenHeight = screenH, margin = margin)
        assertTrue(top + panelH + margin <= screenH)
        assertEquals(300, OverlayGeometry.expandedY(300, panelH, screenH, margin))
        assertEquals(margin, OverlayGeometry.expandedY(0, panelH, screenH, margin))
    }

    @Test fun panelTallerThanTheScreenStartsAtTheMargin() {
        assertEquals(margin, OverlayGeometry.expandedY(500, 3000, screenH, margin))
    }

    @Test fun handleSnapsToTheNearerEdge() {
        assertEquals(HandleSide.LEFT, OverlayGeometry.snapSide(100, 1080))
        assertEquals(HandleSide.RIGHT, OverlayGeometry.snapSide(900, 1080))
        assertEquals(HandleSide.RIGHT, OverlayGeometry.snapSide(540, 1080))
    }

    @Test fun panelSizeFitsSmallScreens() {
        assertEquals(310, OverlayGeometry.panelWidthDp(SizePreset.MEDIUM, 411))
        assertEquals(296, OverlayGeometry.panelWidthDp(SizePreset.LARGE, 320))
        assertEquals(336, OverlayGeometry.panelHeightDp(SizePreset.LARGE, 360))
        assertTrue(OverlayGeometry.panelWidthDp(SizePreset.SMALL, 100) >= 160)
    }

    @Test fun biggerHandlesAreTaller() {
        assertTrue(OverlayGeometry.handleHeightDp(SizePreset.SMALL) < OverlayGeometry.handleHeightDp(SizePreset.MEDIUM))
        assertTrue(OverlayGeometry.handleHeightDp(SizePreset.MEDIUM) < OverlayGeometry.handleHeightDp(SizePreset.LARGE))
    }
}

class DockRulesTest {
    @Test fun addsUntilFullThenRefuses() {
        val dock = (1..8).map { "app$it" }
        assertEquals(DockAddResult.ADDED, planDockAdd(dock, "new"))
        assertEquals(DockAddResult.FULL, planDockAdd(dock + "app9", "new"))
    }

    @Test fun duplicateIsRefusedEvenWhenFull() {
        val full = (1..MAX_DOCK_APPS).map { "app$it" }
        assertEquals(DockAddResult.DUPLICATE, planDockAdd(full, "app3"))
    }

    @Test fun dockHoldsNineApps() = assertEquals(9, MAX_DOCK_APPS)

    @Test fun moveItemReordersAndKeepsEveryElement() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "a", "c", "d"), moveItem(list, 0, 1))
        assertEquals(listOf("d", "a", "b", "c"), moveItem(list, 3, 0))
        assertEquals(listOf("a", "c", "d", "b"), moveItem(list, 1, 3))
    }

    @Test fun moveItemIgnoresBadIndexes() {
        val list = listOf("a", "b")
        assertEquals(list, moveItem(list, 0, 5))
        assertEquals(list, moveItem(list, -1, 0))
        assertEquals(list, moveItem(list, 1, 1))
        assertEquals(emptyList<String>(), moveItem(emptyList<String>(), 0, 1))
    }
}

class AccessibilityMatchTest {
    private val pkg = "com.senzaproject.gamespace"
    private val cls = "com.senzaproject.gamespace.core.assistant.GameMasterAccessibilityService"

    @Test fun findsTheFullClassName() {
        assertTrue(isAccessibilityServiceListed("other/Other:$pkg/$cls", pkg, cls))
    }

    @Test fun findsTheRelativeClassName() {
        assertTrue(isAccessibilityServiceListed("$pkg/.core.assistant.GameMasterAccessibilityService", pkg, cls))
    }

    @Test fun rejectsOtherServicesAndOtherPackages() {
        assertFalse(isAccessibilityServiceListed("$pkg/other.Service", pkg, cls))
        assertFalse(isAccessibilityServiceListed("other.pkg/$cls", pkg, cls))
        assertFalse(isAccessibilityServiceListed("$pkg.debug/$cls", pkg, cls))
    }

    @Test fun emptyOrMissingSettingMeansNotEnabled() {
        assertFalse(isAccessibilityServiceListed(null, pkg, cls))
        assertFalse(isAccessibilityServiceListed("", pkg, cls))
        assertFalse(isAccessibilityServiceListed("garbage", pkg, cls))
    }

    @Test fun debugBuildPackageMatchesItsOwnEntry() {
        assertTrue(isAccessibilityServiceListed("$pkg.debug/$cls", "$pkg.debug", cls))
    }
}
