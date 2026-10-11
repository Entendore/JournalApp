package com.example.journalapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.journalapp.data.EntryFormat
import com.example.journalapp.data.EntryKey
import com.example.journalapp.data.EntryTheme
import com.example.journalapp.data.JournalEntry
import com.example.journalapp.data.PhotoStore
import com.example.journalapp.databinding.ActivityExportBinding
import com.example.journalapp.export.CsvImporter
import com.example.journalapp.export.ExportBuilder
import com.example.journalapp.export.PdfBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportActivity : LockAwareActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"

        fun start(context: Context, sessionId: String? = null) {
            context.startActivity(
                Intent(context, ExportActivity::class.java)
                    .putExtra(EXTRA_SESSION_ID, sessionId)
            )
        }
    }

    private lateinit var binding: ActivityExportBinding
    private lateinit var viewModel: JournalViewModel

    private var sessionId: String? = null
    private var latestEntries: List<JournalEntry> = emptyList()

    private val saveTextLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { saveExport(it) } }

    private val saveCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { saveExport(it) } }

    private val savePdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { saveExport(it) } }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importCsv(it) } }

    private val themeLabel: (EntryTheme) -> String = { getString(it.labelRes) }
    private val formatLabel: (EntryFormat) -> String = { getString(it.labelRes) }
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        viewModel = ViewModelProvider(this)[JournalViewModel::class.java]

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.export_title)

        if (sessionId == null) {
            binding.cardScope.visibility = View.GONE
        } else {
            binding.radioSession.text =
                getString(R.string.scope_session, SessionManager.shortCode(sessionId!!))
            binding.radioSession.isChecked = true
        }

        binding.radioGroupScope.setOnCheckedChangeListener { _, _ -> refreshPreview() }
        binding.radioGroupFormat.setOnCheckedChangeListener { _, _ ->
            binding.textPdfHint.visibility = if (isPdf) View.VISIBLE else View.GONE
            refreshPreview()
        }

        binding.buttonDrive.setOnClickListener { pickSaveTarget() }
        binding.buttonEmail.setOnClickListener { emailExport() }
        binding.buttonImport.setOnClickListener {
            importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
        }

        viewModel.allEntries.observe(this) { entries ->
            latestEntries = entries
            refreshPreview()
            binding.buttonDrive.isEnabled = true
            binding.buttonEmail.isEnabled = true
        }
    }

    private val isSessionScope: Boolean
        get() = sessionId != null && binding.radioSession.isChecked
    private val isCsv: Boolean get() = binding.radioCsv.isChecked
    private val isPdf: Boolean get() = binding.radioPdf.isChecked

    private fun entriesToExport(): List<JournalEntry> =
        if (isSessionScope) latestEntries.filter { it.sessionId == sessionId }
        else latestEntries

    private fun buildFileName(): String {
        val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val base = if (isSessionScope) {
            "Journal_Session_${SessionManager.shortCode(sessionId!!)}_$datePart"
        } else {
            "Journal_Export_$datePart"
        }
        return "$base." + when {
            isPdf -> "pdf"
            isCsv -> "csv"
            else -> "txt"
        }
    }

    private fun pdfHeader(): String {
        val n = entriesToExport().size
        return getString(R.string.export_file_title) + "  ·  " +
            resources.getQuantityString(R.plurals.export_entry_count, n, n)
    }

    private fun pdfMetaLine(e: JournalEntry): String = listOf(
        timeFormat.format(Date(e.entryDate)),
        getString(EntryTheme.from(e.theme).labelRes),
        getString(EntryFormat.from(e.format).labelRes)
    ).joinToString(" · ")

    private fun photoFileFor(name: String): File? =
        PhotoStore.file(this, name).takeIf { it.exists() }

    /** Unified single-pass file build (runs on IO). */
    private suspend fun buildExportFile(): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(File(cacheDir, "exports").apply { mkdirs() }, buildFileName())
            when {
                isPdf -> PdfBuilder.build(
                    entriesToExport(), ::photoFileFor, ::pdfMetaLine, pdfHeader(), file
                )
                isCsv -> file.writeText("\uFEFF" + ExportBuilder.buildCsv(entriesToExport()))
                else -> file.writeText(
                    ExportBuilder.buildText(
                        entriesToExport(), themeLabel, formatLabel,
                        ExportBuilder.TextHeader(
                            getString(R.string.export_file_title),
                            getString(R.string.export_generated),
                            getString(R.string.export_entries)
                        ),
                        { getString(R.string.session_code, SessionManager.shortCode(it)) }
                    )
                )
            }
            file
        } catch (e: Exception) { null }
    }

    private fun refreshPreview() {
        val entries = entriesToExport()
        binding.textCount.text =
            resources.getQuantityString(R.plurals.export_entry_count, entries.size, entries.size)
        if (isPdf) {
            binding.textPreview.text = getString(R.string.preview_pdf_note)
            return
        }
        val text = if (isCsv) ExportBuilder.buildCsv(entries)
        else ExportBuilder.buildText(entries, themeLabel, formatLabel)
        binding.textPreview.text = if (text.length > 400) text.take(400) + "…" else text
    }

    private fun pickSaveTarget() {
        val name = buildFileName()
        when {
            isPdf -> savePdfLauncher.launch(name)
            isCsv -> saveCsvLauncher.launch(name)
            else -> saveTextLauncher.launch(name)
        }
    }

    private fun saveExport(uri: Uri) {
        lifecycleScope.launch {
            val file = buildExportFile()
            val ok = file != null && withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(file!!.readBytes())
                    } != null
                } catch (e: Exception) { false }
            }
            if (ok) {
                markExported()
                Toast.makeText(this@ExportActivity, R.string.export_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ExportActivity, R.string.export_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun emailExport() {
        val entries = entriesToExport()
        if (entries.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_export, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val file = buildExportFile() ?: run {
                Toast.makeText(this@ExportActivity, R.string.export_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            val attachmentUri = FileProvider.getUriForFile(
                this@ExportActivity, "$packageName.fileprovider", file
            )
            val mime = when {
                isPdf -> "application/pdf"
                isCsv -> "text/csv"
                else -> "text/plain"
            }
            val scopeLabel =
                if (isSessionScope) getString(R.string.session_code, SessionManager.shortCode(sessionId!!))
                else resources.getQuantityString(
                    R.plurals.export_entry_count, entries.size, entries.size
                )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject, scopeLabel))
                putExtra(Intent.EXTRA_TEXT, getString(R.string.email_body, entries.size))
                putExtra(Intent.EXTRA_STREAM, attachmentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            markExported()
            startActivity(
                Intent.createChooser(sendIntent, getString(R.string.choose_email_app))
            )
        }
    }

    private fun importCsv(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val text = contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    text?.let { CsvImporter.parse(it) }
                } catch (e: Exception) { null }
            }

            if (result == null || result.entries.isEmpty()) {
                Toast.makeText(this@ExportActivity, R.string.import_failed, Toast.LENGTH_LONG).show()
                return@launch
            }

            val keys = viewModel.existingKeys()
            val fresh = result.entries.filter {
                EntryKey(it.sessionId, it.timestamp, it.title) !in keys
            }
            val skipped = result.entries.size - fresh.size

            AlertDialog.Builder(this@ExportActivity)
                .setTitle(R.string.import_confirm_title)
                .setMessage(getString(R.string.import_result, fresh.size, skipped, result.malformed))
                .setPositiveButton(R.string.import_now) { _, _ ->
                    if (fresh.isEmpty()) return@setPositiveButton
                    viewModel.importAll(fresh) { n ->
                        Toast.makeText(
                            this@ExportActivity,
                            getString(R.string.import_done, n), Toast.LENGTH_SHORT
                        ).show()
                    }
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun markExported() {
        getSharedPreferences("backup_prefs", MODE_PRIVATE).edit()
            .putLong("last_export_at", System.currentTimeMillis())
            .apply()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == android.R.id.home) { finish(); true }
        else super.onOptionsItemSelected(item)
}
