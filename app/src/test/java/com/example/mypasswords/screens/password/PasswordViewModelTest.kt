package com.example.mypasswords.screens.password

import android.app.Application
import com.example.core.data.PasswordRepository
import com.example.core.model.*
import com.example.mypasswords.testing.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = mockk<PasswordRepository>()
    private val app = mockk<Application>()
    private val rows =
        MutableStateFlow(
            listOf(
                PasswordItemModel(1, "Google", "user", categoryId = 2),
                PasswordItemModel(2, "Netflix", "google-user", categoryId = 3),
                PasswordItemModel(3, "CODEHOST", "dev", categoryId = 2),
                PasswordItemModel(4, "בנק", "bank", categoryId = 4),
            )
        )
    private lateinit var vm: PasswordViewModel

    @Before
    fun setup() {
        every { repo.getCategories() } returns Category.entries
        every { app.getString(any()) } answers { "category-${firstArg<Int>()}" }
        every { repo.passwords } returns rows
        coEvery { repo.deletePassword(any()) } just Runs
        coEvery { repo.updatePassword(any(), any()) } just Runs
        coEvery { repo.getDecryptedCredential(any()) } returns null
        vm = PasswordViewModel(repo, app)
    }

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.filteredPasswords.collect()
        }
        runCurrent()
    }

    @Test
    fun pendingFirstSnapshotRemainsLoadingInsteadOfShowingEmptyVault() = runTest {
        every { repo.passwords } returns flow { awaitCancellation() }
        vm = PasswordViewModel(repo, app)
        subscribe()
        assertSame(PasswordListStatus.Loading, vm.uiState.value.listStatus)
        assertTrue(vm.filteredPasswords.value.isEmpty())
    }

    @Test
    fun emptyAndLoadedSnapshotsHaveDifferentStatuses() = runTest {
        rows.value = emptyList()
        subscribe()
        assertSame(PasswordListStatus.Empty, vm.uiState.value.listStatus)
        rows.value = listOf(PasswordItemModel(9, "Site", "user"))
        runCurrent()
        assertSame(PasswordListStatus.Ready, vm.uiState.value.listStatus)
        assertEquals(1, vm.filteredPasswords.value.size)
    }

    @Test
    fun noSearchMatchesDoesNotBecomeAnEmptyVaultStatus() = runTest {
        subscribe()
        vm.onSearchQueryChanged("does not match any site")
        runCurrent()
        assertTrue(vm.filteredPasswords.value.isEmpty())
        assertSame(PasswordListStatus.Ready, vm.uiState.value.listStatus)
    }

    @Test
    fun stoppedSubscriptionReleasesReplayedPlaintextList() = runTest {
        val collector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.filteredPasswords.collect()
            }
        runCurrent()
        assertTrue(vm.filteredPasswords.value.isNotEmpty())
        collector.cancel()
        advanceTimeBy(5001)
        runCurrent()
        assertTrue(vm.filteredPasswords.value.isEmpty())
    }

    private fun detail() =
        FullCredentialModel(1, "Google", "user".toCharArray(), "pass".toCharArray(), "url", 2)

    @Test
    fun emptyEditedFieldsNeverReachRepository() = runTest {
        coEvery { repo.getDecryptedCredential(1) } returns detail()
        vm.onEditClick(rows.value.first())
        runCurrent()
        vm.updateCredential("", "secret")
        runCurrent()
        vm.updateCredential("user", "")
        runCurrent()
        coVerify(exactly = 0) { repo.updatePassword(any(), any()) }
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isUpdating)
    }

    @Test
    fun doubleUpdateWhileSavingSubmitsOnceAndResetsBusyState() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { repo.getDecryptedCredential(1) } returns detail()
        coEvery { repo.updatePassword(any(), any()) } coAnswers { gate.await() }
        vm.onEditClick(rows.value.first())
        runCurrent()
        vm.updateCredential("user", "secret")
        runCurrent()
        assertTrue(vm.uiState.value.isUpdating)
        vm.updateCredential("other", "new secret")
        runCurrent()
        coVerify(exactly = 1) { repo.updatePassword(any(), any()) }
        gate.complete(Unit)
        runCurrent()
        assertFalse(vm.uiState.value.isUpdating)
    }

    @Test
    fun retryRestartsFailedListSubscriptionAndReceivesSubsequentChanges() = runTest {
        var subscriptions = 0
        every { repo.passwords } returns
            flow {
                subscriptions++
                if (subscriptions == 1) throw VaultError.DecryptionError(IllegalStateException())
                emitAll(rows)
            }
        vm = PasswordViewModel(repo, app)
        subscribe()
        assertTrue(vm.uiState.value.listStatus is PasswordListStatus.Failed)
        assertTrue(vm.filteredPasswords.value.isEmpty())
        vm.retryLoadingPasswords()
        runCurrent()
        assertFalse(vm.uiState.value.listStatus is PasswordListStatus.Failed)
        assertEquals(rows.value, vm.filteredPasswords.value)
        rows.value = rows.value.take(1)
        runCurrent()
        assertEquals(1, vm.filteredPasswords.value.size)
        assertEquals(2, subscriptions)
    }

    @Test
    fun repeatedFailedRetriesRemainAvailableAndPreserveError() = runTest {
        every { repo.passwords } returns flow { throw VaultError.UninitializedVault }
        vm = PasswordViewModel(repo, app)
        subscribe()
        repeat(3) {
            vm.retryLoadingPasswords()
            runCurrent()
            assertTrue(vm.uiState.value.listStatus is PasswordListStatus.Failed)
            assertSame(VaultError.UninitializedVault, vm.uiState.value.error)
        }
    }

    @Test
    fun recoveryCodeIsCreatedOnlyOnRequestAndRemovedWhenDialogCloses() = runTest {
        coEvery { repo.createRecoveryCode() } returns "synthetic code"
        vm.showRecoveryDialog()
        coVerify(exactly = 0) { repo.createRecoveryCode() }
        vm.createRecoveryCode()
        runCurrent()
        assertEquals("synthetic code", vm.uiState.value.recoveryCode)
        vm.dismissRecoveryDialog()
        assertNull(vm.uiState.value.recoveryCode)
        assertFalse(vm.uiState.value.isRecoveryDialogVisible)
    }

    @Test
    fun initialStateHasAllLocalizedCategoriesAndNoDialogs() {
        val s = vm.uiState.value
        assertEquals(4, s.categories.size)
        assertEquals(1, s.selectedCategoryId)
        assertFalse(s.isSearchActive)
        assertEquals("", s.searchQuery)
        assertNull(s.itemToDelete)
        assertNull(s.itemToEdit)
        assertNull(s.selectedItemDetail)
        assertNull(s.error)
        assertTrue(vm.filteredPasswords.value.isEmpty())
    }

    @Test
    fun subscriptionEmitsAllRowsInOriginalOrder() = runTest {
        subscribe()
        assertEquals(rows.value, vm.filteredPasswords.value)
    }

    @Test
    fun categoryFiltersOnlyMatchingRows() = runTest {
        subscribe()
        vm.onCategorySelected(2)
        runCurrent()
        assertEquals(listOf(1, 3), vm.filteredPasswords.value.map { it.id })
    }

    @Test
    fun selectingAllRestoresAllCategories() = runTest {
        subscribe()
        vm.onCategorySelected(4)
        runCurrent()
        vm.onCategorySelected(1)
        runCurrent()
        assertEquals(rows.value, vm.filteredPasswords.value)
    }

    @Test
    fun unknownCategoryProducesEmptyList() = runTest {
        subscribe()
        vm.onCategorySelected(999)
        runCurrent()
        assertTrue(vm.filteredPasswords.value.isEmpty())
    }

    @Test
    fun searchIsCaseInsensitiveAndMatchesSubstrings() = runTest {
        subscribe()
        vm.onSearchQueryChanged("oDe")
        runCurrent()
        assertEquals(listOf(3), vm.filteredPasswords.value.map { it.id })
    }

    @Test
    fun advancedSearchMatchesUsernameAsWellAsPlatform() = runTest {
        subscribe()
        vm.onSearchQueryChanged("google")
        runCurrent()
        assertEquals(listOf(1, 2), vm.filteredPasswords.value.map { it.id })
    }

    @Test
    fun blankSearchDoesNotFilter() = runTest {
        subscribe()
        for (q in listOf("", " ", "\t\n")) {
            vm.onSearchQueryChanged(q)
            runCurrent()
            assertEquals(rows.value, vm.filteredPasswords.value)
        }
    }

    @Test
    fun unmatchedSearchProducesEmptyList() = runTest {
        subscribe()
        vm.onSearchQueryChanged("missing")
        runCurrent()
        assertTrue(vm.filteredPasswords.value.isEmpty())
    }

    @Test
    fun unicodeSearchWorks() = runTest {
        subscribe()
        vm.onSearchQueryChanged("בנק")
        runCurrent()
        assertEquals(listOf(4), vm.filteredPasswords.value.map { it.id })
    }

    @Test
    fun searchAndCategoryAreCombined() = runTest {
        subscribe()
        vm.onCategorySelected(2)
        vm.onSearchQueryChanged("Google")
        runCurrent()
        assertEquals(listOf(1), vm.filteredPasswords.value.map { it.id })
        vm.onCategorySelected(3)
        runCurrent()
        assertEquals(listOf(2), vm.filteredPasswords.value.map { it.id })
    }

    @Test
    fun flowUpdatesRecomputeCurrentFilter() = runTest {
        subscribe()
        vm.onCategorySelected(4)
        runCurrent()
        rows.value = rows.value + PasswordItemModel(5, "Bank2", "u", categoryId = 4)
        runCurrent()
        assertEquals(listOf(4, 5), vm.filteredPasswords.value.map { it.id })
    }

    @Test
    fun emptyRepositoryEmitsEmptyList() = runTest {
        rows.value = emptyList()
        subscribe()
        assertTrue(vm.filteredPasswords.value.isEmpty())
    }

    @Test
    fun openingSearchPreservesExistingQuery() {
        vm.onSearchQueryChanged("query")
        vm.onToggleSearch()
        assertTrue(vm.uiState.value.isSearchActive)
        assertEquals("query", vm.uiState.value.searchQuery)
    }

    @Test
    fun closingSearchClearsQuery() = runTest {
        subscribe()
        vm.onToggleSearch()
        vm.onSearchQueryChanged("Google")
        vm.onToggleSearch()
        runCurrent()
        assertFalse(vm.uiState.value.isSearchActive)
        assertEquals("", vm.uiState.value.searchQuery)
        assertEquals(rows.value, vm.filteredPasswords.value)
    }

    @Test
    fun showAndDismissDeleteDoesNotDelete() {
        vm.showDeleteConfirmation(rows.value.first())
        assertEquals(1, vm.uiState.value.itemToDelete!!.id)
        vm.dismissDeleteConfirmation()
        assertNull(vm.uiState.value.itemToDelete)
        coVerify(exactly = 0) { repo.deletePassword(any()) }
    }

    @Test
    fun confirmDeleteWithoutSelectionDoesNothing() = runTest {
        vm.confirmDelete()
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.deletePassword(any()) }
    }

    @Test
    fun successfulDeleteDismissesConfirmation() = runTest {
        vm.showDeleteConfirmation(rows.value.first())
        vm.confirmDelete()
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.deletePassword(1) }
        assertNull(vm.uiState.value.itemToDelete)
    }

    @Test
    fun failedDeleteRetainsSelectionAndVaultError() = runTest {
        coEvery { repo.deletePassword(any()) } throws VaultError.UninitializedVault
        vm.showDeleteConfirmation(rows.value.first())
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.itemToDelete!!.id)
        assertSame(VaultError.UninitializedVault, vm.uiState.value.error)
    }

    @Test
    fun unknownDeleteErrorIsWrapped() = runTest {
        val e = Exception()
        coEvery { repo.deletePassword(any()) } throws e
        vm.showDeleteConfirmation(rows.value.first())
        vm.confirmDelete()
        advanceUntilIdle()
        assertSame(e, (vm.uiState.value.error as VaultError.GenericError).cause)
    }

    @Test
    fun openingDetailFetchesRequestedRecord() = runTest {
        val d = detail()
        coEvery { repo.getDecryptedCredential(1) } returns d
        vm.onPasswordClick(rows.value.first())
        advanceUntilIdle()
        assertSame(d, vm.uiState.value.selectedItemDetail)
        coVerify(exactly = 1) { repo.getDecryptedCredential(1) }
    }

    @Test
    fun missingDetailDoesNotOpenSheet() = runTest {
        vm.onPasswordClick(rows.value.first())
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedItemDetail)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun detailFailureRetainsVaultError() = runTest {
        coEvery { repo.getDecryptedCredential(any()) } throws VaultError.UninitializedVault
        vm.onPasswordClick(rows.value.first())
        advanceUntilIdle()
        assertSame(VaultError.UninitializedVault, vm.uiState.value.error)
        assertNull(vm.uiState.value.selectedItemDetail)
    }

    @Test
    fun detailUnknownFailureIsWrapped() = runTest {
        val e = Exception()
        coEvery { repo.getDecryptedCredential(any()) } throws e
        vm.onPasswordClick(rows.value.first())
        advanceUntilIdle()
        assertSame(e, (vm.uiState.value.error as VaultError.GenericError).cause)
    }

    @Test
    fun dismissDetailWipesSensitiveArrays() = runTest {
        val d = detail()
        coEvery { repo.getDecryptedCredential(1) } returns d
        vm.onPasswordClick(rows.value.first())
        advanceUntilIdle()
        vm.dismissPasswordDetail()
        assertTrue(d.username.all { it == ' ' })
        assertTrue(d.password.all { it == ' ' })
        assertNull(vm.uiState.value.selectedItemDetail)
    }

    @Test
    fun dismissAbsentDetailAndEditAreSafe() {
        vm.dismissPasswordDetail()
        vm.dismissEdit()
        assertNull(vm.uiState.value.selectedItemDetail)
        assertNull(vm.uiState.value.itemToEdit)
    }

    @Test
    fun editFetchesRequestedRecord() = runTest {
        val d = detail()
        coEvery { repo.getDecryptedCredential(1) } returns d
        vm.onEditClick(rows.value.first())
        advanceUntilIdle()
        assertSame(d, vm.uiState.value.itemToEdit)
    }

    @Test
    fun missingEditRecordDoesNotOpenSheet() = runTest {
        vm.onEditClick(rows.value.first())
        advanceUntilIdle()
        assertNull(vm.uiState.value.itemToEdit)
    }

    @Test
    fun editFetchErrorIsPreserved() = runTest {
        coEvery { repo.getDecryptedCredential(any()) } throws VaultError.UninitializedVault
        vm.onEditClick(rows.value.first())
        advanceUntilIdle()
        assertSame(VaultError.UninitializedVault, vm.uiState.value.error)
    }

    @Test
    fun editUnknownFailureIsWrapped() = runTest {
        val e = Exception()
        coEvery { repo.getDecryptedCredential(any()) } throws e
        vm.onEditClick(rows.value.first())
        advanceUntilIdle()
        assertSame(e, (vm.uiState.value.error as VaultError.GenericError).cause)
    }

    @Test
    fun dismissEditWipesSensitiveArrays() = runTest {
        val d = detail()
        coEvery { repo.getDecryptedCredential(1) } returns d
        vm.onEditClick(rows.value.first())
        advanceUntilIdle()
        vm.dismissEdit()
        assertTrue(d.password.all { it == ' ' })
        assertTrue(d.username.all { it == ' ' })
        assertNull(vm.uiState.value.itemToEdit)
    }

    @Test
    fun updatingWithoutEditSelectionDoesNothing() = runTest {
        vm.updateCredential("u", "p")
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.updatePassword(any(), any()) }
    }

    @Test
    fun updatePreservesMetadataAndPassesUtf8ThenClearsEdit() = runTest {
        val d = detail()
        coEvery { repo.getDecryptedCredential(1) } returns d
        vm.onEditClick(rows.value.first())
        advanceUntilIdle()
        var submitted: Credential? = null
        coEvery { repo.updatePassword(1, any()) } answers
            {
                val c = secondArg<Credential>()
                submitted = c.copy(username = c.username.copyOf(), password = c.password.copyOf())
            }
        vm.updateCredential("שלום", "🔐")
        advanceUntilIdle()
        val c = submitted!!
        assertEquals(d.platform, c.platformName)
        assertEquals(d.website, c.website)
        assertEquals(d.categoryId, c.categoryId)
        assertArrayEquals("שלום".toByteArray(), c.username)
        assertArrayEquals("🔐".toByteArray(), c.password)
        assertNull(vm.uiState.value.itemToEdit)
        assertTrue(d.password.all { it == ' ' })
    }

    @Test
    fun failedUpdateKeepsEditAndOriginalError() = runTest {
        val d = detail()
        coEvery { repo.getDecryptedCredential(1) } returns d
        coEvery { repo.updatePassword(any(), any()) } throws VaultError.UninitializedVault
        vm.onEditClick(rows.value.first())
        advanceUntilIdle()
        vm.updateCredential("u", "p")
        advanceUntilIdle()
        assertSame(d, vm.uiState.value.itemToEdit)
        assertSame(VaultError.UninitializedVault, vm.uiState.value.error)
    }

    @Test
    fun unknownUpdateErrorIsWrapped() = runTest {
        val d = detail()
        val e = Exception()
        coEvery { repo.getDecryptedCredential(1) } returns d
        coEvery { repo.updatePassword(any(), any()) } throws e
        vm.onEditClick(rows.value.first())
        advanceUntilIdle()
        vm.updateCredential("u", "p")
        advanceUntilIdle()
        assertSame(e, (vm.uiState.value.error as VaultError.GenericError).cause)
    }

    @Test
    fun clearErrorRetainsDeleteSelection() = runTest {
        coEvery { repo.deletePassword(any()) } throws VaultError.UninitializedVault
        vm.showDeleteConfirmation(rows.value.first())
        vm.confirmDelete()
        advanceUntilIdle()
        vm.clearError()
        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.itemToDelete)
    }

    @Test
    fun upstreamVaultErrorClearsListAndIsPreserved() = runTest {
        every { repo.passwords } returns flow { throw VaultError.UninitializedVault }
        vm = PasswordViewModel(repo, app)
        subscribe()
        assertTrue(vm.filteredPasswords.value.isEmpty())
        assertSame(VaultError.UninitializedVault, vm.uiState.value.error)
    }

    @Test
    fun upstreamUnknownErrorRetainsCause() = runTest {
        val e = Exception()
        every { repo.passwords } returns flow { throw e }
        vm = PasswordViewModel(repo, app)
        subscribe()
        val cause = (vm.uiState.value.error as VaultError.GenericError).cause!!
        assertTrue(cause === e || cause.cause === e)
    }

    @Test
    fun lateNonCooperativeDetailIsWipedInsteadOfReopeningSheet() = runTest {
        val d = detail()
        coEvery { repo.getDecryptedCredential(1) } coAnswers
            {
                try {
                    delay(1000)
                } catch (e: CancellationException) {}
                d
            }
        vm.onPasswordClick(rows.value.first())
        runCurrent()
        vm.dismissPasswordDetail()
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedItemDetail)
        assertTrue(d.password.all { it == ' ' })
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun dismissedPendingEditCannotReappear() = runTest {
        coEvery { repo.getDecryptedCredential(1) } coAnswers
            {
                delay(1000)
                detail()
            }
        vm.onEditClick(rows.value.first())
        runCurrent()
        vm.dismissEdit()
        advanceUntilIdle()
        assertNull(vm.uiState.value.itemToEdit)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun latestDetailRequestWinsWhenPreviousLookupIgnoresCancellation() = runTest {
        val stale = detail()
        val latest = detail().copy(id = 2, password = "latest".toCharArray())
        coEvery { repo.getDecryptedCredential(1) } coAnswers
            {
                try {
                    delay(1000)
                } catch (e: CancellationException) {}
                stale
            }
        coEvery { repo.getDecryptedCredential(2) } returns latest
        vm.onPasswordClick(rows.value[0])
        runCurrent()
        vm.onPasswordClick(rows.value[1])
        advanceUntilIdle()
        assertSame(latest, vm.uiState.value.selectedItemDetail)
        assertTrue(stale.password.all { it == ' ' })
    }

    @Test
    fun latestEditRequestWinsWhenPreviousLookupIgnoresCancellation() = runTest {
        val stale = detail()
        val latest = detail().copy(id = 2)
        coEvery { repo.getDecryptedCredential(1) } coAnswers
            {
                try {
                    delay(1000)
                } catch (e: CancellationException) {}
                stale
            }
        coEvery { repo.getDecryptedCredential(2) } returns latest
        vm.onEditClick(rows.value[0])
        runCurrent()
        vm.onEditClick(rows.value[1])
        advanceUntilIdle()
        assertSame(latest, vm.uiState.value.itemToEdit)
        assertTrue(stale.username.all { it == ' ' })
    }

    @Test
    fun staleLookupErrorDoesNotReplaceLatestSelectionWithError() = runTest {
        coEvery { repo.getDecryptedCredential(1) } coAnswers
            {
                try {
                    delay(1000)
                } catch (e: CancellationException) {}
                throw VaultError.UninitializedVault
            }
        val latest = detail().copy(id = 2)
        coEvery { repo.getDecryptedCredential(2) } returns latest
        vm.onPasswordClick(rows.value[0])
        runCurrent()
        vm.onPasswordClick(rows.value[1])
        advanceUntilIdle()
        assertSame(latest, vm.uiState.value.selectedItemDetail)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun finishingOldUpdateDoesNotDismissNewEditSelection() = runTest {
        val old = detail()
        val latest = detail().copy(id = 2)
        coEvery { repo.getDecryptedCredential(1) } returns old
        coEvery { repo.getDecryptedCredential(2) } returns latest
        coEvery { repo.updatePassword(1, any()) } coAnswers { delay(1000) }
        vm.onEditClick(rows.value[0])
        runCurrent()
        vm.updateCredential("edited", "updated")
        runCurrent()
        vm.onEditClick(rows.value[1])
        runCurrent()
        advanceUntilIdle()
        assertSame(latest, vm.uiState.value.itemToEdit)
    }

    @Test
    fun failedUpdateWipesSubmittedBytesEvenWhenRepositoryDoesNot() = runTest {
        val d = detail()
        coEvery { repo.getDecryptedCredential(1) } returns d
        val input = slot<Credential>()
        coEvery { repo.updatePassword(1, capture(input)) } throws VaultError.UninitializedVault
        vm.onEditClick(rows.value[0])
        runCurrent()
        vm.updateCredential("new-user", "new-pass")
        advanceUntilIdle()
        assertTrue(input.captured.username.all { it == 0.toByte() })
        assertTrue(input.captured.password.all { it == 0.toByte() })
        assertSame(d, vm.uiState.value.itemToEdit)
    }

    @Test
    fun cancelledDeleteDoesNotPublishUserFacingError() = runTest {
        coEvery { repo.deletePassword(1) } throws CancellationException("synthetic cancellation")
        vm.showDeleteConfirmation(rows.value[0])
        vm.confirmDelete()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.itemToDelete)
    }

    @Test
    fun cancelledUpdateDoesNotPublishUserFacingError() = runTest {
        coEvery { repo.getDecryptedCredential(1) } returns detail()
        coEvery { repo.updatePassword(any(), any()) } throws CancellationException()
        vm.onEditClick(rows.value[0])
        runCurrent()
        vm.updateCredential("u", "p")
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.itemToEdit)
    }

    @Test
    fun losingLastSubscriberStopsRepositoryCollectionAfterGracePeriod() = runTest {
        var started = 0
        var stopped = 0
        every { repo.passwords } returns
            flow {
                started++
                try {
                    emit(rows.value)
                    awaitCancellation()
                } finally {
                    stopped++
                }
            }
        vm = PasswordViewModel(repo, app)
        val collector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.filteredPasswords.collect()
            }
        runCurrent()
        assertEquals(1, started)
        collector.cancel()
        runCurrent()
        advanceTimeBy(4999)
        runCurrent()
        assertEquals(0, stopped)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, stopped)
    }

    @Test
    fun resubscribingReloadsLatestRepositorySnapshot() = runTest {
        var snapshots = rows.value
        every { repo.passwords } returns
            flow {
                emit(snapshots)
                awaitCancellation()
            }
        vm = PasswordViewModel(repo, app)
        val first =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.filteredPasswords.collect()
            }
        runCurrent()
        first.cancel()
        advanceTimeBy(5001)
        runCurrent()
        snapshots = listOf(PasswordItemModel(9, "Other", "u"))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.filteredPasswords.collect()
        }
        runCurrent()
        assertEquals(listOf(9), vm.filteredPasswords.value.map { it.id })
    }

    @Test
    fun searchMetacharactersAreTreatedLiterallyRatherThanAsRegex() = runTest {
        rows.value =
            listOf(
                PasswordItemModel(9, "Platform [a].*", "u"),
                PasswordItemModel(10, "Platform aaa", "u"),
            )
        subscribe()
        vm.onSearchQueryChanged("[a].*")
        runCurrent()
        assertEquals(listOf(9), vm.filteredPasswords.value.map { it.id })
    }

    @Test
    fun repeatedDismissalNeverClearsNewlySelectedDetail() = runTest {
        vm.dismissPasswordDetail()
        vm.dismissPasswordDetail()
        val d = detail()
        coEvery { repo.getDecryptedCredential(1) } returns d
        vm.onPasswordClick(rows.value[0])
        advanceUntilIdle()
        assertSame(d, vm.uiState.value.selectedItemDetail)
        assertEquals("pass", String(d.password))
    }

    @Test
    fun clearingViewModelWhileEditingWipesBothFields() = runTest {
        val d = detail()
        coEvery { repo.getDecryptedCredential(1) } returns d
        vm.onEditClick(rows.value[0])
        advanceUntilIdle()
        val store = androidx.lifecycle.ViewModelStore()
        store.put("edit", vm)
        store.clear()
        assertTrue(d.username.all { it == ' ' })
        assertTrue(d.password.all { it == ' ' })
    }
}
