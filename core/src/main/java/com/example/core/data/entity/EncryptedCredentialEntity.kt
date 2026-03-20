package com.example.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credentials")
data class EncryptedCredentialEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val platformName: String,
    val encryptedUsername: String,
    val usernameIv: String,
    val encryptedPassword: String,
    val passwordIv: String,
    val website: String = "",
    val categoryId: Int = 1
)
