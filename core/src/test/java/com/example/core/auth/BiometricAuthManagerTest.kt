package com.example.core.auth

import android.app.Application
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import io.mockk.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BiometricAuthManagerTest {
    private val context = mockk<Context>()
    private val activity = mockk<FragmentActivity>()
    private val platform = mockk<BiometricManager>()
    private lateinit var manager: BiometricAuthManager

    @Before
    fun setup() {
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(context) } returns platform
        every { activity.isFinishing } returns false
        every { activity.isDestroyed } returns false
        manager = BiometricAuthManager(context)
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun capabilityRequiresStrongBiometricsAndReturnsOriginalCode() {
        every { platform.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) } returns
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        assertEquals(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, manager.canAuthenticate())
        verify(exactly = 1) {
            platform.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        }
    }

    private fun prompt(
        onError: (String) -> Unit,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit = {},
        onFailed: () -> Unit = {},
    ) {
        manager.promptBiometricAuth(
            activity,
            "title",
            "subtitle",
            "cancel",
            mockk(),
            onSuccess,
            onError,
            onFailed,
        )
    }

    @Test
    fun everyUnavailableStatusCallsOnlyErrorWithExpectedExplanation() {
        val cases =
            mapOf(
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE to "No biometric hardware available",
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE to "Biometric hardware unavailable",
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED to "No biometrics enrolled",
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED to
                    "Security update required",
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED to "Biometric unsupported",
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN to "Biometric status unknown",
                999 to "Biometric authentication is not available (Code: 999)",
            )
        cases.forEach { (status, message) ->
            every { platform.canAuthenticate(any()) } returns status
            val errors = mutableListOf<String>()
            prompt({ errors.add(it) }, { fail("must not authenticate") }, { fail("must not scan") })
            assertEquals(listOf(message), errors)
        }
    }

    @Test
    fun finishingActivityDoesNotLaunchPromptOrInvokeCallbacks() {
        every { platform.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_SUCCESS
        every { activity.isFinishing } returns true
        prompt({ fail() }, { fail() }, { fail() })
    }

    @Test
    fun destroyedActivityDoesNotLaunchPromptOrInvokeCallbacks() {
        every { platform.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_SUCCESS
        every { activity.isDestroyed } returns true
        prompt({ fail() }, { fail() }, { fail() })
    }
}
