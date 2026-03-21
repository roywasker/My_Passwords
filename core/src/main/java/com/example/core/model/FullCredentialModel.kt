package com.example.core.model

data class FullCredentialModel(
    val id: Int,
    val platform: String,
    val username: String,
    val password: String,
    val website: String = "",
    val categoryId: Int = 1
)
