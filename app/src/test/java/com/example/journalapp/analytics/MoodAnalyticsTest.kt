package com.example.journalapp.analytics

import com.example.journalapp.data.EntryTheme
import com.example.journalapp.data.JournalEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class MoodAnalyticsTest {

    private val defaultLocale = Locale.getDefault()

    @Before fun useUS() { Locale.setDefault(Locale.US) }
    @After fun restore() { Locale.setDefault(defaultLocale) }

    /** Calendar-based day stepping — DST-safe, unlike DAY_MS arithmetic. */
    private fun dayStart(daysAgo: Int): Long = Calendar.getInstance().apply {
        timeInMillis = MoodAnalytics.startOfDay(System.currentTimeMillis())
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    private fun entry(daysAgo: Int, theme: String = "FREE") = JournalEntry(
        title = "t", entryDate = dayStart(daysAgo), theme = theme
    )

    @Test
    fun `startOfDay zeroes the clock fields`() {
        val start = MoodAnalytics.startOfDay(System.currentTimeMillis())
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `grid is weeks-times-7 days, ending at this week's start`() {
        val summary = MoodAnalytics.compute(emptyList(), weeks = 4)
        assertEquals(28, summary.days.size)

        val today = dayStart(0)
        val cal = Calendar.getInstance().apply { timeInMillis = today }
        val offset = ((cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek) + 7) % 7
        val expected = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -offset)
        }.let { c ->
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            c.timeInMillis
        }
        assertEquals(expected, summary.days.last().dayStart)

        cal.timeInMillis = summary.days.first().dayStart
        assertEquals(cal.firstDayOfWeek, cal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `streak counts consecutive days, anchored on today or yesterday`() {
        assertEquals(3, MoodAnalytics.compute(listOf(entry(0), entry(1), entry(2))).currentStreak)
        assertEquals(2, MoodAnalytics.compute(listOf(entry(0), entry(1))).currentStreak)
        assertEquals(1, MoodAnalytics.compute(listOf(entry(1))).currentStreak)
        assertEquals(0, MoodAnalytics.compute(listOf(entry(2))).currentStreak)
    }

    @Test
    fun `dominant theme per day and top theme overall`() {
        val entries = listOf(
            entry(0, EntryTheme.GRATITUDE.name), entry(0, EntryTheme.GRATITUDE.name),
            entry(0, EntryTheme.TRAVEL.name), entry(3, EntryTheme.TRAVEL.name)
        )
        val summary = MoodAnalytics.compute(entries)
        val today = summary.days.last { it.total == 3 }
        assertEquals(EntryTheme.GRATITUDE, today.dominant)
        assertEquals(EntryTheme.GRATITUDE, summary.topTheme)
        assertEquals(2, MoodAnalytics.entriesForDay(entries, dayStart(3)).size)
    }
}
