package com.senzaproject.gamespace.core.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.senzaproject.gamespace.feature.floating.FloatingPanelService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts the assistant after a reboot, but only when the user asked for that and left it switched on.
 *
 * Deliberately not an @AndroidEntryPoint: Hilt's generated base class declares onReceive, and Kotlin cannot call
 * super.onReceive() on the plain BroadcastReceiver it sees at compile time (it is abstract). Reading the
 * repository through an EntryPoint avoids that and behaves the same.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun prefs(): AssistantPrefsRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val prefs = EntryPointAccessors.fromApplication(appContext, BootEntryPoint::class.java).prefs()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val wanted = prefs.enabled.first() && prefs.startOnBoot.first()
                if (wanted && Settings.canDrawOverlays(appContext)) FloatingPanelService.start(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
