package com.example.mypasswords.screens.Finger

import android.app.Application
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthRepository
import com.example.core.auth.BiometricAuthManager
import com.example.core.data.DatabaseManager
import com.example.mypasswords.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class FingerPrintViewModel @Inject constructor(
    private val databaseManager: DatabaseManager,
    private val authRepository: AuthRepository,
    private val biometricAuthManager: BiometricAuthManager,
    private val application: Application
) : AndroidViewModel(application) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun prepareAuthentication(activity: FragmentActivity) {
        viewModelScope.launch {
            try {
                val cipher = databaseManager.getPassphraseCipher()
                val cryptoObject = BiometricPrompt.CryptoObject(cipher)

                biometricAuthManager.promptBiometricAuth(
                    activity = activity,
                    title = application.getString(R.string.login_title),
                    subtitle = application.getString(R.string.login_subtitle),
                    negativeButtonText = application.getString(R.string.negative_button_text),
                    cryptoObject = cryptoObject,
                    onSuccess = { result -> onAuthenticationSucceeded(result) },
                    onError = { error -> onAuthenticationError(error) },
                    onFailed = { /* Handle failure */ }
                )
            } catch (e: Exception) {
                _authState.value = AuthState.Error(application.getString(R.string.fingerprint_auth_error_security))
            }
        }
    }

    private fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        val cipher = result.cryptoObject?.cipher
        if (cipher != null) {
            viewModelScope.launch {
                try {
                    databaseManager.initializeDatabase(cipher)
                    authRepository.setAuthenticated(true)
                    _authState.value = AuthState.Success
                } catch (e: Exception) {
                    _authState.value = AuthState.Error("Failed to initialize vault database")
                }
            }
        } else {
            _authState.value = AuthState.Error(application.getString(R.string.fingerprint_auth_error_cipher_not_released))
        }
    }

    private fun onAuthenticationError(error: String) {
        _authState.value = AuthState.Error(error)
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
