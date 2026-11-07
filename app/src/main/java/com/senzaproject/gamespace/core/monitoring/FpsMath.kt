package com.senzaproject.gamespace.core.monitoring

// Pure frame-rate maths with no Android dependencies, so it is unit-tested on the JVM.

/** Frame rate and per-frame durations measured over one window. */
data class FpsSample(val fps: Float, val frameTimesMs: List<Float>)

/** Parsed output of `dumpsys SurfaceFlinger --latency <layer>`. Times are in nanoseconds. */
data class LatencyData(val refreshPeriodNs: Long, val presentTimesNs: List<Long>)

/**
 * Parses SurfaceFlinger latency output: the first line is the refresh period, then one line per frame with
 * desired present, actual present and frame ready times. Only the actual present time is used.
 * A 0 marks a frame that was never presented and Long.MAX_VALUE one that is still pending; both are skipped.
 */
fun parseSurfaceFlingerLatency(text: String): LatencyData? {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val period = lines.firstOrNull()?.toLongOrNull() ?: return null
    if (period <= 0L) return null
    val presents = lines.drop(1).mapNotNull { line ->
        val columns = line.split(Regex("\\s+"))
        if (columns.size < 3) return@mapNotNull null
        val actual = columns[1].toLongOrNull() ?: return@mapNotNull null
        if (actual <= 0L || actual == Long.MAX_VALUE) null else actual
    }
    return LatencyData(period, presents)
}

/** Frame rate over the most recent [windowNs] of presented frames, or null with fewer than two frames. */
fun recentFrameWindow(presentTimesNs: List<Long>, windowNs: Long = 1_000_000_000L): FpsSample? {
    val times = presentTimesNs.distinct().sorted()
    if (times.size < 2) return null
    val newest = times.last()
    val recent = times.filter { it >= newest - windowNs }
    if (recent.size < 2) return null
    val spanNs = recent.last() - recent.first()
    if (spanNs <= 0L) return null
    return FpsSample(
        fps = (recent.size - 1) * 1_000_000_000f / spanNs,
        frameTimesMs = recent.zipWithNext { a, b -> (b - a) / 1_000_000f },
    )
}

/**
 * Share of frames, 0..100, that took no more than 1.5 times the median frame time.
 * This is a documented heuristic for how even the frame pacing is. Null when there are no frames.
 */
fun stabilityPercent(frameTimesMs: List<Float>): Int? {
    if (frameTimesMs.isEmpty()) return null
    val median = frameTimesMs.sorted()[frameTimesMs.size / 2]
    if (median <= 0f) return null
    val steady = frameTimesMs.count { it <= median * 1.5f }
    return (steady * 100) / frameTimesMs.size
}

/** Turns Choreographer frame callbacks into one [FpsSample] per window. */
class FrameWindowCounter(private val windowNs: Long = 1_000_000_000L) {
    private var windowStartNs = 0L
    private var lastFrameNs = 0L
    private var frames = 0
    private val frameTimes = ArrayList<Float>()

    /** Feed each frame timestamp. Returns a sample when a window completes, otherwise null. */
    fun onFrame(frameTimeNanos: Long): FpsSample? {
        var result: FpsSample? = null
        if (windowStartNs == 0L) {
            windowStartNs = frameTimeNanos
        } else {
            frames++
            frameTimes.add((frameTimeNanos - lastFrameNs) / 1_000_000f)
            val elapsed = frameTimeNanos - windowStartNs
            if (elapsed >= windowNs) {
                result = FpsSample(frames * 1_000_000_000f / elapsed, frameTimes.toList())
                windowStartNs = frameTimeNanos
                frames = 0
                frameTimes.clear()
            }
        }
        lastFrameNs = frameTimeNanos
        return result
    }
}

data class FpsSummary(
    val current: Float,
    val average: Float,
    val minimum: Float,
    val maximum: Float,
    val stabilityPercent: Int?,
    val worstFrameMs: Float?,
    val history: List<Float>,
)

/** Running statistics for one measurement session, with a bounded history for the graph. */
class FpsAccumulator(
    private val historyCapacity: Int = 120,
    private val frameTimeCapacity: Int = 600,
) {
    private val history = ArrayDeque<Float>()
    private val recentFrameTimes = ArrayDeque<Float>()
    private var sum = 0.0
    private var count = 0L
    private var minimum = Float.MAX_VALUE
    private var maximum = 0f

    fun add(fps: Float, frameTimesMs: List<Float>): FpsSummary {
        sum += fps
        count++
        if (fps < minimum) minimum = fps
        if (fps > maximum) maximum = fps
        history.addLast(fps)
        while (history.size > historyCapacity) history.removeFirst()
        frameTimesMs.forEach { recentFrameTimes.addLast(it) }
        while (recentFrameTimes.size > frameTimeCapacity) recentFrameTimes.removeFirst()
        return FpsSummary(
            current = fps,
            average = (sum / count).toFloat(),
            minimum = minimum,
            maximum = maximum,
            stabilityPercent = stabilityPercent(recentFrameTimes.toList()),
            worstFrameMs = frameTimesMs.maxOrNull(),
            history = history.toList(),
        )
    }
}
