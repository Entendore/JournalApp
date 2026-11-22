package com.example.journalapp.analytics

import androidx.annotation.StringRes
import com.example.journalapp.R
import com.example.journalapp.data.EntryTheme
import com.example.journalapp.data.JournalEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

object TimeAnalytics {

    const val HOURS_IN_DAY = 24

    data class HourBucket(val hour: Int, val count: Int, val entries: List<JournalEntry>) {
        val dominant: EntryTheme? by lazy {
            entries.groupingBy { EntryTheme.from(it.theme) }.eachCount()
                .maxByOrNull { it.value }?.key
        }
    }

    data class DayBucket(val dayOfWeek: Int, val entries: List<JournalEntry>) {
        val count: Int get() = entries.size
        val dominant: EntryTheme? by lazy {
            entries.groupingBy { EntryTheme.from(it.theme) }.eachCount()
                .maxByOrNull { it.value }?.key
        }
    }

    data class Summary(val buckets: List<HourBucket>, val peakHour: Int?, val peakCount: Int)

    /** Local hour of day. Deliberately NOT millis % 86_400_000 (that's UTC). */
    fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    /** ONE shared Calendar for the whole pass — O(N), near-zero allocation. */
    private fun hourMap(entries: List<JournalEntry>): Map<Int, MutableList<JournalEntry>> {
        val shared = Calendar.getInstance()
        val map = HashMap<Int, MutableList<JournalEntry>>(HOURS_IN_DAY)
        for (entry in entries) {
            shared.timeInMillis = entry.timestamp
            map.getOrPut(shared.get(Calendar.HOUR_OF_DAY)) { ArrayList() }.add(entry)
        }
        return map
    }

    fun compute(entries: List<JournalEntry>): Summary {
        val map = hourMap(entries)
        val buckets = (0 until HOURS_IN_DAY).map { h ->
            val list = map[h].orEmpty().sortedBy { it.timestamp }
            HourBucket(h, list.size, list)
        }
        val peak = buckets.maxByOrNull { it.count }?.takeIf { it.count > 0 }
        return Summary(buckets, peak?.hour, peak?.count ?: 0)
    }

    /** 7 buckets, ordered from [firstDayOfWeek] (a Calendar.SUNDAY-style constant). */
    fun computeByDay(entries: List<JournalEntry>, firstDayOfWeek: Int): List<DayBucket> {
        val shared = Calendar.getInstance()
        val byDow = HashMap<Int, MutableList<JournalEntry>>()
        for (entry in entries) {
            shared.timeInMillis = entry.timestamp
            byDow.getOrPut(shared.get(Calendar.DAY_OF_WEEK)) { ArrayList() }.add(entry)
        }
        return (0 until 7).map { offset ->
            val dow = ((firstDayOfWeek - 1 + offset) % 7) + 1
            DayBucket(dow, byDow[dow].orEmpty().sortedBy { it.timestamp })
        }
    }

    fun entriesForHour(entries: List<JournalEntry>, hour: Int): List<JournalEntry> =
        hourMap(entries)[hour].orEmpty().sortedBy { it.timestamp }

    private val hourFormat = SimpleDateFormat("h a", Locale.getDefault())

    fun hourLabel(hour: Int): String = hourFormat.format(
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
        }.time
    )

    enum class Period(
        @StringRes val labelRes: Int,
        private val hours: IntRange,
        private val wrap: IntRange = IntRange.EMPTY
    ) {
        MORNING(R.string.period_morning, 5..11),
        AFTERNOON(R.string.period_afternoon, 12..16),
        EVENING(R.string.period_evening, 17..21),
        NIGHT(R.string.period_night, 22..23, 0..4);

        operator fun contains(hour: Int): Boolean = hour in hours || hour in wrap
    }

    data class PeriodShare(val period: Period, val count: Int, val percent: Int)

    fun periodBreakdown(entries: List<JournalEntry>): List<PeriodShare> {
        if (entries.isEmpty()) return Period.values().map { PeriodShare(it, 0, 0) }
        val map = hourMap(entries)
        val total = entries.size
        return Period.values().map { period ->
            val count = map.entries.filter { it.key in period }.sumOf { it.value.size }
            PeriodShare(period, count, (count * 100.0 / total).roundToInt())
        }
    }
}
