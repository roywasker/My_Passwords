package com.example.core.auth

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthRepository(databaseManager: com.example.core.data.DatabaseManager): AuthRepository {
        return AuthRepository(databaseManager)
    }
}
