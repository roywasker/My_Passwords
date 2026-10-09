package com.example.mypasswords

import androidx.biometric.BiometricPrompt
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.example.core.auth.BiometricAuthManager
import com.example.core.data.DatabaseManager
import io.mockk.*
import java.security.KeyStore
import javax.crypto.Cipher
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Real navigation graph and Hilt wiring; biometric/Keystore/database boundaries are test doubles.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28], application = MyPasswordsApp::class, qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityNavigationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val success = slot<(BiometricPrompt.AuthenticationResult) -> Unit>()
    private val error = slot<(String) -> Unit>()
    private val scanFailed = slot<() -> Unit>()
    private val cipher = mockk<Cipher>()
    private val isolation =
        object : TestWatcher() {
            override fun starting(description: Description) {
                val store = mockk<KeyStore>(relaxed = true)
                every { store.containsAlias("VaultMasterKey") } returns true
                mockkStatic(KeyStore::class)
                every { KeyStore.getInstance("AndroidKeyStore") } returns store
                mockkConstructor(DatabaseManager::class, BiometricAuthManager::class)
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
                            capture(error),
                            capture(scanFailed),
                        )
                } just Runs
            }

            override fun finished(description: Description) {
                unmockkAll()
            }
        }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(isolation).around(compose)

    private fun text(id: Int) = compose.activity.getString(id)

    private fun startAuthentication() {
        success.clear()
        compose.onNodeWithText(text(R.string.welcome_screen_button_text)).performClick()
        compose.waitUntil(5000) { success.isCaptured }
    }

    private fun unlock() {
        startAuthentication()
        val result = mockk<BiometricPrompt.AuthenticationResult>()
        every { result.cryptoObject } returns BiometricPrompt.CryptoObject(cipher)
        compose.runOnIdle { success.captured(result) }
        compose.onNodeWithText(text(R.string.password_screen_title)).assertExists()
    }

    @Test
    fun launchShowsWelcomeAndHidesProtectedScreens() {
        compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
        compose.onNodeWithText(text(R.string.password_screen_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.add_password_title)).assertDoesNotExist()
    }

    @Test
    fun successfulAuthenticationOpensPasswordScreen() {
        unlock()
        compose.runOnIdle { assertTrue(compose.activity.authRepository.isAuthenticated.value) }
    }

    @Test
    fun successfulAuthenticationFinishesScreenTransitionWithoutDefaultFadeDelay() {
        startAuthentication()
        compose.waitForIdle()
        val result = mockk<BiometricPrompt.AuthenticationResult>()
        every { result.cryptoObject } returns BiometricPrompt.CryptoObject(cipher)
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { success.captured(result) }
            repeat(6) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            compose.onNodeWithText(text(R.string.password_screen_title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.fingerPrint_screen_title)).assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun cancelledAuthenticationReturnsToWelcomeWithoutUnlocking() {
        startAuthentication()
        compose.runOnIdle { error.captured("cancelled") }
        compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
        compose.runOnIdle { assertFalse(compose.activity.authRepository.isAuthenticated.value) }
    }

    @Test
    fun failedScanReturnsToWelcomeWithoutUnlocking() {
        startAuthentication()
        compose.runOnIdle { scanFailed.captured() }
        compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
        compose.runOnIdle { assertFalse(compose.activity.authRepository.isAuthenticated.value) }
    }

    @Test
    fun addButtonOpensProtectedAddFormAfterAuthentication() {
        unlock()
        compose.onNodeWithContentDescription(text(R.string.add_password_btn)).performClick()
        compose.onNodeWithText(text(R.string.add_password_title)).assertExists()
    }

    @Test
    fun lockingPasswordScreenRedirectsToWelcome() {
        unlock()
        compose.runOnIdle { compose.activity.authRepository.setAuthenticated(false) }
        compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
        compose.onNodeWithText(text(R.string.password_screen_title)).assertDoesNotExist()
    }

    @Test
    fun backFromPasswordsReturnsToWelcome() {
        unlock()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
    }

    @Test
    fun knownLockingAddFormRedirectsToWelcome() {
        unlock()
        compose.onNodeWithContentDescription(text(R.string.add_password_btn)).performClick()
        compose.runOnIdle { compose.activity.authRepository.setAuthenticated(false) }
        compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
    }

    @Test
    fun backgroundingProtectedScreenRequiresReauthenticationOnReturn() {
        unlock()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
        compose.runOnIdle { assertFalse(compose.activity.authRepository.isAuthenticated.value) }
    }

    @Test
    fun backFromAddFormReturnsToPasswordsWhileStillAuthenticated() {
        unlock()
        compose.onNodeWithContentDescription(text(R.string.add_password_btn)).performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(text(R.string.password_screen_title)).assertExists()
        compose.runOnIdle { assertTrue(compose.activity.authRepository.isAuthenticated.value) }
    }

    @Test
    fun recreatedProtectedScreenRetainsAuthenticationAndRoute() {
        unlock()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(text(R.string.password_screen_title)).assertExists()
        compose.runOnIdle { assertTrue(compose.activity.authRepository.isAuthenticated.value) }
    }

    @Test
    fun missingCipherReturnsToWelcomeWithoutOpeningVault() {
        startAuthentication()
        val result = mockk<BiometricPrompt.AuthenticationResult>()
        every { result.cryptoObject } returns null
        compose.runOnIdle { success.captured(result) }
        compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
        coVerify(exactly = 0) { anyConstructed<DatabaseManager>().initializeDatabase(any()) }
    }

    @Test
    fun vaultOpenFailureReturnsToWelcomeWithoutAuthentication() {
        coEvery { anyConstructed<DatabaseManager>().initializeDatabase(any()) } throws
            com.example.core.model.VaultError.DatabaseError(IllegalStateException())
        startAuthentication()
        val result = mockk<BiometricPrompt.AuthenticationResult>()
        every { result.cryptoObject } returns BiometricPrompt.CryptoObject(cipher)
        compose.runOnIdle { success.captured(result) }
        compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
        compose.runOnIdle { assertFalse(compose.activity.authRepository.isAuthenticated.value) }
    }

    @Test
    fun repeatedAuthenticationCancellationDoesNotAccumulateProtectedScreens() {
        repeat(3) {
            startAuthentication()
            compose.runOnIdle { error.captured("cancelled") }
            compose.onNodeWithText(text(R.string.welcome_screen_title)).assertExists()
        }
        compose.onNodeWithText(text(R.string.password_screen_title)).assertDoesNotExist()
    }
}
