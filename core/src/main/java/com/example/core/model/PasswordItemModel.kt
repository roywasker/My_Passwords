package com.example.core.model

data class PasswordItemModel(
    val id: Int,
    val platform: String,
    val userName: String,
    val website: String = "",
    val categoryId: Int = 1
) {
    val letter: String
        get() = platform.trim().firstOrNull()?.uppercase() ?: ""

    val maskedUserName: String
        get() {
            return if (userName.length > 4) {
                userName.take(2) + "****" + userName.takeLast(2)
            } else if (userName.length > 1) {
                userName.take(1) + "**" + userName.takeLast(1)
            } else {
                "***"
            }
        }
}
