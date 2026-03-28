package com.example.mypasswords.util

import android.content.Context
import com.example.core.model.VaultError
import com.example.mypasswords.R

fun VaultError.toMessage(context: Context): String {
    val resId = when (this) {
        VaultError.BiometricHardwareMissing -> R.string.error_biometric_hardware_missing
        VaultError.BiometricNotEnrolled -> R.string.error_biometric_not_enrolled
        VaultError.BiometricAuthFailed -> R.string.error_biometric_auth_failed
        VaultError.KeyInvalidated -> R.string.error_key_invalidated
        VaultError.UninitializedVault -> R.string.error_uninitialized_vault
        is VaultError.EncryptionError -> R.string.error_encryption_failed
        is VaultError.DecryptionError -> R.string.error_decryption_failed
        is VaultError.DatabaseError -> R.string.error_database_access
        is VaultError.GenericError -> {
            return messageOverride ?: context.getString(R.string.error_generic)
        }
    }
    return context.getString(resId)
}
