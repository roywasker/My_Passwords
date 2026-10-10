package com.example.mypasswords.ui

import android.app.Application
import androidx.compose.ui.test.*
import com.example.core.model.*
import com.example.mypasswords.R
import com.example.mypasswords.screens.finger.*
import com.example.mypasswords.screens.password.*
import com.example.mypasswords.screens.welcome.WelcomeScreen
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ScreenBehaviorTest {
    @get:Rule
    val compose =
        androidx.compose.ui.test.junit4.v2.createAndroidComposeRule<
            androidx.activity.ComponentActivity
        >()

    @Test
    fun passwordFieldTellsInputMethodItContainsPasswordWithoutAutocorrect() {
        val vm = addVm(AddPasswordUiState())
        compose.setContent { AddPasswordScreen(vm, {}) }
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performClick()
        compose.runOnIdle {
            val info = android.view.inputmethod.EditorInfo()
            val focused = compose.activity.window.decorView.findFocus()
            assertNotNull(focused)
            assertNotNull(focused.onCreateInputConnection(info))
            assertEquals(
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                info.inputType and android.text.InputType.TYPE_MASK_VARIATION,
            )
            assertEquals(0, info.inputType and android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        }
    }

    @Test
    fun failedListShowsActionableRetryEvenAfterSnackbarErrorIsCleared() {
        val vm = mockk<PasswordViewModel>(relaxed = true)
        every { vm.uiState } returns
            MutableStateFlow(
                PasswordUiState(
                    listStatus = PasswordListStatus.Failed(VaultError.UninitializedVault)
                )
            )
        every { vm.filteredPasswords } returns MutableStateFlow(emptyList())
        compose.setContent { PasswordScreen(vm, {}, {}) }
        compose.onNodeWithText(text(R.string.retry_loading)).assertIsDisplayed().performClick()
        verify(exactly = 1) { vm.retryLoadingPasswords() }
    }

    private fun listScreen(status: PasswordListStatus) {
        val vm = mockk<PasswordViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(PasswordUiState(listStatus = status))
        every { vm.filteredPasswords } returns MutableStateFlow(emptyList())
        compose.setContent { PasswordScreen(vm, {}, {}) }
    }

    @Test
    fun loadingListDoesNotMisleadUserWithEmptyVaultMessage() {
        listScreen(PasswordListStatus.Loading)
        compose.onNodeWithText(text(R.string.passwords_loading)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.passwords_empty)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.retry_loading)).assertDoesNotExist()
    }

    @Test
    fun emptyVaultHasDifferentMessageFromSearchWithNoMatches() {
        listScreen(PasswordListStatus.Empty)
        compose.onNodeWithText(text(R.string.passwords_empty)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.passwords_no_results)).assertDoesNotExist()
    }

    @Test
    fun filteredEmptyListDisplaysNoResultsRatherThanEmptyVault() {
        listScreen(PasswordListStatus.Ready)
        compose.onNodeWithText(text(R.string.passwords_no_results)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.passwords_empty)).assertDoesNotExist()
    }

    @Test
    fun failedListShowsItsErrorWithoutClaimingVaultIsEmpty() {
        listScreen(PasswordListStatus.Failed(VaultError.DatabaseError(IllegalStateException())))
        compose.onNodeWithText(text(R.string.error_database_access)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.passwords_empty)).assertDoesNotExist()
    }

    @Test
    fun recoverySetupRequiresExplicitGenerationBeforeDisplayingCode() {
        var generate = 0
        compose.setContent { RecoveryCodeDialog(null, false, { generate++ }, {}) }
        compose.onNodeWithText(text(R.string.recovery_generate)).performClick()
        compose.runOnIdle { assertEquals(1, generate) }
    }

    @Test
    fun busyRecoverySetupCannotSubmitTwice() {
        compose.setContent { RecoveryCodeDialog(null, true, { fail("Duplicate generation") }, {}) }
        compose.onNodeWithText(text(R.string.recovery_generate)).assertIsNotEnabled()
    }

    @Test
    fun existingRemoteIconUrlUsesLocalLetterInsteadOfNetworkImage() {
        compose.setContent {
            PasswordCard(
                PasswordItemModel(1, "Bank", "user", "https://example.invalid/track"),
                {},
                {},
                {},
            )
        }
        compose.onNodeWithText("B").assertExists()
        compose.onNodeWithContentDescription("Bank").assertDoesNotExist()
    }

    private fun text(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun welcomeButtonStartsAuthentication() {
        var calls = 0
        compose.setContent { WelcomeScreen { calls++ } }
        compose.onNodeWithText(text(R.string.welcome_screen_button_text)).performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun compactWelcomeKeepsAuthenticationActionVisibleWithoutScrolling() {
        var clicks = 0
        compose.setContent { WelcomeScreen { clicks++ } }
        compose
            .onNodeWithText(text(R.string.welcome_screen_button_text))
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun welcomeShowsLocalizedTitleAndSecurityDescription() {
        compose.setContent { WelcomeScreen {} }
        compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
        compose.onNodeWithText(text(R.string.welcome_screen_subtitle)).assertExists()
    }

    @Test
    fun cardShowsMaskedUsernameWithoutPlaintext() {
        compose.setContent {
            PasswordCard(PasswordItemModel(1, "Google", "user@example.com"), {}, {}, {})
        }
        compose.onNodeWithText("us****om").assertExists()
        compose.onNodeWithText("user@example.com").assertDoesNotExist()
    }

    @Test
    fun cardDeleteDoesNotOpenDetailOrEdit() {
        var d = 0
        var e = 0
        var open = 0
        compose.setContent {
            PasswordCard(PasswordItemModel(1, "Google", "user"), { e++ }, { d++ }, { open++ })
        }
        compose.onNodeWithContentDescription(text(R.string.delete_btn)).performClick()
        compose.runOnIdle {
            assertEquals(1, d)
            assertEquals(0, e)
            assertEquals(0, open)
        }
    }

    @Test
    fun cardEditDoesNotDeleteOrOpenDetail() {
        var d = 0
        var e = 0
        var open = 0
        compose.setContent {
            PasswordCard(PasswordItemModel(1, "Google", "user"), { e++ }, { d++ }, { open++ })
        }
        compose.onNodeWithContentDescription(text(R.string.edit_btn)).performClick()
        compose.runOnIdle {
            assertEquals(0, d)
            assertEquals(1, e)
            assertEquals(0, open)
        }
    }

    @Test
    fun cardClickOpensDetailOnly() {
        var d = 0
        var e = 0
        var open = 0
        compose.setContent {
            PasswordCard(PasswordItemModel(1, "Google", "user"), { e++ }, { d++ }, { open++ })
        }
        compose.onNodeWithText("Google").performClick()
        compose.runOnIdle {
            assertEquals(0, d)
            assertEquals(0, e)
            assertEquals(1, open)
        }
    }

    @Test
    fun cardWithoutWebsiteUsesFallbackLetter() {
        compose.setContent { PasswordCard(PasswordItemModel(1, " google ", ""), {}, {}, {}) }
        compose.onNodeWithText("G").assertExists()
        compose.onNodeWithText("***").assertExists()
    }

    @Test
    fun deleteConfirmationIncludesPlatformAndOnlyDeletesAfterConfirm() {
        var d = 0
        var cancel = 0
        compose.setContent { DeleteConfirmationDialog("Google", { d++ }, { cancel++ }) }
        compose.onNodeWithText("Google", substring = true).assertExists()
        compose.runOnIdle { assertEquals(0, d) }
        compose.onNodeWithText(text(R.string.delete_dialog_confirm)).performClick()
        compose.runOnIdle {
            assertEquals(1, d)
            assertEquals(0, cancel)
        }
    }

    @Test
    fun deleteCancelDoesNotConfirm() {
        var d = 0
        var cancel = 0
        compose.setContent { DeleteConfirmationDialog("Google", { d++ }, { cancel++ }) }
        compose.onNodeWithText(text(R.string.delete_dialog_cancel)).performClick()
        compose.runOnIdle {
            assertEquals(0, d)
            assertEquals(1, cancel)
        }
    }

    private fun addVm(state: AddPasswordUiState): AddPasswordViewModel {
        val vm = mockk<AddPasswordViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(state)
        return vm
    }

    @Test
    fun invalidAddFormDisablesSave() {
        val vm = addVm(AddPasswordUiState())
        compose.setContent { AddPasswordScreen(vm, {}) }
        compose.onNodeWithText(text(R.string.save_now_btn)).assertIsNotEnabled()
    }

    @Test
    fun validAddFormForwardsSaveAction() {
        val vm = addVm(AddPasswordUiState(isSaveEnabled = true))
        compose.setContent { AddPasswordScreen(vm, {}) }
        compose.onNodeWithText(text(R.string.save_now_btn)).assertIsEnabled().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onSaveClick() } }
    }

    @Test
    fun savedAddFormNavigatesBack() {
        var back = 0
        val vm = addVm(AddPasswordUiState(isSaved = true))
        compose.setContent { AddPasswordScreen(vm, { back++ }) }
        compose.runOnIdle { assertEquals(1, back) }
    }

    @Test
    fun addFormForwardsGeneratorAction() {
        val vm = addVm(AddPasswordUiState())
        compose.setContent { AddPasswordScreen(vm, {}) }
        compose
            .onNodeWithContentDescription(text(com.example.compose_ui.R.string.credential_generate))
            .performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onGeneratePasswordClicked() } }
    }

    @Test
    fun addFormForwardsTextChangesToCorrectHandlers() {
        val vm = addVm(AddPasswordUiState())
        compose.setContent { AddPasswordScreen(vm, {}) }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Google")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("user")
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("pass")
        compose.runOnIdle {
            verify {
                vm.onPlatformNameChanged("Google")
                vm.onUsernameChanged("user")
                vm.onPasswordChanged("pass")
            }
        }
    }

    @Test
    fun passwordScreenAddButtonForwardsNavigation() {
        var add = 0
        val vm = mockk<PasswordViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(PasswordUiState())
        every { vm.filteredPasswords } returns MutableStateFlow(emptyList())
        compose.setContent { PasswordScreen(vm, {}, { add++ }) }
        compose.onNodeWithContentDescription(text(R.string.add_password_btn)).performClick()
        compose.runOnIdle { assertEquals(1, add) }
    }

    @Test
    fun passwordScreenShowsFilteredRowsOnly() {
        val vm = mockk<PasswordViewModel>(relaxed = true)
        every { vm.uiState } returns
            MutableStateFlow(PasswordUiState(listStatus = PasswordListStatus.Ready))
        every { vm.filteredPasswords } returns
            MutableStateFlow(listOf(PasswordItemModel(1, "Google", "user")))
        compose.setContent { PasswordScreen(vm, {}, {}) }
        compose.onNodeWithText("Google").assertExists()
        compose.onNodeWithText("Netflix").assertDoesNotExist()
    }

    @Test
    fun fingerprintSuccessCallsSuccessAndResetsState() {
        var success = 0
        var cancel = 0
        val vm = mockk<FingerPrintViewModel>(relaxed = true)
        every { vm.authState } returns MutableStateFlow(AuthState.Success)
        compose.setContent { FingerPrintAuthScreen(vm, { success++ }, { cancel++ }) }
        compose.runOnIdle {
            assertEquals(1, success)
            assertEquals(0, cancel)
            verify(exactly = 1) { vm.resetState() }
        }
    }

    @Test
    fun nonfatalAuthenticationErrorCancelsAndResets() {
        var success = 0
        var cancel = 0
        val vm = mockk<FingerPrintViewModel>(relaxed = true)
        every { vm.authState } returns
            MutableStateFlow(AuthState.Error(VaultError.BiometricAuthFailed))
        compose.setContent { FingerPrintAuthScreen(vm, { success++ }, { cancel++ }) }
        compose.runOnIdle {
            assertEquals(0, success)
            assertEquals(1, cancel)
            verify(exactly = 1) { vm.resetState() }
        }
    }

    @Test
    fun fatalAuthenticationErrorRemainsOnScreen() {
        var cancel = 0
        val vm = mockk<FingerPrintViewModel>(relaxed = true)
        every { vm.authState } returns
            MutableStateFlow(AuthState.Error(VaultError.KeyInvalidated, true))
        compose.setContent { FingerPrintAuthScreen(vm, { fail() }, { cancel++ }) }
        compose.runOnIdle {
            assertEquals(0, cancel)
            verify(exactly = 0) { vm.resetState() }
        }
    }

    @Test
    fun addFormDisplaysLocalizedVaultError() {
        val vm = addVm(AddPasswordUiState(error = VaultError.UninitializedVault))
        compose.setContent { AddPasswordScreen(vm, {}) }
        compose.onNodeWithText(text(R.string.error_uninitialized_vault)).assertExists()
    }

    @Test
    fun passwordScreenDisplaysLocalizedVaultError() {
        val vm = mockk<PasswordViewModel>(relaxed = true)
        every { vm.uiState } returns
            MutableStateFlow(PasswordUiState(error = VaultError.UninitializedVault))
        every { vm.filteredPasswords } returns MutableStateFlow(emptyList())
        compose.setContent { PasswordScreen(vm, {}, {}) }
        compose.onNodeWithText(text(R.string.error_uninitialized_vault)).assertExists()
    }

    @Config(qualifiers = "w320dp-h470dp")
    @Test
    fun knownWelcomeAuthenticationButtonWorksOnCompactDisplay() {
        var clicks = 0
        compose.setContent { WelcomeScreen { clicks++ } }
        compose
            .onNodeWithText(text(R.string.welcome_screen_button_text))
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Config(qualifiers = "w320dp-h470dp")
    @Test
    fun knownSaveButtonWorksOnCompactDisplay() {
        val vm = addVm(AddPasswordUiState(isSaveEnabled = true))
        compose.setContent { AddPasswordScreen(vm, {}) }
        compose.onNodeWithText(text(R.string.save_now_btn)).assertIsDisplayed().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onSaveClick() } }
    }

    @Test
    fun largeFontWelcomeActionIsVisibleWithoutScrolling() {
        var clicked = 0
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(1f, 2f)
            ) {
                WelcomeScreen { clicked++ }
            }
        }
        compose
            .onNodeWithText(text(R.string.welcome_screen_button_text))
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertEquals(1, clicked) }
    }

    @Test
    fun largeFontAddFormSaveActionIsVisibleWithoutScrolling() {
        val vm = addVm(AddPasswordUiState(isSaveEnabled = true))
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(1f, 2f)
            ) {
                AddPasswordScreen(vm, {})
            }
        }
        compose.onNodeWithText(text(R.string.save_now_btn)).assertIsDisplayed().performClick()
        compose.runOnIdle { verify(exactly = 1) { vm.onSaveClick() } }
    }

    @Test
    fun failedPlatformImageDisplaysLetterFallbackWithoutNetwork() {
        compose.setContent {
            PasswordCard(
                PasswordItemModel(
                    1,
                    "Synthetic service",
                    "user",
                    website = "synthetic-unsupported://image",
                ),
                {},
                {},
                {},
            )
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("S").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("S").assertExists()
        compose.onNodeWithText("Synthetic service").assertExists()
    }
}
