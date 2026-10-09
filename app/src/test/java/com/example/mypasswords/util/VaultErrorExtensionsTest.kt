package com.example.mypasswords.util

import android.content.Context
import com.example.core.model.VaultError
import com.example.mypasswords.R
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class VaultErrorExtensionsTest {
    private val context = mockk<Context>()

    @Test
    fun eachTypedErrorMapsToItsLocalizedResource() {
        every { context.getString(any()) } answers { "resource-${firstArg<Int>()}" }
        val cause = Exception()
        val cases =
            listOf(
                VaultError.BiometricHardwareMissing to R.string.error_biometric_hardware_missing,
                VaultError.BiometricNotEnrolled to R.string.error_biometric_not_enrolled,
                VaultError.BiometricAuthFailed to R.string.error_biometric_auth_failed,
                VaultError.KeyInvalidated to R.string.error_key_invalidated,
                VaultError.UninitializedVault to R.string.error_uninitialized_vault,
                VaultError.EncryptionError(cause) to R.string.error_encryption_failed,
                VaultError.DecryptionError(cause) to R.string.error_decryption_failed,
                VaultError.DatabaseError(cause) to R.string.error_database_access,
                VaultError.GenericError(cause) to R.string.error_generic,
            )
        cases.forEach { (e, id) -> assertEquals("resource-$id", e.toMessage(context)) }
    }

    @Test
    fun genericOverrideIsReturnedWithoutResourceLookup() {
        assertEquals(
            "custom",
            VaultError.GenericError(messageOverride = "custom").toMessage(context),
        )
        verify(exactly = 0) { context.getString(any()) }
    }

    @Test
    fun emptyOverrideRemainsEmpty() {
        assertEquals("", VaultError.GenericError(messageOverride = "").toMessage(context))
    }

    @Test
    fun internalCauseMessageIsNeverShownToUser() {
        every { context.getString(R.string.error_generic) } returns "localized"
        assertEquals(
            "localized",
            VaultError.GenericError(Exception("synthetic internal details")).toMessage(context),
        )
    }
}
