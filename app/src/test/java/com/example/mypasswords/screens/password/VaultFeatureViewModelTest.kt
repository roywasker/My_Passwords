package com.example.mypasswords.screens.password

import android.app.Application
import com.example.core.data.PasswordRepository
import com.example.core.model.*
import com.example.core.util.*
import com.example.mypasswords.testing.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class VaultFeatureViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = mockk<PasswordRepository>()
    private val app = mockk<Application>()
    private var generation = 0L
    private lateinit var vm: PasswordViewModel
    private val rows =
        MutableStateFlow(
            listOf(
                PasswordItemModel(
                    1,
                    "Zoo",
                    "alice",
                    categoryId = 2,
                    tags = listOf("Work"),
                    isFavorite = true,
                    createdAt = 30,
                ),
                PasswordItemModel(
                    2,
                    "Bank",
                    "bob",
                    categoryId = 3,
                    tags = listOf("אישי"),
                    createdAt = 10,
                ),
            )
        )

    @Before
    fun setup() {
        every { repo.getCategories() } returns Category.entries
        every { app.getString(any()) } returns "synthetic resource"
        every { repo.passwords } returns rows
        every { repo.sessionGeneration() } answers { generation }
        vm = PasswordViewModel(repo, app)
    }

    private fun TestScope.subscribe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.filteredPasswords.collect()
        }
        runCurrent()
    }

    @Test
    fun favoriteTagsSortAndSearchAreAppliedTogether() = runTest {
        subscribe()
        assertEquals(listOf("Work", "אישי"), vm.uiState.value.availableTags)
        vm.selectSort(PasswordSort.NAME)
        runCurrent()
        assertEquals(listOf(2, 1), vm.filteredPasswords.value.map { it.id })
        vm.toggleFavoritesOnly()
        vm.selectTag("work")
        vm.onSearchQueryChanged("alice")
        runCurrent()
        assertEquals(listOf(1), vm.filteredPasswords.value.map { it.id })
        vm.toggleFavoritesOnly()
        vm.selectTag(null)
        vm.onSearchQueryChanged("")
        vm.selectSort(PasswordSort.OLDEST)
        runCurrent()
        assertEquals(listOf(2, 1), vm.filteredPasswords.value.map { it.id })
    }

    @Test
    fun healthRequestShowsLoadingAndPublishesOnlyItsCurrentResult() = runTest {
        val gate = CompletableDeferred<PasswordHealthReport>()
        coEvery { repo.healthReport() } coAnswers { gate.await() }
        vm.showHealthReport()
        runCurrent()
        assertTrue(vm.uiState.value.showHealth)
        assertTrue(vm.uiState.value.featureBusy)
        assertNull(vm.uiState.value.healthReport)
        val report =
            PasswordHealthReport(
                listOf(PasswordHealthItem(1, "Zoo", WeakReason.SHORT, false, false, false))
            )
        gate.complete(report)
        runCurrent()
        assertEquals(report, vm.uiState.value.healthReport)
        assertFalse(vm.uiState.value.featureBusy)
    }

    @Test
    fun lockedSessionCannotPublishLateHealthResult() = runTest {
        coEvery { repo.healthReport() } coAnswers
            {
                generation++
                PasswordHealthReport(emptyList())
            }
        vm.showHealthReport()
        runCurrent()
        assertNull(vm.uiState.value.healthReport)
    }

    @Test
    fun revokedSessionCannotPublishALateFeatureError() = runTest {
        coEvery { repo.healthReport() } coAnswers
            {
                generation++
                throw VaultError.UninitializedVault
            }
        vm.showHealthReport()
        runCurrent()
        assertNull(vm.uiState.value.featureError)
    }

    @Test
    fun dismissedHealthJobCannotReopenOrOverwriteANewerTrashSheet() = runTest {
        val gate = CompletableDeferred<PasswordHealthReport>()
        coEvery { repo.healthReport() } coAnswers { withContext(NonCancellable) { gate.await() } }
        coEvery { repo.trashItems() } returns listOf(rows.value.first())
        vm.showHealthReport()
        runCurrent()
        vm.showTrash()
        runCurrent()
        gate.complete(PasswordHealthReport(emptyList()))
        runCurrent()
        assertFalse(vm.uiState.value.showHealth)
        assertNull(vm.uiState.value.healthReport)
        assertTrue(vm.uiState.value.showTrash)
        assertEquals(1, vm.uiState.value.trashItems.size)
    }

    @Test
    fun restoringTrashRefreshesTheSheetAndPreventsDuplicateSubmissions() = runTest {
        coEvery { repo.trashItems() } returns listOf(rows.value.first()) andThen emptyList()
        val gate = CompletableDeferred<Unit>()
        coEvery { repo.restoreTrash(1) } coAnswers { gate.await() }
        vm.showTrash()
        runCurrent()
        vm.restoreTrash(1)
        runCurrent()
        vm.restoreTrash(1)
        runCurrent()
        coVerify(exactly = 1) { repo.restoreTrash(1) }
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.uiState.value.trashItems.isEmpty())
    }

    @Test
    fun failedPermanentDeletionKeepsItemAndDisplaysFailure() = runTest {
        coEvery { repo.trashItems() } returns listOf(rows.value.first())
        coEvery { repo.permanentlyDelete(1) } throws VaultError.UninitializedVault
        vm.showTrash()
        runCurrent()
        vm.permanentlyDelete(1)
        runCurrent()
        assertEquals(1, vm.uiState.value.trashItems.size)
        assertSame(VaultError.UninitializedVault, vm.uiState.value.featureError)
        assertFalse(vm.uiState.value.featureBusy)
    }

    @Test
    fun historyRestorationTargetsCorrectCredentialAndRefreshesVersions() = runTest {
        val entry = PasswordHistoryItem(7, 1, 100, 50)
        coEvery { repo.history(1) } returns listOf(entry)
        coEvery { repo.restoreHistory(1, 7) } just Runs
        vm.showHistory(1, "Zoo")
        runCurrent()
        assertEquals(listOf(entry), vm.uiState.value.historyItems)
        vm.restoreHistory(7)
        runCurrent()
        coVerify(exactly = 1) { repo.restoreHistory(1, 7) }
        coVerify(exactly = 2) { repo.history(1) }
    }

    @Test
    fun editingTagsCopiesOnlyMetadataAndClearsDecryptedDetails() = runTest {
        val detail =
            FullCredentialModel(
                1,
                "Zoo",
                "user".toCharArray(),
                "secret".toCharArray(),
                tags = listOf("Work"),
            )
        coEvery { repo.getDecryptedCredential(1) } returns detail
        coEvery { repo.setTags(1, "New") } just Runs
        vm.onPasswordClick(rows.value.first())
        runCurrent()
        vm.editTags(detail)
        assertTrue(detail.password.all { it == ' ' })
        assertTrue(vm.uiState.value.tagsItem!!.password.isEmpty())
        assertEquals(listOf("Work"), vm.uiState.value.tagsItem!!.tags)
        vm.saveTags("New")
        runCurrent()
        assertNull(vm.uiState.value.tagsItem)
        coVerify { repo.setTags(1, "New") }
    }

    @Test
    fun favoriteToggleUpdatesOpenDetailsAfterPersistenceSucceeds() = runTest {
        val detail = FullCredentialModel(1, "Zoo", "user".toCharArray(), "secret".toCharArray())
        coEvery { repo.getDecryptedCredential(1) } returns detail
        coEvery { repo.setFavorite(1, true) } just Runs
        vm.onPasswordClick(rows.value.first())
        runCurrent()
        vm.toggleFavorite(1, true)
        runCurrent()
        assertTrue(vm.uiState.value.selectedItemDetail!!.isFavorite)
    }
}
