package com.example.mypasswords.screens.password

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.compose_ui.components.CCategoryItem
import com.example.compose_ui.components.CCategoryList
import com.example.compose_ui.components.CEditPasswordSheet
import com.example.compose_ui.components.CFloatingIcon
import com.example.compose_ui.components.CPasswordDetailSheet
import com.example.compose_ui.components.CText
import com.example.core.model.PasswordItemModel
import com.example.core.util.PasswordGenerator
import com.example.mypasswords.R
import com.example.mypasswords.ui.theme.BackgroundColor
import com.example.mypasswords.ui.theme.ButtonColor
import com.example.mypasswords.ui.theme.CircleIconBackGroundColor
import com.example.mypasswords.ui.theme.CircleIconBorderColor
import com.example.mypasswords.ui.theme.CircleIconColor
import com.example.mypasswords.ui.theme.DeleteButtonBackgroundColor
import com.example.mypasswords.ui.theme.DeleteButtonColor
import com.example.mypasswords.ui.theme.RectangleBackGroundColor
import com.example.mypasswords.ui.theme.RectangleIconBorderColor
import com.example.mypasswords.ui.theme.SubTitleTextColor
import com.example.mypasswords.ui.theme.TitleTextColor
import com.example.mypasswords.util.toMessage

@Composable
fun PasswordScreen(
    viewModel: PasswordViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredPasswords by viewModel.filteredPasswords.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    BackHandler {
        onBackClick()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error.toMessage(context))
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .background(BackgroundColor)
                .systemBarsPadding()
                .then(
                    if (com.example.compose_ui.components.isVaultLandscape())
                        Modifier.displayCutoutPadding().imePadding()
                    else Modifier
                ),
        containerColor = BackgroundColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { AddPasswordFAB(onAddClick = onAddClick) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (com.example.compose_ui.components.isVaultLandscape()) {
                LandscapePasswords(uiState, filteredPasswords, viewModel)
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PasswordTopBar(
                        onVaultClick = viewModel::showRecoveryDialog,
                        isSearchActive = uiState.isSearchActive,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChanged = viewModel::onSearchQueryChanged,
                        onToggleSearch = viewModel::onToggleSearch,
                    )

                    androidx.compose.material3.Surface(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        color = RectangleBackGroundColor,
                        shape = RoundedCornerShape(28.dp),
                        border =
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                RectangleIconBorderColor,
                            ),
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CText(
                                stringResource(R.string.vault_overview_title),
                                color = TitleTextColor,
                                fontSize = 23.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                            )
                            CText(
                                stringResource(R.string.vault_overview_subtitle),
                                color = SubTitleTextColor,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                textAlign = TextAlign.Right,
                            )
                            com.example.mypasswords.ui.theme.VaultBadge(
                                if (uiState.listStatus == PasswordListStatus.Ready)
                                    stringResource(
                                        R.string.vault_items_count,
                                        filteredPasswords.size,
                                    )
                                else stringResource(R.string.vault_locked_hint)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    CCategoryList(
                        categories =
                            uiState.categories.map { chip -> CCategoryItem(chip.id, chip.name) },
                        selectedCategoryId = uiState.selectedCategoryId,
                        onCategorySelected = viewModel::onCategorySelected,
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        activeColor = ButtonColor,
                        inactiveColor = RectangleBackGroundColor,
                        activeTextColor = TitleTextColor,
                        inactiveTextColor = SubTitleTextColor,
                        borderColor = RectangleIconBorderColor,
                    )
                    VaultTools(uiState, viewModel)

                    Spacer(modifier = Modifier.height(24.dp))

                    PasswordResults(uiState, filteredPasswords, viewModel)
                }
            }
            Column {
                uiState.itemToEdit?.let { detail ->
                    CEditPasswordSheet(
                        recordKey = detail.id,
                        platformName = detail.platform,
                        initialUsername = String(detail.username),
                        initialPassword = String(detail.password),
                        website = detail.website,
                        onDismiss = viewModel::dismissEdit,
                        onUpdate = viewModel::updateCredential,
                        onGeneratePassword = { PasswordGenerator.generate() },
                        titleTextColor = TitleTextColor,
                        subTitleTextColor = SubTitleTextColor,
                        buttonColor = ButtonColor,
                        backgroundColor = BackgroundColor,
                        fieldBackgroundColor = RectangleBackGroundColor,
                        fieldBorderColor = RectangleIconBorderColor,
                        title = stringResource(id = R.string.update_password),
                        usernameLabel = stringResource(id = R.string.username_label),
                        passwordLabel = stringResource(id = R.string.password_label),
                        updateButtonText = stringResource(id = R.string.edit),
                        isSaving = uiState.isUpdating,
                    )
                }
            }

            uiState.selectedItemDetail?.let { detail ->
                CPasswordDetailSheet(
                    platformName = detail.platform,
                    username = detail.username,
                    password = detail.password,
                    website = detail.website,
                    onDismiss = viewModel::dismissPasswordDetail,
                    onHistory = { viewModel.showHistory(detail.id, detail.platform) },
                    onManageTags = { viewModel.editTags(detail) },
                    onToggleFavorite = { viewModel.toggleFavorite(detail.id, !detail.isFavorite) },
                    historyText = stringResource(R.string.history_title),
                    tagsText = stringResource(R.string.tags_edit),
                    favoriteText =
                        stringResource(
                            if (detail.isFavorite) R.string.favorite_remove
                            else R.string.favorite_add
                        ),
                    actionsEnabled = !uiState.featureBusy,
                    actionError = uiState.featureError?.toMessage(context),
                    titleTextColor = TitleTextColor,
                    subTitleTextColor = SubTitleTextColor,
                    buttonColor = ButtonColor,
                    backgroundColor = BackgroundColor,
                    fieldBackgroundColor = RectangleBackGroundColor,
                    fieldBorderColor = RectangleIconBorderColor,
                    closeButtonText = stringResource(id = R.string.close_btn),
                    usernameLabel = stringResource(id = R.string.username_label),
                    passwordLabel = stringResource(id = R.string.password_label),
                    loginDetailsSubtitle = stringResource(id = R.string.login_details_subtitle),
                )
            }

            uiState.itemToDelete?.let { item ->
                DeleteConfirmationDialog(
                    platformName = item.platform,
                    onConfirm = viewModel::confirmDelete,
                    onDismiss = viewModel::dismissDeleteConfirmation,
                )
            }
            if (uiState.isRecoveryDialogVisible) {
                RecoveryCodeDialog(
                    uiState.recoveryCode,
                    uiState.isCreatingRecoveryCode,
                    viewModel::createRecoveryCode,
                    viewModel::dismissRecoveryDialog,
                )
            }

            if (!com.example.compose_ui.components.isVaultLandscape()) BottomBlurEffect()
            VaultFeaturePanels(uiState, viewModel)
        }
    }
}

@Composable
private fun PasswordTopBar(
    onVaultClick: () -> Unit,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearch: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(end = 40.dp),
                placeholder = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(
                            text = stringResource(id = R.string.search_placeholder),
                            color = SubTitleTextColor,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right,
                        )
                    }
                },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TitleTextColor,
                        unfocusedTextColor = TitleTextColor,
                        focusedBorderColor = RectangleIconBorderColor,
                        unfocusedBorderColor = RectangleIconBorderColor,
                        focusedContainerColor = RectangleBackGroundColor,
                        unfocusedContainerColor = RectangleBackGroundColor,
                        cursorColor = ButtonColor,
                    ),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                textStyle =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        textAlign = TextAlign.Right,
                    ),
            )

            Box(
                modifier =
                    Modifier.align(Alignment.CenterEnd).size(40.dp).clip(CircleShape).clickable {
                        onToggleSearch()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(id = R.string.close_search_desc),
                    tint = TitleTextColor,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CFloatingIcon(
                    icon = Icons.Default.Search,
                    iconTint = SubTitleTextColor,
                    iconBackGroundColor = RectangleBackGroundColor,
                    borderColor = RectangleIconBorderColor,
                    onClick = onToggleSearch,
                )

                CFloatingIcon(
                    icon = Icons.Default.Security,
                    iconTint = CircleIconColor,
                    iconBackGroundColor = CircleIconBackGroundColor,
                    borderColor = CircleIconBorderColor,
                    contentDescription = stringResource(R.string.recovery_title),
                    onClick = onVaultClick,
                )
            }

            CText(
                text = stringResource(id = R.string.password_screen_title),
                color = TitleTextColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PasswordList(
    passwords: List<PasswordItemModel>,
    onDeletePassword: (PasswordItemModel) -> Unit,
    onPasswordClick: (PasswordItemModel) -> Unit,
    onEditPassword: (PasswordItemModel) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = if (com.example.compose_ui.components.isVaultLandscape()) 12.dp else 24.dp,
                end = if (com.example.compose_ui.components.isVaultLandscape()) 12.dp else 24.dp,
                bottom =
                    if (com.example.compose_ui.components.isVaultLandscape()) 96.dp else 130.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(passwords, key = { it.id }) { item ->
            PasswordCard(
                item = item,
                onEditClick = { onEditPassword(item) },
                onDeleteClick = { onDeletePassword(item) },
                onPasswordClick = { onPasswordClick(item) },
            )
        }
    }
}

@Composable
private fun BoxScope.BottomBlurEffect() {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    BackgroundColor.copy(alpha = 0.9f),
                                    BackgroundColor,
                                )
                        )
                )
    )
}

@Composable
private fun AddPasswordFAB(onAddClick: () -> Unit) {
    FloatingActionButton(
        onClick = onAddClick,
        modifier = Modifier.padding(start = 16.dp, bottom = 20.dp).size(64.dp),
        containerColor = ButtonColor,
        shape = RoundedCornerShape(24.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(id = R.string.add_password_btn),
            tint = Color.White,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
fun PasswordCard(
    item: PasswordItemModel,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPasswordClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(RectangleBackGroundColor)
                .border(
                    width = 1.dp,
                    color = RectangleIconBorderColor,
                    shape = RoundedCornerShape(24.dp),
                )
                .clickable { onPasswordClick() }
                .padding(
                    horizontal =
                        if (com.example.compose_ui.components.isVaultLandscape()) 8.dp else 16.dp,
                    vertical = 16.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Delete Icon
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).clickable { onDeleteClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier.size(34.dp).clip(CircleShape).background(DeleteButtonBackgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(id = R.string.delete_btn),
                    tint = DeleteButtonColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(
            modifier =
                Modifier.width(
                    if (com.example.compose_ui.components.isVaultLandscape()) 6.dp else 12.dp
                )
        )

        // Edit Icon
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).clickable { onEditClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier.size(34.dp).clip(CircleShape).background(CircleIconBackGroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(id = R.string.edit_btn),
                    tint = ButtonColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(
            modifier =
                Modifier.weight(1f)
                    .padding(
                        horizontal =
                            if (com.example.compose_ui.components.isVaultLandscape()) 8.dp
                            else 16.dp
                    ),
            horizontalAlignment = Alignment.End,
        ) {
            if (item.isFavorite)
                Icon(
                    androidx.compose.material.icons.Icons.Default.Star,
                    contentDescription = stringResource(R.string.favorite_marker),
                    tint = CircleIconColor,
                    modifier = Modifier.size(16.dp),
                )
            CText(
                text = item.platform,
                color = TitleTextColor,
                fontSize = 16.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            CText(
                text = item.maskedUserName,
                color = SubTitleTextColor,
                fontSize = 13.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Right,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Platform Icon
        var isImageError by remember(item.id) { mutableStateOf(false) }

        Box(
            modifier =
                Modifier.size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BackgroundColor)
                    .border(
                        width = 1.dp,
                        color = RectangleIconBorderColor,
                        shape = RoundedCornerShape(12.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            CText(
                text = item.letter,
                color = TitleTextColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ListMessage(
    message: String,
    loading: Boolean = false,
    retry: (() -> Unit)? = null,
    rightAligned: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) androidx.compose.material3.CircularProgressIndicator()
        Text(
            message,
            color = SubTitleTextColor,
            modifier =
                Modifier.padding(top = 16.dp)
                    .then(if (rightAligned) Modifier.fillMaxWidth() else Modifier),
            textAlign = if (rightAligned) TextAlign.Right else TextAlign.Right,
        )
        if (retry != null)
            androidx.compose.material3.TextButton(onClick = retry) {
                Text(stringResource(R.string.retry_loading))
            }
    }
}

@Composable
private fun LandscapePasswords(
    uiState: PasswordUiState,
    filteredPasswords: List<PasswordItemModel>,
    viewModel: PasswordViewModel,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val sidebarWidth = if (maxWidth >= 720.dp) 240.dp else 180.dp
        Column(Modifier.fillMaxSize()) {
            PasswordTopBar(
                onVaultClick = viewModel::showRecoveryDialog,
                isSearchActive = uiState.isSearchActive,
                searchQuery = uiState.searchQuery,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onToggleSearch = viewModel::onToggleSearch,
            )
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f).fillMaxSize()) {
                    PasswordResults(uiState, filteredPasswords, viewModel)
                }
                Column(
                    Modifier.width(sidebarWidth)
                        .fillMaxSize()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(top = 8.dp, bottom = 96.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    com.example.mypasswords.ui.theme.VaultBadge(
                        if (uiState.listStatus == PasswordListStatus.Ready)
                            stringResource(R.string.vault_items_count, filteredPasswords.size)
                        else stringResource(R.string.vault_locked_hint)
                    )
                    CCategoryList(
                        categories = uiState.categories.map { CCategoryItem(it.id, it.name) },
                        selectedCategoryId = uiState.selectedCategoryId,
                        onCategorySelected = viewModel::onCategorySelected,
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        activeColor = ButtonColor,
                        inactiveColor = RectangleBackGroundColor,
                        activeTextColor = TitleTextColor,
                        inactiveTextColor = SubTitleTextColor,
                        borderColor = RectangleIconBorderColor,
                    )
                    VaultTools(uiState, viewModel)
                }
            }
        }
    }
}

@Composable
private fun PasswordResults(
    uiState: PasswordUiState,
    filteredPasswords: List<PasswordItemModel>,
    viewModel: PasswordViewModel,
) {
    val context = LocalContext.current
    when (val status = uiState.listStatus) {
        PasswordListStatus.Loading ->
            ListMessage(stringResource(R.string.passwords_loading), loading = true)
        PasswordListStatus.Empty -> ListMessage(stringResource(R.string.passwords_empty))
        is PasswordListStatus.Failed ->
            ListMessage(
                status.error.toMessage(context),
                retry = viewModel::retryLoadingPasswords,
            )
        PasswordListStatus.Ready ->
            if (filteredPasswords.isEmpty()) {
                ListMessage(
                    stringResource(R.string.passwords_no_results),
                    rightAligned = true,
                )
            } else {
                PasswordList(
                    filteredPasswords,
                    viewModel::showDeleteConfirmation,
                    viewModel::onPasswordClick,
                    viewModel::onEditClick,
                )
            }
    }
}
