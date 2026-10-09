package com.example.core.util

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PasswordGeneratorBoundaryTest(
    private val upper: Boolean,
    private val digits: Boolean,
    private val special: Boolean,
    private val length: Int,
) {
    @Test
    fun rejectsLengthThatCannotContainSelectedMandatoryClasses() {
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator.generate(length, upper, digits, special)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "upper={0} digits={1} special={2} invalidLength={3}")
        fun cases(): List<Array<Any>> = buildList {
            for (u in listOf(false, true)) for (d in listOf(false, true)) for (s in
                listOf(false, true)) {
                val minimum = 1 + listOf(u, d, s).count { it }
                for (n in listOf(Int.MIN_VALUE, -1, minimum - 1)) add(arrayOf(u, d, s, n))
            }
        }
    }
}
