package com.example.mypasswords.screens.password

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.core.model.*
import com.example.mypasswords.R
import com.example.mypasswords.ui.theme.*
import com.example.mypasswords.util.toMessage
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureSheet(
    title: String,
    state: PasswordUiState,
    onDismiss: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RectangleBackGroundColor,
        sheetMaxWidth =
            if (com.example.compose_ui.components.isVaultLandscape()) 720.dp
            else BottomSheetDefaults.SheetMaxWidth,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 640.dp),
                contentPadding =
                    PaddingValues(
                        if (com.example.compose_ui.components.isVaultLandscape()) 12.dp else 24.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TitleTextColor,
                        textAlign = TextAlign.Right,
                    )
                    if (state.featureBusy)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    state.featureError?.let {
                        Text(
                            it.toMessage(context),
                            color = DeleteButtonColor,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                content()
            }
        }
    }
}

@Composable
fun VaultTools(state: PasswordUiState, vm: PasswordViewModel) {
    var sortMenu by remember { mutableStateOf(false) }
    var tagsMenu by remember { mutableStateOf(false) }
    val tools: List<@Composable () -> Unit> =
        listOf(
            {
                AssistChip(
                    onClick = vm::showHealthReport,
                    label = { Text(stringResource(R.string.health_action)) },
                )
            },
            {
                AssistChip(
                    onClick = vm::showTrash,
                    label = { Text(stringResource(R.string.trash_title)) },
                )
            },
            {
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = vm::toggleFavoritesOnly,
                    label = { Text(stringResource(R.string.favorites_only)) },
                )
            },
            {
                Box {
                    AssistChip(
                        onClick = { sortMenu = true },
                        label = { Text(stringResource(R.string.sort_action)) },
                    )
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf(
                                PasswordSort.ORIGINAL to R.string.sort_original,
                                PasswordSort.NAME to R.string.sort_name,
                                PasswordSort.NEWEST to R.string.sort_newest,
                                PasswordSort.OLDEST to R.string.sort_oldest,
                            )
                            .forEach { (sort, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = {
                                        vm.selectSort(sort)
                                        sortMenu = false
                                    },
                                )
                            }
                    }
                }
            },
            {
                Box {
                    FilterChip(
                        selected = state.selectedTag != null,
                        onClick = { tagsMenu = true },
                        label = { Text(state.selectedTag ?: stringResource(R.string.tags_label)) },
                    )
                    DropdownMenu(expanded = tagsMenu, onDismissRequest = { tagsMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tags_all)) },
                            onClick = {
                                vm.selectTag(null)
                                tagsMenu = false
                            },
                        )
                        state.availableTags.forEach { tag ->
                            DropdownMenuItem(
                                text = { Text(tag) },
                                onClick = {
                                    vm.selectTag(tag)
                                    tagsMenu = false
                                },
                            )
                        }
                    }
                }
            },
        )
    if (com.example.compose_ui.components.isVaultLandscape()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
        ) {
            tools.forEach { it() }
        }
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            reverseLayout = true,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tools.forEach { tool -> item { tool() } }
        }
    }
}

@Composable
fun VaultFeaturePanels(state: PasswordUiState, vm: PasswordViewModel) {
    var deleteCandidate by remember { mutableStateOf<PasswordItemModel?>(null) }
    var historyCandidate by remember { mutableStateOf<PasswordHistoryItem?>(null) }
    val dismiss = {
        deleteCandidate = null
        historyCandidate = null
        vm.dismissFeatures()
    }
    if (state.showHealth)
        FeatureSheet(stringResource(R.string.health_title), state, dismiss) {
            item { Text(stringResource(R.string.health_explanation), color = SubTitleTextColor) }
            state.healthReport?.let { report ->
                item {
                    Text(
                        stringResource(
                            R.string.health_summary,
                            report.total,
                            report.weakCount,
                            report.reusedCount,
                            report.oldCount,
                        ),
                        color = CircleIconColor,
                    )
                }
                if (report.unknownAgeCount > 0)
                    item {
                        Text(
                            stringResource(R.string.health_unknown_age, report.unknownAgeCount),
                            color = SubTitleTextColor,
                        )
                    }
                val flagged = report.items.filter { it.weakReason != null || it.reused || it.old }
                if (flagged.isEmpty())
                    item { Text(stringResource(R.string.health_no_flags), color = TitleTextColor) }
                items(flagged, key = { it.id }) { item ->
                    Surface(color = BackgroundColor, shape = MaterialTheme.shapes.medium) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                item.platform,
                                style = MaterialTheme.typography.titleMedium,
                                color = TitleTextColor,
                            )
                            if (item.weakReason != null)
                                Text(
                                    stringResource(R.string.health_weak),
                                    color = DeleteButtonColor,
                                )
                            if (item.reused)
                                Text(
                                    stringResource(R.string.health_reused),
                                    color = DeleteButtonColor,
                                )
                            if (item.old)
                                Text(stringResource(R.string.health_old), color = SubTitleTextColor)
                        }
                    }
                }
            }
            if (!state.featureBusy && state.healthReport == null)
                item {
                    TextButton(onClick = vm::showHealthReport) {
                        Text(stringResource(R.string.retry_loading))
                    }
                }
        }
    if (state.showTrash)
        FeatureSheet(stringResource(R.string.trash_title), state, dismiss) {
            item { Text(stringResource(R.string.trash_explanation), color = SubTitleTextColor) }
            if (!state.featureBusy && state.featureError == null && state.trashItems.isEmpty())
                item { Text(stringResource(R.string.trash_empty), color = TitleTextColor) }
            items(state.trashItems, key = { it.id }) { item ->
                Surface(color = BackgroundColor, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            item.platform,
                            style = MaterialTheme.typography.titleMedium,
                            color = TitleTextColor,
                        )
                        Text(item.maskedUserName, color = SubTitleTextColor)
                        Row {
                            TextButton(
                                enabled = !state.featureBusy,
                                onClick = { vm.restoreTrash(item.id) },
                            ) {
                                Text(stringResource(R.string.trash_restore))
                            }
                            TextButton(
                                enabled = !state.featureBusy,
                                onClick = { deleteCandidate = item },
                            ) {
                                Text(
                                    stringResource(R.string.trash_delete_permanently),
                                    color = DeleteButtonColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    if (state.historyCredentialId != null)
        FeatureSheet(stringResource(R.string.history_title), state, dismiss) {
            item {
                Text(
                    state.historyPlatform,
                    style = MaterialTheme.typography.titleMedium,
                    color = TitleTextColor,
                )
            }
            item { Text(stringResource(R.string.history_explanation), color = SubTitleTextColor) }
            if (!state.featureBusy && state.featureError == null && state.historyItems.isEmpty())
                item { Text(stringResource(R.string.history_empty), color = TitleTextColor) }
            items(state.historyItems, key = { it.id }) { entry ->
                Surface(color = BackgroundColor, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(entry.recordedAt)),
                            color = TitleTextColor,
                        )
                        TextButton(
                            enabled = !state.featureBusy,
                            onClick = { historyCandidate = entry },
                        ) {
                            Text(stringResource(R.string.history_restore))
                        }
                    }
                }
            }
        }
    state.tagsItem?.let { item ->
        TagsDialog(item, state.featureBusy, state.featureError, vm::saveTags, vm::dismissFeatures)
    }
    if (deleteCandidate != null && state.showTrash)
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.trash_delete_permanently)) },
            text = { Text(stringResource(R.string.trash_delete_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate?.let { vm.permanentlyDelete(it.id) }
                        deleteCandidate = null
                    }
                ) {
                    Text(
                        stringResource(R.string.trash_delete_permanently),
                        color = DeleteButtonColor,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            },
        )
    if (historyCandidate != null && state.historyCredentialId != null)
        AlertDialog(
            onDismissRequest = { historyCandidate = null },
            title = { Text(stringResource(R.string.history_restore)) },
            text = { Text(stringResource(R.string.history_restore_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        historyCandidate?.let { vm.restoreHistory(it.id) }
                        historyCandidate = null
                    }
                ) {
                    Text(stringResource(R.string.history_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { historyCandidate = null }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            },
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagsDialog(
    item: FullCredentialModel,
    busy: Boolean,
    error: VaultError?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember(item.id) { mutableStateOf(item.tags.joinToString(", ")) }
    val valid = remember(input) { runCatching { TagRules.parse(input) }.isSuccess }
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = RectangleBackGroundColor,
        sheetMaxWidth =
            if (com.example.compose_ui.components.isVaultLandscape()) 720.dp
            else BottomSheetDefaults.SheetMaxWidth,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.tags_edit),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TitleTextColor,
                )
                Text(item.platform, color = SubTitleTextColor)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !busy,
                    isError = !valid,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.tags_label)) },
                )
                Text(stringResource(R.string.tags_help), color = SubTitleTextColor)
                error?.let { Text(it.toMessage(context), color = DeleteButtonColor) }
                Button(
                    enabled = valid && !busy,
                    onClick = { onSave(input) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.save_now_btn))
                }
                TextButton(
                    enabled = !busy,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        }
    }
}
