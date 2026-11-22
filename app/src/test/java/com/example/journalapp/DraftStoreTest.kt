package com.example.journalapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class DraftStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun reset() = DraftStore.clear(context)

    @Test
    fun `draft round-trips including photos`() {
        val draft = DraftStore.Draft(
            entryId = -1, title = "T", content = "C",
            entryDate = 123L, theme = "TRAVEL", format = "FREE_FORM",
            photos = listOf("a.jpg", "b.jpg"), savedAt = 456L
        )
        DraftStore.save(context, draft)
        assertEquals(draft, DraftStore.load(context))
    }

    @Test
    fun `corrupt draft degrades to null`() {
        context.getSharedPreferences("draft_prefs", Context.MODE_PRIVATE).edit()
            .putString("draft", "{ not json").commit()
        assertNull(DraftStore.load(context))
    }

    @Test
    fun `clear removes the draft`() {
        DraftStore.save(context, DraftStore.Draft(1, "", "", 0, "FREE", "FREE_FORM", emptyList(), 0))
        DraftStore.clear(context)
        assertNull(DraftStore.load(context))
    }
}
