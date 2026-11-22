package com.example.journalapp.analytics

import com.example.journalapp.data.EntryTheme
import com.example.journalapp.data.JournalEntry
import java.util.Calendar

object MoodAnalytics {

    data class DayMood(
        val dayStart: Long,
        val themeCounts: Map<EntryTheme, Int>,
        val total: Int
    ) {
        val dominant: EntryTheme? by lazy { themeCounts.maxByOrNull { it.value }?.key }
    }

    data class Summary(
        val days: List<DayMood>,
        val totalEntries: Int,
        val currentStreak: Int,
        val topTheme: EntryTheme?
    )

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun zeroed(millis: Long): Long = startOfDay(millis)

    fun compute(entries: List<JournalEntry>, weeks: Int = 26): Summary {
        val today = zeroed(System.currentTimeMillis())

        val shared = Calendar.getInstance()
        val byDay = HashMap<Long, MutableMap<EntryTheme, Int>>()
        entries.forEach { e ->
            shared.timeInMillis = e.entryDate
            shared.set(Calendar.HOUR_OF_DAY, 0)
            shared.set(Calendar.MINUTE, 0)
            shared.set(Calendar.SECOND, 0)
            shared.set(Calendar.MILLISECOND, 0)
            byDay.getOrPut(shared.timeInMillis) { HashMap() }
                .merge(EntryTheme.from(e.theme), 1, Int::plus)
        }

        // Align to the locale's first day of week (Sun US, Mon much of Europe…)
        val cal = Calendar.getInstance().apply { timeInMillis = today }
        val firstDayOfWeek = cal.firstDayOfWeek
        val offset = ((cal.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek) + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        val gridEnd = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -((weeks - 1) * 7))
        val gridStart = cal.timeInMillis

        val walker = Calendar.getInstance().apply { timeInMillis = gridStart }
        val days = mutableListOf<DayMood>()
        while (walker.timeInMillis <= gridEnd) {
            val day = walker.timeInMillis
            val counts = byDay[day]
            days += DayMood(day, counts ?: emptyMap(), counts?.values?.sum() ?: 0)
            walker.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Streak: consecutive days, anchored on today (or yesterday)
        val probe = Calendar.getInstance()
        var streak = 0
        var cursor = if (byDay.containsKey(today)) today else zeroed(today - 1)
        while (byDay.containsKey(cursor)) {
            streak++
            probe.timeInMillis = cursor
            probe.add(Calendar.DAY_OF_YEAR, -1)
            cursor = zeroed(probe.timeInMillis)
        }

        val topTheme = byDay.values
            .flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, v) -> v.sum() }
            .maxByOrNull { it.value }?.key

        return Summary(days, entries.size, streak, topTheme)
    }

    fun entriesForDay(entries: List<JournalEntry>, dayStart: Long): List<JournalEntry> =
        entries.filter { zeroed(it.entryDate) == dayStart }.sortedBy { it.entryDate }
}
