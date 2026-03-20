package com.example.mypasswords.screens.password

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.compose_ui.components.CCategoryItem
import com.example.compose_ui.components.CCategoryList
import com.example.compose_ui.components.CText
import com.example.mypasswords.R
import com.example.mypasswords.ui.theme.BackgroundColor
import com.example.mypasswords.ui.theme.ButtonColor
import com.example.mypasswords.ui.theme.RectangleBackGroundColor
import com.example.mypasswords.ui.theme.RectangleIconBorderColor
import com.example.mypasswords.ui.theme.SubTitleTextColor
import com.example.mypasswords.ui.theme.TitleTextColor

@Composable
fun AddPasswordScreen(
    viewModel: AddPasswordViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onBackClick()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                AddPasswordTopBar(onBackClick = onBackClick)
            },
            containerColor = BackgroundColor
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .background(BackgroundColor),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Select Category Section
                AddPasswordFieldLabel(stringResource(id = R.string.select_category_label))

                Spacer(modifier = Modifier.height(16.dp))

                CCategoryList(
                    categories = uiState.categories.map { chip -> CCategoryItem(chip.id, chip.name) },
                    selectedCategoryId = uiState.selectedCategoryId,
                    onCategorySelected = viewModel::onCategorySelected,
                    activeColor = ButtonColor,
                    inactiveColor = RectangleBackGroundColor,
                    activeTextColor = TitleTextColor,
                    inactiveTextColor = SubTitleTextColor,
                    borderColor = RectangleIconBorderColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Platform Name Field
                AddPasswordFieldLabel(stringResource(id = R.string.platform_name_label))
                Spacer(modifier = Modifier.height(8.dp))
                AddPasswordTextField(
                    value = uiState.platformName,
                    onValueChange = viewModel::onPlatformNameChanged,
                    placeholder = stringResource(id = R.string.platform_name_placeholder),
                    leadingIcon = Icons.Default.GridView
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Username Field
                AddPasswordFieldLabel(stringResource(id = R.string.username_email_label))
                Spacer(modifier = Modifier.height(8.dp))
                AddPasswordTextField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChanged,
                    placeholder = stringResource(id = R.string.username_email_placeholder),
                    leadingIcon = Icons.Default.PersonOutline
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password Field
                AddPasswordFieldLabel(stringResource(id = R.string.password_label))
                Spacer(modifier = Modifier.height(8.dp))
                var isPasswordVisible by remember { mutableStateOf(false) }
                AddPasswordTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChanged,
                    placeholder = "••••••••••••",
                    leadingIcon = Icons.Default.LockOpen,
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = SubTitleTextColor
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Security Notice
                SecurityNotice()

                Spacer(modifier = Modifier.weight(1f))

                // Action Buttons
                Button(
                    onClick = viewModel::onSaveClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = uiState.isSaveEnabled,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonColor,
                        disabledContainerColor = ButtonColor.copy(alpha = 0.5f)
                    )
                ) {
                    CText(
                        text = stringResource(id = R.string.save_now_btn),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun AddPasswordTopBar(onBackClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 8.dp)
    ) {
        // Icon (X) on the right (Start in RTL)
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = TitleTextColor
            )
        }

        // Title in the center
        CText(
            text = stringResource(id = R.string.add_password_title),
            color = TitleTextColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}


@Composable
private fun AddPasswordFieldLabel(label: String) {
    CText(
        text = label,
        color = TitleTextColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AddPasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TitleTextColor,
            unfocusedTextColor = TitleTextColor,
            focusedBorderColor = RectangleIconBorderColor,
            unfocusedBorderColor = RectangleIconBorderColor,
            focusedContainerColor = RectangleBackGroundColor,
            unfocusedContainerColor = RectangleBackGroundColor,
            cursorColor = ButtonColor
        ),
        placeholder = {
            CText(
                text = placeholder,
                color = SubTitleTextColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        },
        leadingIcon = {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = TitleTextColor
                )
            }
        },
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            textAlign = TextAlign.Start,
            fontSize = 14.sp
        )
    )
}

@Composable
private fun SecurityNotice() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = RectangleIconBorderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFF10B981), // Green shield
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            CText(
                text = stringResource(id = R.string.security_notice),
                color = SubTitleTextColor,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
