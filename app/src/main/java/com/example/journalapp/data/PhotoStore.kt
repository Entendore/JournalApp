package com.example.journalapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

object PhotoStore {

    private const val DIR = "journal_photos"
    private const val MAX_DIM = 2048
    private const val JPEG_QUALITY = 85

    fun decode(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun encode(names: List<String>): String =
        if (names.isEmpty()) "" else JSONArray(names).toString()

    fun file(context: Context, name: String): File =
        File(File(context.filesDir, DIR).apply { mkdirs() }, name)

    /** Downsamples + re-encodes as JPEG. Re-encoding strips EXIF (incl. GPS). */
    suspend fun saveFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val cr = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIM) sample *= 2
            val bitmap = cr.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null

            val name = "photo_${System.currentTimeMillis()}.jpg"
            file(context, name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            }
            bitmap.recycle()
            name
        } catch (e: Exception) { null }
    }

    fun deleteAll(context: Context, names: Collection<String>) =
        names.forEach { file(context, it).delete() }

    /** Removes files no entry references. Deferred (runs at app start) so
     *  swipe-undo can still restore photos. */
    suspend fun cleanupOrphans(context: Context, referenced: Set<String>) =
        withContext(Dispatchers.IO) {
            File(context.filesDir, DIR).listFiles()?.forEach { f ->
                if (f.name !in referenced) f.delete()
            }
        }
}
