package com.senzaproject.gamespace.core.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FpsMathTest {
    private val maxLong = "9223372036854775807"

    @Test fun latency_parsesPeriodAndPresentTimes() {
        val text = """
            16666666
            0	0	0
            1000000000	1016666666	1010000000
            1016666666	1033333332	1030000000
            $maxLong	$maxLong	$maxLong
        """.trimIndent()
        val data = parseSurfaceFlingerLatency(text)!!
        assertEquals(16_666_666L, data.refreshPeriodNs)
        assertEquals(listOf(1_016_666_666L, 1_033_333_332L), data.presentTimesNs)
    }

    @Test fun latency_rejectsEmptyOrInvalidOutput() {
        assertNull(parseSurfaceFlingerLatency(""))
        assertNull(parseSurfaceFlingerLatency("not a number"))
        assertNull(parseSurfaceFlingerLatency("0\n1 2 3"))
    }

    @Test fun latency_ignoresMalformedLines() {
        val data = parseSurfaceFlingerLatency("16666666\nabc\n1 2\n5 7000 9")!!
        assertEquals(listOf(7000L), data.presentTimesNs)
    }

    @Test fun window_computesThirtyFps() {
        val step = 33_333_333L
        val times = (0 until 31).map { 1_000_000_000L + it * step }
        val sample = recentFrameWindow(times)!!
        assertEquals(30f, sample.fps, 0.01f)
        assertEquals(30, sample.frameTimesMs.size)
        assertEquals(33.33f, sample.frameTimesMs.first(), 0.01f)
    }

    @Test fun window_onlyUsesTheLastSecond() {
        val times = listOf(1_000_000_000L, 5_000_000_000L, 5_016_666_667L)
        val sample = recentFrameWindow(times)!!
        assertEquals(60f, sample.fps, 0.01f)
        assertEquals(1, sample.frameTimesMs.size)
    }

    @Test fun window_needsTwoDistinctFrames() {
        assertNull(recentFrameWindow(emptyList()))
        assertNull(recentFrameWindow(listOf(5L)))
        assertNull(recentFrameWindow(listOf(5L, 5L)))
    }

    @Test fun stability_countsEvenFrames() {
        assertEquals(100, stabilityPercent(listOf(16.6f, 16.7f, 16.6f)))
        assertEquals(80, stabilityPercent(listOf(16f, 16f, 16f, 16f, 50f)))
        assertNull(stabilityPercent(emptyList()))
    }

    @Test fun frameCounter_emitsOneSamplePerWindow() {
        val counter = FrameWindowCounter()
        val start = 1_000_000_000L
        val step = 100_000_000L
        assertNull(counter.onFrame(start))
        for (k in 1..9) assertNull(counter.onFrame(start + k * step))
        val first = counter.onFrame(start + 10 * step)
        assertNotNull(first)
        assertEquals(10f, first!!.fps, 0.001f)
        assertEquals(10, first.frameTimesMs.size)
        assertEquals(100f, first.frameTimesMs[0], 0.001f)

        for (k in 11..19) assertNull(counter.onFrame(start + k * step))
        assertEquals(10f, counter.onFrame(start + 20 * step)!!.fps, 0.001f)
    }

    @Test fun accumulator_tracksAverageMinimumAndMaximum() {
        val accumulator = FpsAccumulator()
        accumulator.add(60f, listOf(16.6f, 16.7f))
        val summary = accumulator.add(30f, listOf(33f, 34f))
        assertEquals(30f, summary.current, 0.001f)
        assertEquals(45f, summary.average, 0.001f)
        assertEquals(30f, summary.minimum, 0.001f)
        assertEquals(60f, summary.maximum, 0.001f)
        assertEquals(34f, summary.worstFrameMs!!, 0.001f)
        assertEquals(listOf(60f, 30f), summary.history)
    }

    @Test fun accumulator_historyIsBounded() {
        val accumulator = FpsAccumulator(historyCapacity = 3)
        var summary = accumulator.add(1f, emptyList())
        for (value in 2..5) summary = accumulator.add(value.toFloat(), emptyList())
        assertEquals(listOf(3f, 4f, 5f), summary.history)
        assertEquals(1f, summary.minimum, 0.001f)
        assertNull(summary.stabilityPercent)
    }
}
