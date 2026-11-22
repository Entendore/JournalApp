package com.example.journalapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One autosaved editor state, persisted so a process kill doesn't lose work.
 *  Keyed by entryId so a draft can never restore into the wrong entry. */
object DraftStore {

    private const val PREFS = "draft_prefs"
    private const val KEY = "draft"

    data class Draft(
        val entryId: Long,
        val title: String,
        val content: String,
        val entryDate: Long,
        val theme: String,
        val format: String,
        val photos: List<String>,
        val savedAt: Long
    )

    fun load(context: Context): Draft? = try {
        val json = JSONObject(prefs(context).getString(KEY, null) ?: return null)
        val photos = json.optJSONArray("photos")
            ?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        Draft(
            json.getLong("entryId"), json.getString("title"), json.getString("content"),
            json.getLong("entryDate"), json.getString("theme"), json.getString("format"),
            photos, json.getLong("savedAt")
        )
    } catch (e: Exception) { clear(context); null }

    fun save(context: Context, draft: Draft) = try {
        prefs(context).edit().putString(
            KEY,
            JSONObject()
                .put("entryId", draft.entryId).put("title", draft.title)
                .put("content", draft.content).put("entryDate", draft.entryDate)
                .put("theme", draft.theme).put("format", draft.format)
                .put("photos", JSONArray(draft.photos)).put("savedAt", draft.savedAt)
                .toString()
        ).apply()
    } catch (e: Exception) { /* best effort — never crash the editor over a draft */ }

    fun clear(context: Context) { prefs(context).edit().remove(KEY).apply() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
