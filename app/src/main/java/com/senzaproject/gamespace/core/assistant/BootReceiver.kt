package com.senzaproject.gamespace.core.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.senzaproject.gamespace.feature.floating.FloatingPanelService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Restarts the assistant after a reboot, but only when the user asked for that and left it switched on. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var prefs: AssistantPrefsRepository

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
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
