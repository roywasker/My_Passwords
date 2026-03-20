package com.example.mypasswords.screens.Password

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.compose_ui.components.CFloatingIcon
import com.example.compose_ui.components.CText
import com.example.core.model.PasswordItemModel
import com.example.mypasswords.R
import com.example.mypasswords.theme.*

@Composable
fun PasswordScreen(
    viewModel: PasswordViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredPasswords by viewModel.filteredPasswords.collectAsStateWithLifecycle()

    BackHandler {
        onBackClick()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            PasswordTopBar(
                isSearchActive = uiState.isSearchActive,
                searchQuery = uiState.searchQuery,
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onToggleSearch = viewModel::onToggleSearch
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Categories
            CategoryList(
                categories = uiState.categories,
                selectedCategoryId = uiState.selectedCategoryId,
                onCategorySelected = viewModel::onCategorySelected
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Passwords List
            PasswordList(
                passwords = filteredPasswords,
                onDeletePassword = viewModel::showDeleteConfirmation
            )
        }

        // Delete Confirmation Dialog
        uiState.itemToDelete?.let { item ->
            DeleteConfirmationDialog(
                platformName = item.platform,
                onConfirm = viewModel::confirmDelete,
                onDismiss = viewModel::dismissDeleteConfirmation
            )
        }

        // Bottom blur effect
        BottomBlurEffect()

        // FAB
        AddPasswordFAB(
            onAddClick = {
                val newId = (filteredPasswords.maxOfOrNull { it.id } ?: 0) + 1
                viewModel.onAddPassword(
                    "New Platform $newId",
                    "user$newId",
                    "password$newId",
                    "example.com",
                    uiState.selectedCategoryId
                )
            }
        )
    }
}

@Composable
private fun PasswordTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 40.dp),
                placeholder = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(
                            text = stringResource(id = R.string.search_placeholder),
                            color = SubTitleTextColor,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TitleTextColor,
                    unfocusedTextColor = TitleTextColor,
                    focusedBorderColor = RectangleIconBorderColor,
                    unfocusedBorderColor = RectangleIconBorderColor,
                    focusedContainerColor = RectangleBackGroundColor,
                    unfocusedContainerColor = RectangleBackGroundColor,
                    cursorColor = ButtonColor
                ),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 16.sp,
                    textAlign = TextAlign.Right
                )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onToggleSearch() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(id = R.string.close_search_desc),
                    tint = TitleTextColor
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CFloatingIcon(
                    icon = Icons.Default.Search,
                    iconTint = SubTitleTextColor,
                    iconBackGroundColor = RectangleBackGroundColor,
                    borderColor = RectangleIconBorderColor,
                    onClick = onToggleSearch
                )

                CFloatingIcon(
                    icon = Icons.Default.Security,
                    iconTint = CircleIconColor,
                    iconBackGroundColor = CircleIconBackGroundColor,
                    borderColor = CircleIconBorderColor,
                    onClick = { /* Handle vault action */ }
                )
            }

            CText(
                text = stringResource(id = R.string.password_screen_title),
                color = TitleTextColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CategoryList(
    categories: List<com.example.core.model.CategoryChip>,
    selectedCategoryId: Int,
    onCategorySelected: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
    ) {
        items(categories, key = { it.id }) { category ->
            val isSelected = category.id == selectedCategoryId
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
                    .clickable { onCategorySelected(category.id) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CText(
                    text = category.name,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun PasswordList(
    passwords: List<PasswordItemModel>,
    onDeletePassword: (PasswordItemModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(passwords, key = { it.id }) { item ->
            PasswordCard(
                item = item,
                onEditClick = { /* Handle edit */ },
                onDeleteClick = { onDeletePassword(item) }
            )
        }
    }
}

@Composable
private fun BoxScope.BottomBlurEffect() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .align(Alignment.BottomCenter)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        BackgroundColor.copy(alpha = 0.9f),
                        BackgroundColor
                    )
                )
            )
    )
}

@Composable
private fun BoxScope.AddPasswordFAB(onAddClick: () -> Unit) {
    FloatingActionButton(
        onClick = onAddClick,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 24.dp, bottom = 40.dp)
            .size(65.dp),
        containerColor = ButtonColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(id = R.string.add_password_btn),
            tint = Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
fun PasswordCard(
    item: PasswordItemModel,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RectangleBackGroundColor)
            .border(
                width = 1.dp,
                color = RectangleIconBorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Delete Icon
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DeleteButtonBackgroundColor),
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = stringResource(id = R.string.delete_btn),
                tint = DeleteButtonColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Edit Icon
        IconButton(
            onClick = onEditClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CircleIconBackGroundColor)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(id = R.string.edit_btn),
                tint = ButtonColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            CText(
                text = item.platform,
                color = TitleTextColor,
                fontSize = 16.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            CText(
                text = item.userName,
                color = SubTitleTextColor,
                fontSize = 13.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Platform Icon
        var isImageError by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BackgroundColor)
                .border(
                    width = 1.dp,
                    color = RectangleIconBorderColor,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (item.website.isNotBlank() && !isImageError) {
                AsyncImage(
                    model = "https://www.google.com/s2/favicons?domain=${item.website}&sz=128",
                    contentDescription = item.platform,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    onError = { isImageError = true }
                )
            } else {
                CText(
                    text = item.letter,
                    color = TitleTextColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
