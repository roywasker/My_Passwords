package com.example.compose_ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CCategoryList(
    categories: List<CCategoryItem>,
    selectedCategoryId: Int,
    onCategorySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    activeColor: Color = Color.Blue,
    inactiveColor: Color = Color.Gray,
    activeTextColor: Color = Color.White,
    inactiveTextColor: Color = Color.Black,
    borderColor: Color = Color.LightGray
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        reverseLayout = reverseLayout,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement
    ) {
        items(categories, key = { it.id }) { category ->
            val isSelected = category.id == selectedCategoryId
            val bgColor = if (isSelected) activeColor else inactiveColor
            val textColor = if (isSelected) activeTextColor else inactiveTextColor

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color.Transparent else borderColor,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onCategorySelected(category.id) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CText(
                    text = category.name,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

data class CCategoryItem(val id: Int, val name: String)
