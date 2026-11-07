package com.senzaproject.gamespace.feature.floating

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.assistant.ActiveSession
import com.senzaproject.gamespace.core.assistant.HandleSide
import com.senzaproject.gamespace.core.assistant.OverlayConfig
import com.senzaproject.gamespace.core.assistant.OverlayGeometry
import com.senzaproject.gamespace.core.assistant.RecordingState
import com.senzaproject.gamespace.core.gaming.GameMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PanelTab { MONITOR, CONTROL, QUICK, DOCK }

/** State read by the overlay's Compose UI. Plain snapshot state, written by the controller on the main thread. */
class OverlayUiState {
    var expanded by mutableStateOf(false)
    var config by mutableStateOf(OverlayConfig())
    var active by mutableStateOf<ActiveSession?>(null)
    var defaultMode by mutableStateOf(GameMode.BALANCED)
    var tab by mutableStateOf(PanelTab.MONITOR)
    var recording by mutableStateOf<RecordingState>(RecordingState.Idle)
    var screenWidthDp by mutableIntStateOf(360)
    var screenHeightDp by mutableIntStateOf(800)
}

/**
 * Owns the single overlay window: adds and removes it, moves it, switches between the collapsed handle and
 * the open panel, and keeps it from being added twice. Must be used from the main thread.
 *
 * The window is always as small as what it shows (the handle, or the panel). Touches anywhere else go straight
 * to the game, and Android's untrusted-touch rules never block them because they never touch this window.
 */
class OverlayController(
    private val context: Context,
    private val deps: PanelDependencies,
    private val actions: PanelActions,
    private val scope: CoroutineScope,
    private val onPositionChanged: (side: HandleSide, verticalPercent: Int) -> Unit,
) {
    val state = OverlayUiState()

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: OverlayRootView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var keepAwake = false
    private var dragging = false
    private var dragStartX = 0
    private var dragStartY = 0
    private var collapseJob: Job? = null

    val isShown: Boolean get() = root != null

    private val density: Float get() = context.resources.displayMetrics.density
    private val marginPx: Int get() = (MARGIN_DP * density).toInt()

    init {
        updateScreenDp()
    }

    fun show() {
        if (root != null) return // never two overlay windows
        updateScreenDp()
        val lifecycleOwner = OverlayLifecycleOwner()
        val rootView = OverlayRootView(context)
        rootView.setViewTreeLifecycleOwner(lifecycleOwner)
        rootView.setViewTreeViewModelStoreOwner(lifecycleOwner)
        rootView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        // The theme gives Material components the attributes they expect; a bare Service context has none.
        val composeView = ComposeView(ContextThemeWrapper(context, R.style.Theme_GameMaster)).apply {
            setContent { OverlayContent(state, deps, actions) }
        }
        rootView.addView(
            composeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        rootView.collapsed = !state.expanded
        wire(rootView)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            computeFlags(),
            PixelFormat.TRANSLUCENT,
        )
        params = layoutParams
        applyPosition(update = false)
        try {
            lifecycleOwner.start()
            windowManager.addView(rootView, layoutParams)
        } catch (e: RuntimeException) {
            // BadTokenException when the overlay permission was revoked, SecurityException on some ROMs.
            lifecycleOwner.stop()
            params = null
            throw e
        }
        root = rootView
        owner = lifecycleOwner
    }

    fun hide() {
        collapseJob?.cancel()
        val rootView = root ?: return
        runCatching { windowManager.removeView(rootView) }
        owner?.stop()
        root = null
        owner = null
        params = null
        state.expanded = false
    }

    fun setVisible(visible: Boolean) {
        if (visible) show() else hide()
    }

    fun updateConfig(config: OverlayConfig) {
        state.config = config
        if (!dragging) applyPosition()
    }

    fun setKeepAwake(value: Boolean) {
        if (keepAwake == value) return
        keepAwake = value
        val p = params ?: return
        p.flags = computeFlags()
        update()
    }

    fun onScreenChanged(@Suppress("UNUSED_PARAMETER") configuration: Configuration) {
        updateScreenDp()
        if (!dragging) applyPosition()
    }

    fun expand() {
        collapseJob?.cancel()
        state.expanded = true
        root?.collapsed = false
        applyPosition()
    }

    fun collapse() {
        if (!state.expanded) return
        state.expanded = false
        root?.collapsed = true
        collapseJob?.cancel()
        // Let the closing animation finish before the window shrinks and moves back to the handle position.
        collapseJob = scope.launch {
            delay(COLLAPSE_ANIMATION_MS)
            applyPosition()
        }
    }

    private fun wire(rootView: OverlayRootView) {
        rootView.onTap = { expand() }
        rootView.onOutside = { collapse() }
        rootView.onDragStart = {
            dragging = true
            dragStartX = params?.x ?: 0
            dragStartY = params?.y ?: 0
        }
        rootView.onDrag = { dx, dy -> dragBy(dx.toInt(), dy.toInt()) }
        rootView.onDragEnd = { rawX, _ -> finishDrag(rawX.toInt()) }
    }

    private fun dragBy(dx: Int, dy: Int) {
        val p = params ?: return
        val (screenW, screenH) = screenSize()
        val handleH = handleHeightPx()
        p.y = (dragStartY + dy).coerceIn(marginPx, (screenH - handleH - marginPx).coerceAtLeast(marginPx))
        // The handle follows the finger sideways too, then snaps to the nearest edge on release.
        val sideways = if (state.config.side == HandleSide.LEFT) dx else -dx
        p.x = (dragStartX + sideways).coerceIn(0, (screenW / 2).coerceAtLeast(0))
        update()
    }

    private fun finishDrag(fingerX: Int) {
        dragging = false
        val p = params ?: return
        val (screenW, screenH) = screenSize()
        val side = OverlayGeometry.snapSide(fingerX, screenW)
        val percent = OverlayGeometry.percentFromY(p.y, screenH, handleHeightPx(), marginPx)
        state.config = state.config.copy(side = side, verticalPercent = percent)
        applyPosition()
        onPositionChanged(side, percent)
    }

    private fun applyPosition(update: Boolean = true) {
        val p = params ?: return
        val (_, screenH) = screenSize()
        val handleY = OverlayGeometry.handleY(state.config.verticalPercent, screenH, handleHeightPx(), marginPx)
        p.gravity = Gravity.TOP or if (state.config.side == HandleSide.LEFT) Gravity.START else Gravity.END
        p.x = 0
        p.y = if (state.expanded) {
            OverlayGeometry.expandedY(handleY, panelHeightPx(), screenH, marginPx)
        } else {
            handleY
        }
        p.flags = computeFlags()
        if (update) update()
    }

    private fun computeFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (state.expanded) flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        if (keepAwake) flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        return flags
    }

    private fun update() {
        val rootView = root ?: return
        val p = params ?: return
        runCatching { windowManager.updateViewLayout(rootView, p) }
    }

    private fun handleHeightPx(): Int =
        (OverlayGeometry.handleHeightDp(state.config.handleSize) * density).toInt()

    private fun panelHeightPx(): Int =
        (OverlayGeometry.panelHeightDp(state.config.panelSize, state.screenHeightDp) * density).toInt()

    private fun screenSize(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    private fun updateScreenDp() {
        val metrics = context.resources.displayMetrics
        state.screenWidthDp = (metrics.widthPixels / metrics.density).toInt()
        state.screenHeightDp = (metrics.heightPixels / metrics.density).toInt()
    }

    private companion object {
        const val MARGIN_DP = 8
        const val COLLAPSE_ANIMATION_MS = 220L
    }
}
