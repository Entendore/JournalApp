package com.example.journalapp

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModelProvider
import com.example.journalapp.analytics.MoodAnalytics
import com.example.journalapp.analytics.TimeAnalytics
import com.example.journalapp.data.EntryTheme
import com.example.journalapp.data.JournalEntry
import com.example.journalapp.databinding.ActivityHeatmapBinding
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HeatmapActivity : LockAwareActivity() {

    companion object {
        private const val WEEKS_SHOWN = 26
        private const val CELL_DP = 15
        private const val GAP_DP = 3
        private const val LABEL_HEIGHT_DP = 16
        private const val BAR_MAX_HEIGHT_DP = 96
        private const val MIN_BAR_HEIGHT_DP = 4
        private const val BAR_WIDTH_DP = 9
        private const val HOUR_COLUMN_WIDTH_DP = 24
        private const val HOUR_LABEL_HEIGHT_DP = 24
    }

    private lateinit var binding: ActivityHeatmapBinding
    private lateinit var viewModel: JournalViewModel
    private var currentEntries: List<JournalEntry> = emptyList()

    private val dayFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this)[JournalViewModel::class.java]
        viewModel.allEntries.observe(this) { entries ->
            currentEntries = entries
            val summary = MoodAnalytics.compute(entries, WEEKS_SHOWN)
            bindStats(summary)
            renderHeatmap(summary)
            renderLegend()
            renderHistogram(entries)
            renderDayChart(entries)
        }
    }

    private fun bindStats(summary: MoodAnalytics.Summary) {
        binding.statEntriesValue.text = summary.totalEntries.toString()
        binding.statStreakValue.text = summary.currentStreak.toString()
        binding.statThemeValue.text =
            summary.topTheme?.let { getString(it.labelRes) } ?: "—"
        summary.topTheme?.let {
            binding.statThemeValue.setTextColor(Color.parseColor(it.hex))
        }
    }

    private fun renderHeatmap(summary: MoodAnalytics.Summary) {
        binding.heatmapContainer.removeAllViews()

        val cellPx = dp(CELL_DP)
        val gapPx = dp(GAP_DP)
        val labelH = dp(LABEL_HEIGHT_DP)
        val today = MoodAnalytics.startOfDay(System.currentTimeMillis())
        val emptyColor = MaterialColors.getColor(
            binding.heatmapContainer, com.google.android.material.R.attr.colorSurfaceVariant
        )

        val labelColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labelColumn.addView(View(this), LinearLayout.LayoutParams(cellPx, labelH))
        localeDayHeaders(Calendar.getInstance().firstDayOfWeek).first.forEach { letter ->
            labelColumn.addView(
                TextView(this).apply {
                    text = letter
                    textSize = 9f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(cellPx, cellPx).apply { bottomMargin = gapPx }
            )
        }
        binding.heatmapContainer.addView(labelColumn)

        var lastMonth = -1
        summary.days.chunked(7).forEach { week ->
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

            val firstDay = week.first().dayStart
            val month = Calendar.getInstance().apply { timeInMillis = firstDay }
                .get(Calendar.MONTH)
            val monthText = if (month != lastMonth) {
                lastMonth = month
                monthFormat.format(Date(firstDay))
            } else ""
            column.addView(
                TextView(this).apply {
                    text = monthText
                    textSize = 9f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(cellPx, labelH)
            )

            week.forEach { day ->
                val cell = View(this)
                val isFuture = day.dayStart > today
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cellPx / 4f
                    setColor(colorFor(day, emptyColor))
                    alpha = if (isFuture) 0 else 255
                }
                if (day.dayStart == today) {
                    bg.setStroke(
                        dp(2),
                        MaterialColors.getColor(
                            binding.heatmapContainer,
                            com.google.android.material.R.attr.colorPrimary
                        )
                    )
                }
                cell.background = bg
                if (!isFuture) {
                    cell.contentDescription =
                        if (day.total == 0) getString(R.string.heatmap_no_entries)
                        else dayFormat.format(Date(day.dayStart)) + " — " +
                            resources.getQuantityString(
                                R.plurals.heatmap_day_entries, day.total, day.total
                            ) + ", " + (day.dominant?.let { getString(it.labelRes) } ?: "")
                }
                if (!isFuture && day.total > 0) {
                    cell.setOnClickListener { showDayDialog(day.dayStart) }
                }
                column.addView(
                    cell,
                    LinearLayout.LayoutParams(cellPx, cellPx).apply { bottomMargin = gapPx }
                )
            }

            binding.heatmapContainer.addView(
                column,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = gapPx }
            )
        }
    }

    private fun colorFor(day: MoodAnalytics.DayMood, emptyColor: Int): Int = when {
        day.total == 0 -> emptyColor
        else -> {
            val themeColor = Color.parseColor(day.dominant!!.hex)
            val intensity = when {
                day.total >= 3 -> 1f
                day.total == 2 -> 0.65f
                else -> 0.4f
            }
            ColorUtils.blendARGB(emptyColor, themeColor, intensity)
        }
    }

    private fun renderHistogram(entries: List<JournalEntry>) {
        val timeSummary = TimeAnalytics.compute(entries)

        binding.textPeak.text =
            if (timeSummary.peakHour == null) getString(R.string.no_data_yet)
            else resources.getQuantityString(
                R.plurals.peak_time,
                timeSummary.peakCount,
                TimeAnalytics.hourLabel(timeSummary.peakHour),
                timeSummary.peakCount
            )

        val emptyColor = MaterialColors.getColor(
            binding.histogramBars, com.google.android.material.R.attr.colorSurfaceVariant
        )
        val maxCount = timeSummary.buckets.maxOf { it.count }.coerceAtLeast(1)

        val specs = timeSummary.buckets.map { b ->
            BarSpec(
                count = b.count,
                color = b.dominant?.let { Color.parseColor(it.hex) } ?: emptyColor,
                label = if (b.hour % 6 == 0) {
                    TimeAnalytics.hourLabel(b.hour).replace(" ", "\n")
                } else "",
                contentDescription = TimeAnalytics.hourLabel(b.hour) + " — " +
                    resources.getQuantityString(
                        R.plurals.histogram_hour_entries, b.count, b.count
                    ),
                onClick = if (b.count > 0) {
                    {
                        showEntriesDialog(
                            TimeAnalytics.hourLabel(b.hour),
                            TimeAnalytics.entriesForHour(currentEntries, b.hour)
                        )
                    }
                } else null
            )
        }
        renderBars(binding.histogramBars, specs, maxCount)
        renderPeriodBreakdown(entries)
    }

    private fun renderDayChart(entries: List<JournalEntry>) {
        val firstDay = Calendar.getInstance().firstDayOfWeek
        val buckets = TimeAnalytics.computeByDay(entries, firstDay)
        val (initials, names) = localeDayHeaders(firstDay)
        val emptyColor = MaterialColors.getColor(
            binding.dowBars, com.google.android.material.R.attr.colorSurfaceVariant
        )
        val maxCount = buckets.maxOf { it.count }.coerceAtLeast(1)

        val specs = buckets.mapIndexed { i, bucket ->
            BarSpec(
                count = bucket.count,
                color = bucket.dominant?.let { Color.parseColor(it.hex) } ?: emptyColor,
                label = initials[i],
                contentDescription = names[i] + " — " + resources.getQuantityString(
                    R.plurals.histogram_hour_entries, bucket.count, bucket.count
                ),
                onClick = if (bucket.count > 0) {
                    { showEntriesDialog(names[i], bucket.entries) }
                } else null
            )
        }
        renderBars(binding.dowBars, specs, maxCount)
    }

    private fun renderPeriodBreakdown(entries: List<JournalEntry>) {
        binding.periodRow.removeAllViews()
        TimeAnalytics.periodBreakdown(entries).forEach { share ->
            val tv = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 12f
                text = "${getString(share.period.labelRes)}\n${share.percent}%"
            }
            binding.periodRow.addView(
                tv,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private data class BarSpec(
        val count: Int,
        val color: Int,
        val label: String,
        val contentDescription: String,
        val onClick: (() -> Unit)?
    )

    private fun renderBars(container: LinearLayout, specs: List<BarSpec>, maxCount: Int) {
        container.removeAllViews()
        val barMaxPx = dp(BAR_MAX_HEIGHT_DP)
        val minBarPx = dp(MIN_BAR_HEIGHT_DP)
        specs.forEach { spec ->
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                contentDescription = spec.contentDescription
                spec.onClick?.let { action ->
                    isClickable = true; isFocusable = true
                    setOnClickListener { action() }
                }
            }
            val barHeight = if (spec.count == 0) minBarPx
            else (spec.count * barMaxPx / maxCount).coerceAtLeast(minBarPx)
            column.addView(
                View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(BAR_WIDTH_DP) / 2f
                        setColor(spec.color)
                    }
                },
                LinearLayout.LayoutParams(dp(BAR_WIDTH_DP), barHeight)
            )
            column.addView(
                TextView(this).apply {
                    textSize = 8f; gravity = Gravity.CENTER; text = spec.label
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(HOUR_LABEL_HEIGHT_DP)
                ).apply { topMargin = dp(2) }
            )
            container.addView(
                column,
                LinearLayout.LayoutParams(
                    dp(HOUR_COLUMN_WIDTH_DP), LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(1) }
            )
        }
    }

    /** Narrow initials + full names for 7 days starting at the locale's first day. */
    private fun localeDayHeaders(firstDayOfWeek: Int): Pair<List<String>, List<String>> {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }
        val narrow = SimpleDateFormat("EEEEE", Locale.getDefault())
        val full = SimpleDateFormat("EEEE", Locale.getDefault())
        val initials = mutableListOf<String>()
        val names = mutableListOf<String>()
        repeat(7) {
            initials += narrow.format(cal.time).take(1)
            names += full.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return initials to names
    }

    private fun showDayDialog(dayStart: Long) {
        val dayEntries = MoodAnalytics.entriesForDay(currentEntries, dayStart)
        if (dayEntries.isEmpty()) return
        showEntriesDialog(dayFormat.format(Date(dayStart)), dayEntries)
    }

    private fun showEntriesDialog(title: String, list: List<JournalEntry>) {
        val format = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        val labels = list.map {
            "${format.format(Date(it.timestamp))}  ·  ${it.title.ifEmpty { getString(R.string.untitled) }}"
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which -> openEditor(list[which].id) }
            .setNegativeButton(getString(R.string.close), null)
            .show()
    }

    private fun renderLegend() {
        binding.legendContainer.removeAllViews()
        EntryTheme.values().forEach { theme ->
            val tv = TextView(this).apply {
                textSize = 12f
                val span = SpannableString("●  ${getString(theme.labelRes)}")
                span.setSpan(ForegroundColorSpan(Color.parseColor(theme.hex)), 0, 1, 0)
                text = span
            }
            binding.legendContainer.addView(
                tv,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(14) }
            )
        }
    }

    private fun openEditor(entryId: Long) = startActivity(
        Intent(this, AddEditEntryActivity::class.java)
            .putExtra(AddEditEntryActivity.EXTRA_ENTRY_ID, entryId)
    )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
