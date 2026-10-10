package com.example.core.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VaultMigrationTest {
    @Test
    fun versionOneMigrationPreservesAllCiphertextAndAddsSafeMetadataDefaults() =
        runBlocking<Unit> {
            val context = RuntimeEnvironment.getApplication()
            val name = "synthetic-migration-${UUID.randomUUID()}.db"
            val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                old.execSQL(
                    "CREATE TABLE credentials (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, platformName TEXT NOT NULL, encryptedUsername TEXT NOT NULL, usernameIv TEXT NOT NULL, encryptedPassword TEXT NOT NULL, passwordIv TEXT NOT NULL, website TEXT NOT NULL, categoryId INTEGER NOT NULL)"
                )
                old.execSQL(
                    "INSERT INTO credentials VALUES (42, 'Synthetic site', 'cipher-user', 'iv-user', 'cipher-password', 'iv-password', 'synthetic-site', 3)"
                )
                old.version = 1
            }
            val db =
                Room.databaseBuilder(context, VaultDatabase::class.java, name)
                    .addMigrations(VaultDatabase.MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build()
            try {
                val item = db.credentialDao().getCredentialById(42)!!
                assertEquals("Synthetic site", item.platformName)
                assertEquals("cipher-user", item.encryptedUsername)
                assertEquals("cipher-password", item.encryptedPassword)
                assertEquals("iv-password", item.passwordIv)
                assertEquals(3, item.categoryId)
                assertEquals("", item.tags)
                assertFalse(item.isFavorite)
                assertEquals(0L, item.createdAt)
                assertEquals(0L, item.passwordChangedAt)
                assertNull(item.deletedAt)
                assertTrue(db.credentialDao().getHistory(42).isEmpty())
                assertEquals(2, db.openHelper.writableDatabase.version)
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
}
