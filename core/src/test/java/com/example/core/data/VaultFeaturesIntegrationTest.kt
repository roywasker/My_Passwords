package com.example.core.data

import android.app.Application
import androidx.room.Room
import com.example.core.auth.VaultSession
import com.example.core.crypto.CryptoManager
import com.example.core.data.entity.*
import com.example.core.model.*
import com.example.core.testing.HostCryptoProviderRule
import io.mockk.*
import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Real Room transactions and AES-GCM, with an isolated synthetic key instead of hardware Keystore.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VaultFeaturesIntegrationTest {
    @get:Rule val cryptoProviders = HostCryptoProviderRule()
    private lateinit var db: VaultDatabase
    private lateinit var repo: PasswordRepository
    private val key = ByteArray(32) { 7 }

    @Before
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    VaultDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        val store = mockk<KeyStore>(relaxed = true)
        every { store.containsAlias("VaultMasterKey") } returns true
        every { store.getEntry("VaultMasterKey", null) } returns
            KeyStore.SecretKeyEntry(SecretKeySpec(key, "AES"))
        mockkStatic(KeyStore::class)
        every { KeyStore.getInstance("AndroidKeyStore") } returns store
        val manager = mockk<DatabaseManager>()
        every { manager.session } returns VaultSession()
        every { manager.isInitialized } returns MutableStateFlow(true)
        every { manager.isDatabaseInitialized() } returns true
        every { manager.getDatabase() } returns db
        every { manager.getSessionVaultKey() } returns key
        repo = PasswordRepository(manager, CryptoManager())
    }

    @After
    fun cleanup() {
        db.close()
        unmockkAll()
    }

    private suspend fun add(name: String, password: String, tags: List<String> = emptyList()): Int {
        repo.addPassword(
            Credential(name, "synthetic-user".toByteArray(), password.toByteArray(), tags = tags)
        )
        return db.credentialDao().activeSnapshot().first { it.platformName == name }.id
    }

    @Test
    fun favoritesTagsAndExtendedSearchPersistWithoutChangingSecrets() =
        runBlocking<Unit> {
            val id = add("Google", "first-secret", listOf(" Work ", "work", "בנק"))
            repo.setFavorite(id, true)
            repo.setTags(id, " Personal , אישי,personal")
            val item = repo.passwords.first().single()
            assertTrue(item.isFavorite)
            assertEquals(listOf("Personal", "אישי"), item.tags)
            assertEquals(
                listOf(id),
                PasswordQuery.apply(
                        listOf(item),
                        "synthetic אישי",
                        1,
                        true,
                        "אישי",
                        PasswordSort.NAME,
                    )
                    .map { it.id },
            )
            val detail = repo.getDecryptedCredential(id)!!
            try {
                assertEquals("first-secret", String(detail.password))
                assertTrue(detail.isFavorite)
            } finally {
                detail.clear()
            }
        }

    @Test
    fun changedPasswordArchivesCiphertextAndRestorePreservesCurrentUsernameAndMetadata() =
        runBlocking<Unit> {
            val id = add("Account", "old-secret", listOf("work"))
            repo.setFavorite(id, true)
            repo.updatePassword(
                id,
                Credential("Account", "new-user".toByteArray(), "new-secret".toByteArray()),
            )
            val history = repo.history(id).single()
            assertNotEquals(
                "old-secret",
                db.credentialDao().getHistory(id).single().encryptedPassword,
            )
            repo.restoreHistory(id, history.id)
            val restored = repo.getDecryptedCredential(id)!!
            try {
                assertEquals("old-secret", String(restored.password))
                assertEquals("new-user", String(restored.username))
                assertEquals(listOf("work"), restored.tags)
                assertTrue(restored.isFavorite)
            } finally {
                restored.clear()
            }
            assertEquals(2, repo.history(id).size)
            repo.restoreHistory(id, history.id)
            assertEquals(2, repo.history(id).size)
        }

    @Test
    fun usernameOnlyChangeDoesNotCreatePasswordHistoryOrResetPasswordDate() =
        runBlocking<Unit> {
            val id = add("Account", "same-secret")
            val before = db.credentialDao().getCredentialById(id)!!
            repo.updatePassword(
                id,
                Credential("Account", "changed-user".toByteArray(), "same-secret".toByteArray()),
            )
            val after = db.credentialDao().getCredentialById(id)!!
            assertEquals(before.encryptedPassword, after.encryptedPassword)
            assertEquals(before.passwordIv, after.passwordIv)
            assertEquals(before.passwordChangedAt, after.passwordChangedAt)
            assertTrue(repo.history(id).isEmpty())
        }

    @Test
    fun trashHidesCredentialFromListDetailsAndHealthThenRestoresMetadataAndHistory() =
        runBlocking<Unit> {
            val id = add("Account", "old-secret", listOf("work"))
            repo.setFavorite(id, true)
            repo.updatePassword(
                id,
                Credential("Account", "user".toByteArray(), "new-secret".toByteArray()),
            )
            repo.deletePassword(id)
            repo.deletePassword(id)
            assertTrue(repo.passwords.first().isEmpty())
            assertNull(repo.getDecryptedCredential(id))
            assertEquals(0, repo.healthReport().total)
            assertEquals(id, repo.trashItems().single().id)
            repo.restoreTrash(id)
            assertTrue(repo.passwords.first().single().isFavorite)
            assertEquals(listOf("work"), repo.passwords.first().single().tags)
            assertEquals(1, repo.history(id).size)
        }

    @Test
    fun permanentDeletionIsRestrictedToTrashAndCascadesHistory() =
        runBlocking<Unit> {
            val id = add("Account", "old-secret")
            repo.updatePassword(
                id,
                Credential("Account", "user".toByteArray(), "new-secret".toByteArray()),
            )
            try {
                repo.permanentlyDelete(id)
                fail("Active item must not be permanently deleted")
            } catch (_: VaultError.DatabaseError) {}
            assertNotNull(db.credentialDao().getCredentialById(id))
            repo.deletePassword(id)
            repo.permanentlyDelete(id)
            assertNull(db.credentialDao().getCredentialById(id))
            assertTrue(db.credentialDao().getHistory(id).isEmpty())
        }

    @Test
    fun healthDetectsIdenticalPasswordsDespiteDifferentCiphertextsAndIvs() =
        runBlocking<Unit> {
            add("One", "same-password-value")
            add("Two", "same-password-value")
            val stored = db.credentialDao().activeSnapshot()
            assertNotEquals(stored[0].encryptedPassword, stored[1].encryptedPassword)
            assertNotEquals(stored[0].passwordIv, stored[1].passwordIv)
            val report = repo.healthReport()
            assertEquals(2, report.total)
            assertEquals(2, report.reusedCount)
            assertFalse(report.toString().contains("same-password-value"))
        }

    @Test
    fun thirtyDayCleanupHasExactBoundaryAndNeverDeletesActiveOrRecentItems() =
        runBlocking<Unit> {
            val now = 2_000_000_000_000L
            val cutoff = TrashPolicy.cutoff(now)
            val a = add("Expired", "secret")
            val b = add("Boundary", "secret")
            val c = add("Recent", "secret")
            val active = add("Active", "secret")
            db.credentialDao().moveToTrash(a, cutoff - 1)
            db.credentialDao().moveToTrash(b, cutoff)
            db.credentialDao().moveToTrash(c, cutoff + 1)
            assertEquals(0, db.credentialDao().restoreTrash(b, cutoff))
            assertEquals(2, db.credentialDao().purgeExpiredTrash(cutoff))
            assertNull(db.credentialDao().getCredentialById(a))
            assertNull(db.credentialDao().getCredentialById(b))
            assertNotNull(db.credentialDao().getCredentialById(c))
            assertNotNull(db.credentialDao().getCredentialById(active))
            assertEquals(1, db.credentialDao().restoreTrash(c, cutoff))
        }

    @Test
    fun foreignHistoryIdCannotRestoreAnotherAccountsPassword() =
        runBlocking<Unit> {
            val a = add("One", "old-one")
            val b = add("Two", "old-two")
            repo.updatePassword(a, Credential("One", "u".toByteArray(), "new-one".toByteArray()))
            val history = repo.history(a).single()
            try {
                repo.restoreHistory(b, history.id)
                fail("Expected owner check")
            } catch (_: VaultError.DatabaseError) {}
            val untouched = repo.getDecryptedCredential(b)!!
            try {
                assertEquals("old-two", String(untouched.password))
            } finally {
                untouched.clear()
            }
        }

    @Test
    fun invalidTagsDoNotOverwritePreviouslySavedTags() =
        runBlocking<Unit> {
            val id = add("Account", "secret", listOf("work"))
            try {
                repo.setTags(id, "x".repeat(33))
                fail("Expected tag validation")
            } catch (_: VaultError.DatabaseError) {}
            assertEquals(listOf("work"), repo.passwords.first().single().tags)
        }

    @Test
    fun failedUpdateRollsBackHistoryAndKeepsCurrentCiphertext() =
        runBlocking<Unit> {
            val id = add("Account", "old-secret")
            val before = db.credentialDao().getCredentialById(id)!!
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER synthetic_failure BEFORE UPDATE ON credentials BEGIN SELECT RAISE(ABORT, 'synthetic update failure'); END"
            )
            try {
                repo.updatePassword(
                    id,
                    Credential("Account", "new-user".toByteArray(), "new-secret".toByteArray()),
                )
                fail("Expected failed transaction")
            } catch (_: VaultError.DatabaseError) {}
            assertTrue(db.credentialDao().getHistory(id).isEmpty())
            assertEquals(before, db.credentialDao().getCredentialById(id))
        }

    @Test
    fun missingOrTrashedAccountCannotBeRecreatedByUpdate() =
        runBlocking<Unit> {
            val id = add("Account", "secret")
            repo.deletePassword(id)
            for (target in listOf(id, Int.MAX_VALUE)) {
                val submitted =
                    Credential("Account", "new-user".toByteArray(), "new-secret".toByteArray())
                try {
                    repo.updatePassword(target, submitted)
                    fail("Expected unavailable account")
                } catch (_: VaultError.DatabaseError) {}
                assertTrue(submitted.username.all { it == 0.toByte() })
                assertTrue(submitted.password.all { it == 0.toByte() })
            }
            assertTrue(repo.passwords.first().isEmpty())
            assertEquals(1, repo.trashItems().size)
        }
}
