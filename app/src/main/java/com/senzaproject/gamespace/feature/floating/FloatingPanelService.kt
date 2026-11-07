package com.senzaproject.gamespace.feature.floating

import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.senzaproject.gamespace.MainActivity
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.assistant.AssistantNotifications
import com.senzaproject.gamespace.core.assistant.AssistantPrefsRepository
import com.senzaproject.gamespace.core.assistant.ForegroundAppDetector
import com.senzaproject.gamespace.core.assistant.GameMasterAccessibilityService
import com.senzaproject.gamespace.core.assistant.GameSessionManager
import com.senzaproject.gamespace.core.assistant.OverlayVisibility
import com.senzaproject.gamespace.core.assistant.ScreenRecorder
import com.senzaproject.gamespace.core.gaming.GameLauncher
import com.senzaproject.gamespace.core.gaming.GameRepository
import com.senzaproject.gamespace.core.gaming.LaunchResult
import com.senzaproject.gamespace.core.permissions.AppPermission
import com.senzaproject.gamespace.core.permissions.PermissionRepository
import com.senzaproject.gamespace.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Whether the assistant service is alive right now. Read by the Floating panel screen. */
object AssistantRuntime {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    internal fun setRunning(value: Boolean) { _running.value = value }
}

/**
 * The in-game assistant. A foreground service (Android requires one for anything that keeps running behind a game)
 * that follows which game is in the foreground, runs play sessions with their settings, and hosts the overlay.
 * Started only by the user from the Floating panel screen, or after a reboot if they asked for that.
 */
@AndroidEntryPoint
class FloatingPanelService : Service(), PanelActions {
    @Inject lateinit var prefs: AssistantPrefsRepository
    @Inject lateinit var sessions: GameSessionManager
    @Inject lateinit var detector: ForegroundAppDetector
    @Inject lateinit var gameRepository: GameRepository
    @Inject lateinit var permissions: PermissionRepository
    @Inject lateinit var deps: PanelDependencies
    @Inject lateinit var launcher: GameLauncher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: OverlayController? = null
    private var working = false
    @Volatile private var games: Set<String> = emptySet()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AssistantNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Detached scope: this service's own scope is cancelled in onDestroy a moment from now.
            CoroutineScope(Dispatchers.Default).launch { prefs.setEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            promoteToForeground()
        } catch (e: RuntimeException) {
            // Android refused to run a foreground service from the background (for example after a restart).
            stopSelf()
            return START_NOT_STICKY
        }
        AssistantRuntime.setRunning(true)
        if (!working) {
            working = true
            scope.launch { run() }
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        val notification = notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification() = NotificationCompat.Builder(this, AssistantNotifications.CHANNEL_OVERLAY)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.assistant_notification_title))
        .setContentText(getString(R.string.assistant_notification_text))
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_ROUTE, Routes.FloatingPanel)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(
            0,
            getString(R.string.assistant_stop),
            PendingIntent.getService(
                this, 1,
                Intent(this, FloatingPanelService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private suspend fun run() {
        // A sticky restart can arrive after the user switched the assistant off.
        if (!prefs.enabled.first()) {
            stopSelf()
            return
        }
        sessions.recover()
        val overlay = OverlayController(
            context = this,
            deps = deps,
            actions = this,
            scope = scope,
            onPositionChanged = { side, percent ->
                scope.launch { prefs.updateOverlay { it.copy(side = side, verticalPercent = percent) } }
            },
        )
        controller = overlay
        scope.launch { prefs.overlayConfig.collect { overlay.updateConfig(it) } }
        scope.launch { prefs.defaultMode.collect { overlay.state.defaultMode = it } }
        scope.launch { gameRepository.visibleGames.collect { list -> games = list.map { it.packageName }.toSet() } }
        scope.launch {
            sessions.active.collect {
                overlay.state.active = it
                overlay.setKeepAwake(it?.effective?.keepScreenOn == true)
            }
        }
        scope.launch { com.senzaproject.gamespace.core.assistant.ScreenRecordingState.state.collect { overlay.state.recording = it } }
        tickLoop(overlay)
    }

    private suspend fun tickLoop(overlay: OverlayController) {
        val power = getSystemService(PowerManager::class.java)
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val interactive = power.isInteractive
            val usageGranted = permissions.isGranted(AppPermission.USAGE_ACCESS)
            if (usageGranted) {
                val foreground = if (interactive) withContext(Dispatchers.IO) { detector.poll(now) } else null
                sessions.onTick(now, foreground, interactive, games)
            } else {
                // Without usage access games cannot be detected, so no session can be running.
                sessions.endNow(now)
            }
            if (interactive) {
                val config = overlay.state.config
                val show = Settings.canDrawOverlays(this) &&
                    (config.visibility == OverlayVisibility.ALWAYS || !usageGranted || sessions.active.value?.overlayEnabled == true)
                try {
                    overlay.setVisible(show)
                } catch (e: RuntimeException) {
                    // The overlay permission was taken away between the check and adding the window.
                    toast(R.string.ov_toast_overlay_failed)
                }
            }
            delay(if (sessions.active.value != null) ACTIVE_TICK_MS else IDLE_TICK_MS)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onScreenChanged(newConfig)
    }

    override fun onDestroy() {
        scope.cancel()
        controller?.hide()
        controller = null
        AssistantRuntime.setRunning(false)
        // Close the session and restore the user's settings even though this service is going away.
        val now = System.currentTimeMillis()
        CoroutineScope(Dispatchers.Default).launch {
            sessions.endNow(now)
            detector.reset()
        }
        super.onDestroy()
    }

    // ---- PanelActions ----

    override fun collapse() {
        controller?.collapse()
    }

    private fun collapseIfConfigured() {
        if (controller?.state?.config?.collapseAfterAction == true) controller?.collapse()
    }

    override fun launchApp(packageName: String) {
        when (launcher.launch(packageName)) {
            LaunchResult.Success -> collapseIfConfigured()
            LaunchResult.NotLaunchable, is LaunchResult.Failed -> toast(R.string.ov_toast_launch_failed)
        }
    }

    override fun openSystemSettings(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            collapseIfConfigured()
        } catch (e: ActivityNotFoundException) {
            toast(R.string.perm_open_failed)
        }
    }

    override fun openGameMasterScreen(route: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        controller?.collapse()
    }

    override fun screenshot() {
        controller?.collapse()
        if (!GameMasterAccessibilityService.isConnected) {
            toast(R.string.ov_toast_screenshot_needs_service)
            requestPermission(AppPermission.SCREENSHOT_SERVICE)
            return
        }
        scope.launch {
            // Wait for the panel's closing animation so it is not in the picture.
            delay(SCREENSHOT_DELAY_MS)
            if (!GameMasterAccessibilityService.takeScreenshot()) toast(R.string.ov_toast_screenshot_failed)
        }
    }

    override fun toggleRecording() {
        controller?.collapse()
        ScreenRecorder.toggle(this)
    }

    override fun requestPermission(permission: AppPermission) {
        try {
            startActivity(permissions.settingsIntent(permission).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            controller?.collapse()
        } catch (e: ActivityNotFoundException) {
            toast(R.string.perm_open_failed)
        }
    }

    override fun toast(@StringRes message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_STOP = "com.senzaproject.gamespace.action.STOP_ASSISTANT"
        private const val NOTIFICATION_ID = 4101
        private const val ACTIVE_TICK_MS = 2_000L
        private const val IDLE_TICK_MS = 3_000L
        private const val SCREENSHOT_DELAY_MS = 450L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FloatingPanelService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingPanelService::class.java))
        }
    }
}
