package com.example.core.util

import java.security.SecureRandom

object PasswordGenerator {
    private val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val DIGITS = "0123456789"
    private val SPECIAL = "!@#$%^&*()-_=+[]{}|;:,.<>?"
    
    private val random = SecureRandom()

    fun generate(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSpecial: Boolean = true
    ): String {
        val charPool = StringBuilder(LOWERCASE)
        if (includeUppercase) charPool.append(UPPERCASE)
        if (includeDigits) charPool.append(DIGITS)
        if (includeSpecial) charPool.append(SPECIAL)

        val password = StringBuilder()
        
        // Ensure at least one of each required type is included for strength
        if (includeUppercase) password.append(UPPERCASE[random.nextInt(UPPERCASE.length)])
        if (includeDigits) password.append(DIGITS[random.nextInt(DIGITS.length)])
        if (includeSpecial) password.append(SPECIAL[random.nextInt(SPECIAL.length)])
        password.append(LOWERCASE[random.nextInt(LOWERCASE.length)])

        // Fill the rest with the length
        repeat(length - password.length) {
            password.append(charPool[random.nextInt(charPool.length)])
        }

        // Shuffle the characters
        val shuffledList = password.toString().toList().shuffled(random)
        return shuffledList.joinToString("")
    }
}
