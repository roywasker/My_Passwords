package com.example.core.model

import org.junit.Assert.*
import org.junit.Test

class ModelBehaviorTest {
    private fun credential() =
        Credential("平台", "שם משתמש".toByteArray(), "🔐 password".toByteArray(), "url", 2)

    private fun detail() =
        FullCredentialModel(7, "Google", "user".toCharArray(), "pass".toCharArray(), "url", 2)

    @Test
    fun credentialClearZeroesBothOriginalArrays() {
        val c = credential()
        val u = c.username
        val p = c.password
        c.clear()
        assertTrue(u.all { it == 0.toByte() })
        assertTrue(p.all { it == 0.toByte() })
    }

    @Test
    fun credentialClearIsIdempotent() {
        val c = credential()
        c.clear()
        c.clear()
        assertTrue(c.password.all { it == 0.toByte() })
    }

    @Test
    fun emptyCredentialCanBeCleared() {
        Credential("", byteArrayOf(), byteArrayOf()).clear()
    }

    @Test
    fun credentialEqualityComparesArrayContents() {
        assertEquals(credential(), credential())
        assertEquals(credential().hashCode(), credential().hashCode())
    }

    @Test
    fun credentialDifferentPlatformIsNotEqual() {
        assertNotEquals(credential(), credential().copy(platformName = "other"))
    }

    @Test
    fun credentialDifferentUsernameIsNotEqual() {
        assertNotEquals(credential(), credential().copy(username = byteArrayOf(1)))
    }

    @Test
    fun credentialDifferentPasswordIsNotEqual() {
        assertNotEquals(credential(), credential().copy(password = byteArrayOf(1)))
    }

    @Test
    fun credentialDifferentWebsiteIsNotEqual() {
        assertNotEquals(credential(), credential().copy(website = "other"))
    }

    @Test
    fun credentialDoesNotEqualNullOrOtherType() {
        assertFalse(credential().equals(null))
        assertFalse(credential().equals("other"))
    }

    @Test
    fun detailClearOverwritesOriginalArraysWithSpaces() {
        val d = detail()
        val u = d.username
        val p = d.password
        d.clear()
        assertTrue(u.all { it == ' ' })
        assertTrue(p.all { it == ' ' })
    }

    @Test
    fun detailClearIsIdempotentAndSupportsEmptyArrays() {
        val d = detail().copy(username = charArrayOf(), password = charArrayOf())
        d.clear()
        d.clear()
    }

    @Test
    fun detailEqualityUsesArrayContentsAndMatchingHash() {
        assertEquals(detail(), detail())
        assertEquals(detail().hashCode(), detail().hashCode())
    }

    @Test
    fun everyDetailMetadataFieldParticipatesInEquality() {
        val d = detail()
        listOf(
                d.copy(id = 9),
                d.copy(platform = "other"),
                d.copy(website = "other"),
                d.copy(categoryId = 3),
                d.copy(username = charArrayOf('x')),
                d.copy(password = charArrayOf('x')),
            )
            .forEach { assertNotEquals(d, it) }
    }

    @Test
    fun detailDoesNotEqualNullOrOtherType() {
        assertFalse(detail().equals(null))
        assertFalse(detail().equals("other"))
    }

    @Test
    fun letterHandlesWhitespaceEmptyUnicodeAndCase() {
        mapOf(
                "" to "",
                "  " to "",
                " google " to "G",
                "1bank" to "1",
                "אב" to "א",
                "école" to "É",
                "ß" to "SS",
            )
            .forEach { (p, l) -> assertEquals(l, PasswordItemModel(1, p, "").letter) }
    }

    @Test
    fun allCategoryIdsAreUniqueAndRoundTrip() {
        assertEquals(4, Category.getAllCategories().size)
        assertEquals(4, Category.entries.map { it.id }.distinct().size)
        Category.entries.forEach { assertEquals(it, Category.fromId(it.id)) }
    }

    @Test
    fun unknownCategoryFallsBackToAll() {
        listOf(Int.MIN_VALUE, -1, 0, 5, Int.MAX_VALUE).forEach {
            assertEquals(Category.ALL, Category.fromId(it))
        }
    }

    @Test
    fun wrappedErrorsRetainOriginalCause() {
        val e = IllegalArgumentException("synthetic failure")
        listOf(
                VaultError.EncryptionError(e),
                VaultError.DecryptionError(e),
                VaultError.DatabaseError(e),
                VaultError.GenericError(e),
            )
            .forEach { assertSame(e, it.cause) }
    }

    @Test
    fun genericErrorSupportsNullCauseAndOverride() {
        val e = VaultError.GenericError(messageOverride = "explanation")
        assertNull(e.cause)
        assertEquals("explanation", e.messageOverride)
    }
}
