package com.example.journalapp

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.journalapp.data.JournalDatabase
import com.example.journalapp.data.JournalEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class JournalViewModelTest {

    @get:Rule val instantExecutor = InstantTaskExecutorRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: JournalDatabase
    private lateinit var vm: JournalViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        SessionManager.onAppForeground(context)
        db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java)
            .allowMainThreadQueries().build()
        vm = JournalViewModel(context.applicationContext as Application, db.journalDao())
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun entry(title: String, content: String = "", session: String = "") =
        JournalEntry(title = title, content = content, sessionId = session)

    @Test
    fun `no filter shows everything`() = runTest {
        db.journalDao().insert(entry("a"))
        db.journalDao().insert(entry("b"))
        assertEquals(2, vm.entries.getOrAwaitValue().size)
    }

    @Test
    fun `setQuery escapes LIKE wildcards end to end`() = runTest {
        db.journalDao().insert(entry("a", content = "cost is 100% sure"))
        db.journalDao().insert(entry("b", content = "order 100abc done"))
        vm.setQuery("100%")
        assertEquals(listOf("a"), vm.entries.getOrAwaitValue().map { it.title })
    }

    @Test
    fun `session toggle narrows to the current sitting`() = runTest {
        db.journalDao().insert(entry("mine", session = SessionManager.sessionId))
        db.journalDao().insert(entry("theirs", session = "OTHER"))
        vm.toggleSessionFilter()
        assertEquals(listOf("mine"), vm.entries.getOrAwaitValue().map { it.title })
    }
}
