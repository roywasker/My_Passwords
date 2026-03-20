package com.example.core.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.core.crypo.CryptoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class DatabaseManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager
) {
    private var database: VaultDatabase? = null
    
    // Security: The DPK (Data Protection Key) stored in memory after biometric unwrap
    private var sessionVaultKey: ByteArray? = null
    
    private val sharedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        EncryptedSharedPreferences.create(
            context,
            "vault_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val PASSPHRASE_KEY = "encrypted_db_passphrase"
    private val PASSPHRASE_IV_KEY = "db_passphrase_iv"

    fun isDatabaseInitialized(): Boolean = database != null && sessionVaultKey != null

    fun getDatabase(): VaultDatabase {
        return database ?: throw IllegalStateException("Database not initialized. Biometric auth required.")
    }

    /**
     * Security: Returns the in-memory Vault Key for field-level encryption.
     */
    fun getSessionVaultKey(): ByteArray {
        return sessionVaultKey ?: throw IllegalStateException("Session key not available. Biometric auth required.")
    }

    /**
     * Prepares the cipher to either encrypt a new passphrase or decrypt the existing one.
     */
    fun getPassphraseCipher(): Cipher {
        val encryptedPassphrase = sharedPrefs.getString(PASSPHRASE_KEY, null)
        return if (encryptedPassphrase == null) {
            cryptoManager.getMasterEncryptionCipher()
        } else {
            val ivBase64 = sharedPrefs.getString(PASSPHRASE_IV_KEY, null)
                ?: throw IllegalStateException("IV missing for encrypted passphrase")
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            cryptoManager.getMasterDecryptionCipher(iv)
        }
    }

    suspend fun initializeDatabase(authenticatedCipher: Cipher) {
        val encryptedPassphraseBase64 = sharedPrefs.getString(PASSPHRASE_KEY, null)
        
        val passphrase = if (encryptedPassphraseBase64 == null) {
            // First time: Generate and wrap
            val newPassphrase = ByteArray(32).apply { SecureRandom().nextBytes(this) }
            val (encrypted, iv) = cryptoManager.wrapKey(newPassphrase.copyOf(), authenticatedCipher)
            
            sharedPrefs.edit {
                putString(PASSPHRASE_KEY, encrypted)
                putString(PASSPHRASE_IV_KEY, iv)
            }
            
            newPassphrase
        } else {
            // Subsequent times: Unwrap
            val ivBase64 = sharedPrefs.getString(PASSPHRASE_IV_KEY, null)!!
            cryptoManager.unwrapKey(encryptedPassphraseBase64, authenticatedCipher)
        }

        sessionVaultKey = passphrase.copyOf()
        database = VaultDatabase.build(context, passphrase)
        
        // Security: Clear the local reference to passphrase (Room now has its copy)
        passphrase.fill(0)
    }

    /**
     * Security: Wipe keys from memory and close database.
     */
    fun lock() {
        sessionVaultKey?.fill(0)
        sessionVaultKey = null
        database?.close()
        database = null
    }
}
