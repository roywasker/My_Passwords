package com.example.core.crypto

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VaultRecoveryCodecTest {
    @get:org.junit.Rule val hostCrypto = com.example.core.testing.HostCryptoProviderRule()
    private val codec = VaultRecoveryCodec()
    private val key = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun codeRoundTripsExactVaultKeyWithoutMutatingInput() {
        val envelope = codec.protect(key)
        assertEquals(43, envelope.code.length)
        assertArrayEquals(key, codec.recover(envelope.code, envelope.ciphertext, envelope.iv))
        assertTrue(key.all { it != 0.toByte() })
        assertFalse(envelope.toString().contains(envelope.code))
    }

    @Test
    fun everyEnvelopeUsesDifferentSecretIvAndCiphertext() {
        val a = codec.protect(key)
        val b = codec.protect(key)
        assertNotEquals(a.code, b.code)
        assertNotEquals(a.iv, b.iv)
        assertNotEquals(a.ciphertext, b.ciphertext)
    }

    @Test
    fun unrelatedCodeCannotDecryptEnvelope() {
        val a = codec.protect(key)
        val b = codec.protect(key)
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            codec.recover(b.code, a.ciphertext, a.iv)
        }
    }

    @Test
    fun corruptCiphertextAndIvAreRejected() {
        val a = codec.protect(key)
        val bytes = android.util.Base64.decode(a.ciphertext, android.util.Base64.NO_WRAP)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            codec.recover(
                a.code,
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
                a.iv,
            )
        }
        val iv = android.util.Base64.decode(a.iv, android.util.Base64.NO_WRAP)
        iv[0] = (iv[0].toInt() xor 1).toByte()
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            codec.recover(
                a.code,
                a.ciphertext,
                android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP),
            )
        }
    }

    @Test
    fun malformedCodesAreRejected() {
        val a = codec.protect(key)
        for (code in listOf("", "A".repeat(42), "A".repeat(44), "!".repeat(43))) {
            assertThrows(IllegalArgumentException::class.java) {
                codec.recover(code, a.ciphertext, a.iv)
            }
        }
    }

    @Test
    fun surroundingWhitespaceDoesNotInvalidatePastedCode() {
        val a = codec.protect(key)
        assertArrayEquals(key, codec.recover("  ${a.code}\n", a.ciphertext, a.iv))
    }

    @Test
    fun unexpectedVaultKeySizeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { codec.protect(ByteArray(16)) }
    }
}
