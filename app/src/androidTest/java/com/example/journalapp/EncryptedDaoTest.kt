package com.example.journalapp

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.journalapp.data.JournalDatabase
import com.example.journalapp.data.JournalEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class EncryptedDaoTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sqlcipherBackedRoomHandlesInsertAndSearch() = runBlocking {
        val dbName = "enc_dao_probe"
        context.deleteDatabase(dbName)
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            Log.w("EncryptedDaoTest", "already loaded")
        }
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }

        val db = Room.databaseBuilder(context, JournalDatabase::class.java, dbName)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
        val dao = db.journalDao()
        dao.insert(JournalEntry(title = "needle 100%", content = "match", sessionId = "S"))
        dao.insert(JournalEntry(title = "decoy", content = "100abc", sessionId = "S"))

        assertEquals(2, dao.getAllEntries().first().size)
        assertEquals(listOf("needle 100%"), dao.searchEntries("100\\%").first().map { it.title })
        db.close()
        context.deleteDatabase(dbName)
    }
}
