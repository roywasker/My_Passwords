package com.example.core.auth

import com.example.core.data.VaultDatabase
import com.example.core.model.VaultError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/** Owns the in-memory vault lifetime. DatabaseManager only opens and persists the vault. */
@Singleton
class VaultSession @Inject constructor() {
    internal val stateLock = Any()
    internal val openingMutex = Mutex()
    private var epoch = 0L
    private var database: VaultDatabase? = null
    private var key: ByteArray? = null
    private val ready = MutableStateFlow(false)
    private val authenticated = MutableStateFlow(false)
    private val lockListeners = mutableSetOf<() -> Unit>()
    val isInitialized = ready.asStateFlow()
    val isAuthenticated = authenticated.asStateFlow()
    val generation: Long
        get() = synchronized(stateLock) { epoch }

    fun getDatabase(): VaultDatabase =
        synchronized(stateLock) {
            if (!ready.value) throw VaultError.UninitializedVault
            database ?: throw VaultError.UninitializedVault
        }

    fun getKey(): ByteArray =
        synchronized(stateLock) {
            if (!ready.value) throw VaultError.UninitializedVault
            key ?: throw VaultError.UninitializedVault
        }

    internal fun checkTicket(ticket: Long) =
        synchronized(stateLock) {
            if (ticket != epoch) throw CancellationException("Vault session was revoked")
        }

    internal fun publish(ticket: Long, opened: VaultDatabase, passphrase: ByteArray) =
        synchronized(stateLock) {
            checkTicket(ticket)
            database = opened
            key = passphrase.copyOf()
            ready.value = true
        }

    internal fun closeForReopen(ticket: Long) {
        val previous =
            synchronized(stateLock) {
                checkTicket(ticket)
                revoke()
            }
        previous?.close()
    }

    internal fun confirmAuthentication() {
        authenticated.value = true
    }

    fun lock() {
        val previous =
            synchronized(stateLock) {
                epoch++
                revoke()
            }
        previous?.close()
    }

    internal fun lockIfCurrent(ticket: Long) {
        val previous =
            synchronized(stateLock) {
                if (ticket != epoch) return
                epoch++
                revoke()
            }
        previous?.close()
    }

    /** Callbacks must be fast, non-blocking, and clear only memory owned by their subscriber. */
    fun onLock(listener: () -> Unit): AutoCloseable =
        synchronized(stateLock) {
            lockListeners.add(listener)
            AutoCloseable { synchronized(stateLock) { lockListeners.remove(listener) } }
        }

    private fun revoke(): VaultDatabase? {
        val old = database
        database = null
        key?.fill(0)
        key = null
        ready.value = false
        authenticated.value = false
        lockListeners.toList().forEach { it() }
        return old
    }
}
