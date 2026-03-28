package com.example.mypasswords.screens.password

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.PasswordRepository
import com.example.core.model.CategoryChip
import com.example.core.model.Credential
import com.example.core.util.PasswordGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddPasswordUiState(
    val categories: List<CategoryChip> = emptyList(),
    val selectedCategoryId: Int = 1,
    val platformName: String = "",
    val username: CharArray = charArrayOf(),
    val password: CharArray = charArrayOf(),
    val isSaveEnabled: Boolean = false,
    val isSaved: Boolean = false,
    val error: com.example.core.model.VaultError? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AddPasswordUiState

        if (selectedCategoryId != other.selectedCategoryId) return false
        if (isSaveEnabled != other.isSaveEnabled) return false
        if (isSaved != other.isSaved) return false
        if (error != other.error) return false
        if (categories != other.categories) return false
        if (platformName != other.platformName) return false
        if (!username.contentEquals(other.username)) return false
        if (!password.contentEquals(other.password)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = selectedCategoryId
        result = 31 * result + isSaveEnabled.hashCode()
        result = 31 * result + isSaved.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + categories.hashCode()
        result = 31 * result + platformName.hashCode()
        result = 31 * result + username.contentHashCode()
        result = 31 * result + password.contentHashCode()
        return result
    }
}

@HiltViewModel
class AddPasswordViewModel @Inject constructor(
    private val passwordRepository: PasswordRepository,
    private val application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AddPasswordUiState())
    val uiState: StateFlow<AddPasswordUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        val categories = passwordRepository.getCategories().map { category ->
            CategoryChip(category.id, application.getString(category.nameResId))
        }
        _uiState.update { it.copy(categories = categories) }
    }

    fun onPlatformNameChanged(name: String) {
        _uiState.update { it.copy(platformName = name, isSaveEnabled = validate(name, it.username, it.password)) }
    }

    fun onUsernameChanged(user: String) {
        val userArray = user.toCharArray()
        _uiState.update { it.copy(username = userArray, isSaveEnabled = validate(it.platformName, userArray, it.password)) }
    }

    fun onPasswordChanged(pass: String) {
        val passArray = pass.toCharArray()
        _uiState.update { it.copy(password = passArray, isSaveEnabled = validate(it.platformName, it.username, passArray)) }
    }

    fun onGeneratePasswordClicked() {
        val generatedPassword = PasswordGenerator.generate()
        onPasswordChanged(generatedPassword)
    }

    fun onCategorySelected(categoryId: Int) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    private fun validate(platform: String, user: CharArray, pass: CharArray): Boolean {
        return platform.isNotBlank() && user.isNotEmpty() && pass.isNotEmpty()
    }

    fun onSaveClick() {
        val state = _uiState.value
        if (state.isSaveEnabled) {
            viewModelScope.launch {
                try {
                    val domain = state.platformName.trim().lowercase()
                        .replace("\\s+".toRegex(), "") // Remove all spaces
                        .replace("[^a-z0-9]".toRegex(), "") // Remove non-alphanumeric
                    
                    val iconUrl = if (domain.length >= 3) {
                        "https://www.google.com/s2/favicons?domain=${domain}.com&sz=128"
                    } else {
                        ""
                    }
                    
                    // Convert CharArray to ByteArray for encryption
                    val usernameBytes = String(state.username).toByteArray()
                    val passwordBytes = String(state.password).toByteArray()
                    
                    val credential = Credential(
                        platformName = state.platformName,
                        username = usernameBytes,
                        password = passwordBytes,
                        website = iconUrl,
                        categoryId = state.selectedCategoryId
                    )
                    
                    passwordRepository.addPassword(credential)
                    
                    // Clear all sensitive data
                    credential.clear()
                    usernameBytes.fill(0)
                    passwordBytes.fill(0)
                    state.username.fill(' ')
                    state.password.fill(' ')
                    
                    _uiState.update { it.copy(
                        isSaved = true,
                        username = charArrayOf(),
                        password = charArrayOf(),
                        error = null
                    ) }
                } catch (e: Exception) {
                    val vaultError = if (e is com.example.core.model.VaultError) e else com.example.core.model.VaultError.GenericError(e)
                    _uiState.update { it.copy(error = vaultError) }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
