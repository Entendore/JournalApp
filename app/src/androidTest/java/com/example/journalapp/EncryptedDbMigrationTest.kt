package com.example.journalapp

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.journalapp.data.JournalDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedDbMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration_probe_db"

    @Test
    fun plaintextV3DatabaseIsEncryptedOnFirstOpen() = runBlocking {
        context.deleteDatabase(dbName)

        val plain = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        plain.execSQL(
            "CREATE TABLE IF NOT EXISTS journal_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "title TEXT NOT NULL, content TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL, entryDate INTEGER NOT NULL, " +
                "sessionId TEXT NOT NULL, theme TEXT NOT NULL, format TEXT NOT NULL, " +
                "lastModified INTEGER NOT NULL)"
        )
        plain.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_sessionId ON journal_entries(sessionId)")
        plain.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_entryDate ON journal_entries(entryDate)")
        plain.execSQL(
            "INSERT INTO journal_entries (title, content, timestamp, entryDate, sessionId, theme, format, lastModified) " +
            "VALUES ('legacy', 'pre-encryption row', 100, 100, 'S1', 'FREE', 'FREE_FORM', 0)"
        )
        plain.close()

        assertTrue(isPlaintext(context.getDatabasePath(dbName)))

        val db = JournalDatabase.buildDatabase(context, dbName)
        val row = db.journalDao().getEntryById(1)
        assertEquals("legacy", row?.title)
        assertEquals("", row?.photos)
        db.close()

        assertFalse(isPlaintext(context.getDatabasePath(dbName)))
    }

    private fun isPlaintext(file: File?): Boolean = try {
        file!!.inputStream().use { s ->
            val header = ByteArray(16)
            s.read(header)
            String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
        }
    } catch (e: Exception) { false }
}
