package com.example.core

import com.example.core.model.Credential
import com.example.core.util.PasswordGenerator
import org.junit.Assert.*
import org.junit.Test

/** Regression contracts for repaired production defects. */
@org.junit.runner.RunWith(org.junit.runners.BlockJUnit4ClassRunner::class)
class KnownDefectsTest {
    @Test
    fun rejectsNegativeLength() {
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(-1) }
    }

    @Test
    fun rejectsZeroLength() {
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(0) }
    }

    @Test
    fun rejectsLengthBelowMandatoryCharacterCount() {
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(3) }
    }

    @Test
    fun categoryParticipatesInCredentialEquality() {
        val c = Credential("p", byteArrayOf(1), byteArrayOf(2), categoryId = 2)
        assertNotEquals(c, c.copy(categoryId = 3))
    }

    @Test
    fun cancelledWriteRetainsCancellationException() =
        kotlinx.coroutines.runBlocking<Unit> {
            val manager = io.mockk.mockk<com.example.core.data.DatabaseManager>()
            val crypto = io.mockk.mockk<com.example.core.crypto.CryptoManager>()
            val key = ByteArray(32)
            io.mockk.every { manager.session } returns com.example.core.auth.VaultSession()
            io.mockk.every { manager.isInitialized } returns
                kotlinx.coroutines.flow.MutableStateFlow(true)
            io.mockk.every { manager.getSessionVaultKey() } returns key
            io.mockk.coEvery { crypto.encryptWithKey(any(), key) } throws
                kotlinx.coroutines.CancellationException("cancelled")
            val repository = com.example.core.data.PasswordRepository(manager, crypto)
            val error =
                try {
                    repository.addPassword(Credential("p", byteArrayOf(1), byteArrayOf(2)))
                    null
                } catch (e: Exception) {
                    e
                }
            assertTrue(error is kotlinx.coroutines.CancellationException)
        }
}
