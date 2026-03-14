package com.example.compose_ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CFloatingIcon(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    borderWidth: Dp = 1.dp,
    borderColor: Color,
    onClick: () -> Unit = {},
    iconSize: Dp = 20.dp,
    iconBackGroundColor: Color,
    shape: Shape = CircleShape,
    size: Dp = 40.dp,
    iconTint: Color,
) {
    Box(
        modifier = modifier
            .border(width = borderWidth, color = borderColor, shape = shape)
            .size(size)
            .clip(shape)
            .background(iconBackGroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}