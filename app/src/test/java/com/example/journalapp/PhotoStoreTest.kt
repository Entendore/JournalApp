package com.example.journalapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.journalapp.data.PhotoStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class PhotoStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `photo name list round-trips through JSON`() {
        assertEquals("", PhotoStore.encode(emptyList()))
        assertEquals(emptyList<String>(), PhotoStore.decode(""))
        assertEquals(emptyList<String>(), PhotoStore.decode("not json"))
        val names = listOf("a.jpg", "b.jpg")
        assertEquals(names, PhotoStore.decode(PhotoStore.encode(names)))
    }

    @Test
    fun `cleanupOrphans deletes only unreferenced files`() = runBlocking {
        val dir = File(context.filesDir, "journal_photos").apply { mkdirs() }
        File(dir, "keep.jpg").writeText("x")
        File(dir, "gone.jpg").writeText("x")

        PhotoStore.cleanupOrphans(context, setOf("keep.jpg"))

        assertTrue(File(dir, "keep.jpg").exists())
        assertFalse(File(dir, "gone.jpg").exists())
    }
}
