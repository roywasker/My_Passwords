package com.example.compose_ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonShape: RoundedCornerShape = RoundedCornerShape(20.dp),
    buttonColor: ButtonColors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245ACB)),
    textColor: Color = Color.White,
    textFontSize: TextUnit = 18.sp,
    icon: ImageVector? = null,
    iconSize: Dp = 24.dp,
    textFontWeight: FontWeight = FontWeight.Bold,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = buttonColor,
        shape = buttonShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(iconSize),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Text(
                text = text,
                color = textColor,
                fontSize = textFontSize,
                fontWeight = textFontWeight,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CButtonPreview() {
    CButton(
        text = "Login",
        onClick = {},
    )
}
