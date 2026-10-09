package com.example.core.model

/**
 * Plaintext model used only in memory. Fields should be zeroed out as soon as they are no longer
 * needed.
 */
data class Credential(
    val platformName: String,
    val username: ByteArray,
    val password: ByteArray,
    val website: String = "",
    val categoryId: Int = 1,
) {
    fun clear() {
        username.fill(0)
        password.fill(0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Credential

        if (platformName != other.platformName) return false
        if (!username.contentEquals(other.username)) return false
        if (!password.contentEquals(other.password)) return false
        if (website != other.website) return false
        if (categoryId != other.categoryId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = platformName.hashCode()
        result = 31 * result + username.contentHashCode()
        result = 31 * result + password.contentHashCode()
        result = 31 * result + website.hashCode()
        result = 31 * result + categoryId
        return result
    }
}
