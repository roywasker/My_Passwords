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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CEditPasswordSheet(
    platformName: String,
    initialUsername: String,
    initialPassword: String,
    website: String,
    onDismiss: () -> Unit,
    onUpdate: (String, String) -> Unit,
    titleTextColor: Color = Color.White,
    subTitleTextColor: Color = Color(0xFF94A3B8),
    buttonColor: Color = Color(0xFF1D4ED8),
    backgroundColor: Color = Color(0xFF101622),
    fieldBackgroundColor: Color = Color(0xFF0F172A),
    fieldBorderColor: Color = Color(0x4D808080),
    title: String,
    usernameLabel: String,
    passwordLabel: String,
    updateButtonText: String
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isImageError by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    
                    Spacer(modifier = Modifier.size(16.dp))

                    CText(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleTextColor,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Username Field
                EditFieldGroup(
                    label = usernameLabel,
                    value = username,
                    onValueChange = { username = it },
                    labelColor = subTitleTextColor,
                    fieldBackgroundColor = fieldBackgroundColor,
                    fieldBorderColor = fieldBorderColor,
                    textColor = titleTextColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password Field
                EditFieldGroup(
                    label = passwordLabel,
                    value = password,
                    onValueChange = { password = it },
                    labelColor = subTitleTextColor,
                    fieldBackgroundColor = fieldBackgroundColor,
                    fieldBorderColor = fieldBorderColor,
                    textColor = titleTextColor,
                    isPassword = true,
                    isPasswordVisible = isPasswordVisible,
                    onTogglePasswordVisibility = { isPasswordVisible = !isPasswordVisible }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Update Button
                Button(
                    onClick = { onUpdate(username, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = buttonColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    CText(
                        text = updateButtonText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFieldGroup(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    labelColor: Color,
    fieldBackgroundColor: Color,
    fieldBorderColor: Color,
    textColor: Color,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null
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
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = fieldBorderColor,
                unfocusedBorderColor = fieldBorderColor,
                focusedContainerColor = fieldBackgroundColor,
                unfocusedContainerColor = fieldBackgroundColor,
                cursorColor = textColor,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor
            ),
            textStyle = TextStyle(
                fontSize = 16.sp,
                textAlign = TextAlign.Start
            ),
            singleLine = true,
            visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { onTogglePasswordVisibility?.invoke() }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = labelColor
                        )
                    }
                }
            } else null
        )
    }
}
