package com.example.journalapp.analytics

import com.example.journalapp.data.JournalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TimeAnalyticsTest {

    private fun at(hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun entry(hour: Int, theme: String = "FREE") = JournalEntry(
        title = "t", timestamp = at(hour), theme = theme
    )

    @Test
    fun `hourOf extracts the local hour`() {
        assertEquals(9, TimeAnalytics.hourOf(at(9, 30)))
        assertEquals(0, TimeAnalytics.hourOf(at(0, 1)))
        assertEquals(23, TimeAnalytics.hourOf(at(23, 59)))
    }

    @Test
    fun `buckets count per hour and find the peak`() {
        val summary = TimeAnalytics.compute(listOf(entry(9), entry(9), entry(22)))
        assertEquals(2, summary.buckets[9].count)
        assertEquals(9, summary.peakHour)
        assertEquals(2, summary.peakCount)
        assertEquals(1, summary.buckets[22].count)
    }

    @Test
    fun `night wraps around midnight`() {
        assertTrue(TimeAnalytics.Period.NIGHT.contains(23))
        assertTrue(TimeAnalytics.Period.NIGHT.contains(2))
        assertFalse(TimeAnalytics.Period.NIGHT.contains(5))
    }

    @Test
    fun `period breakdown rounds and totals sensibly`() {
        val shares = TimeAnalytics.periodBreakdown(listOf(entry(9), entry(10), entry(23)))
            .associateBy { it.period }
        assertEquals(2, shares[TimeAnalytics.Period.MORNING]!!.count)
        assertEquals(67, shares[TimeAnalytics.Period.MORNING]!!.percent)
        assertEquals(33, shares[TimeAnalytics.Period.NIGHT]!!.percent)
        assertEquals(0, shares[TimeAnalytics.Period.AFTERNOON]!!.percent)
    }

    @Test
    fun `day buckets honor firstDayOfWeek`() {
        val monday = Calendar.getInstance().apply {
            set(2025, Calendar.JANUARY, 6, 12, 0)
        }.timeInMillis
        val sunday = Calendar.getInstance().apply {
            set(2025, Calendar.JANUARY, 12, 12, 0)
        }.timeInMillis
        val entries = listOf(
            JournalEntry(title = "mon", timestamp = monday),
            JournalEntry(title = "sun", timestamp = sunday)
        )

        val mondayFirst = TimeAnalytics.computeByDay(entries, Calendar.MONDAY)
        assertEquals(Calendar.MONDAY, mondayFirst[0].dayOfWeek)
        assertEquals("mon", mondayFirst[0].entries.single().title)
        assertEquals("sun", mondayFirst[6].entries.single().title)

        val sundayFirst = TimeAnalytics.computeByDay(entries, Calendar.SUNDAY)
        assertEquals(Calendar.SUNDAY, sundayFirst[0].dayOfWeek)
        assertEquals(Calendar.MONDAY, sundayFirst[1].dayOfWeek)
    }
}
