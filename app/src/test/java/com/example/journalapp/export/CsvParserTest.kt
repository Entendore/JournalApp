package com.example.journalapp.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {

    @Test
    fun `plain rows split on commas and newlines`() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), CsvParser.parse("a,b\n1,2"))
    }

    @Test
    fun `quoted fields keep commas, newlines and escaped quotes`() {
        val parsed = CsvParser.parse("\"x,y\",\"line1\nline2\",\"say \"\"hi\"\"\"")
        assertEquals(listOf("x,y", "line1\nline2", "say \"hi\""), parsed.single())
    }

    @Test
    fun `bom is stripped and CRLF is handled`() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d")),
            CsvParser.parse("\uFEFFa,b\r\nc,d\r\n")
        )
    }

    @Test
    fun `empty fields survive, including a trailing one`() {
        assertEquals(listOf(listOf("a", "", "c"), listOf("d", "")), CsvParser.parse("a,,c\nd,"))
    }

    @Test
    fun `unterminated quote terminates without hanging`() {
        assertEquals(listOf(listOf("open")), CsvParser.parse("\"open"))
    }
}
