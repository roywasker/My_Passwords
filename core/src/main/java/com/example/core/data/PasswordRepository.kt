package com.example.core.data

import com.example.core.crypto.CryptoManager
import com.example.core.data.entity.EncryptedCredentialEntity
import com.example.core.model.Category
import com.example.core.model.Credential
import com.example.core.model.PasswordItemModel
import com.example.core.model.VaultError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class PasswordRepository
@Inject
constructor(
    private val databaseManager: DatabaseManager,
    private val cryptoManager: CryptoManager,
) {

    fun getCategories(): List<Category> = Category.getAllCategories()

    suspend fun createRecoveryCode(): String = databaseManager.createRecoveryCode()

    private val session = databaseManager.session
    private val cacheLock = Any()
    private var cacheGeneration = -1L

    private class CachedUsername(val encrypted: String, val iv: String, val characters: CharArray)

    private val usernameCache = mutableMapOf<Int, CachedUsername>()

    init {
        session.onLock { clearUsernameCache() }
    }

    private fun clearUsernameCache() =
        synchronized(cacheLock) {
            usernameCache.values.forEach { it.characters.fill('\u0000') }
            usernameCache.clear()
            cacheGeneration = -1L
        }

    private suspend fun displayItem(
        entity: EncryptedCredentialEntity,
        key: ByteArray,
        ticket: Long,
    ): PasswordItemModel {
        val cached =
            synchronized(session.stateLock) {
                session.checkTicket(ticket)
                synchronized(cacheLock) {
                    if (cacheGeneration != ticket) {
                        clearUsernameCache()
                        cacheGeneration = ticket
                    }
                    usernameCache[entity.id]
                        ?.takeIf {
                            it.encrypted == entity.encryptedUsername && it.iv == entity.usernameIv
                        }
                        ?.let { String(it.characters) }
                }
            }
        val username =
            cached
                ?: run {
                    val bytes =
                        cryptoManager.decryptWithKey(
                            entity.encryptedUsername,
                            entity.usernameIv,
                            key,
                        )
                    val characters =
                        try {
                            String(bytes).toCharArray()
                        } finally {
                            bytes.fill(0)
                        }
                    try {
                        synchronized(session.stateLock) {
                            session.checkTicket(ticket)
                            if (!databaseManager.isDatabaseInitialized())
                                throw kotlinx.coroutines.CancellationException("Vault locked")
                            synchronized(cacheLock) {
                                usernameCache.remove(entity.id)?.characters?.fill('\u0000')
                                usernameCache[entity.id] =
                                    CachedUsername(
                                        entity.encryptedUsername,
                                        entity.usernameIv,
                                        characters,
                                    )
                                String(characters)
                            }
                        }
                    } catch (error: Exception) {
                        characters.fill('\u0000')
                        throw error
                    }
                }
        return PasswordItemModel(
            entity.id,
            entity.platformName,
            username,
            entity.website,
            entity.categoryId,
            tags = com.example.core.model.TagRules.decode(entity.tags),
            isFavorite = entity.isFavorite,
            createdAt = entity.createdAt,
            passwordChangedAt = entity.passwordChangedAt,
            deletedAt = entity.deletedAt,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val passwords: Flow<List<PasswordItemModel>> =
        databaseManager.isInitialized
            .flatMapLatest { ready ->
                if (!ready) {
                    clearUsernameCache()
                    flowOf(emptyList())
                } else {
                    val ticket = session.generation
                    val key = databaseManager.getSessionVaultKey()
                    databaseManager
                        .getDatabase()
                        .credentialDao()
                        .getAllCredentials()
                        .map { entities ->
                            val result = entities.map { displayItem(it, key, ticket) }
                            synchronized(session.stateLock) {
                                session.checkTicket(ticket)
                                if (!databaseManager.isDatabaseInitialized())
                                    throw kotlinx.coroutines.CancellationException("Vault locked")
                                synchronized(cacheLock) {
                                    val ids = entities.mapTo(mutableSetOf()) { it.id }
                                    usernameCache.keys
                                        .filter { it !in ids }
                                        .forEach {
                                            usernameCache.remove(it)?.characters?.fill('\u0000')
                                        }
                                }
                            }
                            result
                        }
                        .flowOn(Dispatchers.IO)
                }
            }
            .catch { error ->
                clearUsernameCache()
                if (error is VaultError) throw error
                throw VaultError.DatabaseError(error)
            }

    suspend fun addPassword(credential: Credential) =
        withContext(Dispatchers.IO) {
            try {
                val sessionKey = databaseManager.getSessionVaultKey()

                val (encryptedUsername, usernameIv) =
                    cryptoManager.encryptWithKey(credential.username, sessionKey)
                val (encryptedPassword, passwordIv) =
                    cryptoManager.encryptWithKey(credential.password, sessionKey)

                val entity =
                    EncryptedCredentialEntity(
                        platformName = credential.platformName,
                        encryptedUsername = encryptedUsername,
                        usernameIv = usernameIv,
                        encryptedPassword = encryptedPassword,
                        passwordIv = passwordIv,
                        website = credential.website,
                        categoryId = credential.categoryId,
                        tags = com.example.core.model.TagRules.encode(credential.tags),
                        createdAt = System.currentTimeMillis(),
                        passwordChangedAt = System.currentTimeMillis(),
                    )

                databaseManager.getDatabase().credentialDao().insertCredential(entity)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is VaultError) throw e
                throw VaultError.DatabaseError(e)
            } finally {
                // Security: clear sensitive data
                credential.clear()
            }
        }

    suspend fun getDecryptedCredential(id: Int): com.example.core.model.FullCredentialModel? =
        withContext(Dispatchers.IO) {
            try {
                if (!databaseManager.isDatabaseInitialized()) return@withContext null

                val sessionKey = databaseManager.getSessionVaultKey()
                val entity =
                    databaseManager.getDatabase().credentialDao().getCredentialById(id)
                        ?: return@withContext null
                if (entity.deletedAt != null) return@withContext null

                // Decrypt username
                val usernameBytes =
                    cryptoManager.decryptWithKey(
                        entity.encryptedUsername,
                        entity.usernameIv,
                        sessionKey,
                    )
                val username = String(usernameBytes).toCharArray()
                usernameBytes.fill(0)

                // Decrypt password
                val passwordBytes =
                    cryptoManager.decryptWithKey(
                        entity.encryptedPassword,
                        entity.passwordIv,
                        sessionKey,
                    )
                val password = String(passwordBytes).toCharArray()
                passwordBytes.fill(0)

                com.example.core.model.FullCredentialModel(
                    id = entity.id,
                    platform = entity.platformName,
                    username = username,
                    password = password,
                    website = entity.website,
                    categoryId = entity.categoryId,
                    tags = com.example.core.model.TagRules.decode(entity.tags),
                    isFavorite = entity.isFavorite,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is VaultError) throw e
                throw VaultError.DatabaseError(e)
            }
        }

    suspend fun updatePassword(id: Int, credential: Credential) =
        withContext(Dispatchers.IO) {
            try {
                val ticket = session.generation
                val dao = databaseManager.getDatabase().credentialDao()
                val key = databaseManager.getSessionVaultKey()
                val old = dao.getCredentialById(id) ?: error("Credential no longer exists")
                check(old.deletedAt == null) { "Credential is in trash" }
                val previousPassword =
                    cryptoManager.decryptWithKey(old.encryptedPassword, old.passwordIv, key)
                val passwordChanged =
                    try {
                        !java.security.MessageDigest.isEqual(previousPassword, credential.password)
                    } finally {
                        previousPassword.fill(0)
                    }
                val previousUser =
                    cryptoManager.decryptWithKey(old.encryptedUsername, old.usernameIv, key)
                val userChanged =
                    try {
                        !java.security.MessageDigest.isEqual(previousUser, credential.username)
                    } finally {
                        previousUser.fill(0)
                    }
                val user =
                    if (userChanged) cryptoManager.encryptWithKey(credential.username, key)
                    else old.encryptedUsername to old.usernameIv
                val password =
                    if (passwordChanged) cryptoManager.encryptWithKey(credential.password, key)
                    else old.encryptedPassword to old.passwordIv
                session.checkTicket(ticket)
                dao.archiveAndUpdate(
                    old,
                    old.copy(
                        platformName = credential.platformName,
                        website = credential.website,
                        categoryId = credential.categoryId,
                        encryptedUsername = user.first,
                        usernameIv = user.second,
                        encryptedPassword = password.first,
                        passwordIv = password.second,
                    ),
                    passwordChanged,
                    System.currentTimeMillis(),
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is VaultError) throw error
                throw VaultError.DatabaseError(error)
            } finally {
                credential.clear()
            }
        }

    suspend fun deletePassword(id: Int) =
        withContext(Dispatchers.IO) {
            try {
                databaseManager
                    .getDatabase()
                    .credentialDao()
                    .moveToTrash(id, System.currentTimeMillis())
                Unit
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is VaultError) throw error
                throw VaultError.DatabaseError(error)
            }
        }

    fun sessionGeneration(): Long = session.generation

    private suspend fun <T> operation(
        block: suspend (com.example.core.data.dao.CredentialDao, ByteArray, Long) -> T
    ): T =
        withContext(Dispatchers.IO) {
            if (!databaseManager.isDatabaseInitialized()) throw VaultError.UninitializedVault
            val ticket = session.generation
            val key =
                synchronized(session.stateLock) { databaseManager.getSessionVaultKey().copyOf() }
            try {
                session.checkTicket(ticket)
                val result = block(databaseManager.getDatabase().credentialDao(), key, ticket)
                session.checkTicket(ticket)
                result
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is VaultError) throw error
                throw VaultError.DatabaseError(error)
            } finally {
                key.fill(0)
            }
        }

    suspend fun setFavorite(id: Int, value: Boolean) = operation { dao, _, _ ->
        check(dao.setFavorite(id, value) == 1)
        Unit
    }

    suspend fun setTags(id: Int, input: String) = operation { dao, _, _ ->
        check(
            dao.setTags(
                id,
                com.example.core.model.TagRules.encode(com.example.core.model.TagRules.parse(input)),
            ) == 1
        )
        Unit
    }

    suspend fun trashItems(): List<PasswordItemModel> = operation { dao, key, ticket ->
        dao.purgeExpiredTrash(com.example.core.model.TrashPolicy.cutoff(System.currentTimeMillis()))
        dao.trashSnapshot().map { displayItem(it, key, ticket) }
    }

    suspend fun restoreTrash(id: Int) = operation { dao, _, _ ->
        check(
            dao.restoreTrash(
                id,
                com.example.core.model.TrashPolicy.cutoff(System.currentTimeMillis()),
            ) == 1
        )
        Unit
    }

    suspend fun permanentlyDelete(id: Int) = operation { dao, _, _ ->
        check(dao.permanentlyDelete(id) == 1)
        Unit
    }

    suspend fun history(id: Int): List<com.example.core.model.PasswordHistoryItem> =
        operation { dao, _, _ ->
            val current = dao.getCredentialById(id) ?: error("Credential no longer exists")
            check(current.deletedAt == null)
            dao.getHistory(id).map {
                com.example.core.model.PasswordHistoryItem(
                    it.id,
                    it.credentialId,
                    it.recordedAt,
                    it.passwordChangedAt,
                )
            }
        }

    suspend fun restoreHistory(id: Int, historyId: Int) = operation { dao, key, ticket ->
        val entry =
            dao.getHistoryEntry(id, historyId)
                ?: error("History does not belong to this credential")
        check(entry.credentialId == id)
        val verified = cryptoManager.decryptWithKey(entry.encryptedPassword, entry.passwordIv, key)
        verified.fill(0)
        session.checkTicket(ticket)
        dao.restoreHistory(id, historyId, System.currentTimeMillis())
    }

    suspend fun healthReport(): com.example.core.util.PasswordHealthReport =
        operation { dao, key, ticket ->
            com.example.core.util.PasswordHealthBuilder(System.currentTimeMillis()).use { report ->
                for (item in dao.activeSnapshot()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    session.checkTicket(ticket)
                    val bytes =
                        cryptoManager.decryptWithKey(item.encryptedPassword, item.passwordIv, key)
                    try {
                        session.checkTicket(ticket)
                        report.add(item.id, item.platformName, bytes, item.passwordChangedAt)
                    } finally {
                        bytes.fill(0)
                    }
                }
                report.build()
            }
        }
}
