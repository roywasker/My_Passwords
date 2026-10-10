package com.example.core.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.core.crypto.CryptoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ensureActive

@Singleton
class DatabaseManager
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    private val recoveryCodec: com.example.core.crypto.VaultRecoveryCodec =
        com.example.core.crypto.VaultRecoveryCodec(),
    val session: com.example.core.auth.VaultSession = com.example.core.auth.VaultSession(),
) {
    private val sessionLock
        get() = session.stateLock

    private val openingMutex
        get() = session.openingMutex

    private val generation
        get() = session.generation

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

    val isInitialized
        get() = session.isInitialized

    fun isDatabaseInitialized(): Boolean = isInitialized.value

    fun getDatabase(): VaultDatabase = session.getDatabase()

    fun getSessionVaultKey(): ByteArray = session.getKey()

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
                val alias = sharedPrefs.getString("biometric_key_alias", null)
                if (alias == null) cryptoManager.getMasterDecryptionCipher(iv)
                else cryptoManager.getMasterDecryptionCipher(iv, alias)
            }
        } catch (e: Exception) {
            throw com.example.core.model.VaultError.GenericError(e)
        }
    }

    suspend fun initializeDatabase(authenticatedCipher: Cipher) {
        val ticket = synchronized(sessionLock) { generation }
        openingMutex.lock()
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                checkTicket(ticket)
                var passphrase: ByteArray? = null
                var opened: VaultDatabase? = null
                try {
                    val encryptedPassphrase = sharedPrefs.getString(PASSPHRASE_KEY, null)
                    passphrase =
                        if (encryptedPassphrase == null) {
                            check(
                                !context.getDatabasePath("vault.db").let {
                                    it.isFile && it.length() > 0
                                }
                            ) {
                                "Existing vault has no stored wrapping key; refusing to overwrite it"
                            }
                            val generated = ByteArray(32).apply { SecureRandom().nextBytes(this) }
                            try {
                                val input = generated.copyOf()
                                val wrapped =
                                    try {
                                        cryptoManager.wrapKey(input, authenticatedCipher)
                                    } finally {
                                        input.fill(0)
                                    }
                                checkTicket(ticket)
                                check(
                                    sharedPrefs
                                        .edit()
                                        .putString(PASSPHRASE_KEY, wrapped.first)
                                        .putString(PASSPHRASE_IV_KEY, wrapped.second)
                                        .commit()
                                ) {
                                    "Could not persist vault wrapping key"
                                }
                                generated
                            } catch (error: Exception) {
                                generated.fill(0)
                                throw error
                            }
                        } else cryptoManager.unwrapKey(encryptedPassphrase, authenticatedCipher)
                    checkTicket(ticket)
                    closeCurrentSession(ticket)
                    opened = openValidated(passphrase, encryptedPassphrase != null)
                    opened
                        .credentialDao()
                        .purgeExpiredTrash(
                            com.example.core.model.TrashPolicy.cutoff(System.currentTimeMillis())
                        )
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    publish(ticket, opened, passphrase)
                    opened = null // Ownership transferred to the session.
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw com.example.core.model.VaultError.DatabaseError(error)
                } finally {
                    try {
                        opened?.close()
                    } finally {
                        passphrase?.fill(0)
                    }
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            clearCancelledSession(ticket)
            throw error
        } finally {
            openingMutex.unlock()
        }
    }

    private fun checkTicket(ticket: Long) = session.checkTicket(ticket)

    private fun closeCurrentSession(ticket: Long) = session.closeForReopen(ticket)

    private fun publish(ticket: Long, opened: VaultDatabase, passphrase: ByteArray) =
        session.publish(ticket, opened, passphrase)

    private fun openValidated(passphrase: ByteArray, allowLegacy: Boolean): VaultDatabase {
        var opened = VaultDatabase.build(context, passphrase)
        try {
            try {
                opened.openHelper.writableDatabase
            } catch (error: net.zetetic.database.sqlcipher.SQLiteNotADatabaseException) {
                if (!allowLegacy) throw error
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
            return opened
        } catch (error: Exception) {
            try {
                opened.close()
            } catch (closeError: Exception) {
                error.addSuppressed(closeError)
            }
            throw error
        }
    }

    private fun clearCancelledSession(ticket: Long) = session.lockIfCurrent(ticket)

    fun clearSession() = session.lock()

    suspend fun createRecoveryCode(): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val ticket = synchronized(sessionLock) { generation }
            val key = synchronized(sessionLock) { getSessionVaultKey().copyOf() }
            try {
                val envelope = recoveryCodec.protect(key)
                synchronized(sessionLock) {
                    checkTicket(ticket)
                    check(
                        sharedPrefs
                            .edit()
                            .putString("recovery_ciphertext", envelope.ciphertext)
                            .putString("recovery_iv", envelope.iv)
                            .commit()
                    ) {
                        "Could not persist recovery envelope"
                    }
                }
                envelope.code
            } finally {
                key.fill(0)
            }
        }

    class RecoveryAttempt
    internal constructor(
        internal val key: ByteArray,
        val cipher: Cipher,
        internal val alias: String,
        internal val ticket: Long,
    ) {
        fun clear() {
            key.fill(0)
        }

        override fun toString() = "RecoveryAttempt([redacted])"
    }

    suspend fun prepareRecovery(code: String): RecoveryAttempt {
        var prepared: RecoveryAttempt? = null
        try {
            return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val ticket = synchronized(sessionLock) { generation }
                val encrypted =
                    sharedPrefs.getString("recovery_ciphertext", null)
                        ?: error("No recovery code configured")
                val iv = sharedPrefs.getString("recovery_iv", null) ?: error("Missing recovery IV")
                val key = recoveryCodec.recover(code, encrypted, iv)
                try {
                    checkTicket(ticket)
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val active = sharedPrefs.getString("biometric_key_alias", "VaultMasterKey")
                    val alias =
                        if (active == "VaultMasterKey") "VaultRecoveryMasterKey"
                        else "VaultMasterKey"
                    RecoveryAttempt(key, cryptoManager.prepareRecoveryCipher(alias), alias, ticket)
                        .also { prepared = it }
                } catch (error: Exception) {
                    key.fill(0)
                    throw error
                }
            }
        } catch (error: Exception) {
            prepared?.clear()
            throw error
        }
    }

    suspend fun completeRecovery(attempt: RecoveryAttempt, authenticatedCipher: Cipher) {
        var acquired = false
        try {
            openingMutex.lock()
            acquired = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var opened: VaultDatabase? = null
                try {
                    checkTicket(attempt.ticket)
                    closeCurrentSession(attempt.ticket)
                    opened = openValidated(attempt.key, true)
                    opened
                        .credentialDao()
                        .purgeExpiredTrash(
                            com.example.core.model.TrashPolicy.cutoff(System.currentTimeMillis())
                        )
                    val input = attempt.key.copyOf()
                    val wrapped =
                        try {
                            cryptoManager.wrapKey(input, authenticatedCipher)
                        } finally {
                            input.fill(0)
                        }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    synchronized(sessionLock) {
                        checkTicket(attempt.ticket)
                        check(
                            sharedPrefs
                                .edit()
                                .putString(PASSPHRASE_KEY, wrapped.first)
                                .putString(PASSPHRASE_IV_KEY, wrapped.second)
                                .putString("biometric_key_alias", attempt.alias)
                                .commit()
                        ) {
                            "Could not persist recovered wrapping key"
                        }
                        publish(attempt.ticket, opened, attempt.key)
                        opened = null
                    }
                } finally {
                    try {
                        opened?.close()
                    } finally {
                        attempt.clear()
                    }
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            clearCancelledSession(attempt.ticket)
            throw error
        } finally {
            if (acquired) openingMutex.unlock()
            attempt.clear()
        }
    }
}
