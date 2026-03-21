package com.example.core.model

data class FullCredentialModel(
    val id: Int,
    val platform: String,
    val username: CharArray,
    val password: CharArray,
    val website: String = "",
    val categoryId: Int = 1
) {
    fun clear() {
        username.fill(' ')
        password.fill(' ')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FullCredentialModel

        if (id != other.id) return false
        if (platform != other.platform) return false
        if (!username.contentEquals(other.username)) return false
        if (!password.contentEquals(other.password)) return false
        if (website != other.website) return false
        if (categoryId != other.categoryId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + platform.hashCode()
        result = 31 * result + username.contentHashCode()
        result = 31 * result + password.contentHashCode()
        result = 31 * result + website.hashCode()
        result = 31 * result + categoryId
        return result
    }
}
