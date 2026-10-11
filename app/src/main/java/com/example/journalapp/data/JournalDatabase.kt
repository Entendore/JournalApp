package com.example.journalapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(entities = [JournalEntry::class], version = 4, exportSchema = true)
abstract class JournalDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao

    companion object {
        const val DB_NAME = "journal_database"

        @Volatile
        private var INSTANCE: JournalDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN entryDate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE journal_entries SET entryDate = timestamp WHERE entryDate = 0")
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN theme TEXT NOT NULL DEFAULT 'FREE'")
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN format TEXT NOT NULL DEFAULT 'FREE_FORM'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_sessionId ON journal_entries(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_entryDate ON journal_entries(entryDate)")
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN photos TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context, dbName: String = DB_NAME): JournalDatabase {
            return INSTANCE ?: synchronized(this) {
                buildDatabase(context, dbName).also { INSTANCE = it }
            }
        }

        /** Uncached factory — also used by tests with throwaway names. */
        fun buildDatabase(context: Context, dbName: String): JournalDatabase {
            System.loadLibrary("sqlcipher")
            val appContext = context.applicationContext
            val passphrase = DbKeyManager.getOrCreate(appContext)
            migratePlaintextIfNeeded(appContext, dbName, passphrase)

            return Room.databaseBuilder(appContext, JournalDatabase::class.java, dbName)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }

        /** One-time upgrade: copy a pre-encryption plaintext DB into a
         *  SQLCipher file via sqlcipher_export, then swap. */
        private fun migratePlaintextIfNeeded(context: Context, dbName: String, passphrase: ByteArray) {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists() || !isPlaintextSQLite(dbFile)) return

            val target = File(dbFile.parentFile, "$dbName.encrypting")
            target.delete()

            val plain = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, "", null, null)
            val hex = passphrase.joinToString("") { "%02x".format(it) }
            plain.rawExecSQL("ATTACH DATABASE '${target.absolutePath}' AS encrypted KEY \"x'$hex'\"")
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plain.rawExecSQL("DETACH DATABASE encrypted")
            plain.close()

            listOf("-wal", "-shm").forEach { File(dbFile.parentFile, dbName + it).delete() }
            dbFile.delete()
            target.renameTo(dbFile)
        }

        private fun isPlaintextSQLite(file: File): Boolean = try {
            file.inputStream().use { s ->
                val header = ByteArray(16)
                s.read(header)
                String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
            }
        } catch (e: Exception) { false }
    }
}
