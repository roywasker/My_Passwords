package com.example.mypasswords.screens.password

import android.app.Application
import com.example.core.data.PasswordRepository
import com.example.core.model.*
import com.example.mypasswords.testing.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AddPasswordViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = mockk<PasswordRepository>()
    private val app = mockk<Application>()
    private lateinit var vm: AddPasswordViewModel

    @Before
    fun setup() {
        every { repo.getCategories() } returns Category.entries
        every { app.getString(any()) } answers { "category-${firstArg<Int>()}" }
        coEvery { repo.addPassword(any()) } just Runs
        vm = AddPasswordViewModel(repo, app)
    }

    private fun fill(
        platform: String = "Google",
        username: String = "user",
        password: String = "pass",
    ) {
        vm.onPlatformNameChanged(platform)
        vm.onUsernameChanged(username)
        vm.onPasswordChanged(password)
    }

    @Test
    fun startsWithEmptyDisabledUnsavedForm() {
        val s = vm.uiState.value
        assertFalse(s.isSaveEnabled)
        assertFalse(s.isSaved)
        assertEquals("", s.platformName)
        assertTrue(s.username.isEmpty())
        assertTrue(s.password.isEmpty())
        assertNull(s.error)
        assertEquals(1, s.selectedCategoryId)
    }

    @Test
    fun loadsEveryLocalizedCategoryInOrder() {
        assertEquals(
            Category.entries.map { CategoryChip(it.id, "category-${it.nameResId}") },
            vm.uiState.value.categories,
        )
    }

    @Test
    fun platformAloneIsInsufficient() {
        vm.onPlatformNameChanged("Google")
        assertFalse(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun usernameAloneIsInsufficient() {
        vm.onUsernameChanged("user")
        assertFalse(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun passwordAloneIsInsufficient() {
        vm.onPasswordChanged("pass")
        assertFalse(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun allThreeFieldsEnableSave() {
        fill()
        assertTrue(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun blankPlatformDisablesSave() {
        listOf("", " ", "\t\n").forEach {
            fill(platform = it)
            assertFalse(vm.uiState.value.isSaveEnabled)
        }
    }

    @Test
    fun emptyUsernameDisablesSave() {
        fill(username = "")
        assertFalse(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun emptyPasswordDisablesSave() {
        fill(password = "")
        assertFalse(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun clearingEachPreviouslyValidFieldDisablesSave() {
        fill()
        vm.onPlatformNameChanged("")
        assertFalse(vm.uiState.value.isSaveEnabled)
        fill()
        vm.onUsernameChanged("")
        assertFalse(vm.uiState.value.isSaveEnabled)
        fill()
        vm.onPasswordChanged("")
        assertFalse(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun unicodeCredentialsAreRetainedExactly() {
        fill("平台", "משתמש", "🔐")
        assertEquals("משתמש", String(vm.uiState.value.username))
        assertEquals("🔐", String(vm.uiState.value.password))
        assertTrue(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun whitespaceUsernameAndPasswordAreCurrentlyAccepted() {
        fill(username = " ", password = " ")
        assertTrue(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun selectingCategoryPreservesInput() {
        fill()
        vm.onCategorySelected(4)
        assertEquals(4, vm.uiState.value.selectedCategoryId)
        assertEquals("user", String(vm.uiState.value.username))
    }

    @Test
    fun invalidFormNeverCallsRepository() = runTest {
        vm.onSaveClick()
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.addPassword(any()) }
    }

    @Test
    fun successfulSavePassesUtf8AndSelectedCategory() = runTest {
        fill("平台", "שלום", "🔐")
        vm.onCategorySelected(3)
        var snapshot: Credential? = null
        coEvery { repo.addPassword(any()) } answers
            {
                val c = firstArg<Credential>()
                snapshot = c.copy(username = c.username.copyOf(), password = c.password.copyOf())
            }
        vm.onSaveClick()
        advanceUntilIdle()
        val c = snapshot!!
        assertEquals("平台", c.platformName)
        assertArrayEquals("שלום".toByteArray(), c.username)
        assertArrayEquals("🔐".toByteArray(), c.password)
        assertEquals(3, c.categoryId)
        assertEquals("", c.website)
    }

    @Test
    fun successfulSaveWipesSubmittedBytesAndOldUiArrays() = runTest {
        fill()
        val old = vm.uiState.value
        val slot = slot<Credential>()
        coEvery { repo.addPassword(capture(slot)) } just Runs
        vm.onSaveClick()
        advanceUntilIdle()
        assertTrue(slot.captured.password.all { it == 0.toByte() })
        assertTrue(slot.captured.username.all { it == 0.toByte() })
        assertTrue(old.username.all { it == ' ' })
        assertTrue(old.password.all { it == ' ' })
        assertTrue(vm.uiState.value.username.isEmpty())
        assertTrue(vm.uiState.value.password.isEmpty())
        assertTrue(vm.uiState.value.isSaved)
        assertNull(vm.uiState.value.error)
    }

    private suspend fun TestScope.savedWebsite(platform: String): String {
        fill(platform)
        var website = "unset"
        coEvery { repo.addPassword(any()) } answers { website = firstArg<Credential>().website }
        vm.onSaveClick()
        advanceUntilIdle()
        return website
    }

    @Test
    fun tagsAreNormalizedAndSavedWithoutChangingCredentials() = runTest {
        fill("Synthetic account")
        vm.onTagsChanged(" Work, work, אישי ")
        var saved: Credential? = null
        coEvery { repo.addPassword(any()) } answers { saved = firstArg() }
        vm.onSaveClick()
        advanceUntilIdle()
        assertEquals(listOf("Work", "אישי"), saved!!.tags)
        assertTrue(saved!!.password.all { it == 0.toByte() })
    }

    @Test
    fun invalidTagsStayInvalidAfterOtherFieldChangesAndCanBeCorrected() {
        fill("Synthetic account")
        vm.onTagsChanged("x".repeat(33))
        assertFalse(vm.uiState.value.isSaveEnabled)
        vm.onUsernameChanged("another user")
        assertFalse(vm.uiState.value.isSaveEnabled)
        vm.onTagsChanged("work")
        assertTrue(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun platformNameDoesNotCreateTrackingUrl() = runTest {
        assertEquals(
            "",
            savedWebsite(" Go og!le "),
        )
    }

    @Test fun shortDomainOmitsFavicon() = runTest { assertEquals("", savedWebsite("AB")) }

    @Test
    fun threeCharacterPlatformDoesNotCreateTrackingUrl() = runTest {
        assertEquals(
            "",
            savedWebsite("ABC"),
        )
    }

    @Test
    fun numericPlatformDoesNotCreateTrackingUrl() = runTest {
        assertEquals(
            "",
            savedWebsite("123"),
        )
    }

    @Test
    fun generatedPasswordHasDefaultStrengthAndEnablesValidForm() {
        vm.onPlatformNameChanged("Google")
        vm.onUsernameChanged("user")
        vm.onGeneratePasswordClicked()
        val p = String(vm.uiState.value.password)
        assertEquals(16, p.length)
        assertTrue(p.any { it.isUpperCase() })
        assertTrue(p.any { it.isLowerCase() })
        assertTrue(p.any { it.isDigit() })
        assertTrue(p.any { !it.isLetterOrDigit() })
        assertTrue(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun generationDoesNotEnableMissingUsername() {
        vm.onPlatformNameChanged("Google")
        vm.onGeneratePasswordClicked()
        assertFalse(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun knownSaveErrorIsPreservedAndFormRemainsAvailable() = runTest {
        fill()
        val e = VaultError.UninitializedVault
        coEvery { repo.addPassword(any()) } throws e
        vm.onSaveClick()
        advanceUntilIdle()
        assertSame(e, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaved)
        assertEquals("pass", String(vm.uiState.value.password))
    }

    @Test
    fun unknownSaveErrorIsWrappedWithOriginalCause() = runTest {
        fill()
        val e = Exception()
        coEvery { repo.addPassword(any()) } throws e
        vm.onSaveClick()
        advanceUntilIdle()
        assertSame(e, (vm.uiState.value.error as VaultError.GenericError).cause)
    }

    @Test
    fun clearErrorDoesNotDiscardInput() = runTest {
        fill()
        coEvery { repo.addPassword(any()) } throws VaultError.UninitializedVault
        vm.onSaveClick()
        advanceUntilIdle()
        vm.clearError()
        assertNull(vm.uiState.value.error)
        assertEquals("user", String(vm.uiState.value.username))
    }

    @Test
    fun retryCanSucceedAfterFailure() = runTest {
        fill()
        coEvery { repo.addPassword(any()) } throws VaultError.UninitializedVault
        vm.onSaveClick()
        advanceUntilIdle()
        coEvery { repo.addPassword(any()) } just Runs
        vm.onSaveClick()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSaved)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun uiStateEqualityAndHashUseArrayContent() {
        val a = AddPasswordUiState(username = "u".toCharArray(), password = "p".toCharArray())
        val b = AddPasswordUiState(username = "u".toCharArray(), password = "p".toCharArray())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, a.copy(password = charArrayOf('x')))
        assertNotEquals(a, a.copy(username = charArrayOf('x')))
    }

    @Test
    fun uiStateEqualityIncludesEveryNonSensitiveField() {
        val a = AddPasswordUiState()
        listOf(
                a.copy(selectedCategoryId = 2),
                a.copy(isSaveEnabled = true),
                a.copy(isSaved = true),
                a.copy(error = VaultError.UninitializedVault),
                a.copy(categories = listOf(CategoryChip(1, "All"))),
                a.copy(platformName = "p"),
            )
            .forEach { assertNotEquals(a, it) }
        assertFalse(a.equals(null))
        assertFalse(a.equals("other"))
    }

    @Test
    fun assigningIdenticalSensitiveInputDoesNotWipeActiveBuffer() {
        fill()
        val username = vm.uiState.value.username
        val password = vm.uiState.value.password
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("pass")
        assertEquals("user", String(vm.uiState.value.username))
        assertEquals("pass", String(vm.uiState.value.password))
        assertSame(username, vm.uiState.value.username)
        assertSame(password, vm.uiState.value.password)
    }

    @Test
    fun pendingSavePreventsDuplicateSubmissionAndInputMutation() = runTest {
        fill()
        coEvery { repo.addPassword(any()) } coAnswers { kotlinx.coroutines.delay(1000) }
        vm.onSaveClick()
        assertTrue(vm.uiState.value.isSaving)
        assertFalse(vm.uiState.value.isSaveEnabled)
        vm.onSaveClick()
        vm.onUsernameChanged("new user")
        vm.onPasswordChanged("new password")
        assertEquals("user", String(vm.uiState.value.username))
        assertEquals("pass", String(vm.uiState.value.password))
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.addPassword(any()) }
        assertFalse(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun failedSaveWipesSubmittedByteArraysAndAllowsRetry() = runTest {
        fill()
        val submitted = slot<Credential>()
        coEvery { repo.addPassword(capture(submitted)) } throws VaultError.UninitializedVault
        vm.onSaveClick()
        advanceUntilIdle()
        assertTrue(submitted.captured.username.all { it == 0.toByte() })
        assertTrue(submitted.captured.password.all { it == 0.toByte() })
        assertFalse(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun savingStateParticipatesInEquality() {
        assertNotEquals(AddPasswordUiState(), AddPasswordUiState(isSaving = true))
    }

    @Test
    fun savedFormCannotBeReenabledByChangingFields() = runTest {
        fill()
        vm.onSaveClick()
        advanceUntilIdle()
        vm.onPlatformNameChanged("Other")
        vm.onUsernameChanged("new")
        vm.onPasswordChanged("new")
        assertFalse(vm.uiState.value.isSaveEnabled)
        assertTrue(vm.uiState.value.username.isEmpty())
        assertTrue(vm.uiState.value.password.isEmpty())
    }

    @Test
    fun generationWhileSavingDoesNotReplaceSubmittedPassword() = runTest {
        fill()
        coEvery { repo.addPassword(any()) } coAnswers { kotlinx.coroutines.delay(1000) }
        vm.onSaveClick()
        vm.onGeneratePasswordClicked()
        assertEquals("pass", String(vm.uiState.value.password))
        advanceUntilIdle()
    }

    @Test
    fun cancelledSaveNeverBecomesGenericUserError() = runTest {
        fill()
        coEvery { repo.addPassword(any()) } throws kotlinx.coroutines.CancellationException()
        vm.onSaveClick()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaved)
    }

    @Test
    fun clearingViewModelBeforeSaveStartsPreventsRepositoryCall() = runTest {
        fill()
        val state = vm.uiState.value
        vm.onSaveClick()
        val store = androidx.lifecycle.ViewModelStore()
        store.put("form", vm)
        store.clear()
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.addPassword(any()) }
        assertTrue(state.password.all { it == ' ' })
    }

    @Test
    fun clearingViewModelDuringSaveWipesCapturedBytesAndForm() = runTest {
        fill()
        val submitted = slot<Credential>()
        coEvery { repo.addPassword(capture(submitted)) } coAnswers
            {
                kotlinx.coroutines.awaitCancellation()
            }
        vm.onSaveClick()
        runCurrent()
        val state = vm.uiState.value
        val store = androidx.lifecycle.ViewModelStore()
        store.put("form", vm)
        store.clear()
        runCurrent()
        assertTrue(submitted.captured.password.all { it == 0.toByte() })
        assertTrue(submitted.captured.username.all { it == 0.toByte() })
        assertTrue(state.password.all { it == ' ' })
    }

    @Test
    fun clearingErrorDuringPendingSaveDoesNotReenableSave() = runTest {
        fill()
        coEvery { repo.addPassword(any()) } coAnswers { kotlinx.coroutines.delay(1000) }
        vm.onSaveClick()
        vm.clearError()
        assertTrue(vm.uiState.value.isSaving)
        assertFalse(vm.uiState.value.isSaveEnabled)
        advanceUntilIdle()
    }

    @Test
    fun repeatedGenerationOverwritesPreviousSensitiveBuffer() {
        vm.onGeneratePasswordClicked()
        val previous = vm.uiState.value.password
        vm.onGeneratePasswordClicked()
        assertTrue(previous.all { it == ' ' })
        assertEquals(16, vm.uiState.value.password.size)
    }
}
