package com.example.journalapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class SessionManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `fresh foreground creates an id`() {
        SessionManager.onAppForeground(context)
        assertTrue(SessionManager.sessionId.isNotEmpty())
    }

    @Test
    fun `returning within the timeout keeps the same sitting`() {
        SessionManager.onAppForeground(context)
        val first = SessionManager.sessionId
        SessionManager.onAppBackground(context)
        SessionManager.onAppForeground(context)
        assertEquals(first, SessionManager.sessionId)
    }

    @Test
    fun `gap beyond two hours starts a new sitting`() {
        SessionManager.onAppForeground(context)
        val first = SessionManager.sessionId
        context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE).edit()
            .putLong("last_active", System.currentTimeMillis() - 3 * 60 * 60 * 1000L)
            .commit()
        SessionManager.onAppForeground(context)
        assertNotEquals(first, SessionManager.sessionId)
    }
}
