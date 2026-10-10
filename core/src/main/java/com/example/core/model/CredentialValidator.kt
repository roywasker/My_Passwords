package com.example.core.model

/** Preserve exact credentials, including spaces and Unicode; never trim a password. */
object CredentialValidator {
    fun isValid(platform: String, username: CharArray, password: CharArray) =
        isValid(platform, username.size, password.size)

    fun isValid(platform: String, username: String, password: String) =
        isValid(platform, username.length, password.length)

    private fun isValid(platform: String, usernameLength: Int, passwordLength: Int) =
        platform.isNotBlank() && usernameLength > 0 && passwordLength > 0
}
