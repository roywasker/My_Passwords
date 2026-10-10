package com.example.mypasswords.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun VaultFeature(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = RectangleBackGroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, RectangleIconBorderColor),
    ) {
        Row(
            Modifier.padding(if (compact) 14.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 0.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TitleTextColor,
                    textAlign = TextAlign.Right,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SubTitleTextColor,
                    textAlign = TextAlign.Right,
                )
            }
            Surface(shape = RoundedCornerShape(16.dp), color = CircleIconBackGroundColor) {
                Icon(
                    icon,
                    null,
                    tint = CircleIconColor,
                    modifier = Modifier.padding(if (compact) 10.dp else 12.dp).size(24.dp),
                )
            }
        }
    }
}

@Composable
fun VaultBadge(text: String) {
    Surface(shape = RoundedCornerShape(50), color = CircleIconBackGroundColor) {
        Text(
            text,
            color = CircleIconColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

fun Modifier.vaultBackdrop(): Modifier =
    background(Brush.verticalGradient(listOf(Color(0xFF102846), BackgroundColor, BackgroundColor)))
