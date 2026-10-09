package com.example.mypasswords.screens.password

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.example.core.data.PasswordRepository
import com.example.core.model.*
import com.example.mypasswords.testing.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@org.junit.runner.RunWith(org.junit.runners.BlockJUnit4ClassRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class KnownDefectsTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = mockk<PasswordRepository>(relaxed = true)
    private val app = mockk<Application>()

    @Before
    fun setup() {
        every { repo.getCategories() } returns emptyList()
        every { repo.passwords } returns MutableStateFlow(emptyList())
    }

    private fun add() =
        AddPasswordViewModel(repo, app).apply {
            onPlatformNameChanged("Google")
            onUsernameChanged("user")
            onPasswordChanged("pass")
        }

    private fun detail() =
        FullCredentialModel(1, "Google", "user".toCharArray(), "pass".toCharArray())

    @Test
    fun replacingUsernameWipesOldBuffer() {
        val vm = add()
        val old = vm.uiState.value.username
        vm.onUsernameChanged("new")
        assertTrue(old.all { it == ' ' || it == '\u0000' })
    }

    @Test
    fun replacingPasswordWipesOldBuffer() {
        val vm = add()
        val old = vm.uiState.value.password
        vm.onPasswordChanged("new")
        assertTrue(old.all { it == ' ' || it == '\u0000' })
    }

    @Test
    fun successfulSaveDisablesFurtherSaving() = runTest {
        val vm = add()
        vm.onSaveClick()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaveEnabled)
    }

    @Test
    fun secondSaveAfterSuccessDoesNotInsertEmptyCredential() = runTest {
        val vm = add()
        vm.onSaveClick()
        advanceUntilIdle()
        vm.onSaveClick()
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.addPassword(any()) }
    }

    @Test
    fun openingAnotherDetailWipesPreviousRecord() = runTest {
        val vm = PasswordViewModel(repo, app)
        val old = detail()
        val next = detail().copy(id = 2)
        coEvery { repo.getDecryptedCredential(1) } returns old
        coEvery { repo.getDecryptedCredential(2) } returns next
        vm.onPasswordClick(PasswordItemModel(1, "Google", "user"))
        advanceUntilIdle()
        vm.onPasswordClick(PasswordItemModel(2, "Other", "user"))
        advanceUntilIdle()
        assertTrue(old.password.all { it == ' ' })
    }

    @Test
    fun openingAnotherEditWipesPreviousRecord() = runTest {
        val vm = PasswordViewModel(repo, app)
        val old = detail()
        coEvery { repo.getDecryptedCredential(1) } returns old
        coEvery { repo.getDecryptedCredential(2) } returns detail().copy(id = 2)
        vm.onEditClick(PasswordItemModel(1, "Google", "user"))
        advanceUntilIdle()
        vm.onEditClick(PasswordItemModel(2, "Other", "user"))
        advanceUntilIdle()
        assertTrue(old.password.all { it == ' ' })
    }

    @Test
    fun clearingAddViewModelWipesForm() {
        val vm = add()
        val old = vm.uiState.value
        val store = ViewModelStore()
        store.put("form", vm)
        store.clear()
        assertTrue(old.username.all { it == ' ' || it == '\u0000' })
        assertTrue(old.password.all { it == ' ' || it == '\u0000' })
    }

    @Test
    fun clearingPasswordViewModelWipesDecryptedDetail() = runTest {
        val vm = PasswordViewModel(repo, app)
        val old = detail()
        coEvery { repo.getDecryptedCredential(1) } returns old
        vm.onPasswordClick(PasswordItemModel(1, "Google", "user"))
        advanceUntilIdle()
        val store = ViewModelStore()
        store.put("vault", vm)
        store.clear()
        assertTrue(old.password.all { it == ' ' })
    }

    @Test
    fun dismissedDetailCannotReappearFromPendingRequest() = runTest {
        val vm = PasswordViewModel(repo, app)
        coEvery { repo.getDecryptedCredential(1) } coAnswers
            {
                kotlinx.coroutines.delay(1000)
                detail()
            }
        vm.onPasswordClick(PasswordItemModel(1, "Google", "user"))
        runCurrent()
        vm.dismissPasswordDetail()
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedItemDetail)
    }
}
