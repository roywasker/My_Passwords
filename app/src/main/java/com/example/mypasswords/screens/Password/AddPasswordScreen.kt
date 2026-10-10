package com.example.mypasswords.screens.password

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.mypasswords.ui.theme.VaultFeature
import com.example.mypasswords.util.toMessage

@Composable
fun AddPasswordScreen(
    viewModel: AddPasswordViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
) {
    val landscape = com.example.compose_ui.components.isVaultLandscape()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onBackClick()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error.toMessage(context))
            viewModel.clearError()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = if (landscape) Modifier.displayCutoutPadding().imePadding() else Modifier,
            topBar = {
                if (landscape) LandscapeAddTopBar(uiState, viewModel, onBackClick)
                else AddPasswordTopBar(onBackClick = onBackClick)
            },
            bottomBar = {
                if (!landscape)
                    Box(
                        Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        AddSaveButton(uiState.isSaveEnabled, viewModel::onSaveClick)
                    }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = BackgroundColor,
        ) { padding ->
            if (landscape) {
                LandscapeAddForm(uiState, viewModel, Modifier.padding(padding))
            } else {
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .background(BackgroundColor),
                    horizontalAlignment = Alignment.Start,
                ) {
                    VaultFeature(
                        Icons.Default.Shield,
                        stringResource(R.string.add_password_intro),
                        stringResource(R.string.add_password_intro_body),
                        Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Select Category Section
                    AddPasswordFieldLabel(stringResource(id = R.string.select_category_label))

                    Spacer(modifier = Modifier.height(16.dp))

                    CCategoryList(
                        categories =
                            uiState.categories.map { chip -> CCategoryItem(chip.id, chip.name) },
                        selectedCategoryId = uiState.selectedCategoryId,
                        onCategorySelected = viewModel::onCategorySelected,
                        activeColor = ButtonColor,
                        inactiveColor = RectangleBackGroundColor,
                        activeTextColor = TitleTextColor,
                        inactiveTextColor = SubTitleTextColor,
                        borderColor = RectangleIconBorderColor,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Platform Name Field
                    AddPasswordFieldLabel(stringResource(id = R.string.platform_name_label))
                    Spacer(modifier = Modifier.height(8.dp))
                    com.example.compose_ui.components.CCredentialTextField(
                        value = uiState.platformName,
                        onValueChange = viewModel::onPlatformNameChanged,
                        placeholder = stringResource(id = R.string.platform_name_placeholder),
                        enabled = !uiState.isSaving,
                        leadingIcon = Icons.Default.GridView,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Username Field
                    AddPasswordFieldLabel(stringResource(id = R.string.username_email_label))
                    Spacer(modifier = Modifier.height(8.dp))
                    com.example.compose_ui.components.CCredentialTextField(
                        value = String(uiState.username),
                        onValueChange = viewModel::onUsernameChanged,
                        placeholder = stringResource(id = R.string.username_email_placeholder),
                        enabled = !uiState.isSaving,
                        leadingIcon = Icons.Default.PersonOutline,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    AddPasswordFieldLabel(stringResource(id = R.string.password_label))
                    Spacer(modifier = Modifier.height(8.dp))
                    com.example.compose_ui.components.CCredentialTextField(
                        value = String(uiState.password),
                        onValueChange = viewModel::onPasswordChanged,
                        placeholder = "••••••••••••",
                        enabled = !uiState.isSaving,
                        isPassword = true,
                        leadingIcon = Icons.Default.LockOpen,
                        onGeneratePassword = viewModel::onGeneratePasswordClicked,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AddPasswordFieldLabel(stringResource(R.string.tags_label))
                    com.example.compose_ui.components.CCredentialTextField(
                        value = uiState.tagsInput,
                        onValueChange = viewModel::onTagsChanged,
                        enabled = !uiState.isSaving,
                    )
                    CText(
                        stringResource(R.string.tags_help),
                        color = SubTitleTextColor,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right,
                    )
                    Spacer(Modifier.height(16.dp))

                    // Security Notice
                    SecurityNotice()

                    Spacer(modifier = Modifier.height(24.dp))

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun AddPasswordTopBar(onBackClick: () -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp)
    ) {
        // Icon (X) on the right (Start in RTL)
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cancel_btn),
                tint = TitleTextColor,
            )
        }

        // Title in the center
        CText(
            text = stringResource(id = R.string.add_password_title),
            color = TitleTextColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
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
        textAlign = TextAlign.Right,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecurityNotice() {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = RectangleIconBorderColor,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFF10B981), // Green shield
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            CText(
                text = stringResource(id = R.string.security_notice),
                color = SubTitleTextColor,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AddSaveButton(enabled: Boolean, onSave: () -> Unit) {
    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ButtonColor,
                disabledContainerColor = ButtonColor.copy(alpha = 0.5f),
            ),
    ) {
        CText(
            text = stringResource(id = R.string.save_now_btn),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LandscapeAddTopBar(
    state: AddPasswordUiState,
    vm: AddPasswordViewModel,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.Close, stringResource(R.string.cancel_btn), tint = TitleTextColor)
        }
        CText(
            stringResource(R.string.add_password_title),
            color = TitleTextColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Right,
        )
        Box(Modifier.width(168.dp)) { AddSaveButton(state.isSaveEnabled, vm::onSaveClick) }
    }
}

@Composable
private fun LandscapeAddForm(
    state: AddPasswordUiState,
    vm: AddPasswordViewModel,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CCategoryList(
            categories = state.categories.map { CCategoryItem(it.id, it.name) },
            selectedCategoryId = state.selectedCategoryId,
            onCategorySelected = vm::onCategorySelected,
            activeColor = ButtonColor,
            inactiveColor = RectangleBackGroundColor,
            activeTextColor = TitleTextColor,
            inactiveTextColor = SubTitleTextColor,
            borderColor = RectangleIconBorderColor,
        )
        com.example.compose_ui.components.VaultFieldPair(
            16.dp,
            first = {
                AddPasswordFieldLabel(stringResource(R.string.platform_name_label))
                Spacer(Modifier.height(8.dp))
                com.example.compose_ui.components.CCredentialTextField(
                    value = state.platformName,
                    onValueChange = vm::onPlatformNameChanged,
                    placeholder = stringResource(R.string.platform_name_placeholder),
                    enabled = !state.isSaving,
                    leadingIcon = Icons.Default.GridView,
                )
            },
            second = {
                AddPasswordFieldLabel(stringResource(R.string.username_email_label))
                Spacer(Modifier.height(8.dp))
                com.example.compose_ui.components.CCredentialTextField(
                    value = String(state.username),
                    onValueChange = vm::onUsernameChanged,
                    placeholder = stringResource(R.string.username_email_placeholder),
                    enabled = !state.isSaving,
                    leadingIcon = Icons.Default.PersonOutline,
                )
            },
        )
        com.example.compose_ui.components.VaultFieldPair(
            16.dp,
            first = {
                AddPasswordFieldLabel(stringResource(R.string.password_label))
                Spacer(Modifier.height(8.dp))
                com.example.compose_ui.components.CCredentialTextField(
                    value = String(state.password),
                    onValueChange = vm::onPasswordChanged,
                    placeholder = "••••••••••••",
                    enabled = !state.isSaving,
                    isPassword = true,
                    leadingIcon = Icons.Default.LockOpen,
                    onGeneratePassword = vm::onGeneratePasswordClicked,
                )
            },
            second = {
                AddPasswordFieldLabel(stringResource(R.string.tags_label))
                Spacer(Modifier.height(8.dp))
                com.example.compose_ui.components.CCredentialTextField(
                    value = state.tagsInput,
                    onValueChange = vm::onTagsChanged,
                    enabled = !state.isSaving,
                )
                CText(
                    stringResource(R.string.tags_help),
                    color = SubTitleTextColor,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                )
            },
        )
        SecurityNotice()
    }
}
