package com.example.mypasswords.screens.finger

import android.app.Application
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.example.core.auth.*
import com.example.core.data.DatabaseManager
import com.example.core.model.VaultError
import com.example.mypasswords.testing.MainDispatcherRule
import io.mockk.*
import javax.crypto.Cipher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class FingerPrintViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val database = mockk<DatabaseManager>()
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val biometric = mockk<BiometricAuthManager>()
    private val app = mockk<Application>()
    private val activity = mockk<FragmentActivity>()
    private val cipher = mockk<Cipher>()
    private val success = slot<(BiometricPrompt.AuthenticationResult) -> Unit>()
    private val error = slot<(String) -> Unit>()
    private val failed = slot<() -> Unit>()
    private val crypto = slot<BiometricPrompt.CryptoObject>()
    private lateinit var vm: FingerPrintViewModel

    @Before
    fun setup() {
        every { database.getPassphraseCipher() } returns cipher
        coEvery { database.initializeDatabase(any()) } just Runs
        every { app.getString(any()) } answers { "resource-${firstArg<Int>()}" }
        every {
            biometric.promptBiometricAuth(
                activity,
                any(),
                any(),
                any(),
                capture(crypto),
                capture(success),
                capture(error),
                capture(failed),
            )
        } just Runs
        vm = FingerPrintViewModel(database, auth, biometric, app)
    }

    private fun TestScope.prepare() {
        vm.prepareAuthentication(activity)
        runCurrent()
    }

    private fun result(withCipher: Boolean = true): BiometricPrompt.AuthenticationResult {
        val result = mockk<BiometricPrompt.AuthenticationResult>()
        every { result.cryptoObject } returns
            if (withCipher) BiometricPrompt.CryptoObject(cipher) else null
        return result
    }

    @Test
    fun startsIdleAndDoesNotAuthenticateAutomatically() {
        assertSame(AuthState.Idle, vm.authState.value)
        verify(exactly = 0) { auth.setAuthenticated(any()) }
    }

    @Test
    fun prepareSuppliesCipherAndLocalizedPrompt() = runTest {
        prepare()
        assertSame(cipher, crypto.captured.cipher)
        verify(exactly = 1) {
            biometric.promptBiometricAuth(
                activity,
                "resource-${com.example.mypasswords.R.string.login_title}",
                "resource-${com.example.mypasswords.R.string.login_subtitle}",
                "resource-${com.example.mypasswords.R.string.negative_button_text}",
                any(),
                any(),
                any(),
                any(),
            )
        }
        assertSame(AuthState.Idle, vm.authState.value)
    }

    @Test
    fun successInitializesVaultBeforePublishingAuthentication() = runTest {
        prepare()
        success.captured(result())
        runCurrent()
        coVerifyOrder {
            database.initializeDatabase(cipher)
            auth.setAuthenticated(true)
        }
        assertSame(AuthState.Success, vm.authState.value)
    }

    @Test
    fun biometricSuccessWithoutCipherNeverAuthenticates() = runTest {
        prepare()
        success.captured(result(false))
        runCurrent()
        assertEquals(
            "Cipher not released",
            ((vm.authState.value as AuthState.Error).error as VaultError.GenericError)
                .messageOverride,
        )
        coVerify(exactly = 0) { database.initializeDatabase(any()) }
        verify(exactly = 0) { auth.setAuthenticated(true) }
    }

    @Test
    fun failedScanPublishesNonfatalAuthFailure() = runTest {
        prepare()
        failed.captured()
        val s = vm.authState.value as AuthState.Error
        assertSame(VaultError.BiometricAuthFailed, s.error)
        assertFalse(s.isFatal)
        verify(exactly = 0) { auth.setAuthenticated(true) }
    }

    @Test
    fun biometricErrorPreservesMessageWithoutAuthenticating() = runTest {
        prepare()
        error.captured("sensor locked")
        val s = vm.authState.value as AuthState.Error
        assertEquals("sensor locked", (s.error as VaultError.GenericError).messageOverride)
        assertFalse(s.isFatal)
        verify(exactly = 0) { auth.setAuthenticated(true) }
    }

    @Test
    fun permanentlyInvalidatedKeyIsFatal() = runTest {
        every { database.getPassphraseCipher() } throws KeyPermanentlyInvalidatedException()
        prepare()
        val s = vm.authState.value as AuthState.Error
        assertSame(VaultError.KeyInvalidated, s.error)
        assertTrue(s.isFatal)
    }

    @Test
    fun knownPreparationErrorIsPreserved() = runTest {
        every { database.getPassphraseCipher() } throws VaultError.UninitializedVault
        prepare()
        assertSame(VaultError.UninitializedVault, (vm.authState.value as AuthState.Error).error)
    }

    @Test
    fun unknownPreparationErrorRetainsCause() = runTest {
        val e = Exception()
        every { database.getPassphraseCipher() } throws e
        prepare()
        assertSame(
            e,
            ((vm.authState.value as AuthState.Error).error as VaultError.GenericError).cause,
        )
    }

    @Test
    fun promptConstructionFailureIsReported() = runTest {
        every {
            biometric.promptBiometricAuth(any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException()
        prepare()
        assertTrue((vm.authState.value as AuthState.Error).error is VaultError.GenericError)
    }

    @Test
    fun vaultInitializationFailureNeverAuthenticates() = runTest {
        coEvery { database.initializeDatabase(any()) } throws VaultError.UninitializedVault
        prepare()
        success.captured(result())
        runCurrent()
        assertSame(VaultError.UninitializedVault, (vm.authState.value as AuthState.Error).error)
        verify(exactly = 0) { auth.setAuthenticated(true) }
    }

    @Test
    fun unknownInitializationErrorIsDatabaseError() = runTest {
        val e = Exception()
        coEvery { database.initializeDatabase(any()) } throws e
        prepare()
        success.captured(result())
        runCurrent()
        assertSame(
            e,
            ((vm.authState.value as AuthState.Error).error as VaultError.DatabaseError).cause,
        )
    }

    @Test
    fun resetAfterErrorReturnsIdle() = runTest {
        prepare()
        failed.captured()
        vm.resetState()
        assertSame(AuthState.Idle, vm.authState.value)
    }

    @Test
    fun resetAfterSuccessReturnsIdle() = runTest {
        prepare()
        success.captured(result())
        runCurrent()
        vm.resetState()
        assertSame(AuthState.Idle, vm.authState.value)
    }

    @Test
    fun failedScanCanBeFollowedBySuccessfulScan() = runTest {
        prepare()
        failed.captured()
        success.captured(result())
        runCurrent()
        assertSame(AuthState.Success, vm.authState.value)
    }

    @Test
    fun knownWrappedPermanentlyInvalidatedKeyIsStillFatal() = runTest {
        every { database.getPassphraseCipher() } throws
            VaultError.GenericError(KeyPermanentlyInvalidatedException())
        prepare()
        val state = vm.authState.value as AuthState.Error
        assertSame(VaultError.KeyInvalidated, state.error)
        assertTrue(state.isFatal)
    }

    @Test
    fun cancellationDuringPreparationDoesNotPublishAuthenticationError() = runTest {
        every { database.getPassphraseCipher() } throws kotlinx.coroutines.CancellationException()
        prepare()
        assertSame(AuthState.Idle, vm.authState.value)
        verify(exactly = 0) { auth.setAuthenticated(true) }
    }

    @Test
    fun cancellationDuringInitializationNeverAuthenticates() = runTest {
        coEvery { database.initializeDatabase(any()) } throws
            kotlinx.coroutines.CancellationException()
        prepare()
        success.captured(result())
        runCurrent()
        assertSame(AuthState.Idle, vm.authState.value)
        verify(exactly = 0) { auth.setAuthenticated(true) }
    }

    @Test
    fun invalidatedKeyNestedThroughSeveralWrappersIsFatal() = runTest {
        every { database.getPassphraseCipher() } throws
            VaultError.GenericError(
                IllegalStateException(
                    VaultError.DatabaseError(KeyPermanentlyInvalidatedException())
                )
            )
        prepare()
        val state = vm.authState.value as AuthState.Error
        assertTrue(state.isFatal)
        assertSame(VaultError.KeyInvalidated, state.error)
    }

    @Test
    fun initializationKeyInvalidationIsFatalWithoutPublishingAuthentication() = runTest {
        coEvery { database.initializeDatabase(any()) } throws
            VaultError.DatabaseError(KeyPermanentlyInvalidatedException())
        prepare()
        success.captured(result())
        runCurrent()
        val state = vm.authState.value as AuthState.Error
        assertTrue(state.isFatal)
        assertSame(VaultError.KeyInvalidated, state.error)
        verify(exactly = 0) { auth.setAuthenticated(true) }
    }

    @Test
    fun malformedAuthenticationResultWithNonCipherCryptoObjectDoesNotUnlock() = runTest {
        val result = mockk<BiometricPrompt.AuthenticationResult>()
        val objectWithoutCipher = mockk<BiometricPrompt.CryptoObject>()
        every { objectWithoutCipher.cipher } returns null
        every { result.cryptoObject } returns objectWithoutCipher
        prepare()
        success.captured(result)
        runCurrent()
        assertTrue(vm.authState.value is AuthState.Error)
        verify(exactly = 0) { auth.setAuthenticated(true) }
    }

    @Test
    fun repeatedResetIsIdempotent() {
        repeat(5) { vm.resetState() }
        assertSame(AuthState.Idle, vm.authState.value)
    }
}
