package com.example.core.model

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PasswordItemModelTest(private val username: String, private val masked: String) {
    @Test
    fun usernameMaskUsesExpectedBoundaryRules() {
        assertEquals(masked, PasswordItemModel(1, "Google", username).maskedUserName)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "mask case {index}")
        fun cases() =
            listOf(
                arrayOf("", "***"),
                arrayOf("a", "***"),
                arrayOf("ab", "a**b"),
                arrayOf("abc", "a**c"),
                arrayOf("abcd", "a**d"),
                arrayOf("abcde", "ab****de"),
                arrayOf("abcdef", "ab****ef"),
                arrayOf("user@example.com", "us****om"),
                arrayOf("אבגדה", "אב****דה"),
                arrayOf("  ", " ** "),
                arrayOf("a".repeat(10000), "aa****aa"),
            )
    }
}
