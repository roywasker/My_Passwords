package com.example.mypasswords.screens.Password

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.PasswordRepository
import com.example.core.model.CategoryChip
import com.example.core.model.PasswordItemModel
import com.example.mypasswords.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// UI State
data class PasswordUiState(
    val categories: List<CategoryChip> = emptyList(),
    val selectedCategoryId: Int = 1,
    val isSearchActive: Boolean = false,
    val searchQuery: String = ""
)

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val passwordRepository: PasswordRepository,
    private val application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PasswordUiState())
    val uiState: StateFlow<PasswordUiState> = _uiState.asStateFlow()

    // Derived state for filtered passwords based on search query
    val filteredPasswords: StateFlow<List<PasswordItemModel>> = combine(
        passwordRepository.passwords,
        _uiState
    ) { passwords, state ->
        if (state.searchQuery.isBlank()) {
            passwords
        } else {
            passwords.filter {
                it.platform.contains(state.searchQuery, ignoreCase = true) ||
                it.userName.contains(state.searchQuery, ignoreCase = true)
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
        val initialCategories = listOf(
            CategoryChip(1, application.getString(R.string.category_all)),
            CategoryChip(2, application.getString(R.string.category_work)),
            CategoryChip(3, application.getString(R.string.category_personal)),
            CategoryChip(4, application.getString(R.string.category_finance))
        )

        _uiState.update { currentState ->
            currentState.copy(
                categories = initialCategories,
                selectedCategoryId = 1
            )
        }
    }

    fun onCategorySelected(categoryId: Int) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun onAddPassword(item: PasswordItemModel) {
        passwordRepository.addPassword(item)
    }

    fun onDeletePassword(passwordId: Int) {
        passwordRepository.deletePassword(passwordId)
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
