package com.example.journalapp.export

import com.example.journalapp.SessionManager
import com.example.journalapp.data.JournalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExportBuilderTextTest {

    private fun dated(y: Int, m: Int, d: Int): Long = Calendar.getInstance().apply {
        set(y, m, d, 12, 0)
    }.timeInMillis

    @Test
    fun `empty export is header-only`() {
        val text = ExportBuilder.buildText(emptyList())
        assertTrue(text.contains("Entries: 0"))
        assertFalse(text.contains("===="))
    }

    @Test
    fun `custom header and session label are used`() {
        val entry = JournalEntry(title = "t", sessionId = "abcd-1234-uuid")
        val text = ExportBuilder.buildText(
            listOf(entry),
            header = ExportBuilder.TextHeader("MY TITLE", "Generated", "Entries"),
            sessionLabel = { "SITTING ${SessionManager.shortCode(it)}" }
        )
        assertTrue(text.contains("MY TITLE"))
        assertTrue(text.contains("Entries: 1"))
        assertTrue(text.contains("SITTING 1234"))
    }

    @Test
    fun `entries are ordered oldest first`() {
        val text = ExportBuilder.buildText(listOf(
            JournalEntry(title = "later", entryDate = dated(2025, Calendar.JANUARY, 10)),
            JournalEntry(title = "earlier", entryDate = dated(2025, Calendar.JANUARY, 5))
        ))
        assertTrue(text.indexOf("earlier") < text.indexOf("later"))
    }
}
