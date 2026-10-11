package com.example.journalapp.data

import androidx.annotation.StringRes
import com.example.journalapp.R

enum class EntryFormat(val labelRes: Int, @StringRes val templateRes: Int) {
    FREE_FORM(R.string.format_free_form, 0),
    DAILY_REFLECTION(R.string.format_daily_reflection, R.string.template_reflection),
    GRATITUDE(R.string.format_gratitude, R.string.template_gratitude),
    TRAVEL_LOG(R.string.format_travel_log, R.string.template_travel),
    GOAL_TRACKER(R.string.format_goal_tracker, R.string.template_goals);

    companion object {
        fun from(name: String): EntryFormat =
            values().firstOrNull { it.name == name } ?: FREE_FORM
    }
}
