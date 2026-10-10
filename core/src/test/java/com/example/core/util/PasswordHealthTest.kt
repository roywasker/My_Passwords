package com.example.core.util

import org.junit.Assert.*
import org.junit.Test

class PasswordHealthTest {
    private val now = 2_000_000_000_000L

    @Test
    fun shortCommonAndRepetitivePasswordsAreFlaggedWithoutExposingValues() {
        PasswordHealthBuilder(now).use { builder ->
            builder.add(1, "One", "short".toByteArray(), now)
            builder.add(2, "Two", "MYpassword12345".toByteArray(), now)
            builder.add(3, "Three", "abababababababab".toByteArray(), now)
            val report = builder.build()
            assertEquals(
                listOf(WeakReason.SHORT, WeakReason.COMMON, WeakReason.REPETITIVE),
                report.items.map { it.weakReason },
            )
            assertEquals(3, report.weakCount)
            assertFalse(report.toString().contains("MYpassword12345"))
        }
    }

    @Test
    fun exactReuseIsDetectedButCaseDifferencesAreNotMerged() {
        PasswordHealthBuilder(now).use { builder ->
            builder.add(1, "One", "LongUniqueValue-A".toByteArray(), now)
            builder.add(2, "Two", "LongUniqueValue-A".toByteArray(), now)
            builder.add(3, "Three", "longuniquevalue-a".toByteArray(), now)
            assertEquals(listOf(true, true, false), builder.build().items.map { it.reused })
        }
    }

    @Test
    fun oldAgeHasExactBoundaryAndUnknownLegacyDatesAreNotMarkedOld() {
        PasswordHealthBuilder(now).use { builder ->
            builder.add(
                1,
                "Recent",
                "LongUniqueValue-A".toByteArray(),
                now - PasswordHealthBuilder.OLD_MILLIS + 1,
            )
            builder.add(
                2,
                "Boundary",
                "LongUniqueValue-B".toByteArray(),
                now - PasswordHealthBuilder.OLD_MILLIS,
            )
            builder.add(3, "Unknown", "LongUniqueValue-C".toByteArray(), 0)
            builder.add(4, "Future", "LongUniqueValue-D".toByteArray(), now + 1)
            val report = builder.build()
            assertEquals(1, report.oldCount)
            assertEquals(2, report.unknownAgeCount)
        }
    }

    @Test
    fun unicodeLengthCountsCodePointsRatherThanUtf16Surrogates() {
        PasswordHealthBuilder(now).use { builder ->
            builder.add(1, "Short emoji", "🔐🌍💙😀😁😂😃😄😅".toByteArray(), now)
            assertEquals(WeakReason.SHORT, builder.build().items.single().weakReason)
        }
    }

    @Test
    fun emptyVaultIsReportedAsEmptyAndClosingWipesFingerprints() {
        val builder = PasswordHealthBuilder(now)
        assertEquals(0, builder.build().total)
        builder.add(1, "One", "LongUniqueValue-A".toByteArray(), now)
        val entries =
            PasswordHealthBuilder::class
                .java
                .getDeclaredField("entries")
                .apply { isAccessible = true }
                .get(builder) as List<*>
        val entry = entries.single()!!
        val fingerprint =
            entry.javaClass.getDeclaredField("fingerprint").apply { isAccessible = true }.get(entry)
                as ByteArray
        builder.close()
        assertTrue(fingerprint.all { it == 0.toByte() })
        assertTrue(entries.isEmpty())
    }
}
