package com.example.journalapp.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CsvImporterEdgeCasesTest {

    @Test
    fun `empty input and header-only files import nothing`() {
        assertEquals(0, CsvImporter.parse("").entries.size)
        val headerOnly = CsvImporter.parse(ExportBuilder.buildCsv(emptyList()))
        assertEquals(0, headerOnly.entries.size)
        assertEquals(0, headerOnly.malformed)
    }

    @Test
    fun `files without a date column count every row as malformed`() {
        val result = CsvImporter.parse("id,title\r\n1,x\r\n2,y\r\n")
        assertTrue(result.entries.isEmpty())
        assertEquals(2, result.malformed)
    }

    @Test
    fun `date-only rows land at noon`() {
        val csv = "id,entry_date,entry_time,title,theme,format,session_id,created_at,content\r\n" +
                "9,2025-01-06,,Noon test,TRAVEL,FREE_FORM,s1,2025-01-06 09:00,body\r\n"
        val entry = CsvImporter.parse(csv).entries.single()

        val expectedNoon = Calendar.getInstance().apply {
            set(2025, Calendar.JANUARY, 6, 12, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expectedNoon, entry.entryDate)
        assertEquals("Noon test", entry.title)
        assertEquals(0, entry.id)
    }
}
