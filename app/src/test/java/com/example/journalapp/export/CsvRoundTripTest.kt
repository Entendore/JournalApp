package com.example.journalapp

import com.example.journalapp.data.JournalEntry
import com.example.journalapp.export.CsvImporter
import com.example.journalapp.export.ExportBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CsvRoundTripTest {

    private fun sample() = JournalEntry(
        id = 7,
        title = "Trip, day \"two\", revisited",
        content = "Line1\nLine2, with commas",
        entryDate = Calendar.getInstance().apply {
            set(2025, Calendar.JANUARY, 6, 12, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis,
        timestamp = 1_736_150_400_000,
        sessionId = "uuid-abcd-1234",
        theme = "TRAVEL",
        format = "GOAL_TRACKER"
    )

    @Test
    fun `export then import preserves every field`() {
        val round = CsvImporter.parse(ExportBuilder.buildCsv(listOf(sample())))
        assertEquals(0, round.malformed)
        val back = round.entries.single()
        val orig = sample()
        assertEquals(orig.title, back.title)
        assertEquals(orig.content, back.content)
        assertEquals(orig.sessionId, back.sessionId)
        assertEquals("TRAVEL", back.theme)
        assertEquals("GOAL_TRACKER", back.format)
        assertEquals(orig.timestamp, back.timestamp)
        assertEquals(orig.entryDate, back.entryDate)
        assertEquals(0, back.id)
    }

    @Test
    fun `bom is stripped and malformed rows are counted`() {
        val good = ExportBuilder.buildCsv(listOf(sample()))
        val dirty = "\uFEFF" + good + "\r\n" + "oops,no,date,here\r\n" +
                "id,entry_date,entry_time,title,theme,format,session_id,created_at,content\r\n" +
                "9,not-a-date,also-bad,t,TRAVEL,FREE_FORM,s,2025-01-06 12:00,c\r\n"
        val result = CsvImporter.parse(dirty)
        assertEquals(1, result.entries.size)
        assertEquals(2, result.malformed)
    }

    @Test
    fun `unknown theme names fall back instead of crashing`() {
        val result = CsvImporter.parse(
            ExportBuilder.buildCsv(listOf(sample())).replace("TRAVEL", "MYSTERY")
        )
        assertEquals("FREE", result.entries.single().theme)
    }
}
