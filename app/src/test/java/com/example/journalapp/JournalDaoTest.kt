package com.example.journalapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.journalapp.data.JournalDao
import com.example.journalapp.data.JournalDatabase
import com.example.journalapp.data.JournalEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class JournalDaoTest {

    private lateinit var db: JournalDatabase
    private lateinit var dao: JournalDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.journalDao()
    }

    @After fun tearDown() = db.close()

    private fun entry(
        title: String, content: String = "", date: Long = 0,
        session: String = "", photos: String = ""
    ) = JournalEntry(
        title = title, content = content, timestamp = date,
        entryDate = date, sessionId = session, photos = photos
    )

    @Test
    fun `list orders by entryDate descending`() = runTest {
        dao.insert(entry("old", date = 1_000))
        dao.insert(entry("new", date = 2_000))
        assertEquals(listOf("new", "old"), dao.getAllEntries().first().map { it.title })
    }

    @Test
    fun `search treats percent as a literal, not a wildcard`() = runTest {
        dao.insert(entry("a", content = "cost is 100% sure"))
        dao.insert(entry("b", content = "order 100abc done"))
        assertEquals(listOf("a"), dao.searchEntries("100\\%").first().map { it.title })
    }

    @Test
    fun `search treats underscore as a literal`() = runTest {
        dao.insert(entry("a", content = "price _x here"))
        dao.insert(entry("b", content = "price ax here"))
        assertEquals(listOf("a"), dao.searchEntries("\\_x").first().map { it.title })
    }

    @Test
    fun `session filter and combined session search`() = runTest {
        dao.insert(entry("s1", session = "S1"))
        dao.insert(entry("s2", session = "S2"))
        assertEquals(listOf("s1"), dao.getSessionEntries("S1").first().map { it.title })
        assertEquals(listOf("s1"), dao.searchSessionEntries("s1", "S1").first().map { it.title })
        assertTrue(dao.searchSessionEntries("nope", "S1").first().isEmpty())
    }

    @Test
    fun `keys projection and bulk insert`() = runTest {
        dao.insertAll(listOf(entry("a", session = "S"), entry("b", session = "S")))
        val keys = dao.getEntryKeys()
        assertEquals(2, keys.size)
        assertTrue(keys.all { it.sessionId == "S" })
    }

    @Test
    fun `photos json survives a round trip`() = runTest {
        dao.insert(entry("p", photos = """["x.jpg","y.jpg"]"""))
        assertEquals("""["x.jpg","y.jpg"]""", dao.getAllEntries().first().single().photos)
    }
}
