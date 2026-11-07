package com.senzaproject.gamespace.core.assistant

import com.senzaproject.gamespace.core.database.GameDao
import com.senzaproject.gamespace.core.database.GameProfileDao
import com.senzaproject.gamespace.core.database.GameProfileEntity
import com.senzaproject.gamespace.core.database.GameSessionEntity
import com.senzaproject.gamespace.core.database.SessionDao
import com.senzaproject.gamespace.core.gaming.GameMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Why a requested change was not applied. Shown to the user instead of being ignored. */
enum class SkipReason { NEEDS_WRITE_SETTINGS, NEEDS_DND_ACCESS, ALREADY_RESTRICTED, WRITE_FAILED }

/** What was really changed for the running game. */
data class AppliedSettings(
    val brightness: Boolean = false,
    val dnd: Boolean = false,
    val volume: Boolean = false,
    val skipped: Set<SkipReason> = emptySet(),
)

data class ActiveSession(
    val packageName: String,
    val label: String,
    val startedAt: Long,
    val mode: GameMode,
    val effective: EffectiveConfig,
    val applied: AppliedSettings,
    /** The game's own "Floating panel" profile switch. In "only in games" mode the handle needs it. */
    val overlayEnabled: Boolean,
)

/**
 * Owns the play session: starts and ends it, writes it to the database, applies the game mode and the game's
 * profile when it starts, and puts the user's original settings back when it ends.
 *
 * The originals are saved to disk before anything is changed, so if the process is killed mid-game the next start
 * can still restore them ([recover]).
 */
@Singleton
class GameSessionManager @Inject constructor(
    private val sessionDao: SessionDao,
    private val gameDao: GameDao,
    private val profileDao: GameProfileDao,
    private val prefs: AssistantPrefsRepository,
    private val system: SystemSettingsController,
) {
    private val lock = Mutex()
    private val tracker = SessionTracker()
    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    private var rowId: Long? = null
    private var lastHeartbeat = 0L

    /** Run once when the assistant starts: close sessions a crash left open and restore any leftover settings. */
    suspend fun recover() = lock.withLock {
        sessionDao.closeOrphans()
        restoreOriginals()
    }

    suspend fun onTick(now: Long, foreground: String?, interactive: Boolean, games: Set<String>) = lock.withLock {
        for (event in tracker.onTick(now, foreground, interactive) { it in games }) {
            when (event) {
                is SessionEvent.Ended -> end(event)
                is SessionEvent.Started -> begin(event.packageName, event.at, null)
            }
        }
        if (tracker.activePackage != null && now - lastHeartbeat >= HEARTBEAT_MS) {
            rowId?.let { sessionDao.heartbeat(it, now) }
            lastHeartbeat = now
        }
    }

    /** Ends the running session now and restores the user's settings. */
    suspend fun endNow(now: Long) = lock.withLock {
        tracker.finish(now)?.let { end(it) }
    }

    /**
     * Panel profile switch. During a game it re-applies the new mode for the rest of that session only.
     * Outside a game it changes the default mode that the next game will use.
     */
    suspend fun switchMode(mode: GameMode) = lock.withLock {
        val current = _active.value
        if (current == null) {
            prefs.setDefaultMode(mode)
            return@withLock
        }
        restoreOriginals()
        val effective = effectiveFor(mode, profileDao.get(current.packageName))
        val applied = applyAndRemember(effective)
        _active.value = current.copy(mode = mode, effective = effective, applied = applied)
    }

    private suspend fun begin(packageName: String, at: Long, modeOverride: GameMode?) {
        // A leftover snapshot means the previous run died before restoring; put things back first.
        restoreOriginals()
        val label = gameDao.get(packageName)?.label ?: packageName
        val profile = profileDao.get(packageName)
        val mode = modeOverride ?: profile?.preferredMode ?: prefs.defaultMode.first()
        rowId = if (profile?.trackSessions != false) {
            sessionDao.insert(GameSessionEntity(packageName = packageName, label = label, startedAt = at, lastSeenAt = at))
        } else {
            null
        }
        lastHeartbeat = at
        val effective = effectiveFor(mode, profile)
        val applied = applyAndRemember(effective)
        _active.value = ActiveSession(packageName, label, at, mode, effective, applied, profile?.overlayEnabled == true)
    }

    private suspend fun end(event: SessionEvent.Ended) {
        rowId?.let { sessionDao.finish(it, maxOf(event.endedAt, event.startedAt)) }
        rowId = null
        restoreOriginals()
        _active.value = null
    }

    private suspend fun effectiveFor(mode: GameMode, profile: GameProfileEntity?): EffectiveConfig {
        val config = prefs.modeConfigs.first().getValue(mode)
        return resolveEffective(mode, config, profile)
    }

    /** Reads the originals, saves them to disk, then changes only what is allowed. */
    private suspend fun applyAndRemember(effective: EffectiveConfig): AppliedSettings {
        val skipped = mutableSetOf<SkipReason>()

        var brightnessValue: Int? = null
        var brightnessMode: Int? = null
        val wantBrightness = effective.brightnessPercent != null
        if (wantBrightness) {
            if (system.canWriteSettings()) {
                brightnessValue = system.readBrightnessValue()
                brightnessMode = system.readBrightnessMode()
                if (brightnessValue == null) skipped += SkipReason.WRITE_FAILED
            } else {
                skipped += SkipReason.NEEDS_WRITE_SETTINGS
            }
        }

        var dndOriginal: Int? = null
        if (effective.dnd) {
            if (!system.hasDndAccess()) {
                skipped += SkipReason.NEEDS_DND_ACCESS
            } else {
                val current = system.readDndFilter()
                // Never weaken a stricter setting the user already chose.
                if (current == DndFilter.ALL) dndOriginal = current else skipped += SkipReason.ALREADY_RESTRICTED
            }
        }

        val volumeOriginal = if (effective.volumePercent != null) system.readMusicVolumePercent() else null

        val snapshot = RestoreSnapshot(brightnessValue, brightnessMode, dndOriginal, volumeOriginal)
        if (!snapshot.isEmpty) prefs.saveSnapshot(snapshot)

        var brightnessDone = false
        var dndDone = false
        var volumeDone = false
        if (brightnessValue != null && effective.brightnessPercent != null) {
            brightnessDone = system.writeBrightness(brightnessValueFromPercent(effective.brightnessPercent))
            if (!brightnessDone) skipped += SkipReason.WRITE_FAILED
        }
        if (dndOriginal != null) {
            dndDone = system.writeDndFilter(DndFilter.PRIORITY)
            if (!dndDone) skipped += SkipReason.WRITE_FAILED
        }
        if (volumeOriginal != null && effective.volumePercent != null) {
            volumeDone = system.writeMusicVolumePercent(effective.volumePercent)
            if (!volumeDone) skipped += SkipReason.WRITE_FAILED
        }
        return AppliedSettings(brightnessDone, dndDone, volumeDone, skipped)
    }

    private suspend fun restoreOriginals() {
        val snapshot = prefs.snapshot.first() ?: return
        if (snapshot.brightnessValue != null || snapshot.brightnessMode != null) {
            system.restoreBrightness(snapshot.brightnessValue, snapshot.brightnessMode)
        }
        snapshot.dndFilter?.let { system.writeDndFilter(it) }
        snapshot.volumePercent?.let { system.writeMusicVolumePercent(it) }
        prefs.clearSnapshot()
    }

    private companion object {
        const val HEARTBEAT_MS = 30_000L
    }
}
