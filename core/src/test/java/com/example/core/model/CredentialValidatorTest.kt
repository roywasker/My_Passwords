package com.example.core.model

import org.junit.Assert.*
import org.junit.Test

class CredentialValidatorTest {
    @Test
    fun addAndEditShareValidationForEmptyWhitespaceAndUnicodeInputs() {
        for ((platform, user, password) in
            listOf(
                Triple("", "user", "pass"),
                Triple("  ", "user", "pass"),
                Triple("Site", "", "pass"),
                Triple("Site", "user", ""),
                Triple("בנק", "שלום", "🔐 日本語"),
                Triple("Site", " user ", " pass "),
                Triple("Site", "u", " "),
            )) {
            assertEquals(
                CredentialValidator.isValid(platform, user, password),
                CredentialValidator.isValid(platform, user.toCharArray(), password.toCharArray()),
            )
        }
        assertFalse(CredentialValidator.isValid(" ", "user", "pass"))
        assertFalse(CredentialValidator.isValid("Site", "", "pass"))
        assertFalse(CredentialValidator.isValid("Site", "user", ""))
        assertTrue(CredentialValidator.isValid("בנק", "user", " "))
    }

    @Test
    fun validationNeverMutatesSensitiveBuffers() {
        val user = " user ".toCharArray()
        val password = " pass ".toCharArray()
        assertTrue(CredentialValidator.isValid("Site", user, password))
        assertEquals(" user ", String(user))
        assertEquals(" pass ", String(password))
    }
}
