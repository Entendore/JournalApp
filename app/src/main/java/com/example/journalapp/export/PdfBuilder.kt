package com.example.journalapp.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.journalapp.data.EntryTheme
import com.example.journalapp.data.JournalEntry
import com.example.journalapp.data.PhotoStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A4 @72dpi, paginated text + photo grid. Framework-only. */
object PdfBuilder {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN

    fun build(
        entries: List<JournalEntry>,
        photoFile: (String) -> File?,
        metaLine: (JournalEntry) -> String,
        headerLine: String,
        out: File
    ): Boolean = try {
        val doc = PdfDocument()
        var pageNum = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        val titlePaint = TextPaint().apply {
            textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; color = Color.BLACK
        }
        val dayPaint = TextPaint().apply {
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; color = Color.DKGRAY
        }
        val entryTitlePaint = TextPaint().apply {
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply { textSize = 11f; isAntiAlias = true; color = Color.BLACK }
        val metaPaint = TextPaint().apply { textSize = 9f; isAntiAlias = true; color = Color.DKGRAY }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNum).create())
            p.canvas.drawColor(Color.WHITE)
            page = p; canvas = p.canvas; y = MARGIN
        }
        fun ensure(height: Float) {
            if (page == null || y + height > PAGE_H - MARGIN) newPage()
        }

        fun drawLayout(layout: StaticLayout) {
            var line = 0
            while (line < layout.lineCount) {
                ensure(0f)
                val available = PAGE_H - MARGIN - y
                var fit = 0
                while (line + fit < layout.lineCount &&
                    layout.getLineBottom(line + fit) - layout.getLineTop(line) <= available
                ) fit++
                if (fit == 0) { newPage(); continue }
                val top = layout.getLineTop(line).toFloat()
                val bottom = layout.getLineBottom(line + fit - 1).toFloat()
                val c = canvas!!
                c.save()
                c.translate(MARGIN, y - top)
                c.clipRect(0f, top, CONTENT_W, bottom)
                layout.draw(c)
                c.restore()
                y += bottom - top
                line += fit
            }
        }

        fun text(text: String, paint: TextPaint, spacingAfter: Float = 4f) {
            if (text.isEmpty()) return
            drawLayout(
                StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_W.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            )
            y += spacingAfter
        }

        fun decodeScaled(file: File, targetW: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > targetW * 3) sample *= 2
            return BitmapFactory.decodeFile(
                file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }

        fun drawPhotos(names: List<String>) {
            val gap = 12f
            val cellW = (CONTENT_W - gap) / 2f
            names.forEach { name ->
                val bmp = photoFile(name)?.let { decodeScaled(it, cellW.toInt()) } ?: return@forEach
                val h = cellW * bmp.height / bmp.width
                ensure(h + 4f)
                canvas!!.drawBitmap(bmp, null, RectF(MARGIN, y, MARGIN + cellW, y + h), null)
                bmp.recycle()
                y += h + 4f
            }
        }

        text(headerLine, titlePaint, 12f)

        var lastDay = ""
        val dayFmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        entries.sortedBy { it.entryDate }.forEach { e ->
            val day = dayFmt.format(Date(e.entryDate))
            if (day != lastDay) { ensure(48f); y += 6f; text(day, dayPaint, 8f); lastDay = day }

            ensure(80f)
            entryTitlePaint.color = Color.parseColor(EntryTheme.from(e.theme).hex)
            if (e.title.isNotEmpty()) text(e.title, entryTitlePaint, 2f)
            text(metaLine(e), metaPaint, 8f)
            e.content.split('\n').forEach { paragraph ->
                if (paragraph.isBlank()) y += 8f
                else text(paragraph, bodyPaint, 6f)
            }
            drawPhotos(PhotoStore.decode(e.photos))
            ensure(20f)
            canvas!!.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint)
            y += 18f
        }

        page?.let { doc.finishPage(it) }
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        true
    } catch (e: Exception) { false }
}
