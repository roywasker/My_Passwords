package com.example.core.auth

import com.example.core.data.VaultDatabase
import com.example.core.model.VaultError
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class VaultSessionTest {
    private val session = VaultSession()
    private val db = mockk<VaultDatabase>(relaxed = true)
    private val key = ByteArray(32) { 7 }

    @Test
    fun startsLockedWithNoDatabaseOrKey() {
        assertFalse(session.isInitialized.value)
        assertFalse(session.isAuthenticated.value)
        assertThrows(VaultError.UninitializedVault::class.java) { session.getKey() }
        assertThrows(VaultError.UninitializedVault::class.java) { session.getDatabase() }
    }

    @Test
    fun openingAndAuthenticationShareOneRevocableSession() {
        session.publish(session.generation, db, key)
        session.confirmAuthentication()
        val held = session.getKey()
        assertNotSame(key, held)
        session.lock()
        assertFalse(session.isInitialized.value)
        assertFalse(session.isAuthenticated.value)
        assertTrue(held.all { it == 0.toByte() })
        assertTrue(key.all { it == 7.toByte() })
        verify(exactly = 1) { db.close() }
    }

    @Test
    fun staleTicketCannotPublishAfterLock() {
        val ticket = session.generation
        session.lock()
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            session.publish(ticket, db, key)
        }
        assertFalse(session.isInitialized.value)
    }

    @Test
    fun oldCancellationCannotCloseANewerSession() {
        val old = session.generation
        session.lock()
        session.publish(session.generation, db, key)
        session.lockIfCurrent(old)
        assertSame(db, session.getDatabase())
        verify(exactly = 0) { db.close() }
    }

    @Test
    fun cleanupListenersRunBeforeDatabaseCloseAndCanUnregister() {
        val held = CharArray(4) { 'x' }
        val registration = session.onLock { held.fill('\u0000') }
        session.publish(session.generation, db, key)
        every { db.close() } answers { assertTrue(held.all { it == '\u0000' }) }
        session.lock()
        registration.close()
        held.fill('y')
        session.lock()
        assertTrue(held.all { it == 'y' })
    }

    @Test
    fun failedCloseStillRevokesAuthenticationAndWipesKey() {
        session.publish(session.generation, db, key)
        session.confirmAuthentication()
        val held = session.getKey()
        every { db.close() } throws IllegalStateException()
        assertThrows(IllegalStateException::class.java) { session.lock() }
        assertFalse(session.isAuthenticated.value)
        assertTrue(held.all { it == 0.toByte() })
    }
}
