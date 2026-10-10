package com.example.mypasswords.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.core.model.*
import com.example.core.util.*
import com.example.mypasswords.R
import com.example.mypasswords.screens.password.*
import com.example.mypasswords.ui.theme.MyPasswordsTheme
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class VaultFeatureUiTest {
    @get:Rule val compose = createComposeRule()
    private val vm = mockk<PasswordViewModel>(relaxed = true)

    private fun text(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun panels(state: PasswordUiState) {
        compose.setContent { MyPasswordsTheme { VaultFeaturePanels(state, vm) } }
    }

    @Test
    fun healthPanelShowsLocalAssessmentAndReusedPasswordWarning() {
        panels(
            PasswordUiState(
                showHealth = true,
                healthReport =
                    PasswordHealthReport(
                        listOf(
                            PasswordHealthItem(
                                1,
                                "Synthetic account",
                                WeakReason.SHORT,
                                true,
                                false,
                                false,
                            )
                        )
                    ),
            )
        )
        compose.onNodeWithText("Synthetic account").assertExists()
        compose.onNodeWithText(text(R.string.health_reused)).assertExists()
        compose.onNodeWithText(text(R.string.health_explanation)).assertExists()
    }

    @Test
    fun failedHealthReportOffersRetryWithoutShowingCleanReport() {
        panels(PasswordUiState(showHealth = true, featureError = VaultError.UninitializedVault))
        compose.onNodeWithText(text(R.string.error_uninitialized_vault)).assertExists()
        compose.onNodeWithText(text(R.string.health_no_flags)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.retry_loading)).performClick()
        verify(exactly = 1) { vm.showHealthReport() }
    }

    @Test
    fun trashRestoreTargetsTheSelectedRecord() {
        panels(
            PasswordUiState(
                showTrash = true,
                trashItems = listOf(PasswordItemModel(7, "Synthetic account", "user")),
            )
        )
        compose.onNodeWithText(text(R.string.trash_restore)).performClick()
        verify(exactly = 1) { vm.restoreTrash(7) }
    }

    @Test
    fun permanentDeletionRequiresConfirmationAndCancellationDeletesNothing() {
        panels(
            PasswordUiState(
                showTrash = true,
                trashItems = listOf(PasswordItemModel(7, "Synthetic account", "user")),
            )
        )
        compose.onNodeWithText(text(R.string.trash_delete_permanently)).performClick()
        verify(exactly = 0) { vm.permanentlyDelete(any()) }
        compose.onNodeWithText(text(R.string.cancel_btn)).performClick()
        verify(exactly = 0) { vm.permanentlyDelete(any()) }
        compose.onNodeWithText(text(R.string.trash_delete_permanently)).performClick()
        compose
            .onAllNodes(hasText(text(R.string.trash_delete_permanently)) and hasClickAction())
            .onLast()
            .performClick()
        verify(exactly = 1) { vm.permanentlyDelete(7) }
    }

    @Test
    fun historyRestoreRequiresExplicitConfirmation() {
        panels(
            PasswordUiState(
                historyCredentialId = 3,
                historyPlatform = "Synthetic account",
                historyItems = listOf(PasswordHistoryItem(8, 3, 100_000, 50_000)),
            )
        )
        compose.onNodeWithText(text(R.string.history_restore)).performClick()
        verify(exactly = 0) { vm.restoreHistory(any()) }
        compose.onNodeWithText(text(R.string.history_restore_warning)).assertExists()
        compose
            .onAllNodes(hasText(text(R.string.history_restore)) and hasClickAction())
            .onLast()
            .performClick()
        verify(exactly = 1) { vm.restoreHistory(8) }
    }

    @Test
    fun excessiveTagsDisableMetadataSaveAndValidTagsAreForwarded() {
        panels(
            PasswordUiState(
                tagsItem = FullCredentialModel(7, "Synthetic account", charArrayOf(), charArrayOf())
            )
        )
        compose.onNode(hasSetTextAction()).performTextReplacement("x".repeat(33))
        compose.onNodeWithText(text(R.string.save_now_btn)).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("work, אישי")
        compose.onNodeWithText(text(R.string.save_now_btn)).assertIsEnabled().performClick()
        verify(exactly = 1) { vm.saveTags("work, אישי") }
    }

    @Test
    fun loadingTrashDoesNotClaimItIsEmpty() {
        panels(PasswordUiState(showTrash = true, featureBusy = true))
        compose.onNodeWithText(text(R.string.trash_empty)).assertDoesNotExist()
    }

    @Test
    fun toolbarExposesHealthAndTrashActionsWithoutADevice() {
        every { vm.uiState } returns
            MutableStateFlow(PasswordUiState(listStatus = PasswordListStatus.Empty))
        every { vm.filteredPasswords } returns MutableStateFlow(emptyList())
        compose.setContent { MyPasswordsTheme { PasswordScreen(vm, {}, {}) } }
        compose.onNodeWithText(text(R.string.health_action)).performClick()
        verify(exactly = 1) { vm.showHealthReport() }
        compose.onNodeWithText(text(R.string.trash_title)).performClick()
        verify(exactly = 1) { vm.showTrash() }
    }
}
