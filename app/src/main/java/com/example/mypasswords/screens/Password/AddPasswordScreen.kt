package com.example.mypasswords.screens.Password

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.compose_ui.components.CText
import com.example.core.model.CategoryChip
import com.example.mypasswords.R
import com.example.mypasswords.theme.*

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

                CategorySelectionList(
                    categories = uiState.categories,
                    selectedCategoryId = uiState.selectedCategoryId,
                    onCategorySelected = viewModel::onCategorySelected
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
private fun CategorySelectionList(
    categories: List<CategoryChip>,
    selectedCategoryId: Int,
    onCategorySelected: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(categories) { category ->
            val isSelected = category.id == selectedCategoryId
            CategoryChipItem(
                category = category,
                isSelected = isSelected,
                onClick = { onCategorySelected(category.id) }
            )
        }
    }
}

@Composable
private fun CategoryChipItem(
    category: CategoryChip,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) ButtonColor else RectangleBackGroundColor
    val textColor = if (isSelected) TitleTextColor else SubTitleTextColor

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = if (isSelected) Color.Transparent else RectangleIconBorderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        CText(
            text = category.name,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
