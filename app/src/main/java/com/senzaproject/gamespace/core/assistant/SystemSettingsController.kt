package com.senzaproject.gamespace.core.assistant

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only place that changes real system settings. Every write needs a permission Android makes the user grant,
 * and every write reports whether it worked, so callers never pretend a setting changed when it did not.
 */
interface SystemSettingsController {
    /** "Modify system settings" (WRITE_SETTINGS). Needed for brightness and touch vibration. */
    fun canWriteSettings(): Boolean
    fun hasDndAccess(): Boolean

    /** 0..255, or null when it cannot be read. */
    fun readBrightnessValue(): Int?
    /** Settings.System.SCREEN_BRIGHTNESS_MODE (0 manual, 1 automatic), or null. */
    fun readBrightnessMode(): Int?
    /** Switches to manual brightness and sets [value] (1..255). False when not permitted. */
    fun writeBrightness(value: Int): Boolean
    fun restoreBrightness(value: Int?, mode: Int?): Boolean

    /** NotificationManager.INTERRUPTION_FILTER_* (1 all, 2 priority, 3 none, 4 alarms), or null without access. */
    fun readDndFilter(): Int?
    fun writeDndFilter(filter: Int): Boolean

    /** Media volume as 0..100. */
    fun readMusicVolumePercent(): Int?
    fun writeMusicVolumePercent(percent: Int): Boolean

    fun readHapticFeedback(): Boolean?
    fun writeHapticFeedback(enabled: Boolean): Boolean
}

@Singleton
class AndroidSystemSettingsController @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemSettingsController {
    private val resolver get() = context.contentResolver

    override fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    override fun hasDndAccess(): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    override fun readBrightnessValue(): Int? = runCatching {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrNull()

    override fun readBrightnessMode(): Int? = runCatching {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
    }.getOrNull()

    override fun writeBrightness(value: Int): Boolean {
        if (!canWriteSettings()) return false
        return runCatching {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(1, 255))
        }.getOrDefault(false)
    }

    override fun restoreBrightness(value: Int?, mode: Int?): Boolean {
        if (!canWriteSettings()) return false
        return runCatching {
            if (value != null) Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(0, 255))
            if (mode != null) Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
            true
        }.getOrDefault(false)
    }

    override fun readDndFilter(): Int? {
        if (!hasDndAccess()) return null
        return context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
            .takeIf { it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
    }

    override fun writeDndFilter(filter: Int): Boolean {
        if (!hasDndAccess()) return false
        return runCatching {
            context.getSystemService(NotificationManager::class.java).setInterruptionFilter(filter)
            true
        }.getOrDefault(false)
    }

    override fun readMusicVolumePercent(): Int? = runCatching {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) null else audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }.getOrNull()

    override fun writeMusicVolumePercent(percent: Int): Boolean = runCatching {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (percent.coerceIn(0, 100) * max + 50) / 100, 0)
        true
    }.getOrDefault(false)

    override fun readHapticFeedback(): Boolean? = runCatching {
        Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED) == 1
    }.getOrNull()

    override fun writeHapticFeedback(enabled: Boolean): Boolean {
        if (!canWriteSettings()) return false
        return runCatching {
            Settings.System.putInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (enabled) 1 else 0)
        }.getOrDefault(false)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SystemSettingsModule {
    @Binds
    abstract fun controller(impl: AndroidSystemSettingsController): SystemSettingsController
}

/** Android's interruption filter values, named. */
object DndFilter {
    const val ALL = 1
    const val PRIORITY = 2
}
