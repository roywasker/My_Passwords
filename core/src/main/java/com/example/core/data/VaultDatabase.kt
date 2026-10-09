package com.example.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.data.dao.CredentialDao
import com.example.core.data.entity.EncryptedCredentialEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.sqlcipher.SQLiteDatabase

@Database(entities = [EncryptedCredentialEntity::class], version = 1)
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

        fun build(context: Context, passphrase: ByteArray): VaultDatabase {
            System.loadLibrary("sqlcipher")
            // SQLCipher retains this array for subsequent connections, not just the first open.
            val ownedPassphrase = passphrase.copyOf()
            try {
                val factory = SupportOpenHelperFactory(ownedPassphrase)
                return Room.databaseBuilder(context, VaultDatabase::class.java, DATABASE_NAME)
                    .openHelperFactory(factory)
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
                ).use { probe ->
                    probe.rawQuery("PRAGMA quick_check", null).use { result ->
                        check(result.moveToFirst() && result.getString(0) == "ok" && !result.moveToNext())
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
