package com.senzaproject.gamespace.core.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Exists for one reason: Android has no public API that lets an ordinary app take a screenshot, and the only
 * supported route is an accessibility service performing the system's own screenshot action.
 * It listens to no events, reads no screen content and has no other behaviour. The user must switch it on in
 * Accessibility settings (on Android 13+ sideloaded apps first need "Allow restricted settings" in App info).
 */
class GameMasterAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile private var instance: GameMasterAccessibilityService? = null

        val isConnected: Boolean get() = instance != null

        /** True when Android accepted the screenshot action. False when the service is not switched on. */
        fun takeScreenshot(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false
    }
}
