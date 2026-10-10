package com.example.core.data.entity

import androidx.room.ColumnInfo
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
    val categoryId: Int = 1,
    @ColumnInfo(defaultValue = "''") val tags: String = "",
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val passwordChangedAt: Long = 0,
    val deletedAt: Long? = null,
)
