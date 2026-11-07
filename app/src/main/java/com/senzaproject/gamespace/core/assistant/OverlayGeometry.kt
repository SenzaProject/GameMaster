package com.senzaproject.gamespace.core.assistant

/** Screen-position maths for the floating handle and panel. All values are pixels except percentages. */
object OverlayGeometry {
    fun handleY(verticalPercent: Int, screenHeight: Int, handleHeight: Int, margin: Int): Int {
        val range = (screenHeight - handleHeight - 2 * margin).coerceAtLeast(0)
        return margin + range * verticalPercent.coerceIn(0, 100) / 100
    }

    fun percentFromY(y: Int, screenHeight: Int, handleHeight: Int, margin: Int): Int {
        val range = (screenHeight - handleHeight - 2 * margin)
        if (range <= 0) return 50
        return ((y - margin) * 100 / range).coerceIn(0, 100)
    }

    /** Top of the open panel: as close to the handle as possible without leaving the screen. */
    fun expandedY(handleY: Int, panelHeight: Int, screenHeight: Int, margin: Int): Int =
        handleY.coerceIn(margin, (screenHeight - panelHeight - margin).coerceAtLeast(margin))

    fun snapSide(fingerX: Int, screenWidth: Int): HandleSide =
        if (fingerX < screenWidth / 2) HandleSide.LEFT else HandleSide.RIGHT

    /** Panel size in dp for a preset, shrunk to fit the screen. */
    fun panelWidthDp(size: SizePreset, screenWidthDp: Int): Int =
        when (size) { SizePreset.SMALL -> 270; SizePreset.MEDIUM -> 310; SizePreset.LARGE -> 350 }
            .coerceAtMost((screenWidthDp - 24).coerceAtLeast(160))

    fun panelHeightDp(size: SizePreset, screenHeightDp: Int): Int =
        when (size) { SizePreset.SMALL -> 340; SizePreset.MEDIUM -> 400; SizePreset.LARGE -> 460 }
            .coerceAtMost((screenHeightDp - 24).coerceAtLeast(160))

    fun handleHeightDp(size: SizePreset): Int =
        when (size) { SizePreset.SMALL -> 56; SizePreset.MEDIUM -> 72; SizePreset.LARGE -> 96 }
}

/** Dock and list helpers. */
const val MAX_DOCK_APPS = 9

enum class DockAddResult { ADDED, DUPLICATE, FULL }

fun planDockAdd(current: List<String>, packageName: String, max: Int = MAX_DOCK_APPS): DockAddResult = when {
    packageName in current -> DockAddResult.DUPLICATE
    current.size >= max -> DockAddResult.FULL
    else -> DockAddResult.ADDED
}

fun <T> moveItem(list: List<T>, from: Int, to: Int): List<T> {
    if (from !in list.indices || to !in list.indices || from == to) return list
    val result = list.toMutableList()
    result.add(to, result.removeAt(from))
    return result
}

/**
 * True when [enabledSetting] (Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, colon separated) lists the service.
 * Android may write the class as a full name or relative to the package ("pkg/.Cls").
 */
fun isAccessibilityServiceListed(enabledSetting: String?, packageName: String, className: String): Boolean {
    if (enabledSetting.isNullOrBlank()) return false
    return enabledSetting.split(':').any { entry ->
        val parts = entry.trim().split('/', limit = 2)
        if (parts.size != 2 || parts[0] != packageName) return@any false
        val cls = if (parts[1].startsWith(".")) packageName + parts[1] else parts[1]
        cls == className
    }
}
