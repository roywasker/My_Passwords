package com.example.compose_ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CPasswordDetailSheet(
    platformName: String,
    username: CharArray,
    password: CharArray,
    website: String,
    onDismiss: () -> Unit,
    titleTextColor: Color = Color.White,
    subTitleTextColor: Color = Color(0xFF94A3B8),
    buttonColor: Color = Color(0xFF1D4ED8),
    backgroundColor: Color = Color(0xFF101622),
    fieldBackgroundColor: Color = Color(0xFF0F172A),
    fieldBorderColor: Color = Color(0x4D808080),
    closeButtonText: String,
    usernameLabel: String,
    passwordLabel: String,
    loginDetailsSubtitle: String
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isImageError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun copyToClipboard(label: String, text: String, isSensitive: Boolean) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        
        if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                } else {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
            clip.description.extras = extras
        }
        
        clipboard.setPrimaryClip(clip)
        
        // Auto-clear clipboard after 60 seconds for security
        if (isSensitive) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (clipboard.primaryClip?.description?.label == label) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }, 60000)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = backgroundColor,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = subTitleTextColor.copy(alpha = 0.4f))
        },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Platform Icon
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(fieldBackgroundColor)
                            .border(1.dp, fieldBorderColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (website.isNotBlank() && !isImageError) {
                            AsyncImage(
                                model = website,
                                contentDescription = platformName,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                onError = { isImageError = true }
                            )
                        } else {
                            CText(
                                text = platformName.firstOrNull()?.uppercase() ?: "",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = titleTextColor
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        CText(
                            text = platformName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = titleTextColor,
                            textAlign = TextAlign.Start
                        )
                        CText(
                            text = loginDetailsSubtitle,
                            fontSize = 14.sp,
                            color = subTitleTextColor,
                            textAlign = TextAlign.Start
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Username Field
                DetailFieldGroup(
                    label = usernameLabel,
                    value = String(username),
                    labelColor = subTitleTextColor,
                    valueColor = titleTextColor,
                    backgroundColor = fieldBackgroundColor,
                    borderColor = fieldBorderColor,
                    onCopy = { copyToClipboard(usernameLabel, String(username), false) }
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Visibility",
                            tint = subTitleTextColor,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { isPasswordVisible = !isPasswordVisible }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    CText(
                        text = closeButtonText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CText(
            text = label,
            fontSize = 14.sp,
            color = labelColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CText(
                text = value,
                fontSize = 16.sp,
                color = valueColor,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.weight(1f))

            if (trailingIcon != null) {
                trailingIcon()
                Spacer(modifier = Modifier.width(12.dp))
            }

            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = labelColor,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onCopy() }
            )
        }
    }
}
