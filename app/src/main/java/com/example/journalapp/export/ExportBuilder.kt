package com.example.journalapp.export

import com.example.journalapp.SessionManager
import com.example.journalapp.data.EntryFormat
import com.example.journalapp.data.EntryTheme
import com.example.journalapp.data.JournalEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds exportable text. No Android dependencies — trivially unit-testable.
 *  CSV uses stable enum NAMES (locale-proof, round-trippable); the .txt uses
 *  localized labels via resolvers. */
object ExportBuilder {

    data class TextHeader(
        val title: String = "MY JOURNAL EXPORT",
        val generated: String = "Generated",
        val entries: String = "Entries"
    )

    private val fullFormat = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val isoDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun buildText(
        entries: List<JournalEntry>,
        themeLabel: (EntryTheme) -> String = { it.name },
        formatLabel: (EntryFormat) -> String = { it.name },
        header: TextHeader = TextHeader(),
        sessionLabel: (String) -> String = { "Session ${SessionManager.shortCode(it)}" }
    ): String {
        val sb = StringBuilder()
        sb.append(header.title).append("\n")
        sb.append(header.generated).append(": ").append(fullFormat.format(Date())).append("\n")
        sb.append(header.entries).append(": ").append(entries.size).append("\n")

        var lastDay = ""
        entries.sortedBy { it.entryDate }.forEach { e ->
            val day = dayFormat.format(Date(e.entryDate))
            if (day != lastDay) {
                sb.append("\n==============================\n").append(day)
                    .append("\n==============================\n")
                lastDay = day
            }
            sb.append("\n")
            if (e.title.isNotEmpty()) sb.append(e.title).append("\n")

            val meta = buildList {
                add(timeFormat.format(Date(e.entryDate)))
                add(themeLabel(EntryTheme.from(e.theme)))
                add(formatLabel(EntryFormat.from(e.format)))
                if (e.sessionId.isNotEmpty()) add(sessionLabel(e.sessionId))
            }.joinToString(" · ")
            sb.append(meta).append("\n\n").append(e.content).append("\n")
            sb.append("----------------------------------------\n")
        }
        return sb.toString()
    }

    fun buildCsv(entries: List<JournalEntry>): String {
        val headerRow = "id,entry_date,entry_time,title,theme,format,session_id,created_at,content"
        val rows = entries.sortedBy { it.entryDate }.map { e ->
            listOf(
                e.id.toString(),
                isoDate.format(Date(e.entryDate)),
                isoDateTime.format(Date(e.entryDate)),
                csvEscape(e.title),
                csvEscape(EntryTheme.from(e.theme).name),
                csvEscape(EntryFormat.from(e.format).name),
                csvEscape(e.sessionId),
                isoDateTime.format(Date(e.timestamp)),
                csvEscape(e.content)
            ).joinToString(",")
        }
        return (listOf(headerRow) + rows).joinToString("\r\n")
    }

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + value.replace("\"", "\"\"") + "\""
        else value
}
