package com.example.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.data.dao.CredentialDao
import com.example.core.data.entity.EncryptedCredentialEntity
import com.example.core.data.entity.PasswordHistoryEntity
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [EncryptedCredentialEntity::class, PasswordHistoryEntity::class], version = 2)
abstract class VaultDatabase : RoomDatabase() {
    private var openingPassphrase: ByteArray? = null

    abstract fun credentialDao(): CredentialDao

    override fun close() {
        try {
            super.close()
        } finally {
            openingPassphrase?.fill(0)
            openingPassphrase = null
        }
    }

    companion object {
        private const val DATABASE_NAME = "vault.db"
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE credentials ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        "ALTER TABLE credentials ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE credentials ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "ALTER TABLE credentials ADD COLUMN passwordChangedAt INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL("ALTER TABLE credentials ADD COLUMN deletedAt INTEGER")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS password_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, credentialId INTEGER NOT NULL, encryptedPassword TEXT NOT NULL, passwordIv TEXT NOT NULL, passwordChangedAt INTEGER NOT NULL, recordedAt INTEGER NOT NULL, FOREIGN KEY(credentialId) REFERENCES credentials(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_password_history_credentialId ON password_history (credentialId)"
                    )
                }
            }

        fun build(context: Context, passphrase: ByteArray): VaultDatabase {
            System.loadLibrary("sqlcipher")
            // SQLCipher retains this array for subsequent connections, not just the first open.
            val ownedPassphrase = passphrase.copyOf()
            try {
                val factory = SupportOpenHelperFactory(ownedPassphrase)
                return Room.databaseBuilder(context, VaultDatabase::class.java, DATABASE_NAME)
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { it.openingPassphrase = ownedPassphrase }
            } catch (error: Exception) {
                ownedPassphrase.fill(0)
                throw error
            }
        }

        /** Repairs only the historical lazy-open bug, after biometric key unwrapping. */
        internal fun recoverLegacyZeroKey(context: Context, passphrase: ByteArray) {
            require(passphrase.size == 32)
            check(context.getDatabasePath(DATABASE_NAME).let { it.isFile && it.length() > 0 })
            val legacyKey = ByteArray(32)
            val replacement = passphrase.copyOf()
            var legacy: VaultDatabase? = null
            try {
                // Probe read-only with a non-destructive corruption handler. A wrong legacy
                // key or damaged file must never trigger SQLCipher's default file deletion.
                SQLiteDatabase.openDatabase(
                        context.getDatabasePath(DATABASE_NAME).absolutePath,
                        legacyKey,
                        null,
                        SQLiteDatabase.OPEN_READONLY,
                        { _, error -> throw error },
                        null,
                    )
                    .use { probe ->
                        probe.rawQuery("PRAGMA quick_check", null).use { result ->
                            check(
                                result.moveToFirst() &&
                                    result.getString(0) == "ok" &&
                                    !result.moveToNext()
                            )
                        }
                    }
                legacy = build(context, legacyKey)
                // Room validates its identity/schema before any re-encryption takes place.
                val nativeDatabase = legacy.openHelper.writableDatabase as SQLiteDatabase
                nativeDatabase.disableWriteAheadLogging()
                nativeDatabase.changePassword(replacement)
            } finally {
                try {
                    legacy?.close()
                } finally {
                    legacyKey.fill(0)
                    replacement.fill(0)
                }
            }
        }
    }
}
