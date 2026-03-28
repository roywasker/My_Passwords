package com.example.core.model

sealed class VaultError : Exception() {
    object BiometricHardwareMissing : VaultError()
    object BiometricNotEnrolled : VaultError()
    object BiometricAuthFailed : VaultError()
    object KeyInvalidated : VaultError()
    object UninitializedVault : VaultError()
    
    data class EncryptionError(override val cause: Throwable) : VaultError()
    data class DecryptionError(override val cause: Throwable) : VaultError()
    data class DatabaseError(override val cause: Throwable) : VaultError()
    data class GenericError(override val cause: Throwable? = null, val messageOverride: String? = null) : VaultError()
}
