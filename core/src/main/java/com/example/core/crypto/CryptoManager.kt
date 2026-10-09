package com.example.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CryptoManager @Inject constructor() {

    private val provider = "AndroidKeyStore"
    private val keyAlias = "VaultMasterKey"
    private val transformation = "AES/GCM/NoPadding"
    private val tagLength = 128

    private val keyStore = KeyStore.getInstance(provider).apply { load(null) }

    init {
        generateMasterKey()
    }

    private fun generateMasterKey() {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider)
            val spec =
                KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey =
        (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey

    /** Used ONLY for wrapping/unwrapping the Vault Key via Biometrics. */
    fun getMasterEncryptionCipher(): Cipher {
        return Cipher.getInstance(transformation).apply {
            init(Cipher.ENCRYPT_MODE, getSecretKey())
        }
    }

    /** Used ONLY for wrapping/unwrapping the Vault Key via Biometrics. */
    fun getMasterDecryptionCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(transformation)
        val spec = GCMParameterSpec(tagLength, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        return cipher
    }

    /**
     * Encrypts data using a session-scoped Vault Key (No biometric prompt needed for individual
     * fields).
     */
    suspend fun encryptWithKey(plainBytes: ByteArray, key: ByteArray): Pair<String, String> =
        withContext(Dispatchers.IO) {
            try {
                val cipher = Cipher.getInstance(transformation)
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))

                val encryptedBytes = cipher.doFinal(plainBytes)
                val iv = cipher.iv

                Pair(
                    Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
                    Base64.encodeToString(iv, Base64.NO_WRAP),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw com.example.core.model.VaultError.EncryptionError(e)
            }
        }

    /** Decrypts data using a session-scoped Vault Key. */
    suspend fun decryptWithKey(
        encryptedBase64: String,
        ivBase64: String,
        key: ByteArray,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            try {
                val cipher = Cipher.getInstance(transformation)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val spec = GCMParameterSpec(tagLength, iv)

                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)

                val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                cipher.doFinal(encryptedBytes)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw com.example.core.model.VaultError.DecryptionError(e)
            }
        }

    /** Internal encryption for wrapping keys using the authenticated Master Cipher. */
    suspend fun wrapKey(plainBytes: ByteArray, authenticatedCipher: Cipher): Pair<String, String> =
        withContext(Dispatchers.IO) {
            try {
                val encryptedBytes = authenticatedCipher.doFinal(plainBytes)
                val iv = authenticatedCipher.iv
                Pair(
                    Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
                    Base64.encodeToString(iv, Base64.NO_WRAP),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw com.example.core.model.VaultError.EncryptionError(e)
            }
        }

    /** Internal decryption for unwrapping keys using the authenticated Master Cipher. */
    suspend fun unwrapKey(encryptedBase64: String, authenticatedCipher: Cipher): ByteArray =
        withContext(Dispatchers.IO) {
            try {
                val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                authenticatedCipher.doFinal(encryptedBytes)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw com.example.core.model.VaultError.DecryptionError(e)
            }
        }
}
