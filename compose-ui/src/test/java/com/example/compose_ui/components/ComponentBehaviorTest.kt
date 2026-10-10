package com.example.compose_ui.components

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ComponentBehaviorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun buttonCallsClickOnce() {
        var clicks = 0
        compose.setContent { MaterialTheme { CButton("Save", { clicks++ }) } }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun sharedCredentialFieldPreservesExactUnicodeAndWhitespace() {
        var received = ""
        compose.setContent {
            var value by remember { mutableStateOf("") }
            CCredentialTextField(
                value = value,
                onValueChange = {
                    received = it
                    value = it
                },
            )
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("  שלום 日本語  ")
        compose.runOnIdle { assertEquals("  שלום 日本語  ", received) }
    }

    @Test
    fun sharedPasswordFieldRevealsOnlyOnRequestWhileKeepingSensitiveInputType() {
        compose.setContent {
            CCredentialTextField(value = "synthetic-secret", onValueChange = {}, isPassword = true)
        }
        compose
            .onNode(hasSetTextAction())
            .assert(
                SemanticsMatcher.keyIsDefined(
                    androidx.compose.ui.semantics.SemanticsProperties.Password
                )
            )
        val show =
            RuntimeEnvironment.getApplication()
                .getString(com.example.compose_ui.R.string.credential_show_password)
        compose.onNodeWithContentDescription(show).performClick()
        compose.onNode(hasSetTextAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            assertTrue(action(layouts))
            assertEquals("synthetic-secret", layouts.single().layoutInput.text.text)
        }
        compose
            .onNode(hasSetTextAction())
            .assert(
                SemanticsMatcher.keyIsDefined(
                    androidx.compose.ui.semantics.SemanticsProperties.Password
                )
            )
    }

    @Test
    fun busySharedPasswordFieldCannotGenerateAReplacement() {
        compose.setContent {
            CCredentialTextField(
                value = "secret",
                onValueChange = { fail("Busy form cannot change") },
                isPassword = true,
                enabled = false,
                onGeneratePassword = { fail("Busy form cannot generate") },
            )
        }
        val generate =
            RuntimeEnvironment.getApplication()
                .getString(com.example.compose_ui.R.string.credential_generate)
        compose.onNodeWithContentDescription(generate).assertIsNotEnabled().performClick()
    }

    @Test
    fun changingEditedRecordResetsUnsavedFieldsEvenForIdenticalPlatformNames() {
        var id by mutableStateOf(1)
        var submitted: Pair<String, String>? = null
        compose.setContent {
            CEditPasswordSheet(
                "Same site",
                "user-$id",
                "secret-$id",
                "",
                {},
                { u, p -> submitted = u to p },
                title = "Edit",
                usernameLabel = "Username",
                passwordLabel = "Password",
                updateButtonText = "Update",
                recordKey = id,
            )
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("unsaved user")
        compose.runOnIdle { id = 2 }
        compose.onNodeWithText("user-2").assertExists()
        compose.onNodeWithText("Update").performClick()
        compose.runOnIdle { assertEquals("user-2" to "secret-2", submitted) }
    }

    @Test
    fun disabledButtonDoesNotInvokeCallback() {
        var clicks = 0
        compose.setContent { CButton("Save", { clicks++ }, enabled = false) }
        compose.onNodeWithText("Save").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun textFieldForwardsTypedUnicodeText() {
        var actual = ""
        compose.setContent {
            var value by remember { mutableStateOf("") }
            CTextField(
                value,
                {
                    value = it
                    actual = it
                },
                "Username",
                Modifier.testTag("input"),
            )
        }
        compose.onNodeWithTag("input").performTextInput("שלום")
        compose.runOnIdle { assertEquals("שלום", actual) }
    }

    @Test
    fun textFieldReplacementAndClearingAreForwarded() {
        var actual = "initial"
        compose.setContent {
            var value by remember { mutableStateOf(actual) }
            CTextField(
                value,
                {
                    value = it
                    actual = it
                },
                "Username",
                Modifier.testTag("input"),
            )
        }
        compose.onNodeWithTag("input").performTextReplacement("changed")
        compose.runOnIdle { assertEquals("changed", actual) }
        compose.onNodeWithTag("input").performTextClearance()
        compose.runOnIdle { assertEquals("", actual) }
    }

    @Test
    fun passwordStartsHiddenCanBeShownAndHiddenAgain() {
        compose.setContent { CPasswordField("secret", {}, Modifier.testTag("password")) }
        compose.onNodeWithContentDescription("Show password").assertExists().performClick()
        compose.onNodeWithContentDescription("Hide password").assertExists().performClick()
        compose.onNodeWithContentDescription("Show password").assertExists()
    }

    @Test
    fun passwordFieldForwardsEdits() {
        var actual = ""
        compose.setContent {
            var value by remember { mutableStateOf("") }
            CPasswordField(
                value,
                {
                    value = it
                    actual = it
                },
                Modifier.testTag("password"),
            )
        }
        compose.onNodeWithTag("password").performTextInput("test!123")
        compose.runOnIdle { assertEquals("test!123", actual) }
    }

    @Test
    fun categoryClickReportsItsId() {
        var selected = 0
        compose.setContent {
            CCategoryList(
                listOf(CCategoryItem(1, "All"), CCategoryItem(4, "Finance")),
                1,
                { selected = it },
            )
        }
        compose.onNodeWithText("Finance").performClick()
        compose.runOnIdle { assertEquals(4, selected) }
    }

    @Test
    fun selectedCategoryCanBeClickedAgain() {
        var clicks = 0
        compose.setContent { CCategoryList(listOf(CCategoryItem(1, "All")), 1, { clicks++ }) }
        compose.onNodeWithText("All").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun emptyCategoryListRendersSafely() {
        compose.setContent { CCategoryList(emptyList(), 1, {}) }
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    private fun showDetail(onDismiss: () -> Unit = {}) {
        compose.setContent {
            CPasswordDetailSheet(
                "Google",
                "user".toCharArray(),
                "secret".toCharArray(),
                "",
                onDismiss,
                closeButtonText = "Close",
                usernameLabel = "Username",
                passwordLabel = "Password",
                loginDetailsSubtitle = "Details",
            )
        }
    }

    @Test
    fun detailsMaskPasswordUntilVisibilityIsToggled() {
        showDetail()
        compose.onNodeWithText("secret").assertDoesNotExist()
        compose.onNodeWithText("•".repeat(12)).assertExists()
        compose.onNodeWithContentDescription("Toggle Visibility").performClick()
        compose.onNodeWithText("secret").assertExists()
        compose.onNodeWithContentDescription("Toggle Visibility").performClick()
        compose.onNodeWithText("secret").assertDoesNotExist()
    }

    @Test
    fun detailCloseInvokesDismiss() {
        var dismissed = 0
        showDetail { dismissed++ }
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test
    fun detailsShowUsernameAndFallbackPlatformLetter() {
        showDetail()
        compose.onNodeWithText("user").assertExists()
        compose.onNodeWithText("G").assertExists()
    }

    @Test
    fun copyingUsernameUsesActualUnmaskedValue() {
        showDetail()
        compose.onAllNodesWithContentDescription("Copy")[0].performClick()
        compose.runOnIdle {
            val clipboard =
                RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            assertEquals("user", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test
    fun copyingHiddenPasswordUsesRealValueAndMarksSensitive() {
        showDetail()
        compose.onAllNodesWithContentDescription("Copy")[1].performClick()
        compose.runOnIdle {
            val clipboard =
                RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            val clip = clipboard.primaryClip!!
            assertEquals("secret", clip.getItemAt(0).text.toString())
            assertTrue(clip.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
        }
    }

    private fun showEdit(
        onUpdate: (String, String) -> Unit,
        onGenerate: (() -> String)? = null,
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            CEditPasswordSheet(
                "Google",
                "user",
                "secret",
                "",
                onDismiss,
                onUpdate,
                onGenerate,
                title = "Edit",
                usernameLabel = "Username",
                passwordLabel = "Password",
                updateButtonText = "Update",
            )
        }
    }

    @Test
    fun editInitiallySubmitsExistingValues() {
        var saved: Pair<String, String>? = null
        showEdit({ u, p -> saved = u to p })
        compose.onNodeWithText("Update").performClick()
        compose.runOnIdle { assertEquals("user" to "secret", saved) }
    }

    @Test
    fun editSubmitsChangedUsernameAndPassword() {
        var saved: Pair<String, String>? = null
        showEdit({ u, p -> saved = u to p })
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("new-user")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("new-pass")
        compose.onNodeWithText("Update").performClick()
        compose.runOnIdle { assertEquals("new-user" to "new-pass", saved) }
    }

    @Test
    fun emptyEditedFieldsDisableUpdate() {
        var saved: Pair<String, String>? = null
        showEdit({ u, p -> saved = u to p })
        compose.onAllNodes(hasSetTextAction())[0].performTextClearance()
        compose.onAllNodes(hasSetTextAction())[1].performTextClearance()
        compose.onNodeWithText("Update").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertNull(saved) }
    }

    @Test
    fun textWrapperRendersUnicodeWithoutChangingIt() {
        compose.setContent { CText("שלום 日本語 🔐") }
        compose.onNodeWithText("שלום 日本語 🔐").assertExists()
    }

    @Test
    fun copiedPasswordIsClearedAfterSixtySeconds() {
        showDetail()
        compose.onAllNodesWithContentDescription("Copy")[1].performClick()
        compose.runOnIdle {
            val clipboard =
                RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofSeconds(59))
            assertEquals("secret", clipboard.primaryClip!!.getItemAt(0).text.toString())
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofSeconds(1))
            assertEquals("", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test
    fun passwordTimerDoesNotClearClipboardWithDifferentLabel() {
        showDetail()
        compose.onAllNodesWithContentDescription("Copy")[1].performClick()
        compose.runOnIdle {
            val clipboard =
                RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("Another app", "new value")
            )
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofSeconds(60))
            assertEquals("new value", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test
    fun knownPasswordTimerMustNotClearNewerClipboardContentWithSameLabel() {
        showDetail()
        compose.onAllNodesWithContentDescription("Copy")[1].performClick()
        compose.runOnIdle {
            val clipboard =
                RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Password", "new value"))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofSeconds(60))
            assertEquals("new value", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test
    fun optionalGeneratorReplacesEditedPasswordBeforeUpdate() {
        var saved: Pair<String, String>? = null
        showEdit({ u, p -> saved = u to p }, { "generated-test-password" })
        compose
            .onAllNodes(hasClickAction() and hasAnyAncestor(hasSetTextAction()))[0]
            .performClick()
        compose.onNodeWithText("Update").performClick()
        compose.runOnIdle { assertEquals("user" to "generated-test-password", saved) }
    }

    @Test
    fun omittingGeneratorLeavesOnlyVisibilityActionInPasswordField() {
        showEdit({ _, _ -> })
        compose
            .onAllNodes(hasClickAction() and hasAnyAncestor(hasSetTextAction()))
            .assertCountEquals(1)
    }

    @Test
    fun editingPasswordCanToggleMaskWithoutChangingSubmittedValue() {
        var saved: Pair<String, String>? = null
        showEdit({ u, p -> saved = u to p })
        fun renderedPassword(): String {
            var text = ""
            compose.onAllNodes(hasSetTextAction())[1].performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
            ) { action ->
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                assertTrue(action(results))
                text = results.single().layoutInput.text.text
            }
            return text
        }
        assertEquals("•".repeat(6), renderedPassword())
        compose
            .onAllNodes(hasClickAction() and hasAnyAncestor(hasSetTextAction()))[0]
            .performClick()
        assertEquals("secret", renderedPassword())
        compose.onNodeWithText("Update").performClick()
        compose.runOnIdle { assertEquals("user" to "secret", saved) }
    }

    @Test
    fun usernameCopyDoesNotExpireOrCarrySensitiveFlag() {
        showDetail()
        compose.onAllNodesWithContentDescription("Copy")[0].performClick()
        compose.runOnIdle {
            val clipboard =
                RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            assertFalse(
                clipboard.primaryClip!!
                    .description
                    .extras
                    ?.getBoolean("android.content.extra.IS_SENSITIVE") ?: false
            )
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofSeconds(90))
            assertEquals("user", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test
    fun repeatedPasswordCopyStartsFreshExpiryInsteadOfUsingOlderTimer() {
        showDetail()
        compose.onAllNodesWithContentDescription("Copy")[1].performClick()
        compose.runOnIdle {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofSeconds(30))
        }
        compose.onAllNodesWithContentDescription("Copy")[1].performClick()
        compose.runOnIdle {
            val clipboard =
                RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofSeconds(30))
            assertEquals("secret", clipboard.primaryClip!!.getItemAt(0).text.toString())
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofSeconds(30))
            assertEquals("", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }

    @Test
    fun clearedClipboardDoesNotCrashPendingExpiryOrCreateNewClip() {
        showDetail()
        compose.onAllNodesWithContentDescription("Copy")[1].performClick()
        compose.runOnIdle {
            val clipboard =
                RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            clipboard.clearPrimaryClip()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofSeconds(60))
            assertNull(clipboard.primaryClip)
        }
    }

    @Config(sdk = [24, 33])
    @Test
    fun sensitiveFlagIsSetOnMinimumSupportedAndAndroid13ClipboardApis() {
        showDetail()
        compose.onAllNodesWithContentDescription("Copy")[1].performClick()
        compose.runOnIdle {
            val clipboard =
                RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            assertTrue(
                clipboard.primaryClip!!
                    .description
                    .extras!!
                    .getBoolean("android.content.extra.IS_SENSITIVE")
            )
        }
    }

    @Test
    fun categoryListReflectsRenamingAndRemovalOfSameStableIds() {
        var categories by mutableStateOf(listOf(CCategoryItem(1, "All"), CCategoryItem(2, "Work")))
        compose.setContent { CCategoryList(categories, 1, {}) }
        compose.runOnIdle { categories = listOf(CCategoryItem(2, "Renamed")) }
        compose.onNodeWithText("Renamed").assertExists()
        compose.onNodeWithText("Work").assertDoesNotExist()
        compose.onNodeWithText("All").assertDoesNotExist()
    }

    @Test
    fun longCategoryListCanScrollToAndSelectLastItem() {
        var selected = 0
        compose.setContent {
            CCategoryList((1..30).map { CCategoryItem(it, "Category $it") }, 1, { selected = it })
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(29)
        compose.onNodeWithText("Category 30").performClick()
        compose.runOnIdle { assertEquals(30, selected) }
    }

    @Test
    fun floatingIconExposesClickActionAndCallsHandler() {
        var clicks = 0
        compose.setContent {
            CFloatingIcon(
                modifier = Modifier.testTag("floating"),
                icon = androidx.compose.material.icons.Icons.Default.Close,
                borderColor = Color.Gray,
                iconBackGroundColor = Color.Black,
                iconTint = Color.White,
                onClick = { clicks++ },
            )
        }
        compose.onNodeWithTag("floating").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun floatingIconDefaultHandlerIsSafe() {
        compose.setContent {
            CFloatingIcon(
                modifier = Modifier.testTag("floating"),
                icon = androidx.compose.material.icons.Icons.Default.Close,
                borderColor = Color.Gray,
                iconBackGroundColor = Color.Black,
                iconTint = Color.White,
            )
        }
        compose.onNodeWithTag("floating").performClick()
    }
}
