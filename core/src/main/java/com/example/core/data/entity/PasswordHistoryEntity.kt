package com.example.core.data.entity

import androidx.room.*

@Entity(
    tableName = "password_history",
    foreignKeys =
        [
            ForeignKey(
                entity = EncryptedCredentialEntity::class,
                parentColumns = ["id"],
                childColumns = ["credentialId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("credentialId")],
)
data class PasswordHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val credentialId: Int,
    val encryptedPassword: String,
    val passwordIv: String,
    val passwordChangedAt: Long,
    val recordedAt: Long,
)
