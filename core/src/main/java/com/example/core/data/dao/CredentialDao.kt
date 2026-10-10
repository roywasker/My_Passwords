package com.example.core.data.dao

import androidx.room.*
import com.example.core.data.entity.EncryptedCredentialEntity
import com.example.core.data.entity.PasswordHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials WHERE deletedAt IS NULL")
    fun getAllCredentials(): Flow<List<EncryptedCredentialEntity>>

    @Query("SELECT * FROM credentials WHERE deletedAt IS NULL")
    suspend fun activeSnapshot(): List<EncryptedCredentialEntity>

    @Query("SELECT * FROM credentials WHERE id = :id")
    suspend fun getCredentialById(id: Int): EncryptedCredentialEntity?

    @Upsert suspend fun insertCredential(credential: EncryptedCredentialEntity)

    @Update suspend fun updateExisting(credential: EncryptedCredentialEntity): Int

    @Query("DELETE FROM credentials WHERE id = :id") suspend fun deleteCredential(id: Int)

    @Query("SELECT * FROM credentials WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, id")
    suspend fun trashSnapshot(): List<EncryptedCredentialEntity>

    @Query("UPDATE credentials SET deletedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun moveToTrash(id: Int, now: Long): Int

    @Query("UPDATE credentials SET deletedAt = NULL WHERE id = :id AND deletedAt > :cutoff")
    suspend fun restoreTrash(id: Int, cutoff: Long): Int

    @Query("DELETE FROM credentials WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun permanentlyDelete(id: Int): Int

    @Query("DELETE FROM credentials WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun purgeExpiredTrash(cutoff: Long): Int

    @Query("UPDATE credentials SET isFavorite = :favorite WHERE id = :id AND deletedAt IS NULL")
    suspend fun setFavorite(id: Int, favorite: Boolean): Int

    @Query("UPDATE credentials SET tags = :tags WHERE id = :id AND deletedAt IS NULL")
    suspend fun setTags(id: Int, tags: String): Int

    @Insert suspend fun insertHistory(history: PasswordHistoryEntity)

    @Query(
        "SELECT * FROM password_history WHERE credentialId = :id ORDER BY recordedAt DESC, id DESC"
    )
    suspend fun getHistory(id: Int): List<PasswordHistoryEntity>

    @Query("SELECT * FROM password_history WHERE id = :historyId AND credentialId = :credentialId")
    suspend fun getHistoryEntry(credentialId: Int, historyId: Int): PasswordHistoryEntity?

    @Transaction
    suspend fun archiveAndUpdate(
        expected: EncryptedCredentialEntity,
        updated: EncryptedCredentialEntity,
        passwordChanged: Boolean,
        now: Long,
    ) {
        val current = getCredentialById(updated.id) ?: error("Credential no longer exists")
        check(current.deletedAt == null) { "Credential is in trash" }
        check(
            current.encryptedPassword == expected.encryptedPassword &&
                current.passwordIv == expected.passwordIv &&
                current.encryptedUsername == expected.encryptedUsername &&
                current.usernameIv == expected.usernameIv
        ) {
            "Credential changed; reload before updating"
        }
        if (passwordChanged)
            insertHistory(
                PasswordHistoryEntity(
                    credentialId = current.id,
                    encryptedPassword = current.encryptedPassword,
                    passwordIv = current.passwordIv,
                    passwordChangedAt = current.passwordChangedAt,
                    recordedAt = now,
                )
            )
        check(
            updateExisting(
                updated.copy(
                    tags = current.tags,
                    isFavorite = current.isFavorite,
                    createdAt = current.createdAt,
                    deletedAt = null,
                    passwordChangedAt = if (passwordChanged) now else current.passwordChangedAt,
                )
            ) == 1
        )
    }

    @Transaction
    suspend fun restoreHistory(credentialId: Int, historyId: Int, now: Long) {
        val current = getCredentialById(credentialId) ?: error("Credential no longer exists")
        check(current.deletedAt == null) { "Restore the item from trash first" }
        val previous =
            getHistoryEntry(credentialId, historyId)
                ?: error("History does not belong to this credential")
        if (
            current.encryptedPassword == previous.encryptedPassword &&
                current.passwordIv == previous.passwordIv
        )
            return
        insertHistory(
            PasswordHistoryEntity(
                credentialId = credentialId,
                encryptedPassword = current.encryptedPassword,
                passwordIv = current.passwordIv,
                passwordChangedAt = current.passwordChangedAt,
                recordedAt = now,
            )
        )
        check(
            updateExisting(
                current.copy(
                    encryptedPassword = previous.encryptedPassword,
                    passwordIv = previous.passwordIv,
                    passwordChangedAt = previous.passwordChangedAt,
                )
            ) == 1
        )
    }
}
