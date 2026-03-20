package com.example.core.model

import com.example.core.R

data class CategoryChip(val id: Int, val name: String)

enum class Category(val id: Int, val nameResId: Int) {
    ALL(1, R.string.category_all),
    WORK(2, R.string.category_work),
    PERSONAL(3, R.string.category_personal),
    FINANCE(4, R.string.category_finance);

    companion object {
        fun fromId(id: Int): Category = entries.find { it.id == id } ?: ALL
        
        fun getAllCategories(): List<Category> = entries
    }
}
