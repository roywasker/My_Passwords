package com.example.core.util

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PasswordGeneratorTest(
    private val upper: Boolean,
    private val digits: Boolean,
    private val special: Boolean,
    private val length: Int,
) {
    @Test
    fun generatedPasswordSatisfiesLengthAndEveryRequestedCharacterClass() {
        val specialChars = "!@#$%^&*()-_=+[]{}|;:,.<>?"
        val allowed =
            "abcdefghijklmnopqrstuvwxyz" +
                (if (upper) "ABCDEFGHIJKLMNOPQRSTUVWXYZ" else "") +
                (if (digits) "0123456789" else "") +
                (if (special) specialChars else "")
        repeat(50) {
            val password = PasswordGenerator.generate(length, upper, digits, special)
            assertEquals(length, password.length)
            assertTrue(password.all { it in allowed })
            assertTrue(password.any { it in 'a'..'z' })
            if (upper) assertTrue(password.any { it in 'A'..'Z' })
            if (digits) assertTrue(password.any { it in '0'..'9' })
            if (special) assertTrue(password.any { it in specialChars })
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "upper={0}, digits={1}, special={2}, length={3}")
        fun cases(): List<Array<Any>> = buildList {
            for (u in listOf(false, true)) for (d in listOf(false, true)) for (s in
                listOf(false, true)) {
                val minimum = 1 + listOf(u, d, s).count { it }
                for (n in listOf(minimum, 16, 64, 1024)) add(arrayOf(u, d, s, n))
            }
        }
    }
}
