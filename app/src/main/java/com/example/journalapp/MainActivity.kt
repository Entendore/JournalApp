package com.example.journalapp

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.journalapp.data.EntryFormat
import com.example.journalapp.data.JournalEntry
import com.example.journalapp.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : LockAwareActivity() {

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val BACKUP_AFTER_MS = 7 * DAY_MS
        private const val NUDGE_REPEAT_MS = 3 * DAY_MS
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: JournalAdapter
    private lateinit var viewModel: JournalViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupEdgeToEdge()

        viewModel = ViewModelProvider(this)[JournalViewModel::class.java]

        adapter = JournalAdapter(
            onClick = { entry -> openEditor(entry.id) },
            onLongClick = { entry -> confirmDelete(entry) },
            onSessionClick = { sessionId -> SessionDetailActivity.start(this, sessionId) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Swipe-to-delete with Undo. Undo re-inserts with the ORIGINAL row id
        // (Room honors non-zero ids), so sessions/heatmap stay intact.
        val swipeHandler = object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
                if (vh is JournalAdapter.HeaderViewHolder) 0
                else super.getSwipeDirs(rv, vh)

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val position = vh.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val item = adapter.currentList[position]
                if (item is ListItem.EntryItem) {
                    viewModel.delete(item.entry)
                    Snackbar.make(binding.root, R.string.entry_deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo) { viewModel.insert(item.entry) }
                        .show()
                } else {
                    adapter.notifyItemChanged(position)
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerView)

        viewModel.allEntries.observe(this) { all ->
            maybeNudgeBackup(all.size, all.minOfOrNull { it.timestamp } ?: 0L)
        }
        viewModel.entries.observe(this) { entries ->
            adapter.submitEntries(entries)
            val filtering = viewModel.filter.value
                ?.let { it.query.isNotBlank() || it.sessionOnly } == true
            if (entries.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.textEmptyTitle.setText(
                    if (filtering) R.string.no_results else R.string.empty_title
                )
                binding.textEmptySubtitle.visibility =
                    if (filtering) View.GONE else View.VISIBLE
                if (!filtering) binding.textEmptySubtitle.setText(R.string.empty_subtitle)
            } else {
                binding.emptyState.visibility = View.GONE
            }
        }

        binding.fabAdd.setOnClickListener { showNewEntryFormatPicker() }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBar.updatePadding(top = bars.top)
            binding.recyclerView.updatePadding(bottom = dp(96) + bars.bottom)
            insets
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showNewEntryFormatPicker() {
        val formats = EntryFormat.values()
        AlertDialog.Builder(this)
            .setTitle(R.string.format_picker_title)
            .setItems(formats.map { getString(it.labelRes) }.toTypedArray()) { _, which ->
                openEditor(AddEditEntryActivity.NEW_ENTRY_ID, formats[which].name)
            }
            .show()
    }

    private fun openEditor(entryId: Long, formatName: String? = null) {
        val intent = Intent(this, AddEditEntryActivity::class.java)
            .putExtra(AddEditEntryActivity.EXTRA_ENTRY_ID, entryId)
        formatName?.let { intent.putExtra(AddEditEntryActivity.EXTRA_FORMAT, it) }
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        // Restore toolbar state after rotation
        menu.findItem(R.id.action_session_filter).isChecked =
            viewModel.filter.value?.sessionOnly == true

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setQuery(query.orEmpty().trim()); return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setQuery(newText.orEmpty().trim()); return true
            }
        })
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.setQuery("")
                return true
            }
        })

        val currentQuery = viewModel.filter.value?.query.orEmpty()
        if (currentQuery.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(currentQuery, false)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_analytics -> { startActivity(Intent(this, HeatmapActivity::class.java)); true }
        R.id.action_export -> { ExportActivity.start(this); true }
        R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        R.id.action_session_filter -> {
            item.isChecked = !item.isChecked
            viewModel.toggleSessionFilter()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun confirmDelete(entry: JournalEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(entry) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun maybeNudgeBackup(totalEntries: Int, oldestTimestamp: Long) {
        if (totalEntries == 0) return
        val prefs = getSharedPreferences("backup_prefs", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastExport = prefs.getLong("last_export_at", 0)
        val lastNudge = prefs.getLong("last_nudge_at", 0)

        if (lastExport == 0 && now - oldestTimestamp < BACKUP_AFTER_MS) return
        if (lastExport != 0 && now - lastExport < BACKUP_AFTER_MS) return
        if (now - lastNudge < NUDGE_REPEAT_MS) return

        prefs.edit().putLong("last_nudge_at", now).apply()
        Snackbar.make(binding.root, R.string.backup_nudge, Snackbar.LENGTH_LONG)
            .setAction(R.string.export) { ExportActivity.start(this) }
            .show()
    }
}
