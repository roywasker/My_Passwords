package com.example.mypasswords.screens.password

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.PasswordRepository
import com.example.core.model.CategoryChip
import com.example.core.model.PasswordItemModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// UI State
data class PasswordUiState(
    val categories: List<CategoryChip> = emptyList(),
    val selectedCategoryId: Int = 1,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val itemToDelete: PasswordItemModel? = null
)

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val passwordRepository: PasswordRepository,
    private val application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PasswordUiState())
    val uiState: StateFlow<PasswordUiState> = _uiState.asStateFlow()

    // Derived state for filtered passwords based on search query and category
    val filteredPasswords: StateFlow<List<PasswordItemModel>> = combine(
        passwordRepository.passwords,
        _uiState
    ) { passwords, state ->
        val categoryFiltered = if (state.selectedCategoryId == 1) {
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val categories = passwordRepository.getCategories().map { category ->
            CategoryChip(category.id, application.getString(category.nameResId))
        }
        
        _uiState.update { currentState ->
            currentState.copy(
                categories = categories,
                selectedCategoryId = 1
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
            passwordRepository.deletePassword(item.id)
            dismissDeleteConfirmation()
        }
    }

    fun onToggleSearch() {
        _uiState.update { it.copy(
            isSearchActive = !it.isSearchActive,
            searchQuery = if (it.isSearchActive) "" else it.searchQuery
        ) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
}
