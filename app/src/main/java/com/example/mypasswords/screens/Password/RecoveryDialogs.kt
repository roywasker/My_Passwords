package com.example.mypasswords.screens.password

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compose_ui.components.SensitiveClipboardCleaner
import com.example.mypasswords.R

@Composable
fun RecoveryCodeDialog(
    code: String?,
    isCreating: Boolean,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val recoveryTitle = stringResource(R.string.recovery_title)
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides
            androidx.compose.ui.unit.LayoutDirection.Rtl
    ) {
        AlertDialog(
            modifier =
                if (com.example.compose_ui.components.isVaultLandscape()) Modifier.width(560.dp)
                else Modifier,
            properties =
                androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = !com.example.compose_ui.components.isVaultLandscape()
                ),
            containerColor = com.example.mypasswords.ui.theme.RectangleBackGroundColor,
            titleContentColor = com.example.mypasswords.ui.theme.TitleTextColor,
            textContentColor = com.example.mypasswords.ui.theme.SubTitleTextColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            onDismissRequest = { if (!isCreating) onDismiss() },
            title = {
                RightDialogText(
                    stringResource(R.string.recovery_title),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right,
                )
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = androidx.compose.ui.AbsoluteAlignment.Right,
                ) {
                    RightDialogText(
                        stringResource(
                            if (code == null) R.string.recovery_setup_explanation
                            else R.string.recovery_save_explanation
                        )
                    )
                    if (code != null) {
                        Text(
                            code,
                            color = com.example.mypasswords.ui.theme.CircleIconColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .background(
                                        com.example.mypasswords.ui.theme.BackgroundColor,
                                        androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                    )
                                    .padding(16.dp),
                        )
                        TextButton(
                            onClick = {
                                SensitiveClipboardCleaner(context).copy(recoveryTitle, code)
                            }
                        ) {
                            RightDialogText(stringResource(R.string.recovery_copy))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isCreating,
                    onClick = { if (code == null) onGenerate() else onDismiss() },
                ) {
                    RightDialogText(
                        stringResource(
                            if (code == null) R.string.recovery_generate
                            else R.string.recovery_saved
                        )
                    )
                }
            },
            dismissButton = {
                if (code == null)
                    TextButton(enabled = !isCreating, onClick = onDismiss) {
                        RightDialogText(stringResource(R.string.cancel_btn))
                    }
            },
        )
    }
}

@Composable
fun RecoveryInputDialog(onRecover: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides
            androidx.compose.ui.unit.LayoutDirection.Rtl
    ) {
        AlertDialog(
            modifier =
                if (com.example.compose_ui.components.isVaultLandscape()) Modifier.width(560.dp)
                else Modifier,
            properties =
                androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = !com.example.compose_ui.components.isVaultLandscape()
                ),
            containerColor = com.example.mypasswords.ui.theme.RectangleBackGroundColor,
            titleContentColor = com.example.mypasswords.ui.theme.TitleTextColor,
            textContentColor = com.example.mypasswords.ui.theme.SubTitleTextColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            onDismissRequest = onDismiss,
            title = {
                RightDialogText(
                    stringResource(R.string.recovery_title),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right,
                )
            },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = androidx.compose.ui.AbsoluteAlignment.Right,
                ) {
                    RightDialogText(stringResource(R.string.recovery_enter_explanation))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        singleLine = true,
                        label = { RightDialogText(stringResource(R.string.recovery_code_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false,
                            ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = code.isNotBlank(),
                    onClick = {
                        val submitted = code
                        code = ""
                        onRecover(submitted)
                    },
                ) {
                    RightDialogText(stringResource(R.string.recovery_start))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        code = ""
                        onDismiss()
                    }
                ) {
                    RightDialogText(stringResource(R.string.cancel_btn))
                }
            },
        )
    }
}

@Composable
private fun RightDialogText(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: androidx.compose.ui.text.style.TextAlign =
        androidx.compose.ui.text.style.TextAlign.Right,
) {
    Text(text, modifier = modifier.fillMaxWidth(), textAlign = textAlign)
}
