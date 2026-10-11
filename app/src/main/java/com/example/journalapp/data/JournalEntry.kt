package com.example.journalapp.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "journal_entries",
    indices = [Index("sessionId"), Index("entryDate")]
)
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val entryDate: Long = timestamp,
    val sessionId: String = "",
    val theme: String = EntryTheme.FREE.name,
    val format: String = EntryFormat.FREE_FORM.name,
    val lastModified: Long = 0,
    val photos: String = ""
)
