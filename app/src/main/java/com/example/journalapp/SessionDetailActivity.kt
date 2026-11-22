package com.example.journalapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.journalapp.analytics.MoodAnalytics
import com.example.journalapp.data.JournalEntry
import com.example.journalapp.databinding.ActivitySessionDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionDetailActivity : LockAwareActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"

        fun start(context: Context, sessionId: String) {
            context.startActivity(
                Intent(context, SessionDetailActivity::class.java)
                    .putExtra(EXTRA_SESSION_ID, sessionId)
            )
        }
    }

    private lateinit var binding: ActivitySessionDetailBinding
    private lateinit var adapter: JournalAdapter
    private lateinit var viewModel: JournalViewModel

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        viewModel = ViewModelProvider(this)[JournalViewModel::class.java]

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title =
            getString(R.string.session_code, SessionManager.shortCode(sessionId))

        adapter = JournalAdapter(
            onClick = { entry -> openEditor(entry.id) },
            onLongClick = { entry -> confirmDelete(entry) },
            onSessionClick = null
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.getSessionEntries(sessionId).observe(this) { entries ->
            adapter.submitEntries(entries)
            binding.textEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            bindSummary(entries)
        }
    }

    private fun bindSummary(entries: List<JournalEntry>) {
        supportActionBar?.subtitle =
            resources.getQuantityString(R.plurals.session_entry_count, entries.size, entries.size)
        if (entries.isEmpty()) return

        val min = entries.minOf { it.entryDate }
        val max = entries.maxOf { it.entryDate }
        binding.textSessionSpan.text =
            if (MoodAnalytics.startOfDay(min) == MoodAnalytics.startOfDay(max)) {
                "${timeFormat.format(Date(min))} – ${timeFormat.format(Date(max))}"
            } else {
                "${dateFormat.format(Date(min))} – ${dateFormat.format(Date(max))}"
            }
    }

    private fun openEditor(id: Long) = startActivity(
        Intent(this, AddEditEntryActivity::class.java)
            .putExtra(AddEditEntryActivity.EXTRA_ENTRY_ID, id)
    )

    private fun confirmDelete(entry: JournalEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(entry) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_session_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_export_session -> {
            ExportActivity.start(this, intent.getStringExtra(EXTRA_SESSION_ID))
            true
        }
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
