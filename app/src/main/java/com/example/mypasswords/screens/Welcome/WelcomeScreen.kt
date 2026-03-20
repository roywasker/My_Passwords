package com.example.mypasswords.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compose_ui.components.CButton
import com.example.compose_ui.components.CFloatingIcon
import com.example.compose_ui.components.CIconWithUnderText
import com.example.compose_ui.components.CImageResource
import com.example.compose_ui.components.CText
import com.example.mypasswords.R
import com.example.mypasswords.ui.theme.BackgroundColor
import com.example.mypasswords.ui.theme.ButtonColor
import com.example.mypasswords.ui.theme.CircleIconBackGroundColor
import com.example.mypasswords.ui.theme.CircleIconBorderColor
import com.example.mypasswords.ui.theme.CircleIconColor
import com.example.mypasswords.ui.theme.RectangleBackGroundColor
import com.example.mypasswords.ui.theme.RectangleIconBorderColor
import com.example.mypasswords.ui.theme.ShieldBlurColor
import com.example.mypasswords.ui.theme.SubTitleTextColor
import com.example.mypasswords.ui.theme.TitleTextColor

@Composable
fun WelcomeScreen(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                CFloatingIcon(
                    icon = Icons.Default.Security,
                    iconSize = 20.dp,
                    size = 40.dp,
                    iconBackGroundColor = CircleIconBackGroundColor,
                    borderColor = CircleIconBorderColor,
                    borderWidth = 1.dp,
                    iconTint = CircleIconColor,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CentralShieldGraphic()

            Spacer(modifier = Modifier.height(20.dp))

            CText(
                text = stringResource(id = R.string.welcome_screen_title),
                color = TitleTextColor,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(20.dp))

            CText(
                text = stringResource(id = R.string.welcome_screen_subtitle),
                color = SubTitleTextColor,
                lineHeight = 28.sp,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(30.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CIconWithUnderText(icon = Icons.Default.Devices, label = stringResource(id = R.string.welcome_screen_backup_icon))
                VerticalDivider(
                    modifier = Modifier.height(30.dp),
                    color = Color.DarkGray.copy(alpha = 0.5f)
                )
                CIconWithUnderText(icon = Icons.Default.Sync, label = stringResource(id = R.string.welcome_screen_sync_icon))
                VerticalDivider(
                    modifier = Modifier.height(30.dp),
                    color = Color.DarkGray.copy(alpha = 0.5f)
                )
                CIconWithUnderText(icon = Icons.Default.LockReset, label = stringResource(id = R.string.welcome_screen_encrypt_icon))
            }

            Spacer(modifier = Modifier.height(30.dp))

            CButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(64.dp),
                buttonColor = ButtonDefaults.buttonColors(
                    containerColor = ButtonColor
                ),
                text = stringResource(id = R.string.welcome_screen_button_text),
                textColor = Color.White,
                textFontSize = 18.sp,
                textFontWeight = FontWeight.Bold,
                onClick = onClick,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                iconSize = 24.dp
            )
        }
    }
}

@Composable
fun CentralShieldGraphic() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp)
            .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ShieldBlurColor.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .drawBehind {
                    drawCircle(
                        color = Color(0xFF1E3A8A).copy(alpha = 0.8f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
        )
        Box(
            modifier = Modifier
                .size(170.dp)
                .drawBehind {
                    drawCircle(
                        color = Color(0xFF1E3A8A).copy(alpha = 0.8f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
        )

        Box(
            modifier = Modifier
                .size(90.dp),
            contentAlignment = Alignment.Center
        ) {
            CImageResource(
                drawableResId = R.drawable.shield,
                modifier = Modifier.size(50.dp),
            )
        }

        CFloatingIcon(
            icon = Icons.Default.Fingerprint,
            iconSize = 22.dp,
            size = 44.dp,
            iconBackGroundColor = RectangleBackGroundColor,
            borderColor = RectangleIconBorderColor,
            borderWidth = 1.dp,
            iconTint = CircleIconColor,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-20).dp, y = 30.dp)
        )
        CFloatingIcon(
            icon = Icons.Default.VpnKey,
            iconSize = 20.dp,
            size = 44.dp,
            iconBackGroundColor = RectangleBackGroundColor,
            borderColor = RectangleIconBorderColor,
            borderWidth = 1.dp,
            iconTint = CircleIconColor,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 20.dp, y = (-30).dp)
        )
    }
}


@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    WelcomeScreen(onClick = {})
}
