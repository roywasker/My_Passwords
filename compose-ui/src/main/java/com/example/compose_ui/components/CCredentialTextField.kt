package com.example.compose_ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CCredentialTextField(
    label: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    labelColor: Color = Color(0xFFACBED4),
    fieldBackgroundColor: Color = Color(0xFF0C192A),
    fieldBorderColor: Color = Color(0xFF2B4058),
    textColor: Color = Color.White,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean? = null,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    onGeneratePassword: (() -> Unit)? = null,
    enabled: Boolean = true,
    placeholder: String = "",
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    var localVisibility by remember { mutableStateOf(false) }
    val visible = isPasswordVisible ?: localVisibility
    Column(modifier = Modifier.fillMaxWidth()) {
        if (label != null) {
            CText(
                text = label,
                fontSize = 14.sp,
                color = labelColor,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            enabled = enabled,
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = fieldBorderColor,
                    unfocusedBorderColor = fieldBorderColor,
                    focusedContainerColor = fieldBackgroundColor,
                    unfocusedContainerColor = fieldBackgroundColor,
                    cursorColor = textColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                ),
            textStyle =
                TextStyle(
                    fontSize = 16.sp,
                    textAlign = TextAlign.Right,
                ),
            placeholder =
                if (placeholder.isBlank()) null
                else {
                    {
                        CText(
                            text = placeholder,
                            color = labelColor,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Right,
                        )
                    }
                },
            leadingIcon = leadingIcon?.let { icon -> { Icon(icon, null, tint = textColor) } },
            singleLine = true,
            visualTransformation =
                if (isPassword && !visible) PasswordVisualTransformation()
                else VisualTransformation.None,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                    autoCorrectEnabled = false,
                ),
            trailingIcon =
                if (isPassword) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onGeneratePassword != null) {
                                IconButton(onClick = onGeneratePassword, enabled = enabled) {
                                    Icon(
                                        imageVector = Icons.Default.Cached,
                                        contentDescription =
                                            androidx.compose.ui.res.stringResource(
                                                com.example.compose_ui.R.string.credential_generate
                                            ),
                                        tint = textColor.copy(alpha = 0.8f),
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (onTogglePasswordVisibility != null)
                                        onTogglePasswordVisibility()
                                    else {
                                        localVisibility = !localVisibility
                                    }
                                },
                                enabled = enabled,
                            ) {
                                Icon(
                                    imageVector =
                                        if (visible) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                    contentDescription =
                                        androidx.compose.ui.res.stringResource(
                                            if (visible)
                                                com.example.compose_ui.R.string
                                                    .credential_hide_password
                                            else
                                                com.example.compose_ui.R.string
                                                    .credential_show_password
                                        ),
                                    tint = labelColor,
                                )
                            }
                        }
                    }
                } else null,
        )
    }
}
