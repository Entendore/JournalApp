package com.example.journalapp.export

/** Minimal RFC 4180 parser: quoted fields, "" escapes, embedded commas/newlines,
 *  CRLF or LF rows, BOM strip. */
object CsvParser {

    fun parse(raw: String): List<List<String>> {
        val text = raw.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        val field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' ->
                        if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ }
                        else inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row += field.toString(); field.clear() }
                c == '\r' -> { /* folded into the following \n */ }
                c == '\n' -> { row += field.toString(); field.clear(); rows += row; row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}
