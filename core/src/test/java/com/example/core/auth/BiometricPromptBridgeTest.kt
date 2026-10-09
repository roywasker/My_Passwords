package com.example.core.auth

import android.app.Application
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import io.mockk.*
import java.util.concurrent.Executor
import javax.crypto.Cipher
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests the actual adapter callbacks; only AndroidX prompt presentation is intercepted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BiometricPromptBridgeTest {
    private lateinit var activity: FragmentActivity
    private lateinit var controller:
        org.robolectric.android.controller.ActivityController<FragmentActivity>
    private lateinit var manager: BiometricAuthManager
    private lateinit var callback: BiometricPrompt.AuthenticationCallback
    private val policy = slot<BiometricPrompt.PromptInfo>()
    private val presentedCrypto = slot<BiometricPrompt.CryptoObject>()
    private val errors = mutableListOf<String>()
    private val successes = mutableListOf<BiometricPrompt.AuthenticationResult>()
    private var failed = 0
    private val callbackMatcher =
        object : Matcher<BiometricPrompt.AuthenticationCallback> {
            override fun match(arg: BiometricPrompt.AuthenticationCallback?): Boolean {
                if (arg == null) return false
                callback = arg
                return true
            }
        }

    @Before
    fun setup() {
        controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        activity = controller.get()
        val availability = mockk<BiometricManager>()
        every {
            availability.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_SUCCESS
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(activity) } returns availability
        mockkConstructor(BiometricPrompt::class)
        every {
            constructedWith<BiometricPrompt>(
                    EqMatcher(activity),
                    OfTypeMatcher<Executor>(Executor::class),
                    callbackMatcher,
                )
                .authenticate(capture(policy), capture(presentedCrypto))
        } just Runs
        manager = BiometricAuthManager(activity)
    }

    @After
    fun cleanup() {
        controller.pause().stop().destroy()
        unmockkAll()
    }

    private fun present(): BiometricPrompt.CryptoObject {
        val crypto = BiometricPrompt.CryptoObject(mockk<Cipher>())
        manager.promptBiometricAuth(
            activity,
            "Synthetic title",
            "Synthetic subtitle",
            "Cancel",
            crypto,
            { successes.add(it) },
            { errors.add(it) },
            { failed++ },
        )
        return crypto
    }

    @Test
    fun promptUsesStrongAuthenticationAndForwardsCipherAndLocalizedLabels() {
        val crypto = present()
        assertEquals("Synthetic title", policy.captured.title.toString())
        assertEquals("Synthetic subtitle", policy.captured.subtitle.toString())
        assertEquals("Cancel", policy.captured.negativeButtonText.toString())
        assertEquals(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
            policy.captured.allowedAuthenticators,
        )
        assertSame(crypto, presentedCrypto.captured)
        assertTrue(successes.isEmpty())
        assertTrue(errors.isEmpty())
        assertEquals(0, failed)
    }

    @Test
    fun successfulPromptForwardsExactAuthenticationResultOnlyToSuccessCallback() {
        present()
        val result = mockk<BiometricPrompt.AuthenticationResult>()
        callback.onAuthenticationSucceeded(result)
        assertSame(result, successes.single())
        assertTrue(errors.isEmpty())
        assertEquals(0, failed)
    }

    @Test
    fun errorConvertsCharSequenceToStringWithoutInvokingSuccess() {
        present()
        callback.onAuthenticationError(
            BiometricPrompt.ERROR_LOCKOUT,
            StringBuilder("Synthetic lockout"),
        )
        assertEquals(listOf("Synthetic lockout"), errors)
        assertTrue(successes.isEmpty())
        assertEquals(0, failed)
    }

    @Test
    fun failedScansDoNotBecomeFatalErrorsAndCanBeFollowedBySuccess() {
        present()
        repeat(3) { callback.onAuthenticationFailed() }
        val result = mockk<BiometricPrompt.AuthenticationResult>()
        callback.onAuthenticationSucceeded(result)
        assertEquals(3, failed)
        assertSame(result, successes.single())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun userCancellationForwardsErrorTextWithoutPublishingAuthentication() {
        present()
        callback.onAuthenticationError(
            BiometricPrompt.ERROR_USER_CANCELED,
            "Synthetic cancellation",
        )
        assertEquals(listOf("Synthetic cancellation"), errors)
        assertTrue(successes.isEmpty())
        assertEquals(0, failed)
    }
}
