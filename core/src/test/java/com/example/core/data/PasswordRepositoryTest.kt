package com.example.core.data

import com.example.core.crypto.CryptoManager
import com.example.core.data.dao.CredentialDao
import com.example.core.data.entity.EncryptedCredentialEntity
import com.example.core.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordRepositoryTest {
    private val manager = mockk<DatabaseManager>()
    private val crypto = mockk<CryptoManager>()
    private val db = mockk<VaultDatabase>()
    private val dao = mockk<CredentialDao>()
    private val initialized = MutableStateFlow(true)
    private val rows = MutableStateFlow<List<EncryptedCredentialEntity>>(emptyList())
    private val key = ByteArray(32) { 7 }
    private lateinit var repo: PasswordRepository

    @Before
    fun setup() {
        every { manager.session } returns com.example.core.auth.VaultSession()
        every { manager.isInitialized } returns initialized
        every { manager.isDatabaseInitialized() } answers { initialized.value }
        every { manager.getSessionVaultKey() } returns key
        every { manager.getDatabase() } returns db
        every { db.credentialDao() } returns dao
        every { dao.getAllCredentials() } returns rows
        coEvery { dao.insertCredential(any()) } just Runs
        coEvery { dao.deleteCredential(any()) } just Runs
        coEvery { dao.moveToTrash(any(), any()) } returns 1
        coEvery { dao.archiveAndUpdate(any(), any(), any(), any()) } just Runs
        coEvery { dao.getCredentialById(any()) } returns null
        coEvery { crypto.encryptWithKey(any(), key) } answers
            {
                if (firstArg<ByteArray>().contentEquals("user".toByteArray()))
                    "encrypted-user" to "user-iv"
                else "encrypted-pass" to "pass-iv"
            }
        coEvery { crypto.decryptWithKey("encrypted-user", "user-iv", key) } answers
            {
                "user".toByteArray()
            }
        coEvery { crypto.decryptWithKey("encrypted-pass", "pass-iv", key) } answers
            {
                "pass".toByteArray()
            }
        repo = PasswordRepository(manager, crypto)
    }

    private fun credential() =
        Credential("Google", "user".toByteArray(), "pass".toByteArray(), "url", 2)

    private fun entity(id: Int = 8) =
        EncryptedCredentialEntity(
            id,
            "Google",
            "encrypted-user",
            "user-iv",
            "encrypted-pass",
            "pass-iv",
            "url",
            2,
        )

    @Test
    fun unchangedUsernamesAreNotDecryptedWhenOtherRecordsOrMetadataChange() =
        runBlocking<Unit> {
            rows.value = listOf(entity())
            assertEquals("user", repo.passwords.first().single().userName)
            rows.value = listOf(entity().copy(platformName = "Renamed", categoryId = 3))
            assertEquals("Renamed", repo.passwords.first().single().platform)
            rows.value = listOf(entity().copy(encryptedPassword = "changed-password"), entity(9))
            assertEquals(2, repo.passwords.first().size)
            coVerify(exactly = 2) { crypto.decryptWithKey("encrypted-user", "user-iv", key) }
        }

    @Test
    fun changedUsernameCiphertextInvalidatesOnlyItsOwnCacheEntry() =
        runBlocking<Unit> {
            rows.value = listOf(entity(8), entity(9))
            repo.passwords.first()
            coEvery { crypto.decryptWithKey("changed-user", "new-iv", key) } returns
                "new-user".toByteArray()
            rows.value =
                listOf(
                    entity(8).copy(encryptedUsername = "changed-user", usernameIv = "new-iv"),
                    entity(9),
                )
            assertEquals(listOf("new-user", "user"), repo.passwords.first().map { it.userName })
            coVerify(exactly = 2) { crypto.decryptWithKey("encrypted-user", "user-iv", key) }
            coVerify(exactly = 1) { crypto.decryptWithKey("changed-user", "new-iv", key) }
        }

    @Test
    fun deletingAndReaddingRecordDoesNotReuseRemovedUsername() =
        runBlocking<Unit> {
            rows.value = listOf(entity())
            repo.passwords.first()
            rows.value = emptyList()
            assertTrue(repo.passwords.first().isEmpty())
            rows.value = listOf(entity())
            repo.passwords.first()
            coVerify(exactly = 2) { crypto.decryptWithKey("encrypted-user", "user-iv", key) }
        }

    @Test
    fun sessionLockWipesCacheBuffersAndNextSessionDecryptsAgain() =
        runBlocking<Unit> {
            rows.value = listOf(entity())
            repo.passwords.first()
            val cache =
                PasswordRepository::class
                    .java
                    .getDeclaredField("usernameCache")
                    .apply { isAccessible = true }
                    .get(repo) as Map<*, *>
            val entry = cache.values.single()!!
            val held =
                entry.javaClass
                    .getDeclaredField("characters")
                    .apply { isAccessible = true }
                    .get(entry) as CharArray
            manager.session.lock()
            assertTrue(held.all { it == '\u0000' })
            assertTrue(cache.isEmpty())
            repo.passwords.first()
            coVerify(exactly = 2) { crypto.decryptWithKey("encrypted-user", "user-iv", key) }
        }

    @Test
    fun lockDuringDecryptionRejectsAndWipesLatePlaintextInsteadOfCachingIt() =
        runBlocking<Unit> {
            rows.value = listOf(entity())
            val plaintext = "user".toByteArray()
            coEvery { crypto.decryptWithKey("encrypted-user", "user-iv", key) } coAnswers
                {
                    initialized.value = false
                    manager.session.lock()
                    plaintext
                }
            assertTrue(withTimeout(3000) { repo.passwords.first() }.isEmpty())
            assertTrue(plaintext.all { it == 0.toByte() })
        }

    private suspend fun failure(block: suspend () -> Unit): Throwable =
        try {
            block()
            error("Expected failure")
        } catch (e: VaultError) {
            e
        }

    @Test
    fun categoriesExposeAllKnownCategories() {
        assertEquals(Category.entries, repo.getCategories())
    }

    @Test
    fun healthAnalysisWipesDecryptedPasswordsAndItsOwnedKeyCopy() =
        runBlocking<Unit> {
            coEvery { dao.activeSnapshot() } returns listOf(entity())
            val secret = "short".toByteArray()
            var held: ByteArray? = null
            coEvery { crypto.decryptWithKey("encrypted-pass", "pass-iv", any()) } answers
                {
                    held = thirdArg()
                    secret
                }
            val report = repo.healthReport()
            assertEquals(1, report.weakCount)
            assertTrue(secret.all { it == 0.toByte() })
            assertTrue(held!!.all { it == 0.toByte() })
            assertTrue(key.all { it == 7.toByte() })
        }

    @Test
    fun failedHealthAnalysisWipesEarlierSecretsAndDoesNotReturnCleanReport() =
        runBlocking<Unit> {
            coEvery { dao.activeSnapshot() } returns
                listOf(entity(), entity(9).copy(encryptedPassword = "corrupt"))
            val secret = "short".toByteArray()
            coEvery { crypto.decryptWithKey("encrypted-pass", "pass-iv", any()) } returns secret
            coEvery { crypto.decryptWithKey("corrupt", "pass-iv", any()) } throws
                VaultError.DecryptionError(IllegalStateException())
            assertTrue(failure { repo.healthReport() } is VaultError.DecryptionError)
            assertTrue(secret.all { it == 0.toByte() })
        }

    @Test
    fun lockedVaultCannotAnalyzeHealthReadHistoryOrModifyMetadata() =
        runBlocking<Unit> {
            initialized.value = false
            assertSame(VaultError.UninitializedVault, failure { repo.healthReport() })
            assertSame(VaultError.UninitializedVault, failure { repo.history(8) })
            assertSame(VaultError.UninitializedVault, failure { repo.setFavorite(8, true) })
            assertSame(VaultError.UninitializedVault, failure { repo.setTags(8, "work") })
            assertSame(VaultError.UninitializedVault, failure { repo.trashItems() })
            coVerify(exactly = 0) { dao.activeSnapshot() }
        }

    @Test
    fun addEncryptsBothFieldsAndPreservesMetadata() = runBlocking {
        val slot = slot<EncryptedCredentialEntity>()
        coEvery { dao.insertCredential(capture(slot)) } just Runs
        val c = credential()
        repo.addPassword(c)
        assertEquals(entity(0), slot.captured.copy(createdAt = 0, passwordChangedAt = 0))
        assertTrue(slot.captured.createdAt > 0 && slot.captured.passwordChangedAt > 0)
        assertTrue(c.username.all { it == 0.toByte() })
        assertTrue(c.password.all { it == 0.toByte() })
        coVerify(exactly = 2) { crypto.encryptWithKey(any(), key) }
    }

    @Test
    fun addDoesNotInsertPlaintext() = runBlocking {
        val slot = slot<EncryptedCredentialEntity>()
        coEvery { dao.insertCredential(capture(slot)) } just Runs
        repo.addPassword(credential())
        assertNotEquals("user", slot.captured.encryptedUsername)
        assertNotEquals("pass", slot.captured.encryptedPassword)
    }

    @Test
    fun addWrapsDaoFailureAndClearsInput() = runBlocking {
        val cause = IllegalStateException("insert")
        coEvery { dao.insertCredential(any()) } throws cause
        val c = credential()
        assertSame(cause, (failure { repo.addPassword(c) } as VaultError.DatabaseError).cause)
        assertTrue(c.password.all { it == 0.toByte() })
    }

    @Test
    fun addPreservesEncryptionErrorAndClearsInput() = runBlocking {
        val error = VaultError.EncryptionError(Exception())
        coEvery { crypto.encryptWithKey(any(), key) } throws error
        val c = credential()
        assertSame(error, failure { repo.addPassword(c) })
        assertTrue(c.username.all { it == 0.toByte() })
        coVerify(exactly = 0) { dao.insertCredential(any()) }
    }

    @Test
    fun failedSecondEncryptionNeverInsertsPartiallyEncryptedRecord() = runBlocking {
        coEvery {
            crypto.encryptWithKey(match { it.contentEquals("pass".toByteArray()) }, key)
        } throws VaultError.EncryptionError(Exception())
        val c = credential()
        failure { repo.addPassword(c) }
        coVerify(exactly = 0) { dao.insertCredential(any()) }
        assertTrue(c.password.all { it == 0.toByte() })
    }

    @Test
    fun lockedVaultAddPreservesVaultErrorAndClearsInput() = runBlocking {
        every { manager.getSessionVaultKey() } throws VaultError.UninitializedVault
        val c = credential()
        assertSame(VaultError.UninitializedVault, failure { repo.addPassword(c) })
        assertTrue(c.password.all { it == 0.toByte() })
    }

    @Test
    fun updateKeepsIdAndReusesUnchangedCiphertext() = runBlocking {
        val slot = slot<EncryptedCredentialEntity>()
        coEvery { dao.getCredentialById(77) } returns entity(77)
        coEvery { dao.archiveAndUpdate(any(), capture(slot), any(), any()) } just Runs
        val c = credential()
        repo.updatePassword(77, c)
        assertEquals(entity(77), slot.captured)
        assertTrue(c.password.all { it == 0.toByte() })
    }

    @Test
    fun updateFailureClearsInputAndKeepsOriginalCause() = runBlocking {
        val e = Exception("write")
        coEvery { dao.getCredentialById(9) } returns entity(9)
        coEvery { dao.archiveAndUpdate(any(), any(), any(), any()) } throws e
        val c = credential()
        assertSame(e, (failure { repo.updatePassword(9, c) } as VaultError.DatabaseError).cause)
        assertTrue(c.password.all { it == 0.toByte() })
    }

    @Test
    fun updatePreservesVaultError() = runBlocking {
        every { manager.getSessionVaultKey() } throws VaultError.UninitializedVault
        assertSame(VaultError.UninitializedVault, failure { repo.updatePassword(9, credential()) })
    }

    @Test
    fun deletePassesRequestedId() = runBlocking {
        repo.deletePassword(77)
        coVerify(exactly = 1) { dao.moveToTrash(77, any()) }
        coVerify(exactly = 0) { crypto.encryptWithKey(any(), any()) }
    }

    @Test
    fun deleteWrapsUnknownError() = runBlocking {
        val e = Exception()
        coEvery { dao.moveToTrash(any(), any()) } throws e
        assertSame(e, (failure { repo.deletePassword(1) } as VaultError.DatabaseError).cause)
    }

    @Test
    fun deletePreservesVaultError() = runBlocking {
        every { manager.getDatabase() } throws VaultError.UninitializedVault
        assertSame(VaultError.UninitializedVault, failure { repo.deletePassword(1) })
    }

    @Test
    fun lockedVaultDetailsReturnNullWithoutAccessingKeyOrDao() = runBlocking {
        initialized.value = false
        assertNull(repo.getDecryptedCredential(1))
        verify(exactly = 0) { manager.getSessionVaultKey() }
        coVerify(exactly = 0) { dao.getCredentialById(any()) }
    }

    @Test
    fun missingRecordReturnsNullWithoutDecrypting() = runBlocking {
        assertNull(repo.getDecryptedCredential(999))
        coVerify(exactly = 0) { crypto.decryptWithKey(any(), any(), any()) }
    }

    @Test
    fun detailsDecryptBothFieldsAndWipeTemporaryBytes() = runBlocking {
        val user = "שלום".toByteArray()
        val pass = "🔐".toByteArray()
        coEvery { dao.getCredentialById(8) } returns entity()
        coEvery { crypto.decryptWithKey("encrypted-user", "user-iv", key) } returns user
        coEvery { crypto.decryptWithKey("encrypted-pass", "pass-iv", key) } returns pass
        val d = repo.getDecryptedCredential(8)!!
        assertEquals(8, d.id)
        assertEquals("Google", d.platform)
        assertEquals("שלום", String(d.username))
        assertEquals("🔐", String(d.password))
        assertEquals("url", d.website)
        assertEquals(2, d.categoryId)
        assertTrue(user.all { it == 0.toByte() })
        assertTrue(pass.all { it == 0.toByte() })
    }

    @Test
    fun detailDaoFailureIsWrapped() = runBlocking {
        val e = Exception()
        coEvery { dao.getCredentialById(any()) } throws e
        assertSame(
            e,
            (failure { repo.getDecryptedCredential(1) } as VaultError.DatabaseError).cause,
        )
    }

    @Test
    fun detailDecryptionErrorIsPreserved() = runBlocking {
        coEvery { dao.getCredentialById(8) } returns entity()
        val e = VaultError.DecryptionError(Exception())
        coEvery { crypto.decryptWithKey(any(), any(), key) } throws e
        assertSame(e, failure { repo.getDecryptedCredential(8) })
    }

    @Test
    fun lockedPasswordFlowIsEmptyWithoutDaoAccess() = runBlocking {
        initialized.value = false
        assertTrue(repo.passwords.first().isEmpty())
        verify(exactly = 0) { dao.getAllCredentials() }
    }

    @Test
    fun emptyDatabaseEmitsEmptyList() = runBlocking { assertTrue(repo.passwords.first().isEmpty()) }

    @Test
    fun listDecryptsOnlyUsernameAndWipesBytes() = runBlocking {
        val bytes = "user".toByteArray()
        coEvery { crypto.decryptWithKey("encrypted-user", "user-iv", key) } returns bytes
        rows.value = listOf(entity())
        assertEquals(
            listOf(PasswordItemModel(8, "Google", "user", "url", 2)),
            repo.passwords.first(),
        )
        assertTrue(bytes.all { it == 0.toByte() })
        coVerify(exactly = 0) { crypto.decryptWithKey("encrypted-pass", any(), any()) }
    }

    @Test
    fun listPreservesRecordOrder() = runBlocking {
        rows.value = listOf(entity(9), entity(2), entity(7))
        assertEquals(listOf(9, 2, 7), repo.passwords.first().map { it.id })
    }

    @Test
    fun flowWrapsUnknownDatabaseFailure() = runBlocking {
        val e = Exception()
        every { dao.getAllCredentials() } returns flow { throw e }
        val cause = (failure { repo.passwords.first() } as VaultError.DatabaseError).cause
        assertTrue(cause === e || cause.cause === e)
    }

    @Test
    fun flowPreservesDecryptionError() = runBlocking {
        rows.value = listOf(entity())
        val e = VaultError.DecryptionError(Exception())
        coEvery { crypto.decryptWithKey(any(), any(), key) } throws e
        assertSame(e, failure { repo.passwords.first() })
    }

    @Test
    fun lockingCancelsCurrentDaoFlowAndEmitsEmptyList() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        every { dao.getAllCredentials() } returns
            flow {
                started.complete(Unit)
                try {
                    emit(listOf(entity()))
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        val empty = CompletableDeferred<Unit>()
        val job = launch { repo.passwords.collect { if (it.isEmpty()) empty.complete(Unit) } }
        withTimeout(5000) { started.await() }
        initialized.value = false
        withTimeout(5000) {
            empty.await()
            cancelled.await()
        }
        job.cancelAndJoin()
    }

    @Test
    fun unlockingSwitchesFromEmptyToDatabaseRecords() = runBlocking {
        initialized.value = false
        rows.value = listOf(entity())
        val first = CompletableDeferred<Unit>()
        val unlocked = CompletableDeferred<List<PasswordItemModel>>()
        val job = launch {
            repo.passwords.collect {
                if (it.isEmpty()) first.complete(Unit) else unlocked.complete(it)
            }
        }
        first.await()
        initialized.value = true
        assertEquals(8, withTimeout(5000) { unlocked.await() }.single().id)
        job.cancelAndJoin()
    }

    @Test
    fun updateCancellationPropagatesAndWipesInput() =
        runBlocking<Unit> {
            coEvery { dao.getCredentialById(any()) } answers { entity(firstArg()) }

            coEvery { dao.archiveAndUpdate(any(), any(), any(), any()) } throws
                CancellationException()
            val c =
                credential()
                    .copy(
                        username = "changed-user".toByteArray(),
                        password = "changed-password".toByteArray(),
                    )
            try {
                repo.updatePassword(8, c)
                fail()
            } catch (e: CancellationException) {}
            assertTrue(c.username.all { it == 0.toByte() })
            assertTrue(c.password.all { it == 0.toByte() })
        }

    @Test
    fun deleteCancellationRemainsCancellationRatherThanDatabaseError() =
        runBlocking<Unit> {
            coEvery { dao.moveToTrash(any(), any()) } throws CancellationException()
            try {
                repo.deletePassword(8)
                fail()
            } catch (e: CancellationException) {}
        }

    @Test
    fun detailsCancellationDoesNotBecomeDatabaseError() =
        runBlocking<Unit> {
            coEvery { dao.getCredentialById(any()) } throws CancellationException()
            try {
                repo.getDecryptedCredential(8)
                fail()
            } catch (e: CancellationException) {}
        }

    @Test
    fun emptySecretsAreEncryptedRatherThanInsertedAsRawValues() =
        runBlocking<Unit> {
            val captured = slot<EncryptedCredentialEntity>()
            coEvery { dao.insertCredential(capture(captured)) } just Runs
            repo.addPassword(credential().copy(username = byteArrayOf(), password = byteArrayOf()))
            assertTrue(captured.captured.encryptedPassword.isNotEmpty())
            coVerify(exactly = 2) { crypto.encryptWithKey(match { it.isEmpty() }, key) }
        }

    @Test
    fun genericCryptoFailureIsWrappedAndInputIsWiped() =
        runBlocking<Unit> {
            val cause = IllegalArgumentException()
            coEvery { crypto.encryptWithKey(any(), key) } throws cause
            val c = credential()
            assertSame(cause, (failure { repo.addPassword(c) } as VaultError.DatabaseError).cause)
            assertTrue(c.password.all { it == 0.toByte() })
            coVerify(exactly = 0) { dao.insertCredential(any()) }
        }

    @Test
    fun repeatedUnlockCyclesAcquireFreshSessionKeyInsteadOfReusingPreviousOne() =
        runBlocking<Unit> {
            initialized.value = false
            val second = ByteArray(32) { 8 }
            var current = key
            every { manager.getSessionVaultKey() } answers { current }
            rows.value = listOf(entity())
            coEvery { crypto.decryptWithKey("encrypted-user", "user-iv", second) } answers
                {
                    "second-user".toByteArray()
                }
            val emissions =
                kotlinx.coroutines.channels.Channel<List<PasswordItemModel>>(
                    kotlinx.coroutines.channels.Channel.UNLIMITED
                )
            val job = launch { repo.passwords.collect { emissions.send(it) } }
            assertTrue(withTimeout(5000) { emissions.receive() }.isEmpty())
            initialized.value = true
            assertEquals("user", withTimeout(5000) { emissions.receive() }.single().userName)
            initialized.value = false
            assertTrue(withTimeout(5000) { emissions.receive() }.isEmpty())
            current = second
            initialized.value = true
            assertEquals("second-user", withTimeout(5000) { emissions.receive() }.single().userName)
            job.cancelAndJoin()
            emissions.close()
        }
}
