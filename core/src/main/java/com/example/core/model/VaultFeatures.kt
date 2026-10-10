package com.example.core.model

import java.util.Locale

object TagRules {
    fun parse(input: String): List<String> = normalize(input.split(',', '\n', ';'))

    fun normalize(values: List<String>): List<String> {
        val tags =
            values
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase(Locale.ROOT) }
        require(
            tags.size <= 10 &&
                tags.all {
                    it.codePointCount(0, it.length) <= 32 &&
                        !it.contains('\n') &&
                        !it.contains(',') &&
                        !it.contains(';')
                }
        ) {
            "Use up to ten tags, each up to 32 characters"
        }
        return tags
    }

    fun encode(values: List<String>): String = normalize(values).joinToString("\n")

    fun decode(stored: String): List<String> =
        if (stored.isEmpty()) emptyList() else stored.split('\n')
}

enum class PasswordSort {
    ORIGINAL,
    NAME,
    NEWEST,
    OLDEST,
}

object PasswordQuery {
    fun apply(
        items: List<PasswordItemModel>,
        query: String,
        categoryId: Int,
        favoritesOnly: Boolean,
        tag: String?,
        sort: PasswordSort,
    ): List<PasswordItemModel> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val filtered = items.filter { item ->
            (categoryId == 1 || item.categoryId == categoryId) &&
                (!favoritesOnly || item.isFavorite) &&
                (tag == null || item.tags.any { it.equals(tag, ignoreCase = true) }) &&
                terms.all { term ->
                    listOf(
                            item.platform,
                            item.userName,
                            if (item.website.startsWith("https://www.google.com/s2/favicons?"))
                                item.website.substringAfter("domain=", "").substringBefore('&')
                            else item.website,
                        )
                        .plus(item.tags)
                        .any { it.contains(term, ignoreCase = true) }
                }
        }
        return when (sort) {
            PasswordSort.ORIGINAL -> filtered
            PasswordSort.NAME ->
                filtered.sortedWith(
                    compareBy<PasswordItemModel> { it.platform.lowercase(Locale.ROOT) }
                        .thenBy { it.id }
                )
            PasswordSort.NEWEST ->
                filtered.sortedWith(
                    compareByDescending<PasswordItemModel> { it.createdAt }.thenBy { it.id }
                )
            PasswordSort.OLDEST ->
                filtered.sortedWith(compareBy<PasswordItemModel> { it.createdAt }.thenBy { it.id })
        }
    }
}

data class PasswordHistoryItem(
    val id: Int,
    val credentialId: Int,
    val recordedAt: Long,
    val passwordChangedAt: Long,
)

object TrashPolicy {
    const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

    fun cutoff(now: Long): Long = now - RETENTION_MILLIS
}
