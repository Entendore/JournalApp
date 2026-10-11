package com.example.journalapp

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.journalapp.data.EntryFormat
import com.example.journalapp.data.EntryTheme
import com.example.journalapp.data.JournalEntry
import com.example.journalapp.data.PhotoAttachAdapter
import com.example.journalapp.data.PhotoStore
import com.example.journalapp.databinding.ActivityAddEditBinding
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddEditEntryActivity : LockAwareActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "extra_entry_id"
        const val EXTRA_FORMAT = "extra_format"
        const val NEW_ENTRY_ID = -1L
        private const val KEY_DATE = "key_date"
        private const val KEY_THEME = "key_theme"
        private const val KEY_FORMAT = "key_format"
        private const val KEY_ORIGINAL = "key_original"
        private const val DRAFT_DEBOUNCE_MS = 400L
    }

    private data class Original(
        val title: String,
        val content: String,
        val date: Long,
        val theme: String,
        val format: String,
        val photos: List<String>
    ) : java.io.Serializable

    private lateinit var binding: ActivityAddEditBinding
    private lateinit var viewModel: JournalViewModel
    private var entryId = NEW_ENTRY_ID

    private var entryDateMillis = System.currentTimeMillis()
    private var selectedTheme = EntryTheme.FREE.name
    private var selectedFormat = EntryFormat.FREE_FORM.name
    private var original: Original? = null
    private var isSaving = false

    private val photoNames = mutableListOf<String>()
    private val removedOriginalPhotos = mutableSetOf<String>()
    private lateinit var photoAdapter: PhotoAttachAdapter
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { addPhoto(it) }
        }

    private val draftHandler = Handler(Looper.getMainLooper())
    private val draftWrite = Runnable { writeDraftNow() }
    private val dayFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isSaving) return
            if (isDirty()) confirmDiscard() else finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[JournalViewModel::class.java]
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        entryId = intent.getLongExtra(EXTRA_ENTRY_ID, NEW_ENTRY_ID)

        if (savedInstanceState != null) {
            entryDateMillis = savedInstanceState.getLong(KEY_DATE, System.currentTimeMillis())
            selectedTheme = savedInstanceState.getString(KEY_THEME, EntryTheme.FREE.name)
            selectedFormat = savedInstanceState.getString(KEY_FORMAT, EntryFormat.FREE_FORM.name)
            @Suppress("DEPRECATION")
            original = savedInstanceState.getSerializable(KEY_ORIGINAL) as? Original
        }

        setupThemeChips()
        setupPhotos()
        binding.buttonDate.setOnClickListener { showDatePicker() }

        val editorWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                updateWordCount()
                scheduleDraftSave()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.editTextContent.addTextChangedListener(editorWatcher)
        binding.editTextTitle.addTextChangedListener(editorWatcher)

        onBackPressedDispatcher.addCallback(this, backCallback)

        if (entryId == NEW_ENTRY_ID) {
            supportActionBar?.title = getString(R.string.new_entry)
            val format = EntryFormat.from(
                intent.getStringExtra(EXTRA_FORMAT) ?: EntryFormat.FREE_FORM.name
            )
            selectedFormat = format.name
            supportActionBar?.subtitle = getString(format.labelRes)

            if (savedInstanceState == null) {
                val template = if (format.templateRes != 0) getString(format.templateRes) else ""
                if (template.isNotEmpty()) binding.editTextContent.setText(template)
                original = Original(
                    "", template.trim(), entryDateMillis, selectedTheme, selectedFormat,
                    emptyList()
                )
            }
        } else {
            supportActionBar?.title = getString(R.string.edit_entry)
            lifecycleScope.launch {
                val entry = viewModel.getEntry(entryId) ?: run {
                    finish()
                    return@launch
                }
                if (savedInstanceState == null) {
                    binding.editTextTitle.setText(entry.title)
                    binding.editTextContent.setText(entry.content)
                    entryDateMillis = entry.entryDate
                    selectedTheme = entry.theme
                    selectedFormat = entry.format
                    updateDateLabel()
                    checkThemeChip()
                }
                photoNames.clear()
                photoNames += PhotoStore.decode(entry.photos)
                photoAdapter.notifyDataSetChanged()
                updatePhotoUi()
                original = Original(
                    entry.title.trim(), entry.content.trim(), entry.entryDate,
                    entry.theme, entry.format, PhotoStore.decode(entry.photos)
                )
                supportActionBar?.subtitle = getString(EntryFormat.from(selectedFormat).labelRes)
                updateWordCount()
            }
        }

        updateDateLabel()
        checkThemeChip()
        updateWordCount()

        if (savedInstanceState == null) {
            DraftStore.load(this)?.let { draft ->
                val matches = draft.entryId == entryId
                val hasText = draft.title.isNotBlank() || draft.content.isNotBlank()
                if (matches && hasText) offerDraft(draft)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_DATE, entryDateMillis)
        outState.putString(KEY_THEME, selectedTheme)
        outState.putString(KEY_FORMAT, selectedFormat)
        original?.let { outState.putSerializable(KEY_ORIGINAL, it) }
    }

    // ── Dirty tracking + guarded back ────────────────────────────────────────

    private fun currentSnapshot(): Original = Original(
        binding.editTextTitle.text.toString().trim(),
        binding.editTextContent.text.toString().trim(),
        entryDateMillis, selectedTheme, selectedFormat,
        photoNames.toList()
    )

    private fun isDirty(): Boolean {
        val orig = original ?: return false
        return currentSnapshot() != orig
    }

    private fun confirmDiscard() {
        AlertDialog.Builder(this)
            .setTitle(R.string.discard_title)
            .setMessage(R.string.discard_message)
            .setPositiveButton(R.string.save) { _, _ -> saveEntry() }
            .setNegativeButton(R.string.discard) { _, _ ->
                DraftStore.clear(this)
                finish()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    // ── Word counter ──────────────────────────────────────────────────────────

    private fun updateWordCount() {
        val text = binding.editTextContent.text.toString().trim()
        val words = countWords(text)
        binding.textWordCount.text =
            resources.getQuantityString(R.plurals.word_count, words, words, text.length)
    }

    private fun countWords(text: String): Int {
        var count = 0
        var inWord = false
        for (ch in text) {
            val ws = ch.isWhitespace()
            if (!ws && !inWord) count++
            inWord = !ws
        }
        return count
    }

    // ── Draft auto-save ──────────────────────────────────────────────────────

    private fun scheduleDraftSave() {
        draftHandler.removeCallbacks(draftWrite)
        draftHandler.postDelayed(draftWrite, DRAFT_DEBOUNCE_MS)
    }

    /** Pristine editors never overwrite the draft — cancelling the restore
     *  dialog and leaving must not destroy the work the draft holds. */
    private fun writeDraftNow() {
        draftHandler.removeCallbacks(draftWrite)
        if (!isDirty()) return
        DraftStore.save(
            this,
            DraftStore.Draft(
                entryId = entryId,
                title = binding.editTextTitle.text.toString(),
                content = binding.editTextContent.text.toString(),
                entryDate = entryDateMillis,
                theme = selectedTheme,
                format = selectedFormat,
                photos = photoNames.toList(),
                savedAt = System.currentTimeMillis()
            )
        )
    }

    private fun offerDraft(draft: DraftStore.Draft) {
        val when_ = DateUtils.getRelativeTimeSpanString(draft.savedAt)
        AlertDialog.Builder(this)
            .setTitle(R.string.draft_found_title)
            .setMessage(getString(R.string.draft_found_message, when_))
            .setPositiveButton(R.string.draft_restore) { _, _ -> applyDraft(draft) }
            .setNegativeButton(R.string.draft_discard) { _, _ -> DraftStore.clear(this) }
            .show()
    }

    private fun applyDraft(draft: DraftStore.Draft) {
        binding.editTextTitle.setText(draft.title)
        binding.editTextContent.setText(draft.content)
        entryDateMillis = draft.entryDate
        selectedTheme = draft.theme
        selectedFormat = draft.format
        photoNames.clear()
        photoNames += draft.photos
        photoAdapter.notifyDataSetChanged()
        updatePhotoUi()
        updateDateLabel()
        checkThemeChip()
        supportActionBar?.subtitle = getString(EntryFormat.from(selectedFormat).labelRes)
        updateWordCount()
    }

    override fun onPause() {
        if (!isSaving) writeDraftNow()
        super.onPause()
    }

    override fun onDestroy() {
        draftHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ── Theme chips ──────────────────────────────────────────────────────────

    private fun setupThemeChips() {
        EntryTheme.values().forEach { theme ->
            val themeColor = Color.parseColor(theme.hex)
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = getString(theme.labelRes)
                isCheckable = true
                tag = theme.name
                chipBackgroundColor = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(
                        themeColor,
                        MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant)
                    )
                )
                setTextColor(
                    ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(
                            Color.WHITE,
                            MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
                        )
                    )
                )
            }
            binding.chipGroupTheme.addView(chip)
        }
        binding.chipGroupTheme.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedTheme = findViewById<Chip>(checkedIds[0]).tag as String
                scheduleDraftSave()
            }
        }
    }

    private fun checkThemeChip() {
        for (i in 0 until binding.chipGroupTheme.childCount) {
            val chip = binding.chipGroupTheme.getChildAt(i) as Chip
            if (chip.tag == selectedTheme) {
                binding.chipGroupTheme.check(chip.id)
                return
            }
        }
    }

    // ── Photos ───────────────────────────────────────────────────────────────

    private fun setupPhotos() {
        photoAdapter = PhotoAttachAdapter(
            photoNames,
            onRemove = { pos ->
                val name = photoNames.removeAt(pos)
                if (original?.photos?.contains(name) == true) removedOriginalPhotos.add(name)
                photoAdapter.notifyItemRemoved(pos)
                updatePhotoUi()
                scheduleDraftSave()
            },
            onClick = { pos -> showPhotoDialog(pos) }
        )
        binding.photoList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.photoList.adapter = photoAdapter
        binding.buttonAddPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
    }

    private fun addPhoto(uri: Uri) {
        lifecycleScope.launch {
            val name = PhotoStore.saveFromUri(this@AddEditEntryActivity, uri)
            if (name == null) {
                Toast.makeText(this@AddEditEntryActivity, R.string.photo_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            photoNames.add(name)
            photoAdapter.notifyItemInserted(photoNames.size - 1)
            updatePhotoUi()
            scheduleDraftSave()
        }
    }

    private fun updatePhotoUi() {
        binding.photoList.visibility = if (photoNames.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showPhotoDialog(position: Int) {
        val image = ImageView(this).apply {
            adjustViewBounds = true
            setPadding(24, 24, 24, 24)
            load(PhotoStore.file(this@AddEditEntryActivity, photoNames[position]))
        }
        AlertDialog.Builder(this)
            .setView(image)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    // ── Date picker ──────────────────────────────────────────────────────────

    private fun showDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = entryDateMillis }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
                cal.set(Calendar.HOUR_OF_DAY, 12)
                cal.set(Calendar.MINUTE, 0)
                entryDateMillis = cal.timeInMillis
                updateDateLabel()
                scheduleDraftSave()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateLabel() {
        binding.buttonDate.text = dayFormat.format(Date(entryDateMillis))
    }

    // ── Menu ─────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_add_edit, menu)
        menu.findItem(R.id.action_delete).isVisible = entryId != NEW_ENTRY_ID
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_save -> { saveEntry(); true }
        R.id.action_format -> { showFormatPicker(); true }
        R.id.action_delete -> { confirmDelete(); true }
        android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showFormatPicker() {
        val formats = EntryFormat.values()
        val labels = formats.map { getString(it.labelRes) }.toTypedArray()
        val checkedIndex = formats.indexOfFirst { it.name == selectedFormat }
        AlertDialog.Builder(this)
            .setTitle(R.string.format_picker_title)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                selectedFormat = formats[which].name
                supportActionBar?.subtitle = getString(formats[which].labelRes)
                applyTemplate(formats[which])
                scheduleDraftSave()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyTemplate(format: EntryFormat) {
        if (format.templateRes == 0) return
        val template = getString(format.templateRes)
        val current = binding.editTextContent.text.toString()
        if (current.isBlank()) {
            binding.editTextContent.setText(template)
        } else if (current.trim() != template.trim()) {
            binding.editTextContent.append("\n\n$template")
            Toast.makeText(this, R.string.template_added, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Save / delete ────────────────────────────────────────────────────────

    private fun saveEntry() {
        if (isSaving) return
        val title = binding.editTextTitle.text.toString().trim()
        val content = binding.editTextContent.text.toString().trim()

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_save, Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        lifecycleScope.launch {
            if (entryId == NEW_ENTRY_ID) {
                viewModel.insert(
                    JournalEntry(
                        title = title.ifEmpty { getString(R.string.untitled) },
                        content = content,
                        entryDate = entryDateMillis,
                        sessionId = SessionManager.sessionId,
                        theme = selectedTheme,
                        format = selectedFormat,
                        photos = PhotoStore.encode(photoNames)
                    )
                )
            } else {
                viewModel.getEntry(entryId)?.let { entry ->
                    viewModel.update(
                        entry.copy(
                            title = title.ifEmpty { getString(R.string.untitled) },
                            content = content,
                            entryDate = entryDateMillis,
                            theme = selectedTheme,
                            format = selectedFormat,
                            lastModified = System.currentTimeMillis(),
                            photos = PhotoStore.encode(photoNames)
                        )
                    )
                }
                PhotoStore.deleteAll(this@AddEditEntryActivity, removedOriginalPhotos)
            }
            DraftStore.clear(this@AddEditEntryActivity)
            finish()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    viewModel.getEntry(entryId)?.let { viewModel.delete(it) }
                    DraftStore.clear(this@AddEditEntryActivity)
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
