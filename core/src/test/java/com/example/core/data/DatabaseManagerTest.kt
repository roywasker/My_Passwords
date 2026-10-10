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
    @get:Rule val hostCrypto = com.example.core.testing.HostCryptoProviderRule()
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
    fun openingVaultAutomaticallyPurgesOnlyExpiredTrash() =
        runBlocking<Unit> {
            val before = System.currentTimeMillis()
            manager.initializeDatabase(cipher)
            coVerify(exactly = 1) {
                db.credentialDao()
                    .purgeExpiredTrash(
                        match {
                            it >= com.example.core.model.TrashPolicy.cutoff(before) &&
                                it <=
                                    com.example.core.model.TrashPolicy.cutoff(
                                        System.currentTimeMillis()
                                    )
                        }
                    )
            }
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
    fun existingLegacyVaultIsRecoveredAndReopenedWithAuthenticatedKey() =
        runBlocking<Unit> {
            stored()
            val key = ByteArray(32) { 7 }
            coEvery { crypto.unwrapKey("stored-key", cipher) } returns key
            every { db.openHelper.writableDatabase } throws
                net.zetetic.database.sqlcipher.SQLiteNotADatabaseException(
                    "synthetic legacy key"
                ) andThen
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
    fun failedLegacyRecoveryPreservesOriginalCauseAndDoesNotPublishSession() =
        runBlocking<Unit> {
            stored()
            coEvery { crypto.unwrapKey("stored-key", cipher) } returns ByteArray(32) { 7 }
            val original =
                net.zetetic.database.sqlcipher.SQLiteNotADatabaseException("synthetic wrong key")
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
    fun firstUseNeverAttemptsLegacyRecovery() =
        runBlocking<Unit> {
            every { db.openHelper.writableDatabase } throws
                net.zetetic.database.sqlcipher.SQLiteNotADatabaseException("synthetic orphan vault")
            try {
                manager.initializeDatabase(cipher)
                fail("Expected failed open")
            } catch (error: VaultError.DatabaseError) {
                assertTrue(
                    error.cause is net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
                )
            }
            verify(exactly = 0) { VaultDatabase.recoverLegacyZeroKey(any(), any()) }
        }

    @Test
    fun lockingDuringDatabaseOpenRejectsLateSessionAndClosesHandle() =
        runBlocking<Unit> {
            every { db.openHelper.writableDatabase } answers
                {
                    manager.clearSession()
                    mockk<androidx.sqlite.db.SupportSQLiteDatabase>(relaxed = true)
                }
            try {
                manager.initializeDatabase(cipher)
                fail("Expected revoked session")
            } catch (_: kotlinx.coroutines.CancellationException) {}
            assertFalse(manager.isDatabaseInitialized())
            assertThrows(VaultError.UninitializedVault::class.java) { manager.getSessionVaultKey() }
            verify(exactly = 1) { db.close() }
        }

    @Test
    fun lockingWhileUnwrappingRejectsLateKeyAndWipesIt() =
        runBlocking<Unit> {
            stored()
            val key = ByteArray(32) { 7 }
            coEvery { crypto.unwrapKey(any(), any()) } coAnswers
                {
                    manager.clearSession()
                    key
                }
            try {
                manager.initializeDatabase(cipher)
                fail("Expected revoked session")
            } catch (_: kotlinx.coroutines.CancellationException) {}
            assertTrue(key.all { it == 0.toByte() })
            verify(exactly = 0) { VaultDatabase.build(any(), any()) }
            assertFalse(manager.isDatabaseInitialized())
        }

    @Test
    fun failedWrappingKeyCommitDoesNotCreateDatabaseOrUnlock() =
        runBlocking<Unit> {
            val fakePrefs = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { fakePrefs.getString(any(), any()) } returns null
            every { fakePrefs.edit() } returns editor
            every { editor.putString(any(), any()) } returns editor
            every { editor.commit() } returns false
            DatabaseManager::class
                .java
                .getDeclaredField("sharedPrefs\$delegate")
                .apply { isAccessible = true }
                .set(manager, lazyOf(fakePrefs))
            try {
                manager.initializeDatabase(cipher)
                fail("Expected persistence failure")
            } catch (error: VaultError.DatabaseError) {
                assertTrue(error.cause is IllegalStateException)
            }
            verify(exactly = 0) { editor.apply() }
            verify(exactly = 0) { VaultDatabase.build(any(), any()) }
            assertFalse(manager.isDatabaseInitialized())
        }

    @Test
    fun existingFileWithoutWrappingKeyIsNotOverwrittenOrAssignedANewKey() =
        runBlocking<Unit> {
            val file = context.getDatabasePath("vault.db")
            file.parentFile!!.mkdirs()
            file.writeText("synthetic existing vault")
            try {
                try {
                    manager.initializeDatabase(cipher)
                    fail("Expected missing key refusal")
                } catch (_: VaultError.DatabaseError) {}
                assertEquals("synthetic existing vault", file.readText())
                assertFalse(prefs.contains("encrypted_db_passphrase"))
                coVerify(exactly = 0) { crypto.wrapKey(any(), any()) }
                verify(exactly = 0) { VaultDatabase.build(any(), any()) }
            } finally {
                context.deleteDatabase("vault.db")
            }
        }

    @Test
    fun configuredRecoveryCodeRestoresVaultAfterNewBiometricAuthentication() =
        runBlocking<Unit> {
            manager.initializeDatabase(cipher)
            val expected = manager.getSessionVaultKey().copyOf()
            val code = manager.createRecoveryCode()
            assertFalse(prefs.all.values.any { it == code })
            manager.clearSession()
            every { crypto.prepareRecoveryCipher("VaultRecoveryMasterKey") } returns cipher
            val attempt = manager.prepareRecovery(code)
            assertFalse(manager.isDatabaseInitialized())
            manager.completeRecovery(attempt, cipher)
            assertArrayEquals(expected, manager.getSessionVaultKey())
            assertTrue(attempt.key.all { it == 0.toByte() })
            assertEquals("VaultRecoveryMasterKey", prefs.getString("biometric_key_alias", null))
            every { crypto.getMasterDecryptionCipher(any(), "VaultRecoveryMasterKey") } returns
                cipher
            assertSame(cipher, manager.getPassphraseCipher())
            expected.fill(0)
        }

    @Test
    fun wrongRecoveryCodeDoesNotReplaceBiometricKeyOrOpenDatabase() =
        runBlocking<Unit> {
            manager.initializeDatabase(cipher)
            manager.createRecoveryCode()
            manager.clearSession()
            clearMocks(crypto, answers = false, recordedCalls = true)
            try {
                manager.prepareRecovery("A".repeat(43))
                fail("Expected wrong code")
            } catch (_: javax.crypto.AEADBadTagException) {}
            verify(exactly = 0) { crypto.prepareRecoveryCipher(any()) }
            assertFalse(manager.isDatabaseInitialized())
            assertNull(prefs.getString("biometric_key_alias", null))
        }

    @Test
    fun noConfiguredRecoveryCodeNeverGeneratesNewHardwareKey() =
        runBlocking<Unit> {
            try {
                manager.prepareRecovery("A".repeat(43))
                fail("Expected missing recovery envelope")
            } catch (_: IllegalStateException) {}
            verify(exactly = 0) { crypto.prepareRecoveryCipher(any()) }
        }

    @Test
    fun lockedVaultCannotCreateRecoveryEnvelope() =
        runBlocking<Unit> {
            try {
                manager.createRecoveryCode()
                fail("Expected locked vault")
            } catch (_: VaultError.UninitializedVault) {}
            assertFalse(prefs.contains("recovery_ciphertext"))
        }

    @Test
    fun lockingBetweenRecoveryPreparationAndSuccessRevokesRecovery() =
        runBlocking<Unit> {
            manager.initializeDatabase(cipher)
            val code = manager.createRecoveryCode()
            manager.clearSession()
            every { crypto.prepareRecoveryCipher(any()) } returns cipher
            val attempt = manager.prepareRecovery(code)
            manager.clearSession()
            try {
                manager.completeRecovery(attempt, cipher)
                fail("Expected revoked recovery")
            } catch (_: kotlinx.coroutines.CancellationException) {}
            assertFalse(manager.isDatabaseInitialized())
            assertTrue(attempt.key.all { it == 0.toByte() })
            assertNull(prefs.getString("biometric_key_alias", null))
        }

    @Test
    fun recoveryDatabaseFailurePreservesWrappingKeyAndRecoveryEnvelope() =
        runBlocking<Unit> {
            manager.initializeDatabase(cipher)
            val code = manager.createRecoveryCode()
            val before = prefs.all.toMap()
            manager.clearSession()
            every { crypto.prepareRecoveryCipher(any()) } returns cipher
            val attempt = manager.prepareRecovery(code)
            every { db.openHelper.writableDatabase } throws
                IllegalStateException("synthetic database error")
            try {
                manager.completeRecovery(attempt, cipher)
                fail("Expected open failure")
            } catch (_: IllegalStateException) {}
            assertEquals(before, prefs.all)
            assertTrue(attempt.key.all { it == 0.toByte() })
            assertFalse(manager.isDatabaseInitialized())
        }

    @Test
    fun repeatedRecoveryUsesInactiveSlotAndPreservesExistingCode() =
        runBlocking<Unit> {
            manager.initializeDatabase(cipher)
            val code = manager.createRecoveryCode()
            every { crypto.prepareRecoveryCipher(any()) } returns cipher
            manager.clearSession()
            val first = manager.prepareRecovery(code)
            assertEquals("VaultRecoveryMasterKey", first.alias)
            manager.completeRecovery(first, cipher)
            manager.clearSession()
            val second = manager.prepareRecovery(code)
            assertEquals("VaultMasterKey", second.alias)
            manager.completeRecovery(second, cipher)
            assertTrue(manager.isDatabaseInitialized())
            verify(exactly = 1) { crypto.prepareRecoveryCipher("VaultRecoveryMasterKey") }
            verify(exactly = 1) { crypto.prepareRecoveryCipher("VaultMasterKey") }
        }
}
