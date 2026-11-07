package com.senzaproject.gamespace.core.assistant

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.senzaproject.gamespace.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A countdown that tells the player to take a break. The end time is saved, and a notification is scheduled with
 * AlarmManager. The alarm is inexact (no exact-alarm permission is requested), so in Doze mode it can arrive a few
 * minutes late. Needs the notification permission to be visible.
 */
@Singleton
class GamingTimer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AssistantPrefsRepository,
) {
    suspend fun start(minutes: Int) {
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        prefs.setTimerEnd(endAt)
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pendingIntent())
    }

    suspend fun cancel() {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent())
        prefs.setTimerEnd(null)
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, TimerReceiver::class.java).setAction(ACTION_TIMER),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_TIMER = "com.senzaproject.gamespace.action.GAMING_TIMER"
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GamingTimer.ACTION_TIMER) return
        AssistantNotifications.ensureChannels(context)
        val notification = NotificationCompat.Builder(context, AssistantNotifications.CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.timer_done_title))
            .setContentText(context.getString(R.string.timer_done_body))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(TIMER_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val TIMER_NOTIFICATION_ID = 4103
    }
}
