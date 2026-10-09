package com.example.core.auth

import com.example.core.data.DatabaseManager
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class AuthRepositoryTest {
    private val database = mockk<DatabaseManager>(relaxed = true)
    private val auth = AuthRepository(database)

    @Test
    fun startsLocked() {
        assertFalse(auth.isAuthenticated.value)
        verify(exactly = 0) { database.clearSession() }
    }

    @Test
    fun successfulAuthenticationDoesNotClearSession() {
        auth.setAuthenticated(true)
        assertTrue(auth.isAuthenticated.value)
        verify(exactly = 0) { database.clearSession() }
    }

    @Test
    fun logoutClearsSession() {
        auth.setAuthenticated(true)
        auth.setAuthenticated(false)
        assertFalse(auth.isAuthenticated.value)
        verify(exactly = 1) { database.clearSession() }
    }

    @Test
    fun lockingAnAlreadyLockedVaultStillClearsSession() {
        auth.setAuthenticated(false)
        auth.setAuthenticated(false)
        verify(exactly = 2) { database.clearSession() }
    }

    @Test
    fun repeatedSuccessfulAuthenticationRemainsUnlocked() {
        repeat(3) { auth.setAuthenticated(true) }
        assertTrue(auth.isAuthenticated.value)
        verify(exactly = 0) { database.clearSession() }
    }

    @Test
    fun canAuthenticateAgainAfterLogout() {
        auth.setAuthenticated(true)
        auth.setAuthenticated(false)
        auth.setAuthenticated(true)
        assertTrue(auth.isAuthenticated.value)
        verify(exactly = 1) { database.clearSession() }
    }

    @Test
    fun stateIsLockedEvenWhenClosingDatabaseThrows() {
        every { database.clearSession() } throws IllegalStateException()
        auth.setAuthenticated(true)
        assertThrows(IllegalStateException::class.java) { auth.setAuthenticated(false) }
        assertFalse(auth.isAuthenticated.value)
    }
}
