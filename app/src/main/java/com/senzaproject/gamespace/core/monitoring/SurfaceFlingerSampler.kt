package com.senzaproject.gamespace.core.monitoring

enum class SfFailure { COMMAND_FAILED, PERMISSION_DENIED, LAYER_NOT_FOUND, NO_DATA }

sealed interface SfReading {
    data class Frames(val sample: FpsSample) : SfReading
    /** The layer exists but presented no new frame since the last reading, so the game is paused or idle. */
    data object NoNewFrames : SfReading
    data class Unavailable(val reason: SfFailure) : SfReading
}

/** Chooses the layer that belongs to [packageName]. Games draw on a SurfaceView, so those layers win. */
fun pickLayer(layers: List<String>, packageName: String): String? {
    val candidates = layers.map { it.trim() }.filter {
        it.contains(packageName) && !it.startsWith("Background for") && !it.startsWith("Bounds for")
    }
    return candidates.firstOrNull { it.startsWith("SurfaceView") } ?: candidates.firstOrNull()
}

private fun ShellResult.deniedByAndroid(): Boolean {
    val text = stdout + stderr
    return text.contains("Permission Denial", ignoreCase = true) ||
        text.contains("permission denied", ignoreCase = true) ||
        text.contains("android.permission.DUMP")
}

/**
 * Measures a game's frame rate from SurfaceFlinger's frame timestamps (`dumpsys SurfaceFlinger --latency`).
 * Android only allows this for callers holding the DUMP permission (granted over ADB) or root.
 * Create one sampler per measurement session.
 */
class SurfaceFlingerSampler(private val shell: ShellRunner) {
    private var layer: String? = null
    private var lastNewestNs = 0L

    suspend fun sample(packageName: String): SfReading {
        var name = layer
        if (name == null) {
            val listing = shell.run(listOf("dumpsys", "SurfaceFlinger", "--list"))
                ?: return SfReading.Unavailable(SfFailure.COMMAND_FAILED)
            if (listing.deniedByAndroid()) return SfReading.Unavailable(SfFailure.PERMISSION_DENIED)
            name = pickLayer(listing.stdout.lines(), packageName)
                ?: return SfReading.Unavailable(SfFailure.LAYER_NOT_FOUND)
            layer = name
        }
        val result = shell.run(listOf("dumpsys", "SurfaceFlinger", "--latency", name))
            ?: return SfReading.Unavailable(SfFailure.COMMAND_FAILED)
        if (result.deniedByAndroid()) return SfReading.Unavailable(SfFailure.PERMISSION_DENIED)

        val data = parseSurfaceFlingerLatency(result.stdout)
        val newest = data?.presentTimesNs?.maxOrNull()
        if (data == null || newest == null) {
            // The layer was probably recreated (for example the game restarted). Look it up again next time.
            layer = null
            return SfReading.Unavailable(SfFailure.NO_DATA)
        }
        if (newest <= lastNewestNs) return SfReading.NoNewFrames
        lastNewestNs = newest
        val window = recentFrameWindow(data.presentTimesNs) ?: return SfReading.NoNewFrames
        return SfReading.Frames(window)
    }
}
