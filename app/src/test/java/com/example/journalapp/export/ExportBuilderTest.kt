package com.example.journalapp

import com.example.journalapp.data.EntryTheme
import com.example.journalapp.data.JournalEntry
import com.example.journalapp.export.ExportBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportBuilderTest {

    private val entry = JournalEntry(
        id = 1,
        title = "Trip, day \"two\"",
        content = "Line1\nLine2",
        entryDate = 1_700_000_000_000,
        sessionId = "abcd1234-uuid",
        theme = EntryTheme.TRAVEL.name
    )

    @Test
    fun `csv escapes commas quotes and newlines`() {
        val csv = ExportBuilder.buildCsv(listOf(entry))
        assertTrue(csv.contains("\"Trip, day \"\"two\"\"\""))
        assertTrue(csv.contains("\"Line1\nLine2\""))
    }

    @Test
    fun `csv keeps the full session uuid, not the short code`() {
        assertTrue(ExportBuilder.buildCsv(listOf(entry)).contains("abcd1234-uuid"))
    }

    @Test
    fun `text export contains title and content`() {
        val text = ExportBuilder.buildText(listOf(entry))
        assertTrue(text.contains("Trip, day \"two\""))
        assertTrue(text.contains("Line1\nLine2"))
    }
}
