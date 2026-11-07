package com.senzaproject.gamespace.core.assistant

sealed interface SessionEvent {
    data class Started(val packageName: String, val at: Long) : SessionEvent
    data class Ended(val packageName: String, val startedAt: Long, val endedAt: Long) : SessionEvent
}

/**
 * Turns "which app is in the foreground" readings into game sessions.
 *
 * A session ends only after the game has been out of the foreground for [graceMs]. That keeps a quick
 * look at a notification, a screen lock or a short call from splitting one play session into many.
 * The recorded end time is the moment the game was left, not the end of the grace period.
 * Switching straight to another library game ends the first session at once.
 */
class SessionTracker(private val graceMs: Long = DEFAULT_GRACE_MS) {
    var activePackage: String? = null
        private set
    var activeStartedAt: Long = 0L
        private set
    private var leftAt: Long? = null

    fun onTick(now: Long, foreground: String?, interactive: Boolean, isGame: (String) -> Boolean): List<SessionEvent> {
        val effective = if (interactive && foreground != null && isGame(foreground)) foreground else null
        val events = mutableListOf<SessionEvent>()
        val active = activePackage
        if (active == null) {
            if (effective != null) {
                activePackage = effective
                activeStartedAt = now
                leftAt = null
                events += SessionEvent.Started(effective, now)
            }
            return events
        }
        if (effective == active) {
            leftAt = null
        } else if (effective != null) {
            events += SessionEvent.Ended(active, activeStartedAt, leftAt ?: now)
            activePackage = effective
            activeStartedAt = now
            leftAt = null
            events += SessionEvent.Started(effective, now)
        } else {
            val left = leftAt
            if (left == null) {
                leftAt = now
            } else if (now - left >= graceMs) {
                events += SessionEvent.Ended(active, activeStartedAt, left)
                activePackage = null
                leftAt = null
            }
        }
        return events
    }

    /** Ends the running session immediately, for example when the assistant is switched off. */
    fun finish(now: Long): SessionEvent.Ended? {
        val active = activePackage ?: return null
        val ended = SessionEvent.Ended(active, activeStartedAt, leftAt ?: now)
        activePackage = null
        leftAt = null
        return ended
    }

    companion object {
        const val DEFAULT_GRACE_MS = 30_000L
    }
}

/**
 * Follows UsageStatsManager events to know which app is in the foreground.
 * Event type numbers are the documented UsageEvents.Event constants (ACTIVITY_RESUMED, ACTIVITY_PAUSED,
 * ACTIVITY_STOPPED), kept as plain ints so this class can be tested without Android.
 */
class ForegroundTracker {
    var current: String? = null
        private set

    fun onEvent(type: Int, packageName: String) {
        when (type) {
            RESUMED -> current = packageName
            PAUSED, STOPPED -> if (current == packageName) current = null
        }
    }

    fun reset() {
        current = null
    }

    companion object {
        const val RESUMED = 1
        const val PAUSED = 2
        const val STOPPED = 23
    }
}
