package com.example.core.model

import org.junit.Assert.*
import org.junit.Test

class VaultFeaturesRulesTest {
    private val items =
        listOf(
            PasswordItemModel(
                1,
                "Google",
                "alice@example.com",
                categoryId = 2,
                tags = listOf("Work", "בנק"),
                isFavorite = true,
                createdAt = 30,
            ),
            PasswordItemModel(
                2,
                "Netflix",
                "bob",
                categoryId = 3,
                tags = listOf("אישי"),
                createdAt = 10,
            ),
            PasswordItemModel(
                3,
                "GitHub",
                "alice-dev",
                categoryId = 2,
                tags = listOf("Work"),
                isFavorite = true,
                createdAt = 20,
            ),
        )

    @Test
    fun combinedSearchUsesAndAcrossPlatformUsernameAndTags() {
        assertEquals(
            listOf(1),
            PasswordQuery.apply(items, "ALICE בנק", 2, true, "work", PasswordSort.ORIGINAL).map {
                it.id
            },
        )
        assertTrue(
            PasswordQuery.apply(items, "alice", 3, false, null, PasswordSort.ORIGINAL).isEmpty()
        )
    }

    @Test
    fun favoriteTagAndCategoryFiltersRemainIndependent() {
        assertEquals(
            listOf(1, 3),
            PasswordQuery.apply(items, "", 1, true, null, PasswordSort.ORIGINAL).map { it.id },
        )
        assertEquals(
            listOf(2),
            PasswordQuery.apply(items, "", 1, false, "אישי", PasswordSort.ORIGINAL).map { it.id },
        )
    }

    @Test
    fun sortingByNameAndCreationDateHasDeterministicResults() {
        assertEquals(
            listOf(3, 1, 2),
            PasswordQuery.apply(items, "", 1, false, null, PasswordSort.NAME).map { it.id },
        )
        assertEquals(
            listOf(1, 3, 2),
            PasswordQuery.apply(items, "", 1, false, null, PasswordSort.NEWEST).map { it.id },
        )
        assertEquals(
            listOf(2, 3, 1),
            PasswordQuery.apply(items, "", 1, false, null, PasswordSort.OLDEST).map { it.id },
        )
    }

    @Test
    fun searchMetacharactersAreLiteralAndWhitespaceIsIgnored() {
        assertEquals(
            items,
            PasswordQuery.apply(items, " \t\n ", 1, false, null, PasswordSort.ORIGINAL),
        )
        assertTrue(
            PasswordQuery.apply(items, ".*", 1, false, null, PasswordSort.ORIGINAL).isEmpty()
        )
    }

    @Test
    fun legacyGoogleIconServiceIsNotMistakenForAnAccountDomain() {
        val item =
            PasswordItemModel(
                1,
                "Service",
                "user",
                "https://www.google.com/s2/favicons?domain=netflix.com&sz=128",
            )
        assertTrue(
            PasswordQuery.apply(listOf(item), "google", 1, false, null, PasswordSort.ORIGINAL)
                .isEmpty()
        )
        assertEquals(
            1,
            PasswordQuery.apply(listOf(item), "netflix.com", 1, false, null, PasswordSort.ORIGINAL)
                .size,
        )
    }

    @Test
    fun tagsTrimDeduplicateAndRoundTripUnicodeWithoutEmptyTags() {
        val tags = TagRules.parse(" Work, work; אישי\nבנק , ,")
        assertEquals(listOf("Work", "אישי", "בנק"), tags)
        assertEquals(tags, TagRules.decode(TagRules.encode(tags)))
        assertTrue(TagRules.parse(" , ; \n").isEmpty())
        assertEquals(listOf("🔐".repeat(32)), TagRules.normalize(listOf("🔐".repeat(32))))
    }

    @Test
    fun excessiveOrAmbiguousTagsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TagRules.parse((1..11).joinToString(","))
        }
        assertThrows(IllegalArgumentException::class.java) { TagRules.parse("x".repeat(33)) }
        assertThrows(IllegalArgumentException::class.java) { TagRules.normalize(listOf("bad,tag")) }
    }

    @Test
    fun trashRetentionIsExactlyThirtyDays() {
        assertEquals(30L * 24 * 60 * 60 * 1000, TrashPolicy.RETENTION_MILLIS)
        assertEquals(0L, TrashPolicy.cutoff(TrashPolicy.RETENTION_MILLIS))
    }
}
