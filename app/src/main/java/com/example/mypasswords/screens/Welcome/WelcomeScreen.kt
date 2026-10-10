package com.example.mypasswords.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compose_ui.components.CButton
import com.example.compose_ui.components.CText
import com.example.mypasswords.R
import com.example.mypasswords.ui.theme.ButtonColor
import com.example.mypasswords.ui.theme.CircleIconBackGroundColor
import com.example.mypasswords.ui.theme.CircleIconBorderColor
import com.example.mypasswords.ui.theme.CircleIconColor
import com.example.mypasswords.ui.theme.RectangleBackGroundColor
import com.example.mypasswords.ui.theme.ShieldBlurColor
import com.example.mypasswords.ui.theme.SubTitleTextColor
import com.example.mypasswords.ui.theme.TitleTextColor
import com.example.mypasswords.ui.theme.VaultBadge
import com.example.mypasswords.ui.theme.VaultFeature
import com.example.mypasswords.ui.theme.vaultBackdrop

@Composable
fun WelcomeScreen(onClick: () -> Unit) {
    if (com.example.compose_ui.components.isVaultLandscape()) {
        LandscapeWelcome(onClick)
        return
    }
    Column(
        modifier =
            Modifier.fillMaxSize()
                .vaultBackdrop()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VaultBadge(stringResource(R.string.vault_private_badge))
                androidx.compose.material3.Text(
                    "MY PASSWORDS",
                    color = SubTitleTextColor,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
            CentralShieldGraphic()
            CText(
                stringResource(R.string.welcome_screen_title),
                color = TitleTextColor,
                fontSize = 28.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right,
            )
            CText(
                stringResource(R.string.welcome_screen_subtitle),
                color = SubTitleTextColor,
                fontSize = 16.sp,
                lineHeight = 27.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right,
            )
            VaultFeature(
                Icons.Default.Fingerprint,
                stringResource(R.string.welcome_biometric_title),
                stringResource(R.string.welcome_biometric_body),
                compact = true,
            )
            VaultFeature(
                Icons.Default.LockReset,
                stringResource(R.string.welcome_local_title),
                stringResource(R.string.welcome_local_body),
                compact = true,
            )
        }
        CButton(
            text = stringResource(R.string.welcome_screen_button_text),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            buttonShape = RoundedCornerShape(20.dp),
            buttonColor = ButtonDefaults.buttonColors(containerColor = ButtonColor),
            textFontSize = 17.sp,
            textFontWeight = FontWeight.SemiBold,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
        )
    }
}

@Composable
fun CentralShieldGraphic() {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(118.dp)
                .background(
                    Brush.radialGradient(
                        listOf(ShieldBlurColor.copy(alpha = 0.35f), Color.Transparent)
                    ),
                    CircleShape,
                )
        )
        Box(
            Modifier.size(104.dp)
                .border(1.dp, CircleIconBorderColor.copy(alpha = 0.65f), RoundedCornerShape(48.dp))
        )
        Box(
            Modifier.size(88.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.linearGradient(
                        listOf(CircleIconBackGroundColor, RectangleBackGroundColor)
                    )
                )
                .border(1.dp, CircleIconBorderColor, RoundedCornerShape(36.dp)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                Icons.Default.Security,
                null,
                tint = CircleIconColor,
                modifier = Modifier.size(42.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    WelcomeScreen(onClick = {})
}

@Composable
private fun LandscapeWelcome(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .vaultBackdrop()
            .systemBarsPadding()
            .displayCutoutPadding()
            .padding(20.dp)
    ) {
        // A weighted footer stays visible while each pane can scroll independently.
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(
                Modifier.weight(1f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    VaultFeature(
                        Icons.Default.Fingerprint,
                        stringResource(R.string.welcome_biometric_title),
                        stringResource(R.string.welcome_biometric_body),
                        compact = true,
                    )
                    VaultFeature(
                        Icons.Default.LockReset,
                        stringResource(R.string.welcome_local_title),
                        stringResource(R.string.welcome_local_body),
                        compact = true,
                    )
                }
                CButton(
                    text = stringResource(R.string.welcome_screen_button_text),
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    buttonShape = RoundedCornerShape(20.dp),
                    buttonColor = ButtonDefaults.buttonColors(containerColor = ButtonColor),
                    textFontSize = 17.sp,
                    textFontWeight = FontWeight.SemiBold,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                )
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VaultBadge(stringResource(R.string.vault_private_badge))
                CentralShieldGraphic()
                CText(
                    stringResource(R.string.welcome_screen_title),
                    color = TitleTextColor,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                )
                CText(
                    stringResource(R.string.welcome_screen_subtitle),
                    color = SubTitleTextColor,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                )
            }
        }
    }
}
