package com.example.core.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.core.crypto.CryptoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DatabaseManager
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
) {
    private var database: VaultDatabase? = null

    // Security: The DPK (Data Protection Key) stored in memory after biometric unwrap
    private var sessionVaultKey: ByteArray? = null

    private val sharedPrefs: SharedPreferences by lazy {
        val masterKey =
            MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        EncryptedSharedPreferences.create(
            context,
            "vault_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val PASSPHRASE_KEY = "encrypted_db_passphrase"
    private val PASSPHRASE_IV_KEY = "db_passphrase_iv"

    private val _isInitialized = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    fun isDatabaseInitialized(): Boolean = _isInitialized.value

    fun getDatabase(): VaultDatabase {
        if (!_isInitialized.value) throw com.example.core.model.VaultError.UninitializedVault
        return database ?: throw com.example.core.model.VaultError.UninitializedVault
    }

    /** Security: Returns the in-memory Vault Key for field-level encryption. */
    fun getSessionVaultKey(): ByteArray {
        if (!_isInitialized.value) throw com.example.core.model.VaultError.UninitializedVault
        return sessionVaultKey ?: throw com.example.core.model.VaultError.UninitializedVault
    }

    /** Prepares the cipher to either encrypt a new passphrase or decrypt the existing one. */
    fun getPassphraseCipher(): Cipher {
        val encryptedPassphrase = sharedPrefs.getString(PASSPHRASE_KEY, null)
        return try {
            if (encryptedPassphrase == null) {
                cryptoManager.getMasterEncryptionCipher()
            } else {
                val ivBase64 =
                    sharedPrefs.getString(PASSPHRASE_IV_KEY, null)
                        ?: throw IllegalStateException("IV missing for encrypted passphrase")
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                cryptoManager.getMasterDecryptionCipher(iv)
            }
        } catch (e: Exception) {
            throw com.example.core.model.VaultError.GenericError(e)
        }
    }

    suspend fun initializeDatabase(authenticatedCipher: Cipher) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var passphrase: ByteArray? = null
            try {
                val encryptedPassphrase = sharedPrefs.getString(PASSPHRASE_KEY, null)
                passphrase =
                    if (encryptedPassphrase == null) {
                        val generated = ByteArray(32).apply { SecureRandom().nextBytes(this) }
                        try {
                            val wrappingInput = generated.copyOf()
                            val (encrypted, iv) =
                                try {
                                    cryptoManager.wrapKey(wrappingInput, authenticatedCipher)
                                } finally {
                                    wrappingInput.fill(0)
                                }
                            sharedPrefs.edit {
                                putString(PASSPHRASE_KEY, encrypted)
                                putString(PASSPHRASE_IV_KEY, iv)
                            }
                            generated
                        } catch (e: Exception) {
                            generated.fill(0)
                            throw e
                        }
                    } else {
                        cryptoManager.unwrapKey(encryptedPassphrase, authenticatedCipher)
                    }

                clearSession()
                var opened = VaultDatabase.build(context, passphrase)
                try {
                    // Open SQLCipher before wiping the passphrase or publishing an unlocked
                    // session.
                    try {
                        opened.openHelper.writableDatabase
                    } catch (error: net.zetetic.database.sqlcipher.SQLiteNotADatabaseException) {
                        // Older releases wiped the key before Room's lazy first open, creating
                        // databases encrypted with 32 zero bytes. Never reset or delete a vault.
                        if (encryptedPassphrase == null) throw error
                        opened.close()
                        try {
                            VaultDatabase.recoverLegacyZeroKey(context, passphrase)
                        } catch (recoveryError: Exception) {
                            error.addSuppressed(recoveryError)
                            throw error
                        }
                        opened = VaultDatabase.build(context, passphrase)
                        opened.openHelper.writableDatabase
                    }
                    database = opened
                    sessionVaultKey = passphrase.copyOf()
                    _isInitialized.value = true
                } catch (e: Exception) {
                    try {
                        opened.close()
                    } catch (closeError: Exception) {
                        e.addSuppressed(closeError)
                    }
                    throw e
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw com.example.core.model.VaultError.DatabaseError(e)
            } finally {
                passphrase?.fill(0)
            }
        }

    fun clearSession() {
        val previousDatabase = database
        // Revoke access and wipe memory even if the database fails to close.
        database = null
        sessionVaultKey?.fill(0)
        sessionVaultKey = null
        _isInitialized.value = false
        previousDatabase?.close()
    }
}
