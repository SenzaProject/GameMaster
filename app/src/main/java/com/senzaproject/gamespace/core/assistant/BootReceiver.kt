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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BootEntryPoint {
    fun prefs(): AssistantPrefsRepository
}

/**
 * Restarts the assistant after a reboot, but only when the user asked for that and left it switched on.
 * Uses a Hilt entry point instead of @AndroidEntryPoint: in Kotlin that annotation needs a super.onReceive call
 * that does not compile against the abstract BroadcastReceiver.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = EntryPointAccessors.fromApplication(context.applicationContext, BootEntryPoint::class.java).prefs()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val wanted = prefs.enabled.first() && prefs.startOnBoot.first()
                if (wanted && Settings.canDrawOverlays(context)) FloatingPanelService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
