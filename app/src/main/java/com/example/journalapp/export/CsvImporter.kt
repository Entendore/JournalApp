package com.example.journalapp.export

import com.example.journalapp.data.EntryFormat
import com.example.journalapp.data.EntryTheme
import com.example.journalapp.data.JournalEntry
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object CsvImporter {

    data class Result(val entries: List<JournalEntry>, val malformed: Int)

    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    private val isoDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }

    fun parse(raw: String): Result {
        val rows = CsvParser.parse(raw)
        if (rows.isEmpty()) return Result(emptyList(), 0)

        val header = rows.first().map { it.trim().lowercase(Locale.US) }
        if (!header.contains("entry_date")) return Result(emptyList(), rows.size - 1)
        val idx = { col: String -> header.indexOf(col) }

        var malformed = 0
        val entries = mutableListOf<JournalEntry>()

        for (row in rows.drop(1)) {
            val get = { col: String ->
                val i = idx(col)
                if (i == -1 || i >= row.size) "" else row[i].trim()
            }
            try {
                val entryDate = when {
                    get("entry_time").isNotEmpty() -> isoDateTime.parse(get("entry_time")).time
                    get("entry_date").isNotEmpty() -> noonOn(isoDate.parse(get("entry_date")).time)
                    else -> throw ParseException("no date", 0)
                }
                val createdAt = isoDateTime.parse(get("created_at")).time

                entries += JournalEntry(
                    id = 0,   // always a new row; dedupe is decided by the caller
                    title = get("title"),
                    content = get("content"),
                    timestamp = createdAt,
                    entryDate = entryDate,
                    sessionId = get("session_id"),
                    theme = EntryTheme.from(get("theme").uppercase(Locale.US)).name,
                    format = EntryFormat.from(get("format").uppercase(Locale.US)).name
                )
            } catch (e: ParseException) { malformed++ }
              catch (e: IndexOutOfBoundsException) { malformed++ }
        }
        return Result(entries, malformed)
    }

    private fun noonOn(date: Long): Long = Calendar.getInstance().apply {
        timeInMillis = date
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
