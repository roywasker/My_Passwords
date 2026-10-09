package com.example.mypasswords

import androidx.biometric.BiometricPrompt
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.example.core.auth.BiometricAuthManager
import com.example.core.data.DatabaseManager
import com.example.core.data.PasswordRepository
import com.example.core.model.*
import io.mockk.*
import java.security.KeyStore
import javax.crypto.Cipher
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Real Activity, navigation, ViewModels and UI; authentication and persistence are explicit fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MyPasswordsApp::class, qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class VaultUserJourneyTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val success = slot<(BiometricPrompt.AuthenticationResult) -> Unit>()
    private val cipher = mockk<Cipher>()
    private val records = mutableMapOf<Int, FullCredentialModel>()
    private val items = MutableStateFlow<List<PasswordItemModel>>(emptyList())
    private var nextId = 1

    private fun publish() {
        items.value =
            records.values.map {
                PasswordItemModel(it.id, it.platform, String(it.username), "", it.categoryId)
            }
    }

    private val isolation =
        object : TestWatcher() {
            override fun starting(description: Description) {
                val store = mockk<KeyStore>(relaxed = true)
                every { store.containsAlias("VaultMasterKey") } returns true
                mockkStatic(KeyStore::class)
                every { KeyStore.getInstance("AndroidKeyStore") } returns store
                mockkConstructor(
                    DatabaseManager::class,
                    BiometricAuthManager::class,
                    PasswordRepository::class,
                )
                every { anyConstructed<DatabaseManager>().getPassphraseCipher() } returns cipher
                coEvery { anyConstructed<DatabaseManager>().initializeDatabase(any()) } just Runs
                every { anyConstructed<DatabaseManager>().clearSession() } just Runs
                every {
                    anyConstructed<BiometricAuthManager>()
                        .promptBiometricAuth(
                            any(),
                            any(),
                            any(),
                            any(),
                            any(),
                            capture(success),
                            any(),
                            any(),
                        )
                } just Runs
                every { anyConstructed<PasswordRepository>().getCategories() } returns
                    Category.entries
                every { anyConstructed<PasswordRepository>().passwords } returns items
                coEvery { anyConstructed<PasswordRepository>().addPassword(any()) } answers
                    {
                        val c = firstArg<Credential>()
                        val id = nextId++
                        records[id] =
                            FullCredentialModel(
                                id,
                                c.platformName,
                                String(c.username).toCharArray(),
                                String(c.password).toCharArray(),
                                "",
                                c.categoryId,
                            )
                        c.clear()
                        publish()
                    }
                coEvery {
                    anyConstructed<PasswordRepository>().getDecryptedCredential(any())
                } answers
                    {
                        records[firstArg<Int>()]?.let {
                            it.copy(
                                username = it.username.copyOf(),
                                password = it.password.copyOf(),
                            )
                        }
                    }
                coEvery {
                    anyConstructed<PasswordRepository>().updatePassword(any(), any())
                } answers
                    {
                        val id = firstArg<Int>()
                        val c = secondArg<Credential>()
                        records.remove(id)?.clear()
                        records[id] =
                            FullCredentialModel(
                                id,
                                c.platformName,
                                String(c.username).toCharArray(),
                                String(c.password).toCharArray(),
                                "",
                                c.categoryId,
                            )
                        c.clear()
                        publish()
                    }
                coEvery { anyConstructed<PasswordRepository>().deletePassword(any()) } answers
                    {
                        records.remove(firstArg<Int>())?.clear()
                        publish()
                    }
            }

            override fun finished(description: Description) {
                records.values.forEach { it.clear() }
                records.clear()
                unmockkAll()
            }
        }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(isolation).around(compose)

    private fun text(id: Int) = compose.activity.getString(id)

    private fun login() {
        success.clear()
        compose.onNodeWithText(text(R.string.welcome_screen_button_text)).performClick()
        compose.waitUntil(5000) { success.isCaptured }
        val result = mockk<BiometricPrompt.AuthenticationResult>()
        every { result.cryptoObject } returns BiometricPrompt.CryptoObject(cipher)
        compose.runOnIdle { success.captured(result) }
        compose.onNodeWithText(text(R.string.password_screen_title)).assertExists()
    }

    private fun add(
        platform: String = "Synthetic service",
        user: String = "synthetic-user",
        password: String = "synthetic-password",
    ) {
        compose.onNodeWithContentDescription(text(R.string.add_password_btn)).performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(platform)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(user)
        compose.onAllNodes(hasSetTextAction())[2].performTextInput(password)
        compose.onNodeWithText(text(R.string.save_now_btn)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.password_screen_title)).assertExists()
    }

    @Test
    fun completeAddReadEditDeleteJourneyUsesRealViewModelsAndNavigation() {
        login()
        add()
        compose.onNodeWithText("Synthetic service").performClick()
        compose.onNodeWithText("synthetic-password").assertDoesNotExist()
        compose.onNodeWithContentDescription("Toggle Visibility").performClick()
        compose.onNodeWithText("synthetic-password").assertExists()
        compose.onNodeWithText(text(R.string.close_btn)).performClick()
        compose.onNodeWithContentDescription(text(R.string.edit_btn)).performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("updated-user")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("updated-password")
        compose.onNodeWithText(text(R.string.edit)).performClick()
        compose.runOnIdle {
            assertEquals("updated-password", String(records.values.single().password))
        }
        compose.onNodeWithContentDescription(text(R.string.delete_btn)).performClick()
        compose.onNodeWithText(text(R.string.delete_dialog_confirm)).performClick()
        compose.onNodeWithText("Synthetic service").assertDoesNotExist()
        compose.runOnIdle { assertTrue(records.isEmpty()) }
    }

    @Test
    fun cancellingDeleteKeepsRecordAndDetailAccessible() {
        login()
        add()
        compose.onNodeWithContentDescription(text(R.string.delete_btn)).performClick()
        compose.onNodeWithText(text(R.string.delete_dialog_cancel)).performClick()
        compose.onNodeWithText("Synthetic service").performClick()
        compose.onNodeWithText("synthetic-user").assertExists()
        compose.runOnIdle { assertEquals(1, records.size) }
    }

    @Test
    fun authenticationCanBeRepeatedAfterLockWithoutLosingStoredItems() {
        login()
        add()
        compose.runOnIdle { compose.activity.authRepository.setAuthenticated(false) }
        compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
        login()
        compose.onNodeWithText("Synthetic service").assertExists()
        compose.onNodeWithText("synthetic-password").assertDoesNotExist()
    }

    @Test
    fun backingOutOfIncompleteFormDoesNotPersistAnything() {
        login()
        compose.onNodeWithContentDescription(text(R.string.add_password_btn)).performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("unsaved")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(text(R.string.password_screen_title)).assertExists()
        compose.runOnIdle { assertTrue(records.isEmpty()) }
        coVerify(exactly = 0) { anyConstructed<PasswordRepository>().addPassword(any()) }
    }

    @Test
    fun failedSaveKeepsFormThenRetryPersistsAndReturnsToList() {
        login()
        coEvery { anyConstructed<PasswordRepository>().addPassword(any()) } throws
            VaultError.DatabaseError(IllegalStateException("synthetic failure"))
        compose.onNodeWithContentDescription(text(R.string.add_password_btn)).performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Retry service")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("retry-user")
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("retry-pass")
        compose.onNodeWithText(text(R.string.save_now_btn)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.error_database_access)).assertExists()
        compose.onNodeWithText(text(R.string.add_password_title)).assertExists()
        compose.runOnIdle { assertTrue(records.isEmpty()) }
        coEvery { anyConstructed<PasswordRepository>().addPassword(any()) } answers
            {
                val c = firstArg<Credential>()
                val id = nextId++
                records[id] =
                    FullCredentialModel(
                        id,
                        c.platformName,
                        String(c.username).toCharArray(),
                        String(c.password).toCharArray(),
                        "",
                        c.categoryId,
                    )
                c.clear()
                publish()
            }
        compose.onNodeWithText(text(R.string.save_now_btn)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.password_screen_title)).assertExists()
        compose.onNodeWithText("Retry service").assertExists()
    }
}
