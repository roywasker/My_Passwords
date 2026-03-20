package com.example.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.entity.EncryptedCredentialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials")
    fun getAllCredentials(): Flow<List<EncryptedCredentialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: EncryptedCredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteCredential(id: Int)
}
