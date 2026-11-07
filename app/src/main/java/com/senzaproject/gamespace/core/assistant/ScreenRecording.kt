package com.senzaproject.gamespace.core.assistant

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.senzaproject.gamespace.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val since: Long) : RecordingState
    data class Failed(val message: String) : RecordingState
}

/** Process-wide recording state, read by the toolbox and the floating panel. */
object ScreenRecordingState {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()
    internal fun set(value: RecordingState) { _state.value = value }
}

/**
 * Entry points for screen recording. Android requires the user to confirm every recording in a system dialog,
 * so [start] opens a transparent activity that asks, and only then the foreground service begins.
 */
object ScreenRecorder {
    fun start(context: Context) {
        if (ScreenRecordingState.state.value is RecordingState.Recording) return
        val intent = Intent(context, ScreenCaptureConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, ScreenRecordService::class.java).setAction(ScreenRecordService.ACTION_STOP))
    }

    fun toggle(context: Context) {
        if (ScreenRecordingState.state.value is RecordingState.Recording) stop(context) else start(context)
    }
}

/** Transparent activity that shows Android's "start recording?" dialog and hands the result to the service. */
class ScreenCaptureConsentActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, ScreenRecordService::class.java)
                .putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(ScreenRecordService.EXTRA_DATA, data)
            ContextCompat.startForegroundService(this, intent)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // The launcher result is delivered after a recreation.
        val manager = getSystemService(MediaProjectionManager::class.java)
        launcher.launch(manager.createScreenCaptureIntent())
    }
}

/**
 * Records the screen to Movies/GameMaster with MediaRecorder and a MediaProjection virtual display.
 * Video only: capturing game audio needs a separate capture API that is not built.
 */
class ScreenRecordService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var outputUri: android.net.Uri? = null
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishRecording()
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (code != Activity.RESULT_OK || data == null || recorder != null) {
            stopSelf()
            return START_NOT_STICKY
        }
        AssistantNotifications.ensureChannels(this)
        // On Android 14+ the foreground service must be running before the projection token is used.
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        try {
            begin(code, data)
        } catch (e: Exception) {
            discard()
            ScreenRecordingState.set(RecordingState.Failed(e.message ?: e.javaClass.simpleName))
            Toast.makeText(this, getString(R.string.rec_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(code: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection = manager.getMediaProjection(code, data) ?: error("No projection")
        this.projection = projection
        // Android 14+ requires a callback before a virtual display is created.
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post { finishRecording(); stopSelf() }
            }
        }, handler)

        val metrics = resources.displayMetrics
        val (width, height) = recordingSize(metrics.widthPixels, metrics.heightPixels)

        val name = "GameMaster_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/GameMaster")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create file")
        outputUri = uri
        val fd = contentResolver.openFileDescriptor(uri, "w") ?: error("Cannot open file")
        descriptor = fd

        val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        recorder = rec
        rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        rec.setVideoSize(width, height)
        rec.setVideoFrameRate(30)
        rec.setVideoEncodingBitRate(8_000_000)
        rec.setOutputFile(fd.fileDescriptor)
        rec.prepare()
        display = projection.createVirtualDisplay(
            "GameMasterRecording", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rec.surface, null, null,
        )
        rec.start()
        ScreenRecordingState.set(RecordingState.Recording(System.currentTimeMillis()))
    }

    private fun finishRecording() {
        if (stopping) return
        stopping = true
        var saved = false
        try {
            recorder?.stop()
            saved = true
        } catch (e: RuntimeException) {
            // MediaRecorder throws when stopped before it wrote any frame; the empty file is deleted below.
        }
        releaseAll()
        val uri = outputUri
        if (uri != null) {
            if (saved) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(uri, done, null, null)
                Toast.makeText(this, R.string.rec_saved, Toast.LENGTH_LONG).show()
            } else {
                contentResolver.delete(uri, null, null)
            }
        }
        outputUri = null
        ScreenRecordingState.set(RecordingState.Idle)
        stopping = false
    }

    /** Throws away a recording that never started. */
    private fun discard() {
        releaseAll()
        outputUri?.let { contentResolver.delete(it, null, null) }
        outputUri = null
    }

    private fun releaseAll() {
        runCatching { display?.release() }
        runCatching { recorder?.release() }
        runCatching { projection?.stop() }
        runCatching { descriptor?.close() }
        display = null
        recorder = null
        projection = null
        descriptor = null
    }

    override fun onDestroy() {
        if (recorder != null) finishRecording()
        super.onDestroy()
    }

    private fun notification() = NotificationCompat.Builder(this, AssistantNotifications.CHANNEL_RECORDING)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.rec_notification_title))
        .setOngoing(true)
        .addAction(
            0,
            getString(R.string.rec_stop),
            PendingIntent.getService(
                this, 0,
                Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    companion object {
        const val ACTION_STOP = "com.senzaproject.gamespace.action.STOP_RECORDING"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        private const val NOTIFICATION_ID = 4102
    }
}

/** Even dimensions (the encoder needs them), with the long side capped at 1920 pixels. */
fun recordingSize(width: Int, height: Int): Pair<Int, Int> {
    val scale = minOf(1f, 1920f / maxOf(width, height))
    return Pair(((width * scale).toInt() / 2) * 2, ((height * scale).toInt() / 2) * 2)
}
