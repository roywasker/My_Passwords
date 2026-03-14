package com.example.core.data

import com.example.core.model.PasswordItemModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordRepository @Inject constructor() {
    
    // In a real app, this would come from Room or a remote source
    private val _passwords = MutableStateFlow<List<PasswordItemModel>>(emptyList())
    val passwords: Flow<List<PasswordItemModel>> = _passwords.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        val initialPasswords = listOf(
            PasswordItemModel(1, "Google", "user@google.com", letter = "G", website = "google.com"),
            PasswordItemModel(2, "FaceLpook", "fb_user_it3", letter = "f", website = "facebook.com"),
            PasswordItemModel(3, "Instagram", "@insta_handle", letter = "I", website = "instagram.com"),
            PasswordItemModel(4, "Nenfliy", "family_streamer", letter = "N", website = "netflix.com")
        )
        _passwords.value = initialPasswords
    }

    fun addPassword(item: PasswordItemModel) {
        _passwords.update { it + item }
    }

    fun deletePassword(id: Int) {
        _passwords.update { it.filter { item -> item.id != id } }
    }
}
