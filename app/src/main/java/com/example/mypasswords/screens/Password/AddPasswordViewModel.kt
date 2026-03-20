package com.example.mypasswords.screens.password

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.PasswordRepository
import com.example.core.model.CategoryChip
import com.example.core.model.Credential
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
    val username: String = "",
    val password: String = "",
    val isSaveEnabled: Boolean = false,
    val isSaved: Boolean = false
)

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
        _uiState.update { it.copy(username = user, isSaveEnabled = validate(it.platformName, user, it.password)) }
    }

    fun onPasswordChanged(pass: String) {
        _uiState.update { it.copy(password = pass, isSaveEnabled = validate(it.platformName, it.username, pass)) }
    }

    fun onCategorySelected(categoryId: Int) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    private fun validate(platform: String, user: String, pass: String): Boolean {
        return platform.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
    }

    fun onSaveClick() {
        val state = _uiState.value
        if (state.isSaveEnabled) {
            viewModelScope.launch {
                val credential = Credential(
                    platformName = state.platformName,
                    username = state.username.toByteArray(),
                    password = state.password.toByteArray(),
                    website = "", // Optional for now
                    categoryId = state.selectedCategoryId
                )
                passwordRepository.addPassword(credential)
                credential.clear() // Security: clear sensitive bytes from memory
                _uiState.update { it.copy(isSaved = true) }
            }
        }
    }
}
