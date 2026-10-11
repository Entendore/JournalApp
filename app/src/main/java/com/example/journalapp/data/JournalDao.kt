package com.example.journalapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Query("SELECT * FROM journal_entries ORDER BY entryDate DESC, timestamp DESC")
    fun getAllEntries(): Flow<List<JournalEntry>>

    @Query(
        "SELECT * FROM journal_entries " +
        "WHERE title LIKE '%' || :query || '%' ESCAPE '\\' " +
        "OR content LIKE '%' || :query || '%' ESCAPE '\\' " +
        "ORDER BY entryDate DESC, timestamp DESC"
    )
    fun searchEntries(query: String): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE sessionId = :sessionId ORDER BY entryDate DESC, timestamp DESC")
    fun getSessionEntries(sessionId: String): Flow<List<JournalEntry>>

    @Query(
        "SELECT * FROM journal_entries " +
        "WHERE sessionId = :sessionId AND (title LIKE '%' || :query || '%' ESCAPE '\\' " +
        "OR content LIKE '%' || :query || '%' ESCAPE '\\') " +
        "ORDER BY entryDate DESC, timestamp DESC"
    )
    fun searchSessionEntries(sessionId: String, query: String): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): JournalEntry?

    @Query("SELECT sessionId, timestamp, title FROM journal_entries")
    suspend fun getEntryKeys(): List<EntryKey>

    @Query("SELECT photos FROM journal_entries")
    suspend fun getAllPhotoJson(): List<String>

    @Insert suspend fun insert(entry: JournalEntry)
    @Insert suspend fun insertAll(entries: List<JournalEntry>)
    @Update suspend fun update(entry: JournalEntry)
    @Delete suspend fun delete(entry: JournalEntry)
}
