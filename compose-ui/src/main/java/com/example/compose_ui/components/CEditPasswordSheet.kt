package com.example.compose_ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CEditPasswordSheet(
    platformName: String,
    initialUsername: String,
    initialPassword: String,
    website: String,
    onDismiss: () -> Unit,
    onUpdate: (String, String) -> Unit,
    onGeneratePassword: (() -> String)? = null,
    titleTextColor: Color = Color.White,
    subTitleTextColor: Color = Color(0xFFACBED4),
    buttonColor: Color = Color(0xFF245ACB),
    backgroundColor: Color = Color(0xFF142235),
    fieldBackgroundColor: Color = Color(0xFF0C192A),
    fieldBorderColor: Color = Color(0xFF2B4058),
    title: String,
    usernameLabel: String,
    passwordLabel: String,
    updateButtonText: String,
    isSaving: Boolean = false,
    recordKey: Any? = null,
) {
    var username by remember(recordKey) { mutableStateOf(initialUsername) }
    var password by remember(recordKey) { mutableStateOf(initialPassword) }
    var isPasswordVisible by remember(recordKey) { mutableStateOf(false) }
    var isImageError by remember(website) { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = backgroundColor,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = subTitleTextColor.copy(alpha = 0.4f))
        },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            bottom = if (isVaultLandscape()) 16.dp else 32.dp,
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Platform Icon
                    Box(
                        modifier =
                            Modifier.size(if (isVaultLandscape()) 40.dp else 56.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(fieldBackgroundColor)
                                .border(1.dp, fieldBorderColor, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CText(
                            text = platformName.trim().firstOrNull()?.uppercase() ?: "",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = titleTextColor,
                        )
                    }

                    Spacer(modifier = Modifier.size(16.dp))

                    CText(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleTextColor,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                VaultFieldPair(
                    16.dp,
                    first = {
                        // Username Field
                        CCredentialTextField(
                            label = usernameLabel,
                            value = username,
                            onValueChange = { username = it },
                            labelColor = subTitleTextColor,
                            fieldBackgroundColor = fieldBackgroundColor,
                            fieldBorderColor = fieldBorderColor,
                            textColor = titleTextColor,
                            enabled = !isSaving,
                        )
                    },
                    second = {
                        // Password Field
                        CCredentialTextField(
                            label = passwordLabel,
                            value = password,
                            onValueChange = { password = it },
                            labelColor = subTitleTextColor,
                            fieldBackgroundColor = fieldBackgroundColor,
                            fieldBorderColor = fieldBorderColor,
                            textColor = titleTextColor,
                            isPassword = true,
                            isPasswordVisible = isPasswordVisible,
                            onTogglePasswordVisibility = { isPasswordVisible = !isPasswordVisible },
                            onGeneratePassword = onGeneratePassword?.let { { password = it() } },
                            enabled = !isSaving,
                        )
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Update Button
                Button(
                    enabled =
                        !isSaving &&
                            com.example.core.model.CredentialValidator.isValid(
                                platformName,
                                username,
                                password,
                            ),
                    onClick = { onUpdate(username, password) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors =
                        androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = buttonColor
                        ),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    CText(
                        text = updateButtonText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
