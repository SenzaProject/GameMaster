package com.senzaproject.gamespace.core.assistant

import java.time.Instant
import java.time.ZoneId

/** One finished (or last-seen) play session, in epoch milliseconds. */
data class SessionSpan(val packageName: String, val label: String, val startedAt: Long, val endedAt: Long)

data class GameTotal(val packageName: String, val label: String, val totalMs: Long, val sessions: Int)

data class StatsSummary(
    val totalMs: Long,
    val sessionCount: Int,
    val averageMs: Long,
    val perGame: List<GameTotal>,
    /** Play time per calendar day, oldest first, ending today. */
    val dailyMs: List<Long>,
)

enum class StatsRange(val days: Int?) { WEEK(7), MONTH(30), ALL(null) }

/**
 * Summarises sessions inside a range. A null [rangeDays] means all history.
 * Time is clipped to the window, so a session that started before it only counts the part inside.
 * The daily chart always covers the last [chartDays] days, split at local midnight.
 */
fun summarizeSessions(
    spans: List<SessionSpan>,
    nowMs: Long,
    rangeDays: Int?,
    chartDays: Int,
    zone: ZoneId,
): StatsSummary {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    fun dayStart(daysAgo: Int): Long = today.minusDays(daysAgo.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    val windowStart = if (rangeDays == null) 0L else dayStart(rangeDays - 1)

    val valid = spans.filter { it.endedAt > it.startedAt }
    val inWindow = valid.filter { it.endedAt > windowStart }
    val clipped = inWindow.map { it to (it.endedAt - maxOf(it.startedAt, windowStart)) }
    val total = clipped.sumOf { it.second }
    val count = inWindow.count { it.startedAt >= windowStart }

    val perGame = clipped.groupBy { it.first.packageName }.map { (pkg, items) ->
        GameTotal(pkg, items.maxBy { it.first.startedAt }.first.label, items.sumOf { it.second }, items.size)
    }.sortedByDescending { it.totalMs }

    val daily = (chartDays - 1 downTo 0).map { ago ->
        val start = dayStart(ago)
        val end = if (ago == 0) today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() else dayStart(ago - 1)
        valid.sumOf { (minOf(it.endedAt, end) - maxOf(it.startedAt, start)).coerceAtLeast(0L) }
    }
    return StatsSummary(total, count, if (count == 0) 0L else total / count, perGame, daily)
}

/** "1 h 05 min" style text for a duration; seconds only below one minute. */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "%d h %02d min".format(hours, minutes)
        minutes > 0 -> "%d min".format(minutes)
        else -> "%d s".format(totalSeconds)
    }
}
