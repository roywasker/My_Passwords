package com.example.mypasswords.screens.password

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.compose_ui.components.CText
import com.example.mypasswords.R
import com.example.mypasswords.ui.theme.BackgroundColor
import com.example.mypasswords.ui.theme.DeleteButtonBackgroundColor
import com.example.mypasswords.ui.theme.DeleteButtonColor
import com.example.mypasswords.ui.theme.RectangleBackGroundColor
import com.example.mypasswords.ui.theme.RectangleIconBorderColor
import com.example.mypasswords.ui.theme.SubTitleTextColor
import com.example.mypasswords.ui.theme.TitleTextColor

@Composable
fun DeleteConfirmationDialog(
    platformName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val landscape = com.example.compose_ui.components.isVaultLandscape()
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = !landscape),
    ) {
        Surface(
            modifier =
                (if (landscape) Modifier.widthIn(max = 600.dp) else Modifier)
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(32.dp),
            color = BackgroundColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, RectangleIconBorderColor),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(
                    modifier =
                        Modifier.verticalScroll(rememberScrollState())
                            .padding(if (landscape) 16.dp else 24.dp)
                            .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Trash Icon
                    Box(
                        modifier =
                            Modifier.size(if (landscape) 40.dp else 64.dp)
                                .clip(CircleShape)
                                .background(DeleteButtonBackgroundColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = DeleteButtonColor,
                            modifier = Modifier.size(30.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(if (landscape) 8.dp else 24.dp))

                    // Title
                    CText(
                        text = stringResource(id = R.string.delete_dialog_title),
                        color = TitleTextColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    val descTemplate = stringResource(id = R.string.delete_dialog_description)
                    val parts = descTemplate.split("%s")
                    val descriptionText = buildAnnotatedString {
                        withStyle(style = ParagraphStyle(textDirection = TextDirection.Rtl)) {
                            if (parts.isNotEmpty()) append(parts[0])
                            withStyle(
                                style =
                                    SpanStyle(color = TitleTextColor, fontWeight = FontWeight.Bold)
                            ) {
                                append(platformName)
                            }
                            if (parts.size > 1) append(parts[1])
                        }
                    }

                    CText(
                        text = descriptionText,
                        color = SubTitleTextColor,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(if (landscape) 12.dp else 32.dp))

                    com.example.compose_ui.components.VaultFieldPair(
                        12.dp,
                        first = {
                            // Delete Button
                            Button(
                                onClick = onConfirm,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors =
                                    ButtonDefaults.buttonColors(containerColor = DeleteButtonColor),
                            ) {
                                CText(
                                    text = stringResource(id = R.string.delete_dialog_confirm),
                                    color = DeleteButtonBackgroundColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        },
                        second = {
                            // Cancel Button
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = RectangleBackGroundColor
                                    ),
                                border =
                                    androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        RectangleIconBorderColor,
                                    ),
                            ) {
                                CText(
                                    text = stringResource(id = R.string.delete_dialog_cancel),
                                    color = TitleTextColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
