package com.senzaproject.gamespace.core.assistant

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the app in the foreground from UsageStatsManager events. Needs the Usage access permission;
 * without it Android returns no events and this reports null. Call [poll] from one place only.
 */
@Singleton
class ForegroundAppDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val tracker = ForegroundTracker()
    private var lastQueryEnd = 0L

    /** Not main-safe in theory (binder call), but each query is small. Call it from a background dispatcher. */
    fun poll(now: Long): String? {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        // The first query looks back far enough to learn what is already open. Later ones overlap slightly
        // so an event that landed right on the boundary is never missed; replaying events is harmless.
        val begin = if (lastQueryEnd == 0L) now - INITIAL_LOOKBACK_MS else lastQueryEnd - OVERLAP_MS
        val events = try {
            manager.queryEvents(begin, now)
        } catch (e: SecurityException) {
            return null
        } ?: return tracker.current
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            tracker.onEvent(event.eventType, pkg)
        }
        lastQueryEnd = now
        return tracker.current
    }

    fun reset() {
        tracker.reset()
        lastQueryEnd = 0L
    }

    private companion object {
        const val INITIAL_LOOKBACK_MS = 6 * 60 * 60 * 1000L
        const val OVERLAP_MS = 2_000L
    }
}
