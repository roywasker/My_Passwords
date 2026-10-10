package com.example.core.util

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

enum class WeakReason {
    SHORT,
    COMMON,
    REPETITIVE,
}

data class PasswordHealthItem(
    val id: Int,
    val platform: String,
    val weakReason: WeakReason?,
    val reused: Boolean,
    val old: Boolean,
    val ageUnknown: Boolean,
)

data class PasswordHealthReport(val items: List<PasswordHealthItem>) {
    val total
        get() = items.size

    val weakCount
        get() = items.count { it.weakReason != null }

    val reusedCount
        get() = items.count { it.reused }

    val oldCount
        get() = items.count { it.old }

    val unknownAgeCount
        get() = items.count { it.ageUnknown }
}

/** Local heuristic, not an entropy estimate or a claim that a password has never been breached. */
class PasswordHealthBuilder(private val now: Long) : AutoCloseable {
    private val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }

    private data class Entry(
        val id: Int,
        val platform: String,
        val reason: WeakReason?,
        val changedAt: Long,
        val fingerprint: ByteArray,
    )

    private val entries = mutableListOf<Entry>()

    private class Fingerprint(private val bytes: ByteArray) {
        override fun hashCode() = bytes.contentHashCode()

        override fun equals(other: Any?) =
            other is Fingerprint && MessageDigest.isEqual(bytes, other.bytes)
    }

    fun add(id: Int, platform: String, password: ByteArray, changedAt: Long) {
        val buffer = Charsets.UTF_8.decode(ByteBuffer.wrap(password))
        val chars = CharArray(buffer.remaining()).also { buffer.get(it) }
        if (buffer.hasArray()) buffer.array().fill('\u0000')
        try {
            val common =
                listOf("password", "123456", "qwerty", "letmein", "admin", "iloveyou").any { word ->
                    chars.size >= word.length &&
                        (0..chars.size - word.length).any { offset ->
                            word.indices.all { i -> chars[offset + i].lowercaseChar() == word[i] }
                        }
                }
            val reason =
                when {
                    common -> WeakReason.COMMON
                    chars.isNotEmpty() && chars.toSet().size <= 2 -> WeakReason.REPETITIVE
                    Character.codePointCount(chars, 0, chars.size) < 12 -> WeakReason.SHORT
                    else -> null
                }
            val digest =
                MessageDigest.getInstance("SHA-256").apply { update(salt) }.digest(password)
            entries.add(Entry(id, platform, reason, changedAt, digest))
        } finally {
            chars.fill('\u0000')
        }
    }

    fun build(): PasswordHealthReport {
        val counts = entries.groupingBy { Fingerprint(it.fingerprint) }.eachCount()
        return PasswordHealthReport(
            entries.map { item ->
                PasswordHealthItem(
                    item.id,
                    item.platform,
                    item.reason,
                    (counts[Fingerprint(item.fingerprint)] ?: 0) > 1,
                    item.changedAt > 0 &&
                        now >= item.changedAt &&
                        now - item.changedAt >= OLD_MILLIS,
                    item.changedAt <= 0 || item.changedAt > now,
                )
            }
        )
    }

    override fun close() {
        salt.fill(0)
        entries.forEach { it.fingerprint.fill(0) }
        entries.clear()
    }

    companion object {
        const val OLD_MILLIS = 180L * 24 * 60 * 60 * 1000
    }
}
