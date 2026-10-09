package com.example.core.crypto

import android.app.Application
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.mockk.*
import java.security.KeyStore
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checks the requested Android Keystore policy using mocks, not device key material. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CryptoMasterKeyConfigurationTest {
    private val store = mockk<KeyStore>(relaxed = true)
    private val generator = mockk<KeyGenerator>()
    private val spec = slot<AlgorithmParameterSpec>()

    @Before
    fun setup() {
        mockkStatic(KeyStore::class, KeyGenerator::class)
        every { KeyStore.getInstance("AndroidKeyStore") } returns store
        every { store.containsAlias("VaultMasterKey") } returns false
        every {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        } returns generator
        every { generator.init(capture(spec)) } just Runs
        every { generator.generateKey() } returns mockk<SecretKey>()
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun creates256BitGcmKeyRequiringAuthenticationAndEnrollmentInvalidation() {
        CryptoManager()
        val s = spec.captured as KeyGenParameterSpec
        assertEquals("VaultMasterKey", s.keystoreAlias)
        assertEquals(256, s.keySize)
        assertEquals(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT, s.purposes)
        assertArrayEquals(arrayOf(KeyProperties.BLOCK_MODE_GCM), s.blockModes)
        assertArrayEquals(arrayOf(KeyProperties.ENCRYPTION_PADDING_NONE), s.encryptionPaddings)
        assertTrue(s.isUserAuthenticationRequired)
        assertTrue(s.isInvalidatedByBiometricEnrollment)
        verify(exactly = 1) { generator.generateKey() }
    }

    @Test
    fun existingMasterKeyIsNotRegenerated() {
        every { store.containsAlias("VaultMasterKey") } returns true
        CryptoManager()
        verify(exactly = 0) { generator.generateKey() }
    }

    @Test
    fun generationFailureIsNotSilentlyIgnored() {
        val failure = IllegalStateException("synthetic key generation failure")
        every { generator.generateKey() } throws failure
        assertSame(failure, assertThrows(IllegalStateException::class.java) { CryptoManager() })
    }
}
