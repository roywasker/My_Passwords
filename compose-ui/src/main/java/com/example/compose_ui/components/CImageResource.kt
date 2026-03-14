package com.example.compose_ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource

@Composable
fun CImageResource(
    @DrawableRes drawableResId: Int,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
) {
    Image(
        painter = rememberVectorPainter(ImageVector.vectorResource(id = drawableResId)),
        contentDescription = "",
        modifier = modifier,
        colorFilter = colorFilter
    )

}