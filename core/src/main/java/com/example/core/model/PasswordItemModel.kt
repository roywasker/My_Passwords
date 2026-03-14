package com.example.core.model

data class PasswordItemModel(
    val id: Int,
    val platform: String,
    val userName: String,
    val letter: String = "",
    val website: String = ""
)
