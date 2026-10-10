package com.example.mypasswords.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.compose_ui.components.CEditPasswordSheet
import com.example.compose_ui.components.CPasswordDetailSheet
import com.example.mypasswords.R
import com.example.mypasswords.screens.password.*
import com.example.mypasswords.screens.welcome.WelcomeScreen
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.*

/** Reuses all portrait fixtures in landscape, plus checks actual layout and action reachability. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w891dp-h411dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeLayoutTest : DesignPreviewTest() {
    override fun render(name: String, content: @Composable () -> Unit) {
        super.render("landscape-$name", content)
    }

    @Test
    fun welcomeActionIsVisibleWithoutScrollingAndWorks() {
        var clicks = 0
        render("welcome-action") { WelcomeScreen { clicks++ } }
        compose
            .onNodeWithText(text(R.string.welcome_screen_button_text))
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp-land-mdpi")
    fun welcomeActionRemainsReachableWithLargeFontOnShortScreen() {
        render("welcome-large-font") {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) { WelcomeScreen {} }
        }
        compose.onNodeWithText(text(R.string.welcome_screen_button_text)).assertIsDisplayed()
    }

    private fun addVm(state: AddPasswordUiState): AddPasswordViewModel {
        val vm = mockk<AddPasswordViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(state)
        return vm
    }

    @Test
    fun addFormUsesTwoColumnsAndSaveIsVisibleWithoutScrolling() {
        val vm = addVm(AddPasswordUiState(isSaveEnabled = true))
        render("form-columns") { AddPasswordScreen(vm, {}) }
        val platform =
            compose.onNodeWithText(text(R.string.platform_name_label)).getUnclippedBoundsInRoot()
        val username =
            compose.onNodeWithText(text(R.string.username_email_label)).getUnclippedBoundsInRoot()
        assertEquals(platform.top.value, username.top.value, 1f)
        assertTrue(platform.left != username.left)
        compose.onNodeWithText(text(R.string.save_now_btn)).assertIsDisplayed().performClick()
        verify(exactly = 1) { vm.onSaveClick() }
    }

    @Test
    fun invalidLandscapeFormStillDisablesSave() {
        render("invalid-form") { AddPasswordScreen(addVm(AddPasswordUiState()), {}) }
        compose.onNodeWithText(text(R.string.save_now_btn)).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun busyLandscapeFormDisablesInput() {
        render("busy-form") { AddPasswordScreen(addVm(AddPasswordUiState(isSaving = true)), {}) }
        val editableField =
            SemanticsMatcher.keyIsDefined(
                androidx.compose.ui.semantics.SemanticsProperties.EditableText
            )
        compose.onAllNodes(editableField).assertCountEquals(4)
        repeat(4) { index -> compose.onAllNodes(editableField)[index].assertIsNotEnabled() }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w480dp-h320dp-land-mdpi")
    fun narrowLandscapeStacksFieldsInsteadOfSqueezingThem() {
        val vm = addVm(AddPasswordUiState())
        render("narrow-form") { AddPasswordScreen(vm, {}) }
        val platform =
            compose.onNodeWithText(text(R.string.platform_name_label)).getUnclippedBoundsInRoot()
        val username =
            compose.onNodeWithText(text(R.string.username_email_label)).getUnclippedBoundsInRoot()
        assertTrue(username.top > platform.top)
        compose.onNodeWithText(text(R.string.save_now_btn)).assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextInput("synthetic")
        verify { vm.onUsernameChanged("synthetic") }
    }

    @Test
    fun reducedHeightKeepsSaveVisibleWhileFormCanScroll() {
        val vm = addVm(AddPasswordUiState(isSaveEnabled = true))
        render("reduced-height-form") {
            Box(Modifier.height(180.dp)) { AddPasswordScreen(vm, {}) }
        }
        compose.onNodeWithText(text(R.string.save_now_btn)).assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.save_now_btn)).assertIsDisplayed().performClick()
        verify { vm.onSaveClick() }
    }

    @Test
    fun featureSheetsKeepTheirActionsReachableInLandscape() {
        val vm = mockk<PasswordViewModel>(relaxed = true)
        render("trash") {
            VaultFeaturePanels(
                PasswordUiState(
                    showTrash = true,
                    trashItems =
                        listOf(com.example.core.model.PasswordItemModel(1, "Synthetic", "demo")),
                ),
                vm,
            )
        }
        compose
            .onNodeWithText(text(R.string.trash_restore))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        verify { vm.restoreTrash(1) }
        compose
            .onNodeWithText(text(R.string.trash_delete_permanently))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun recoveryInputKeepsSubmissionAndCancelVisible() {
        render("recovery-input") { RecoveryInputDialog({}, {}) }
        compose
            .onNodeWithText(text(R.string.recovery_start))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.cancel_btn)).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("synthetic-code")
        compose.onNodeWithText(text(R.string.recovery_start)).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun listToolsAndFirstEntryAreVisibleTogether() {
        val vm = mockk<PasswordViewModel>(relaxed = true)
        every { vm.uiState } returns
            MutableStateFlow(PasswordUiState(listStatus = PasswordListStatus.Ready))
        every { vm.filteredPasswords } returns
            MutableStateFlow(
                listOf(com.example.core.model.PasswordItemModel(1, "Synthetic account", "demo"))
            )
        render("list-tools") { PasswordScreen(vm, {}, {}) }
        compose.onNodeWithText("Synthetic account").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.health_action)).assertIsDisplayed().performClick()
        verify(exactly = 1) { vm.showHealthReport() }
        compose.onNodeWithText(text(R.string.trash_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.favorites_only)).assertIsDisplayed()
    }

    @Test
    fun failedListKeepsRetryVisibleAndFunctional() {
        val vm = mockk<PasswordViewModel>(relaxed = true)
        every { vm.uiState } returns
            MutableStateFlow(
                PasswordUiState(
                    listStatus =
                        PasswordListStatus.Failed(
                            com.example.core.model.VaultError.UninitializedVault
                        )
                )
            )
        every { vm.filteredPasswords } returns MutableStateFlow(emptyList())
        render("list-retry") { PasswordScreen(vm, {}, {}) }
        compose.onNodeWithText(text(R.string.retry_loading)).assertIsDisplayed().performClick()
        verify(exactly = 1) { vm.retryLoadingPasswords() }
    }

    @Test
    fun editFieldsAreSideBySideAndUpdateKeepsValues() {
        var submitted: Pair<String, String>? = null
        render("edit-columns") {
            CEditPasswordSheet(
                "Synthetic",
                "demo-user",
                "demo-password",
                "",
                {},
                { u, p -> submitted = u to p },
                title = "Edit",
                usernameLabel = "Username",
                passwordLabel = "Password",
                updateButtonText = "Update",
            )
        }
        val username = compose.onNodeWithText("Username").getUnclippedBoundsInRoot()
        val password = compose.onNodeWithText("Password").getUnclippedBoundsInRoot()
        assertEquals(username.top.value, password.top.value, 1f)
        assertTrue(username.left != password.left)
        compose.onNodeWithText("Update").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("demo-user" to "demo-password", submitted) }
    }

    @Test
    fun detailActionsWrapAndRemainReachableWithLongUsername() {
        var favorites = 0
        render("detail-actions") {
            CPasswordDetailSheet(
                "Synthetic",
                "long-username".repeat(20).toCharArray(),
                "demo".toCharArray(),
                "",
                {},
                closeButtonText = "Close",
                usernameLabel = "Username",
                passwordLabel = "Password",
                loginDetailsSubtitle = "Login",
                onToggleFavorite = { favorites++ },
                onManageTags = {},
                onHistory = {},
                favoriteText = "Favorite",
                tagsText = "Tags",
                historyText = "History",
            )
        }
        compose.onNodeWithText("Favorite").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Tags").assertIsDisplayed()
        compose.onNodeWithText("History").assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, favorites) }
    }

    @Test
    fun deleteConfirmationKeepsBothActionsVisible() {
        var confirmed = 0
        var dismissed = 0
        render("delete-actions") {
            DeleteConfirmationDialog("Synthetic", { confirmed++ }, { dismissed++ })
        }
        compose
            .onNodeWithText(text(R.string.delete_dialog_cancel))
            .assertIsDisplayed()
            .performClick()
        compose
            .onNodeWithText(text(R.string.delete_dialog_confirm))
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertEquals(1, confirmed)
            assertEquals(1, dismissed)
        }
    }
}
