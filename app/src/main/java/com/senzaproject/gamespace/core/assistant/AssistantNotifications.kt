package com.senzaproject.gamespace.core.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.senzaproject.gamespace.R

object AssistantNotifications {
    const val CHANNEL_OVERLAY = "assistant_overlay"
    const val CHANNEL_RECORDING = "assistant_recording"
    const val CHANNEL_TIMER = "assistant_timer"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_OVERLAY, context.getString(R.string.channel_overlay), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, context.getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}
