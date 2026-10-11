package com.example.journalapp.data

import androidx.annotation.StringRes
import com.example.journalapp.R

enum class EntryTheme(@StringRes val labelRes: Int, val hex: String) {
    FREE(R.string.theme_free, "#90A4AE"),
    GRATITUDE(R.string.theme_gratitude, "#F9A825"),
    REFLECTION(R.string.theme_reflection, "#5C6BC0"),
    TRAVEL(R.string.theme_travel, "#26A69A"),
    DREAM(R.string.theme_dream, "#AB47BC"),
    MILESTONE(R.string.theme_milestone, "#EF5350"),
    WORK(R.string.theme_work, "#42A5F5");

    companion object {
        fun from(name: String): EntryTheme =
            values().firstOrNull { it.name == name } ?: FREE
    }
}
