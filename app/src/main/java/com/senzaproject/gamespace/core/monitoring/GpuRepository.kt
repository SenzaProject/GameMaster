package com.senzaproject.gamespace.core.monitoring

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class GpuInfo(
    val renderer: String?,
    val vendor: String?,
    val glVersion: String?,
    val glesVersion: String?,
    val vulkanVersion: String?,
)

/** Live GPU numbers. Only some Qualcomm Adreno phones let apps read them; everywhere else they are null. */
data class GpuLoad(val busyPercent: Int?, val frequencyMhz: Int?)

@Singleton
class GpuRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var cached: GpuInfo? = null

    /** GPU model and graphics API support, read once. */
    suspend fun info(): GpuInfo = cached ?: withContext(Dispatchers.Default) {
        readInfo().also { cached = it }
    }

    fun loads(intervalMs: Long = 2_000L): Flow<GpuLoad> = flow {
        while (true) {
            val busy = parseLeadingInt(readTextOrNull("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"))
            val hz = readTextOrNull("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")?.trim()?.toLongOrNull()
            emit(GpuLoad(busy, hz?.takeIf { it > 0L }?.let { (it / 1_000_000L).toInt() }))
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun readInfo(): GpuInfo {
        val egl = queryEgl()
        val manager = context.getSystemService(ActivityManager::class.java)
        val gles = runCatching { manager.deviceConfigurationInfo.glEsVersion }.getOrNull()
        val vulkan = runCatching {
            context.packageManager.systemAvailableFeatures
                .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
                ?.let { vulkanVersionString(it.version) }
        }.getOrNull()
        return GpuInfo(egl?.renderer, egl?.vendor, egl?.version, gles, vulkan)
    }

    private data class EglStrings(val renderer: String?, val vendor: String?, val version: String?)

    /** Creates a 1x1 off-screen OpenGL ES context just long enough to read the driver's identification strings. */
    private fun queryEgl(): EglStrings? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        var eglContext = EGL14.EGL_NO_CONTEXT
        var eglSurface = EGL14.EGL_NO_SURFACE
        try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val configAttributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            if (!EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, count, 0) || count[0] < 1) return null
            val config = configs[0] ?: return null
            eglContext = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            eglSurface = EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
            )
            if (eglContext == EGL14.EGL_NO_CONTEXT || eglSurface == EGL14.EGL_NO_SURFACE) return null
            if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) return null
            return EglStrings(
                renderer = GLES20.glGetString(GLES20.GL_RENDERER),
                vendor = GLES20.glGetString(GLES20.GL_VENDOR),
                version = GLES20.glGetString(GLES20.GL_VERSION),
            )
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
        }
    }
}
