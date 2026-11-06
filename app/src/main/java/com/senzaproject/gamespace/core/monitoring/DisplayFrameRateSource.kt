package com.senzaproject.gamespace.core.monitoring

import android.view.Choreographer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts the frames Android's display pipeline actually delivers to this app, once per second.
 * It measures Game Master's own screen, not a game. It keeps requesting frames while collected, so
 * collect it only while the FPS screen is visible. Must be collected on the main thread.
 */
@Singleton
class DisplayFrameRateSource @Inject constructor() {
    fun samples(): Flow<FpsSample> = callbackFlow {
        val choreographer = Choreographer.getInstance()
        val counter = FrameWindowCounter()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                counter.onFrame(frameTimeNanos)?.let { trySend(it) }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        awaitClose { choreographer.removeFrameCallback(callback) }
    }
}
