package com.example.mypasswords.screens.password

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.PasswordRepository
import com.example.core.model.*
import com.example.core.model.CategoryChip
import com.example.core.model.Credential
import com.example.core.model.PasswordItemModel
import com.example.core.model.VaultError
import com.example.core.util.PasswordHealthReport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PasswordListStatus {
    data object Loading : PasswordListStatus

    data object Empty : PasswordListStatus

    data object Ready : PasswordListStatus

    data class Failed(val error: VaultError) : PasswordListStatus
}

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
    val isUpdating: Boolean = false,
    val listStatus: PasswordListStatus = PasswordListStatus.Loading,
    val isRecoveryDialogVisible: Boolean = false,
    val recoveryCode: String? = null,
    val isCreatingRecoveryCode: Boolean = false,
    val favoritesOnly: Boolean = false,
    val selectedTag: String? = null,
    val sort: PasswordSort = PasswordSort.ORIGINAL,
    val availableTags: List<String> = emptyList(),
    val showHealth: Boolean = false,
    val healthReport: PasswordHealthReport? = null,
    val showTrash: Boolean = false,
    val trashItems: List<PasswordItemModel> = emptyList(),
    val historyCredentialId: Int? = null,
    val historyPlatform: String = "",
    val historyItems: List<PasswordHistoryItem> = emptyList(),
    val tagsItem: FullCredentialModel? = null,
    val featureBusy: Boolean = false,
    val featureError: VaultError? = null,
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
    private val reloadList = MutableStateFlow(0L)
    private var featureRequest = 0L
    private var featureJob: kotlinx.coroutines.Job? = null

    // Derived state for filtered passwords based on search query and category
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredPasswords: StateFlow<List<PasswordItemModel>> =
        combine(
                reloadList.flatMapLatest {
                    passwordRepository.passwords
                        .map { rows ->
                            _uiState.update {
                                it.copy(
                                    listStatus =
                                        if (rows.isEmpty()) PasswordListStatus.Empty
                                        else PasswordListStatus.Ready,
                                    availableTags =
                                        rows
                                            .flatMap { it.tags }
                                            .distinctBy { it.lowercase(java.util.Locale.ROOT) }
                                            .sorted(),
                                )
                            }
                            rows
                        }
                        .onStart {
                            _uiState.update { it.copy(listStatus = PasswordListStatus.Loading) }
                            emit(emptyList())
                        }
                        .catch { e ->
                            val error = if (e is VaultError) e else VaultError.GenericError(e)
                            _uiState.update {
                                it.copy(
                                    error = error,
                                    listStatus = PasswordListStatus.Failed(error),
                                )
                            }
                            emit(emptyList())
                        }
                },
                _uiState,
            ) { passwords, state ->
                PasswordQuery.apply(
                    passwords,
                    state.searchQuery,
                    state.selectedCategoryId,
                    state.favoritesOnly,
                    state.selectedTag,
                    state.sort,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000, replayExpirationMillis = 0),
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

    fun toggleFavoritesOnly() {
        _uiState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    }

    fun selectTag(tag: String?) {
        _uiState.update { it.copy(selectedTag = tag) }
    }

    fun selectSort(sort: PasswordSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    fun dismissFeatures() {
        featureRequest++
        featureJob?.cancel()
        featureJob = null
        _uiState.update {
            it.copy(
                showHealth = false,
                healthReport = null,
                showTrash = false,
                trashItems = emptyList(),
                historyCredentialId = null,
                historyItems = emptyList(),
                tagsItem = null,
                featureBusy = false,
                featureError = null,
            )
        }
    }

    private fun <T> feature(
        block: suspend () -> T,
        apply: (PasswordUiState, T) -> PasswordUiState,
    ) {
        if (_uiState.value.featureBusy) return
        val request = featureRequest
        val session = passwordRepository.sessionGeneration()
        _uiState.update { it.copy(featureBusy = true, featureError = null) }
        featureJob = viewModelScope.launch {
            try {
                val result = block()
                if (request == featureRequest && session == passwordRepository.sessionGeneration())
                    _uiState.update { apply(it, result) }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (request == featureRequest && session == passwordRepository.sessionGeneration())
                    _uiState.update {
                        it.copy(
                            featureError =
                                if (error is VaultError) error else VaultError.GenericError(error)
                        )
                    }
            } finally {
                if (request == featureRequest) _uiState.update { it.copy(featureBusy = false) }
            }
        }
    }

    fun showHealthReport() {
        dismissFeatures()
        _uiState.update { it.copy(showHealth = true) }
        feature({ passwordRepository.healthReport() }) { state, report ->
            state.copy(healthReport = report)
        }
    }

    fun showTrash() {
        dismissFeatures()
        _uiState.update { it.copy(showTrash = true) }
        feature({ passwordRepository.trashItems() }) { state, items ->
            state.copy(trashItems = items)
        }
    }

    fun restoreTrash(id: Int) =
        feature({
            passwordRepository.restoreTrash(id)
            passwordRepository.trashItems()
        }) { state, items ->
            state.copy(trashItems = items)
        }

    fun permanentlyDelete(id: Int) =
        feature({
            passwordRepository.permanentlyDelete(id)
            passwordRepository.trashItems()
        }) { state, items ->
            state.copy(trashItems = items)
        }

    fun showHistory(id: Int, platform: String) {
        dismissPasswordDetail()
        dismissFeatures()
        _uiState.update { it.copy(historyCredentialId = id, historyPlatform = platform) }
        feature({ passwordRepository.history(id) }) { state, items ->
            state.copy(historyItems = items)
        }
    }

    fun restoreHistory(historyId: Int) {
        val id = _uiState.value.historyCredentialId ?: return
        feature({
            passwordRepository.restoreHistory(id, historyId)
            passwordRepository.history(id)
        }) { state, items ->
            state.copy(historyItems = items)
        }
    }

    fun editTags(detail: FullCredentialModel) {
        val metadata = detail.copy(username = charArrayOf(), password = charArrayOf())
        dismissPasswordDetail()
        dismissFeatures()
        _uiState.update { it.copy(tagsItem = metadata) }
    }

    fun saveTags(input: String) {
        val id = _uiState.value.tagsItem?.id ?: return
        feature({ passwordRepository.setTags(id, input) }) { state, _ ->
            state.copy(tagsItem = null)
        }
    }

    fun toggleFavorite(id: Int, favorite: Boolean) {
        feature({ passwordRepository.setFavorite(id, favorite) }) { state, _ ->
            state.copy(
                selectedItemDetail =
                    state.selectedItemDetail?.let {
                        if (it.id == id) it.copy(isFavorite = favorite) else it
                    }
            )
        }
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
        if (_uiState.value.isUpdating) return
        if (
            !com.example.core.model.CredentialValidator.isValid(
                currentEdit.platform,
                username,
                password,
            )
        ) {
            _uiState.update {
                it.copy(
                    error =
                        VaultError.GenericError(
                            messageOverride =
                                application.getString(
                                    com.example.mypasswords.R.string.error_required_fields
                                )
                        )
                )
            }
            return
        }
        _uiState.update { it.copy(isUpdating = true) }
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
                if (_uiState.value.itemToEdit === currentEdit)
                    _uiState.update { it.copy(error = vaultError) }
            } finally {
                _uiState.update { it.copy(isUpdating = false) }
            }
        }
    }

    fun retryLoadingPasswords() {
        _uiState.update { it.copy(error = null, listStatus = PasswordListStatus.Loading) }
        reloadList.update { it + 1 }
    }

    fun showRecoveryDialog() {
        _uiState.update { it.copy(isRecoveryDialogVisible = true) }
    }

    fun dismissRecoveryDialog() {
        _uiState.update { it.copy(isRecoveryDialogVisible = false, recoveryCode = null) }
    }

    fun createRecoveryCode() {
        if (_uiState.value.isCreatingRecoveryCode) return
        _uiState.update { it.copy(isCreatingRecoveryCode = true) }
        viewModelScope.launch {
            try {
                val code = passwordRepository.createRecoveryCode()
                _uiState.update {
                    if (it.isRecoveryDialogVisible) it.copy(recoveryCode = code) else it
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(error = VaultError.GenericError(error)) }
            } finally {
                _uiState.update { it.copy(isCreatingRecoveryCode = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        dismissFeatures()
        dismissRecoveryDialog()
        dismissPasswordDetail()
        dismissEdit()
    }
}
