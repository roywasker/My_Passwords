package com.example.compose_ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CPasswordDetailSheet(
    platformName: String,
    username: CharArray,
    password: CharArray,
    website: String,
    onDismiss: () -> Unit,
    titleTextColor: Color = Color.White,
    subTitleTextColor: Color = Color(0xFFACBED4),
    buttonColor: Color = Color(0xFF245ACB),
    backgroundColor: Color = Color(0xFF142235),
    fieldBackgroundColor: Color = Color(0xFF0C192A),
    fieldBorderColor: Color = Color(0xFF2B4058),
    closeButtonText: String,
    usernameLabel: String,
    passwordLabel: String,
    loginDetailsSubtitle: String,
    onHistory: (() -> Unit)? = null,
    onManageTags: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    historyText: String = "Password history",
    tagsText: String = "Tags",
    favoriteText: String = "Favorite",
    actionsEnabled: Boolean = true,
    actionError: String? = null,
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isImageError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun copyToClipboard(label: String, text: String, isSensitive: Boolean) {
        if (isSensitive) SensitiveClipboardCleaner(context).copy(label, text)
        else {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }

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

                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.padding(start = 16.dp),
                    ) {
                        CText(
                            text = platformName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = titleTextColor,
                            textAlign = TextAlign.Right,
                        )
                        CText(
                            text = loginDetailsSubtitle,
                            fontSize = 14.sp,
                            color = subTitleTextColor,
                            textAlign = TextAlign.Right,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                VaultFieldPair(
                    12.dp,
                    first = {
                        // Username Field
                        DetailFieldGroup(
                            label = usernameLabel,
                            value = String(username),
                            labelColor = subTitleTextColor,
                            valueColor = titleTextColor,
                            backgroundColor = fieldBackgroundColor,
                            borderColor = fieldBorderColor,
                            onCopy = { copyToClipboard(usernameLabel, String(username), false) },
                        )
                    },
                    second = {
                        // Password Field
                        DetailFieldGroup(
                            label = passwordLabel,
                            value = if (isPasswordVisible) String(password) else "•".repeat(12),
                            labelColor = subTitleTextColor,
                            valueColor = titleTextColor,
                            backgroundColor = fieldBackgroundColor,
                            borderColor = fieldBorderColor,
                            onCopy = { copyToClipboard(passwordLabel, String(password), true) },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector =
                                            if (isPasswordVisible) Icons.Default.VisibilityOff
                                            else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility",
                                        tint = subTitleTextColor,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    },
                )

                Spacer(modifier = Modifier.height(20.dp))
                VaultActionGroup {
                    if (onToggleFavorite != null)
                        androidx.compose.material3.TextButton(
                            enabled = actionsEnabled,
                            onClick = onToggleFavorite,
                        ) {
                            CText(favoriteText, fontSize = 14.sp, color = subTitleTextColor)
                        }
                    if (onManageTags != null)
                        androidx.compose.material3.TextButton(
                            enabled = actionsEnabled,
                            onClick = onManageTags,
                        ) {
                            CText(tagsText, fontSize = 14.sp, color = subTitleTextColor)
                        }
                    if (onHistory != null)
                        androidx.compose.material3.TextButton(
                            enabled = actionsEnabled,
                            onClick = onHistory,
                        ) {
                            CText(historyText, fontSize = 14.sp, color = subTitleTextColor)
                        }
                }
                if (actionError != null)
                    CText(
                        actionError,
                        fontSize = 14.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    CText(
                        text = closeButtonText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailFieldGroup(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    onCopy: () -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CText(
            text = label,
            fontSize = 14.sp,
            color = labelColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CText(
                text = value,
                fontSize = 16.sp,
                color = valueColor,
                textAlign = TextAlign.Right,
                modifier = if (isVaultLandscape()) Modifier.weight(1f) else Modifier,
                maxLines = if (isVaultLandscape()) 2 else Int.MAX_VALUE,
                overflow =
                    if (isVaultLandscape()) androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    else androidx.compose.ui.text.style.TextOverflow.Clip,
            )

            if (!isVaultLandscape()) Spacer(modifier = Modifier.weight(1f))

            if (trailingIcon != null) {
                trailingIcon()
                Spacer(modifier = Modifier.width(12.dp))
            }

            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = labelColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
