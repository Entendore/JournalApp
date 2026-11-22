package com.example.journalapp.data

/** Room projection POJO — dedupe key for CSV import. */
data class EntryKey(val sessionId: String, val timestamp: Long, val title: String)
