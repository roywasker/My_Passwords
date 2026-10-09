package com.example.mypasswords.screens.password

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.PasswordRepository
import com.example.core.model.CategoryChip
import com.example.core.model.Credential
import com.example.core.model.PasswordItemModel
import com.example.core.model.VaultError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// UI State
data class PasswordUiState(
    val categories: List<CategoryChip> = emptyList(),
    val selectedCategoryId: Int = 1,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val itemToDelete: PasswordItemModel? = null,
    val selectedItemDetail: com.example.core.model.FullCredentialModel? = null,
    val itemToEdit: com.example.core.model.FullCredentialModel? = null,
    val error: VaultError? = null,
)

@HiltViewModel
class PasswordViewModel
@Inject
constructor(
    private val passwordRepository: PasswordRepository,
    private val application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PasswordUiState())
    val uiState: StateFlow<PasswordUiState> = _uiState.asStateFlow()
    private var detailRequestId = 0L
    private var editRequestId = 0L
    private var detailJob: kotlinx.coroutines.Job? = null
    private var editJob: kotlinx.coroutines.Job? = null

    // Derived state for filtered passwords based on search query and category
    val filteredPasswords: StateFlow<List<PasswordItemModel>> =
        combine(
                passwordRepository.passwords,
                _uiState,
            ) { passwords, state ->
                val categoryFiltered =
                    if (state.selectedCategoryId == 1) {
                        passwords
                    } else {
                        passwords.filter { it.categoryId == state.selectedCategoryId }
                    }

                if (state.searchQuery.isBlank()) {
                    categoryFiltered
                } else {
                    categoryFiltered.filter {
                        it.platform.contains(state.searchQuery, ignoreCase = true)
                    }
                }
            }
            .catch { e ->
                val vaultError = if (e is VaultError) e else VaultError.GenericError(e)
                _uiState.update { it.copy(error = vaultError) }
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val categories =
            passwordRepository.getCategories().map { category ->
                CategoryChip(category.id, application.getString(category.nameResId))
            }

        _uiState.update { currentState ->
            currentState.copy(
                categories = categories,
                selectedCategoryId = 1,
            )
        }
    }

    fun onCategorySelected(categoryId: Int) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun showDeleteConfirmation(item: PasswordItemModel) {
        _uiState.update { it.copy(itemToDelete = item) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(itemToDelete = null) }
    }

    fun confirmDelete() {
        val item = _uiState.value.itemToDelete ?: return
        viewModelScope.launch {
            try {
                passwordRepository.deletePassword(item.id)
                dismissDeleteConfirmation()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val vaultError = if (e is VaultError) e else VaultError.GenericError(e)
                _uiState.update { it.copy(error = vaultError) }
            }
        }
    }

    fun onToggleSearch() {
        _uiState.update {
            it.copy(
                isSearchActive = !it.isSearchActive,
                searchQuery = if (it.isSearchActive) "" else it.searchQuery,
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onPasswordClick(item: PasswordItemModel) {
        dismissPasswordDetail()
        val request = detailRequestId
        detailJob = viewModelScope.launch {
            try {
                val detail = passwordRepository.getDecryptedCredential(item.id)
                if (request != detailRequestId) {
                    detail?.clear()
                    return@launch
                }
                _uiState.update { it.copy(selectedItemDetail = detail) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val vaultError = if (e is VaultError) e else VaultError.GenericError(e)
                if (request == detailRequestId) _uiState.update { it.copy(error = vaultError) }
            }
        }
    }

    fun dismissPasswordDetail() {
        detailRequestId++
        detailJob?.cancel()
        detailJob = null
        _uiState.value.selectedItemDetail?.clear()
        _uiState.update { it.copy(selectedItemDetail = null) }
    }

    fun onEditClick(item: PasswordItemModel) {
        dismissEdit()
        val request = editRequestId
        editJob = viewModelScope.launch {
            try {
                val detail = passwordRepository.getDecryptedCredential(item.id)
                if (request != editRequestId) {
                    detail?.clear()
                    return@launch
                }
                _uiState.update { it.copy(itemToEdit = detail) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val vaultError = if (e is VaultError) e else VaultError.GenericError(e)
                if (request == editRequestId) _uiState.update { it.copy(error = vaultError) }
            }
        }
    }

    fun dismissEdit() {
        editRequestId++
        editJob?.cancel()
        editJob = null
        _uiState.value.itemToEdit?.clear()
        _uiState.update { it.copy(itemToEdit = null) }
    }

    fun updateCredential(username: String, password: String) {
        val currentEdit = _uiState.value.itemToEdit ?: return
        viewModelScope.launch {
            try {
                val updatedCredential =
                    Credential(
                        platformName = currentEdit.platform,
                        username = username.toByteArray(),
                        password = password.toByteArray(),
                        website = currentEdit.website,
                        categoryId = currentEdit.categoryId,
                    )
                try {
                    passwordRepository.updatePassword(currentEdit.id, updatedCredential)
                    if (_uiState.value.itemToEdit === currentEdit) dismissEdit()
                } finally {
                    updatedCredential.clear()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val vaultError = if (e is VaultError) e else VaultError.GenericError(e)
                _uiState.update { it.copy(error = vaultError) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        dismissPasswordDetail()
        dismissEdit()
    }
}
