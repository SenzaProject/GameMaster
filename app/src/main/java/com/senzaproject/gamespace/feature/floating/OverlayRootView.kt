package com.senzaproject.gamespace.feature.floating

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import kotlin.math.hypot

/**
 * Root of the overlay window. While the panel is collapsed it takes every touch itself, so the handle can be
 * tapped and dragged using raw screen coordinates (the window moves under the finger, so view-relative
 * coordinates would jitter). While the panel is open it passes touches to Compose and reports touches that
 * land outside the window, which is how the panel closes when the player taps the game.
 */
class OverlayRootView(context: Context) : FrameLayout(context) {
    var collapsed = true
    var onTap: (() -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDrag: ((dx: Float, dy: Float) -> Unit)? = null
    var onDragEnd: ((rawX: Float, rawY: Float) -> Unit)? = null
    var onOutside: (() -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = collapsed

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (!collapsed) onOutside?.invoke()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!collapsed) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && hypot(dx, dy) > slop) {
                    dragging = true
                    onDragStart?.invoke()
                }
                if (dragging) onDrag?.invoke(dx, dy)
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) onDragEnd?.invoke(event.rawX, event.rawY) else onTap?.invoke()
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnd?.invoke(event.rawX, event.rawY)
                dragging = false
            }
        }
        return true
    }
}

/**
 * Compose needs a lifecycle, a saved-state registry and a ViewModel store on the view tree. An overlay window has
 * no Activity to provide them, so this small owner stands in for it and is driven by the service.
 */
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
