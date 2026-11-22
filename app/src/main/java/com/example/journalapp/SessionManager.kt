package com.example.journalapp

import android.content.Context
import java.util.UUID

/** A "session" = one writing sitting. Same ID while foreground gaps stay < 2h,
 *  so a process kill mid-draft doesn't fragment the sitting. */
object SessionManager {

    @Volatile
    var sessionId: String = ""
        private set

    private const val PREFS = "session_prefs"
    private const val KEY_ID = "id"
    private const val KEY_LAST = "last_active"
    private const val TIMEOUT_MS = 2 * 60 * 60 * 1000L

    fun onAppForeground(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val stored = prefs.getString(KEY_ID, null)
        val last = prefs.getLong(KEY_LAST, 0)
        sessionId = if (stored != null && now - last < TIMEOUT_MS) stored
                    else UUID.randomUUID().toString()
        prefs.edit().putString(KEY_ID, sessionId).putLong(KEY_LAST, now).apply()
    }

    fun onAppBackground(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST, System.currentTimeMillis()).apply()
    }

    fun shortCode(id: String): String =
        if (id.isEmpty()) "—" else id.takeLast(4).uppercase()
}
