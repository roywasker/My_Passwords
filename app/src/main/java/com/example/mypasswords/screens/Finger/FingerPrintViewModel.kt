package com.example.mypasswords.screens.finger

import android.app.Application
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthRepository
import com.example.core.auth.BiometricAuthManager
import com.example.core.data.DatabaseManager
import com.example.core.model.VaultError
import com.example.mypasswords.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()

    object Success : AuthState()

    object ScanNotRecognized : AuthState()

    data class Error(val error: VaultError, val isFatal: Boolean = false) : AuthState()
}

@HiltViewModel
class FingerPrintViewModel
@Inject
constructor(
    private val databaseManager: DatabaseManager,
    private val authRepository: AuthRepository,
    private val biometricAuthManager: BiometricAuthManager,
    private val application: Application,
) : AndroidViewModel(application) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    private var requestId = 0L
    private var authenticationJob: kotlinx.coroutines.Job? = null
    private var recovery: DatabaseManager.RecoveryAttempt? = null

    fun prepareAuthentication(activity: FragmentActivity) {
        cancelAuthentication()
        _authState.value = AuthState.Idle
        val request = requestId
        val session = authRepository.sessionGeneration
        authenticationJob = viewModelScope.launch {
            try {
                val cipher = databaseManager.getPassphraseCipher()
                val cryptoObject = BiometricPrompt.CryptoObject(cipher)

                biometricAuthManager.promptBiometricAuth(
                    activity = activity,
                    title = application.getString(R.string.login_title),
                    subtitle = application.getString(R.string.login_subtitle),
                    negativeButtonText = application.getString(R.string.negative_button_text),
                    cryptoObject = cryptoObject,
                    onSuccess = { result ->
                        if (isCurrent(request, session))
                            onAuthenticationSucceeded(result, request, session)
                    },
                    onError = { error ->
                        if (isCurrent(request, session)) onAuthenticationError(error)
                    },
                    onFailed = { if (isCurrent(request, session)) onAuthenticationFailed() },
                )
            } catch (e: KeyPermanentlyInvalidatedException) {
                _authState.value =
                    AuthState.Error(
                        VaultError.KeyInvalidated,
                        isFatal = true,
                    )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = if (e is VaultError) e else VaultError.GenericError(e)
                _authState.value =
                    if (isKeyInvalidated(e))
                        AuthState.Error(VaultError.KeyInvalidated, isFatal = true)
                    else AuthState.Error(error)
            }
        }
    }

    private fun isCurrent(request: Long, session: Long) =
        request == requestId && session == authRepository.sessionGeneration

    private fun onAuthenticationSucceeded(
        result: BiometricPrompt.AuthenticationResult,
        request: Long,
        session: Long,
    ) {
        val cipher = result.cryptoObject?.cipher
        if (cipher != null) {
            val attempt = recovery
            authenticationJob = viewModelScope.launch {
                try {
                    if (attempt == null) databaseManager.initializeDatabase(cipher)
                    else databaseManager.completeRecovery(attempt, cipher)
                    if (!isCurrent(request, session)) return@launch
                    authRepository.setAuthenticated(true)
                    _authState.value = AuthState.Success
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!isCurrent(request, session)) return@launch
                    val error = if (e is VaultError) e else VaultError.DatabaseError(e)
                    _authState.value =
                        if (isKeyInvalidated(e))
                            AuthState.Error(VaultError.KeyInvalidated, isFatal = true)
                        else AuthState.Error(error)
                } finally {
                    attempt?.clear()
                    if (recovery === attempt) recovery = null
                }
            }
        } else {
            _authState.value =
                AuthState.Error(VaultError.GenericError(messageOverride = "Cipher not released"))
        }
    }

    private fun onAuthenticationError(error: String) {
        _authState.value = AuthState.Error(VaultError.GenericError(messageOverride = error))
    }

    private fun onAuthenticationFailed() {
        _authState.value = AuthState.ScanNotRecognized
    }

    fun prepareRecoveryAuthentication(activity: FragmentActivity, code: String) {
        cancelAuthentication()
        _authState.value = AuthState.Idle
        val request = requestId
        val session = authRepository.sessionGeneration
        authenticationJob = viewModelScope.launch {
            try {
                val attempt = databaseManager.prepareRecovery(code)
                if (!isCurrent(request, session)) {
                    attempt.clear()
                    return@launch
                }
                recovery = attempt
                biometricAuthManager.promptBiometricAuth(
                    activity,
                    application.getString(R.string.login_title),
                    application.getString(R.string.recovery_auth_subtitle),
                    application.getString(R.string.negative_button_text),
                    BiometricPrompt.CryptoObject(attempt.cipher),
                    {
                        if (isCurrent(request, session))
                            onAuthenticationSucceeded(it, request, session)
                    },
                    {
                        if (isCurrent(request, session)) {
                            recovery?.clear()
                            recovery = null
                            onAuthenticationError(it)
                        }
                    },
                    { if (isCurrent(request, session)) onAuthenticationFailed() },
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(request, session))
                    _authState.value =
                        AuthState.Error(
                            VaultError.GenericError(
                                messageOverride =
                                    application.getString(R.string.error_recovery_failed)
                            ),
                            isFatal = true,
                        )
            }
        }
    }

    fun cancelAuthentication() {
        requestId++
        authenticationJob?.cancel()
        authenticationJob = null
        recovery?.clear()
        recovery = null
        biometricAuthManager.cancelAuthentication()
    }

    override fun onCleared() {
        cancelAuthentication()
    }

    private fun isKeyInvalidated(error: Throwable): Boolean {
        var current: Throwable? = error
        val visited =
            java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        while (current != null && visited.add(current)) {
            if (
                current is KeyPermanentlyInvalidatedException ||
                    current === VaultError.KeyInvalidated
            )
                return true
            current = current.cause
        }
        return false
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
