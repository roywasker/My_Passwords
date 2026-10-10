package com.example.core.auth

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository
@Inject
constructor(private val databaseManager: com.example.core.data.DatabaseManager) {
    val isAuthenticated
        get() = databaseManager.session.isAuthenticated

    val sessionGeneration
        get() = databaseManager.session.generation

    fun setAuthenticated(value: Boolean) {
        if (value) databaseManager.session.confirmAuthentication()
        else databaseManager.clearSession()
    }
}
