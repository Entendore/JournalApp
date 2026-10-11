package com.example.journalapp.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.SecureRandom

/** 32 raw bytes of SQLCipher key material, persisted Keystore-encrypted.
 *  If the Keystore is unreadable (device restore), the DB can't be decrypted
 *  on this install: preserve the file, start fresh; CSV restore recovers. */
object DbKeyManager {

    private const val PREFS_FILE = "db_key_prefs"
    private const val PREF_KEY = "db_key_b64"

    fun getOrCreate(context: Context): ByteArray {
        val viaEncrypted = runCatching { loadVia(context) { encryptedPrefs(context) } }
        if (viaEncrypted.isSuccess) return viaEncrypted.getOrThrow()

        preserveUnreadableDb(context)
        return runCatching { loadVia(context) { encryptedPrefs(context) } }
            .getOrElse { loadVia(context) { plainPrefs(context) } }
    }

    private fun loadVia(context: Context, prefs: () -> SharedPreferences): ByteArray {
        val stored = prefs().getString(PREF_KEY, null)
        if (stored != null) return Base64.decode(stored, Base64.NO_WRAP)
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs().edit().putString(PREF_KEY, Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
        return fresh
    }

    private fun preserveUnreadableDb(context: Context) {
        runCatching {
            val db = context.getDatabasePath("journal_database")
            if (db.exists()) {
                db.renameTo(File(db.parentFile, "journal_database.unreadable-${System.currentTimeMillis()}"))
                listOf("-wal", "-shm").forEach { File(db.parentFile, "journal_database$it").delete() }
            }
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, PREFS_FILE, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun plainPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences("${PREFS_FILE}_plain", Context.MODE_PRIVATE)
}
