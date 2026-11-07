package com.senzaproject.gamespace.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StatsMathTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private val minute = 60_000L
    private val hour = 60 * minute
    private val now = at(10, 12)

    private fun span(pkg: String, start: Long, durationMs: Long, label: String = pkg) =
        SessionSpan(pkg, label, start, start + durationMs)

    @Test fun emptyHistoryGivesZeros() {
        val s = summarizeSessions(emptyList(), now, 7, 7, zone)
        assertEquals(0L, s.totalMs)
        assertEquals(0, s.sessionCount)
        assertEquals(0L, s.averageMs)
        assertEquals(List(7) { 0L }, s.dailyMs)
    }

    @Test fun totalsAverageAndCount() {
        val spans = listOf(span("a", at(10, 8), hour), span("b", at(9, 20), 3 * hour))
        val s = summarizeSessions(spans, now, 7, 7, zone)
        assertEquals(4 * hour, s.totalMs)
        assertEquals(2, s.sessionCount)
        assertEquals(2 * hour, s.averageMs)
    }

    @Test fun perGameIsSortedByTimeDescending() {
        val spans = listOf(span("a", at(10, 1), hour), span("b", at(10, 3), 3 * hour), span("a", at(10, 7), hour))
        val s = summarizeSessions(spans, now, 7, 7, zone)
        assertEquals(listOf("b", "a"), s.perGame.map { it.packageName })
        assertEquals(2 * hour, s.perGame[1].totalMs)
        assertEquals(2, s.perGame[1].sessions)
    }

    @Test fun sessionsOlderThanTheRangeAreLeftOut() {
        val old = span("a", at(1, 8), hour)
        val s = summarizeSessions(listOf(old), now, 7, 7, zone)
        assertEquals(0L, s.totalMs)
        assertEquals(0, s.sessionCount)
    }

    @Test fun allTimeIncludesOldSessions() {
        val old = span("a", at(1, 8), hour)
        val s = summarizeSessions(listOf(old), now, null, 30, zone)
        assertEquals(hour, s.totalMs)
        assertEquals(1, s.sessionCount)
    }

    @Test fun sessionOverlappingTheStartOfTheRangeIsClipped() {
        // Range is 7 days incl. today: it starts at 00:00 on the 4th. This session runs from 23:00 on the 3rd to 01:00 on the 4th.
        val s = summarizeSessions(listOf(SessionSpan("a", "a", at(3, 23), at(4, 1))), now, 7, 7, zone)
        assertEquals(hour, s.totalMs)
        assertEquals(0, s.sessionCount) // it started before the window
    }

    @Test fun sessionAcrossMidnightIsSplitBetweenDays() {
        val s = summarizeSessions(listOf(SessionSpan("a", "a", at(9, 23), at(10, 1))), now, 7, 7, zone)
        assertEquals(hour, s.dailyMs[5])
        assertEquals(hour, s.dailyMs[6])
        assertEquals(2 * hour, s.totalMs)
    }

    @Test fun dailyChartEndsToday() {
        val s = summarizeSessions(listOf(span("a", at(10, 9), 30 * minute)), now, 7, 7, zone)
        assertEquals(7, s.dailyMs.size)
        assertEquals(30 * minute, s.dailyMs.last())
        assertEquals(0L, s.dailyMs.first())
    }

    @Test fun zeroAndNegativeLengthSessionsAreIgnored() {
        val s = summarizeSessions(listOf(SessionSpan("a", "a", at(10, 5), at(10, 5)), SessionSpan("b", "b", at(10, 6), at(10, 5))), now, 7, 7, zone)
        assertEquals(0L, s.totalMs)
        assertEquals(0, s.sessionCount)
    }

    @Test fun latestLabelIsUsedForAGame() {
        val spans = listOf(span("a", at(9, 1), hour, "Old name"), span("a", at(10, 1), hour, "New name"))
        assertEquals("New name", summarizeSessions(spans, now, 7, 7, zone).perGame.single().label)
    }

    @Test fun formatsDurations() {
        assertEquals("45 s", formatDuration(45_000))
        assertEquals("5 min", formatDuration(5 * minute + 20_000))
        assertEquals("1 h 05 min", formatDuration(hour + 5 * minute))
        assertEquals("0 s", formatDuration(-10))
        assertTrue(formatDuration(26 * hour).startsWith("26 h"))
    }
}
