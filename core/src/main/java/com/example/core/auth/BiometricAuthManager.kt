package com.example.core.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricAuthManager @Inject constructor(@ApplicationContext private val context: Context) {
    private var activePrompt: BiometricPrompt? = null

    fun cancelAuthentication() {
        val prompt = activePrompt
        activePrompt = null
        prompt?.cancelAuthentication()
    }

    fun canAuthenticate(): Int {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    }

    fun promptBiometricAuth(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        cryptoObject: BiometricPrompt.CryptoObject,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit,
    ) {
        val authStatus = canAuthenticate()
        if (authStatus != BiometricManager.BIOMETRIC_SUCCESS) {
            val errorMessage =
                when (authStatus) {
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                        "No biometric hardware available"
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                        "Biometric hardware unavailable"
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometrics enrolled"
                    BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                        "Security update required"
                    BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "Biometric unsupported"
                    BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "Biometric status unknown"
                    else -> "Biometric authentication is not available (Code: $authStatus)"
                }
            onError(errorMessage)
            return
        }

        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        val executor = ContextCompat.getMainExecutor(context)

        var currentPrompt: BiometricPrompt? = null
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (activePrompt === currentPrompt) activePrompt = null
                    onError(errString.toString())
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    if (activePrompt === currentPrompt) activePrompt = null
                    onSuccess(result)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        cancelAuthentication()
        currentPrompt = biometricPrompt
        activePrompt = biometricPrompt

        val promptInfo =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }
}
