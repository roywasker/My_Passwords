package com.example.core.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.core.crypto.CryptoManager
import com.example.core.model.VaultError
import io.mockk.*
import javax.crypto.Cipher
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DatabaseManagerTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var manager: DatabaseManager
    private val crypto = mockk<CryptoManager>()
    private val cipher = mockk<Cipher>()
    private val db = mockk<VaultDatabase>(relaxed = true)

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("synthetic-test-preferences", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        manager = DatabaseManager(context, crypto)
        // Test-only seam: replace the lazy preferences with isolated plaintext test storage.
        // No production preferences or Keystore entries are accessed.
        DatabaseManager::class
            .java
            .getDeclaredField("sharedPrefs\$delegate")
            .apply { isAccessible = true }
            .set(manager, lazyOf(prefs))
        every { crypto.getMasterEncryptionCipher() } returns cipher
        every { crypto.getMasterDecryptionCipher(any()) } returns cipher
        coEvery { crypto.wrapKey(any(), cipher) } returns ("wrapped" to "iv")
        mockkObject(VaultDatabase.Companion)
        every { VaultDatabase.build(context, any()) } returns db
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    private fun stored() {
        prefs
            .edit()
            .putString("encrypted_db_passphrase", "stored-key")
            .putString("db_passphrase_iv", Base64.encodeToString(byteArrayOf(1, 2), Base64.NO_WRAP))
            .commit()
    }

    @Test
    fun startsUninitializedWithInaccessibleDatabaseAndKey() {
        assertFalse(manager.isDatabaseInitialized())
        assertFalse(manager.isInitialized.value)
        assertThrows(VaultError.UninitializedVault::class.java) { manager.getDatabase() }
        assertThrows(VaultError.UninitializedVault::class.java) { manager.getSessionVaultKey() }
    }

    @Test
    fun firstUseRequestsEncryptionCipher() {
        assertSame(cipher, manager.getPassphraseCipher())
        verify(exactly = 1) { crypto.getMasterEncryptionCipher() }
        verify(exactly = 0) { crypto.getMasterDecryptionCipher(any()) }
    }

    @Test
    fun returningUserUsesStoredIvForDecryptionCipher() {
        stored()
        assertSame(cipher, manager.getPassphraseCipher())
        verify { crypto.getMasterDecryptionCipher(match { it.contentEquals(byteArrayOf(1, 2)) }) }
    }

    @Test
    fun missingIvIsWrappedAsGenericError() {
        prefs.edit().putString("encrypted_db_passphrase", "key").commit()
        val e = assertThrows(VaultError.GenericError::class.java) { manager.getPassphraseCipher() }
        assertTrue(e.cause is IllegalStateException)
    }

    @Test
    fun encryptionCipherFailureRetainsCause() {
        val e = IllegalStateException()
        every { crypto.getMasterEncryptionCipher() } throws e
        assertSame(
            e,
            assertThrows(VaultError.GenericError::class.java) { manager.getPassphraseCipher() }
                .cause,
        )
    }

    @Test
    fun decryptionCipherFailureRetainsCause() {
        stored()
        val e = IllegalStateException()
        every { crypto.getMasterDecryptionCipher(any()) } throws e
        assertSame(
            e,
            assertThrows(VaultError.GenericError::class.java) { manager.getPassphraseCipher() }
                .cause,
        )
    }

    @Test
    fun firstInitializationCreates32ByteSessionKeyAndStoresOnlyWrappedValue() =
        runBlocking<Unit> {
            var generated: ByteArray? = null
            var passedToDb: ByteArray? = null
            coEvery { crypto.wrapKey(any(), cipher) } answers
                {
                    generated = firstArg<ByteArray>().copyOf()
                    "wrapped" to "iv"
                }
            every { VaultDatabase.build(context, any()) } answers
                {
                    passedToDb = secondArg()
                    db
                }
            manager.initializeDatabase(cipher)
            assertTrue(manager.isDatabaseInitialized())
            assertSame(db, manager.getDatabase())
            assertEquals(32, manager.getSessionVaultKey().size)
            assertArrayEquals(generated, manager.getSessionVaultKey())
            assertTrue(passedToDb!!.all { it == 0.toByte() })
            assertEquals("wrapped", prefs.getString("encrypted_db_passphrase", null))
            assertEquals("iv", prefs.getString("db_passphrase_iv", null))
        }

    @Test
    fun subsequentInitializationUnwrapsStoredKeyAndClearsTemporaryArray() =
        runBlocking<Unit> {
            stored()
            val key = ByteArray(32) { 9 }
            coEvery { crypto.unwrapKey("stored-key", cipher) } returns key
            manager.initializeDatabase(cipher)
            assertTrue(key.all { it == 0.toByte() })
            assertTrue(manager.getSessionVaultKey().all { it == 9.toByte() })
            coVerify(exactly = 0) { crypto.wrapKey(any(), any()) }
        }

    @Test
    fun clearSessionClosesDatabaseWipesSharedKeyReferenceAndLocks() =
        runBlocking<Unit> {
            manager.initializeDatabase(cipher)
            val key = manager.getSessionVaultKey()
            manager.clearSession()
            assertTrue(key.all { it == 0.toByte() })
            assertFalse(manager.isInitialized.value)
            verify(exactly = 1) { db.close() }
            assertThrows(VaultError.UninitializedVault::class.java) { manager.getDatabase() }
            assertThrows(VaultError.UninitializedVault::class.java) { manager.getSessionVaultKey() }
        }

    @Test
    fun clearSessionIsIdempotentBeforeInitialization() {
        manager.clearSession()
        manager.clearSession()
        assertFalse(manager.isDatabaseInitialized())
    }

    @Test
    fun clearSessionPreservesWrappedKeyForNextLogin() =
        runBlocking<Unit> {
            manager.initializeDatabase(cipher)
            manager.clearSession()
            assertEquals("wrapped", prefs.getString("encrypted_db_passphrase", null))
            assertEquals("iv", prefs.getString("db_passphrase_iv", null))
        }

    @Test
    fun reinitializationClosesOldDatabaseAndWipesOldSessionKey() =
        runBlocking<Unit> {
            manager.initializeDatabase(cipher)
            val previous = manager.getSessionVaultKey()
            coEvery { crypto.unwrapKey("wrapped", cipher) } returns ByteArray(32) { 3 }
            manager.initializeDatabase(cipher)
            verify(exactly = 1) { db.close() }
            assertTrue(previous.all { it == 0.toByte() })
            assertTrue(manager.getSessionVaultKey().all { it == 3.toByte() })
        }

    @Test
    fun wrapFailureNeverOpensDatabaseOrPublishesInitializedState() =
        runBlocking<Unit> {
            val e = VaultError.EncryptionError(Exception())
            coEvery { crypto.wrapKey(any(), cipher) } throws e
            val thrown =
                try {
                    manager.initializeDatabase(cipher)
                    null
                } catch (e: VaultError.DatabaseError) {
                    e
                }
            assertSame(e, thrown!!.cause)
            assertFalse(manager.isInitialized.value)
            verify(exactly = 0) { VaultDatabase.build(any(), any()) }
            assertFalse(prefs.contains("encrypted_db_passphrase"))
        }

    @Test
    fun unwrapFailureNeverOpensDatabase() =
        runBlocking<Unit> {
            stored()
            coEvery { crypto.unwrapKey(any(), cipher) } throws
                VaultError.DecryptionError(Exception())
            try {
                manager.initializeDatabase(cipher)
                fail()
            } catch (e: VaultError.DatabaseError) {
                assertTrue(e.cause is VaultError.DecryptionError)
            }
            verify(exactly = 0) { VaultDatabase.build(any(), any()) }
            assertFalse(manager.isDatabaseInitialized())
        }

    @Test
    fun databaseConstructionFailureRetainsCause() =
        runBlocking<Unit> {
            val e = IllegalStateException()
            every { VaultDatabase.build(context, any()) } throws e
            val thrown =
                try {
                    manager.initializeDatabase(cipher)
                    null
                } catch (e: VaultError.DatabaseError) {
                    e
                }
            assertSame(e, thrown!!.cause)
            assertFalse(manager.isInitialized.value)
        }

    @Test
    fun knownFailedInitializationDoesNotExposeSessionKey() =
        runBlocking<Unit> {
            every { VaultDatabase.build(context, any()) } throws IllegalStateException()
            try {
                manager.initializeDatabase(cipher)
            } catch (e: VaultError.DatabaseError) {}
            assertThrows(VaultError.UninitializedVault::class.java) { manager.getSessionVaultKey() }
        }

    @Test
    fun knownCloseFailureStillWipesSessionAndResetsInitializedState() =
        runBlocking<Unit> {
            manager.initializeDatabase(cipher)
            val key = manager.getSessionVaultKey()
            every { db.close() } throws IllegalStateException()
            try {
                manager.clearSession()
            } catch (e: IllegalStateException) {}
            assertTrue(key.all { it == 0.toByte() })
            assertFalse(manager.isInitialized.value)
        }

    @Test
    fun knownInitializationWipesTemporaryWrappingInput() =
        runBlocking<Unit> {
            val input = slot<ByteArray>()
            coEvery { crypto.wrapKey(capture(input), cipher) } returns ("wrapped" to "iv")
            manager.initializeDatabase(cipher)
            assertTrue(input.captured.all { it == 0.toByte() })
        }

    @Test
    fun databaseOpenFailureClosesHandleAndNeverPublishesSession() =
        runBlocking<Unit> {
            val e = IllegalStateException("synthetic open failure")
            every { db.openHelper.writableDatabase } throws e
            try {
                manager.initializeDatabase(cipher)
                fail("Expected failed open")
            } catch (error: VaultError.DatabaseError) {
                assertSame(e, error.cause)
            }
            assertFalse(manager.isInitialized.value)
            assertThrows(VaultError.UninitializedVault::class.java) { manager.getSessionVaultKey() }
            verify(exactly = 1) { db.close() }
        }

    @Test
    fun existingLegacyVaultIsRecoveredAndReopenedWithAuthenticatedKey() = runBlocking<Unit> {
        stored()
        val key = ByteArray(32) { 7 }
        coEvery { crypto.unwrapKey("stored-key", cipher) } returns key
        every { db.openHelper.writableDatabase } throws
            net.zetetic.database.sqlcipher.SQLiteNotADatabaseException("synthetic legacy key") andThen
            mockk<androidx.sqlite.db.SupportSQLiteDatabase>(relaxed = true)
        every { VaultDatabase.recoverLegacyZeroKey(context, any()) } just Runs
        manager.initializeDatabase(cipher)
        assertTrue(manager.isDatabaseInitialized())
        assertTrue(manager.getSessionVaultKey().all { it == 7.toByte() })
        assertTrue(key.all { it == 0.toByte() })
        verify(exactly = 1) { VaultDatabase.recoverLegacyZeroKey(context, any()) }
        verify(exactly = 2) { VaultDatabase.build(context, any()) }
    }

    @Test
    fun failedLegacyRecoveryPreservesOriginalCauseAndDoesNotPublishSession() = runBlocking<Unit> {
        stored()
        coEvery { crypto.unwrapKey("stored-key", cipher) } returns ByteArray(32) { 7 }
        val original = net.zetetic.database.sqlcipher.SQLiteNotADatabaseException("synthetic wrong key")
        val recovery = IllegalStateException("synthetic unrecoverable vault")
        every { db.openHelper.writableDatabase } throws original
        every { VaultDatabase.recoverLegacyZeroKey(context, any()) } throws recovery
        try {
            manager.initializeDatabase(cipher)
            fail("Expected failed recovery")
        } catch (error: VaultError.DatabaseError) {
            assertSame(original, error.cause)
            assertTrue(original.suppressed.contains(recovery))
        }
        assertFalse(manager.isDatabaseInitialized())
        assertEquals("stored-key", prefs.getString("encrypted_db_passphrase", null))
    }

    @Test
    fun firstUseNeverAttemptsLegacyRecovery() = runBlocking<Unit> {
        every { db.openHelper.writableDatabase } throws
            net.zetetic.database.sqlcipher.SQLiteNotADatabaseException("synthetic orphan vault")
        try {
            manager.initializeDatabase(cipher)
            fail("Expected failed open")
        } catch (error: VaultError.DatabaseError) {
            assertTrue(error.cause is net.zetetic.database.sqlcipher.SQLiteNotADatabaseException)
        }
        verify(exactly = 0) { VaultDatabase.recoverLegacyZeroKey(any(), any()) }
    }
}
