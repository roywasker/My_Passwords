package com.example.mypasswords.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
    darkColorScheme(
        primary = Purple80,
        onPrimary = Color(0xFF06265D),
        primaryContainer = ButtonColor,
        onPrimaryContainer = Color.White,
        secondary = PurpleGrey80,
        onSecondary = Color(0xFF003833),
        secondaryContainer = CircleIconBackGroundColor,
        tertiary = Pink80,
        background = BackgroundColor,
        onBackground = TitleTextColor,
        surface = RectangleBackGroundColor,
        onSurface = TitleTextColor,
        surfaceVariant = Color(0xFF1C3047),
        onSurfaceVariant = SubTitleTextColor,
        outline = RectangleIconBorderColor,
        outlineVariant = RectangleIconBorderColor,
        error = DeleteButtonColor,
        errorContainer = DeleteButtonBackgroundColor,
    )
private val LightColorScheme =
    lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40,
        background = Color(0xFFF4F7FC),
        surface = Color.White,
        onBackground = BackgroundColor,
        onSurface = BackgroundColor,
    )

@Composable
fun MyPasswordsTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes =
            Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
        content = content,
    )
}
