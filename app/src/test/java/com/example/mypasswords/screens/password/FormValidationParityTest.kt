package com.example.mypasswords.screens.password

import android.app.Application
import com.example.core.data.PasswordRepository
import com.example.core.model.*
import com.example.mypasswords.testing.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FormValidationParityTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun addingAndEditingAcceptExactlyTheSameCredentialsWithoutNormalizingSecrets() = runTest {
        for ((platform, user, password) in
            listOf(
                Triple("Site", "", "secret"),
                Triple("Site", "user", ""),
                Triple(" ", "user", "secret"),
                Triple("בנק", "משתמש", "🔐 日本語"),
                Triple("Site", " user ", " pass "),
                Triple("Site", "user", " "),
            )) {
            val repo = mockk<PasswordRepository>(relaxed = true)
            val app = mockk<Application>(relaxed = true)
            every { repo.getCategories() } returns Category.entries
            every { app.getString(any()) } returns "synthetic resource"
            coEvery { repo.getDecryptedCredential(1) } returns
                FullCredentialModel(
                    1,
                    platform,
                    "old-user".toCharArray(),
                    "old-secret".toCharArray(),
                )
            var added: Pair<String, String>? = null
            var updated: Pair<String, String>? = null
            coEvery { repo.addPassword(any()) } answers
                {
                    val c = firstArg<Credential>()
                    added = String(c.username) to String(c.password)
                }
            coEvery { repo.updatePassword(any(), any()) } answers
                {
                    val c = secondArg<Credential>()
                    updated = String(c.username) to String(c.password)
                }
            val add = AddPasswordViewModel(repo, app)
            add.onPlatformNameChanged(platform)
            add.onUsernameChanged(user)
            add.onPasswordChanged(password)
            add.onSaveClick()
            val edit = PasswordViewModel(repo, app)
            edit.onEditClick(PasswordItemModel(1, platform, "old-user"))
            runCurrent()
            edit.updateCredential(user, password)
            runCurrent()
            val valid = CredentialValidator.isValid(platform, user, password)
            assertEquals(valid, added != null)
            assertEquals(valid, updated != null)
            if (valid) {
                assertEquals(user to password, added)
                assertEquals(added, updated)
            }
        }
    }
}
