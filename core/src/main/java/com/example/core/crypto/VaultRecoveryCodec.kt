package com.example.core.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/** A random 256-bit recovery secret wraps the vault key; only the wrapped value is persisted. */
class VaultRecoveryCodec @Inject constructor() {
    class Envelope(val code: String, val ciphertext: String, val iv: String) {
        override fun toString() = "RecoveryEnvelope([redacted])"
    }

    fun protect(vaultKey: ByteArray): Envelope {
        require(vaultKey.size == 32)
        val recoveryKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(recoveryKey, "AES"))
            return Envelope(
                Base64.encodeToString(
                    recoveryKey,
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                ),
                Base64.encodeToString(cipher.doFinal(vaultKey), Base64.NO_WRAP),
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            )
        } finally {
            recoveryKey.fill(0)
        }
    }

    fun recover(code: String, ciphertext: String, iv: String): ByteArray {
        require(code.trim().matches(Regex("[A-Za-z0-9_-]{43}"))) { "Invalid recovery code" }
        val key = Base64.decode(code.trim(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        try {
            require(key.size == 32)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            val result = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
            if (result.size != 32) {
                result.fill(0)
                error("Invalid recovered vault key")
            }
            return result
        } finally {
            key.fill(0)
        }
    }
}
