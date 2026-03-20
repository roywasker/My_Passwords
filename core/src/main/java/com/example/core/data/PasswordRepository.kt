package com.example.core.data

import com.example.core.crypo.CryptoManager
import com.example.core.data.entity.EncryptedCredentialEntity
import com.example.core.model.Category
import com.example.core.model.CategoryChip
import android.content.Context
import com.example.core.model.Credential
import com.example.core.model.PasswordItemModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordRepository @Inject constructor(
    private val databaseManager: DatabaseManager,
    private val cryptoManager: CryptoManager
) {
    
    fun getCategories(context: Context): List<CategoryChip> {
        return Category.getAllCategories().map {
            CategoryChip(it.id, context.getString(it.nameResId))
        }
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val passwords: Flow<List<PasswordItemModel>> = flowOf(databaseManager)
        .flatMapLatest { manager ->
            if (manager.isDatabaseInitialized()) {
                val sessionKey = manager.getSessionVaultKey()
                manager.getDatabase().credentialDao().getAllCredentials().map { entities ->
                    entities.map { entity ->
                        // Decrypt username for display
                        val usernameBytes = cryptoManager.decryptWithKey(
                            entity.encryptedUsername,
                            entity.usernameIv,
                            sessionKey
                        )
                        val username = String(usernameBytes)
                        usernameBytes.fill(0) // Security: clear decrypted bytes

                        PasswordItemModel(
                            id = entity.id,
                            platform = entity.platformName,
                            userName = username,
                            website = entity.website,
                            categoryId = entity.categoryId
                        )
                    }
                }
            } else {
                flowOf(emptyList())
            }
        }

    suspend fun addPassword(credential: Credential) = withContext(Dispatchers.IO) {
        val sessionKey = databaseManager.getSessionVaultKey()
        
        val (encryptedUsername, usernameIv) = cryptoManager.encryptWithKey(credential.username, sessionKey)
        val (encryptedPassword, passwordIv) = cryptoManager.encryptWithKey(credential.password, sessionKey)

        val entity = EncryptedCredentialEntity(
            platformName = credential.platformName,
            encryptedUsername = encryptedUsername,
            usernameIv = usernameIv,
            encryptedPassword = encryptedPassword,
            passwordIv = passwordIv,
            website = credential.website,
            categoryId = credential.categoryId
        )

        databaseManager.getDatabase().credentialDao().insertCredential(entity)
        
        // Security: clear sensitive data
        credential.clear()
    }

    suspend fun deletePassword(id: Int) = withContext(Dispatchers.IO) {
        databaseManager.getDatabase().credentialDao().deleteCredential(id)
    }
}
