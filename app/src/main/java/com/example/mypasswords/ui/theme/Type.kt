package com.example.mypasswords.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        textAlign = androidx.compose.ui.text.style.TextAlign.Right,
        fontFamily = FontFamily.SansSerif,
        fontSize = size.sp,
        lineHeight = height.sp,
        fontWeight = weight,
    )

val Typography =
    Typography(
        displaySmall = type(36, 44, FontWeight.Bold),
        headlineLarge = type(32, 40, FontWeight.Bold),
        headlineMedium = type(28, 36, FontWeight.Bold),
        headlineSmall = type(24, 32, FontWeight.SemiBold),
        titleLarge = type(22, 30, FontWeight.SemiBold),
        titleMedium = type(18, 26, FontWeight.SemiBold),
        titleSmall = type(16, 24, FontWeight.Medium),
        bodyLarge = type(16, 26),
        bodyMedium = type(14, 22),
        bodySmall = type(12, 18),
        labelLarge = type(15, 22, FontWeight.SemiBold),
        labelMedium = type(12, 18, FontWeight.Medium),
        labelSmall = type(11, 16, FontWeight.Medium),
    )
