package com.example.journalapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.journalapp.data.EntryFormat
import com.example.journalapp.data.EntryTheme
import com.example.journalapp.data.JournalEntry
import com.example.journalapp.data.PhotoStore
import com.example.journalapp.databinding.ItemJournalEntryBinding
import com.example.journalapp.databinding.ItemJournalHeaderBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ListItem {
    data class Header(val dateLabel: String) : ListItem()
    data class EntryItem(val entry: JournalEntry) : ListItem()
}

class JournalAdapter(
    private val onClick: (JournalEntry) -> Unit,
    private val onLongClick: (JournalEntry) -> Unit,
    private val onSessionClick: ((String) -> Unit)? = null
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(DiffCallback) {

    private val dayFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun submitEntries(entries: List<JournalEntry>) {
        val items = mutableListOf<ListItem>()
        var lastDay = ""
        for (entry in entries) {
            val day = dayFormat.format(Date(entry.entryDate))
            if (day != lastDay) {
                items += ListItem.Header(day)
                lastDay = day
            }
            items += ListItem.EntryItem(entry)
        }
        submitList(items)
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position) is ListItem.Header) TYPE_HEADER else TYPE_ENTRY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADER) {
            HeaderViewHolder(
                ItemJournalHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        } else {
            EntryViewHolder(
                ItemJournalEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Header -> (holder as HeaderViewHolder).bind(item.dateLabel)
            is ListItem.EntryItem -> (holder as EntryViewHolder).bind(item.entry)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemJournalHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(label: String) { binding.textHeader.text = label }
    }

    inner class EntryViewHolder(private val binding: ItemJournalEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: JournalEntry) {
            val ctx = binding.root.context
            val theme = EntryTheme.from(entry.theme)
            val format = EntryFormat.from(entry.format)

            binding.textTitle.text = entry.title.ifEmpty { ctx.getString(R.string.untitled) }
            val timeText = timeFormat.format(Date(entry.entryDate))
            binding.textTime.text =
                if (entry.lastModified > 0) ctx.getString(R.string.edited_suffix, timeText)
                else timeText
            binding.textPreview.text = entry.content
                .replace(Regex("\\s+"), " ")
                .ifEmpty { ctx.getString(R.string.no_content) }
            binding.textFormat.text = ctx.getString(format.labelRes)

            val themeColor = Color.parseColor(theme.hex)
            binding.viewThemeStripe.setBackgroundColor(themeColor)
            binding.chipTheme.text = ctx.getString(theme.labelRes)
            binding.chipTheme.background?.mutate()?.setTint(themeColor)

            val photos = PhotoStore.decode(entry.photos)
            if (photos.isEmpty()) {
                binding.imageThumb.visibility = View.GONE
                binding.textPhotoCount.visibility = View.GONE
            } else {
                binding.imageThumb.visibility = View.VISIBLE
                binding.imageThumb.load(PhotoStore.file(ctx, photos.first()))
                if (photos.size > 1) {
                    binding.textPhotoCount.visibility = View.VISIBLE
                    binding.textPhotoCount.text = "+${photos.size - 1}"
                } else {
                    binding.textPhotoCount.visibility = View.GONE
                }
            }

            if (entry.sessionId.isEmpty() || onSessionClick == null) {
                binding.textSession.visibility = View.GONE
            } else {
                binding.textSession.visibility = View.VISIBLE
                binding.textSession.text =
                    ctx.getString(R.string.session_code, SessionManager.shortCode(entry.sessionId))
                binding.textSession.contentDescription =
                    ctx.getString(R.string.session_chip_cd, SessionManager.shortCode(entry.sessionId))
                binding.textSession.setOnClickListener { onSessionClick?.invoke(entry.sessionId) }
            }

            binding.root.setOnClickListener { onClick(entry) }
            binding.root.setOnLongClickListener { onLongClick(entry); true }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<ListItem>() {
            override fun areItemsTheSame(old: ListItem, new: ListItem): Boolean = when {
                old is ListItem.Header && new is ListItem.Header -> old.dateLabel == new.dateLabel
                old is ListItem.EntryItem && new is ListItem.EntryItem -> old.entry.id == new.entry.id
                else -> false
            }
            override fun areContentsTheSame(old: ListItem, new: ListItem) = old == new
        }
    }
}
