package com.example.core.model

data class PasswordItemModel(
    val id: Int,
    val platform: String,
    val userName: String,
    val website: String = "",
    val categoryId: Int = 1
) {
    val letter: String
        get() = platform.trim().firstOrNull()?.uppercase()?.toString() ?: ""
}
