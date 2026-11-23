package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BreadcrumbSegment
import com.example.data.model.FileCategoryShortcut
import com.example.data.model.FileItem
import com.example.data.model.FileSortField
import com.example.data.model.SortDirection
import com.example.data.model.StorageCategory
import com.example.ui.components.LimitedModeBanner
import com.example.ui.theme.LocalStorageColors
import com.example.ui.theme.MonoNumericStyle
import com.example.ui.theme.StorageLensTheme
import com.example.util.Formatters
import com.example.viewmodel.FileManagerUiState

@Composable
fun FileManagerScreen(
    uiState: FileManagerUiState,
    onShortcutSelect: (FileCategoryShortcut) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onBreadcrumbClick: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortChange: (FileSortField, SortDirection) -> Unit,
    onRetryClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onShowNewFolderDialog: () -> Unit,
    onDismissNewFolderDialog: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit,
    onRequestRename: (FileItem) -> Unit,
    onDismissRenameDialog: () -> Unit,
    onConfirmRename: (String) -> Unit,
    onRequestDelete: (FileItem) -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
    onShowDetails: (FileItem) -> Unit,
    onDismissDetails: () -> Unit,
    onClearBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = uiState.canNavigateUp) {
        onNavigateUp()
    }

    if (uiState.showNewFolderDialog) {
        FolderNameDialog(
            title = "Create New Folder",
            initialName = "",
            confirmLabel = "Create",
            errorText = uiState.dialogValidationError,
            onDismiss = onDismissNewFolderDialog,
            onConfirm = onCreateFolder
        )
    }

    uiState.pendingRenameItem?.let { item ->
        FolderNameDialog(
            title = "Rename \"${item.name}\"",
            initialName = item.name,
            confirmLabel = stringResource(R.string.action_rename),
            errorText = uiState.dialogValidationError,
            onDismiss = onDismissRenameDialog,
            onConfirm = onConfirmRename
        )
    }

    uiState.pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete ${if (item.isDirectory) "Folder" else "File"}?") },
            text = {
                Text(
                    if (item.isDirectory) {
                        "Permanently delete folder \"${item.name}\" and all files inside it?"
                    } else {
                        "Permanently delete \"${item.name}\" (${Formatters.formatBytes(item.sizeBytes)})?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_file_delete_button")
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteDialog) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    uiState.detailsItem?.let { item ->
        FileDetailsDialog(item = item, onDismiss = onDismissDetails)
    }

    var showSortMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Header & Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Files",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${uiState.items.size} item(s) • Sorted by ${uiState.sortField.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRetryClick,
                        modifier = Modifier.testTag("refresh_files_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh folder"
                        )
                    }

                    if (uiState.activeShortcut == FileCategoryShortcut.ALL) {
                        IconButton(
                            onClick = onShowNewFolderDialog,
                            modifier = Modifier.testTag("new_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CreateNewFolder,
                                contentDescription = stringResource(R.string.action_new_folder)
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("sort_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = "Sort files"
                            )
                        }
                        SortDropdownMenu(
                            expanded = showSortMenu,
                            currentField = uiState.sortField,
                            currentDirection = uiState.sortDirection,
                            onDismiss = { showSortMenu = false },
                            onSelect = { field, direction ->
                                showSortMenu = false
                                onSortChange(field, direction)
                            }
                        )
                    }
                }
            }

            // Search Input
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search file or folder name...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("files_search_input")
            )

            // Category Shortcuts
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FileCategoryShortcut.entries) { shortcut ->
                    FilterChip(
                        selected = uiState.activeShortcut == shortcut,
                        onClick = { onShortcutSelect(shortcut) },
                        label = { Text(shortcut.label) },
                        modifier = Modifier.testTag("shortcut_chip_${shortcut.name.lowercase()}")
                    )
                }
            }

            // Breadcrumb + Back button bar
            BreadcrumbBar(
                breadcrumbs = uiState.breadcrumbs,
                canNavigateUp = uiState.canNavigateUp,
                onNavigateUp = onNavigateUp,
                onBreadcrumbClick = onBreadcrumbClick
            )

            uiState.actionBannerMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearBanner) {
                            Text("OK")
                        }
                    }
                }
            }

            if (!uiState.hasFullStorageAccess) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    LimitedModeBanner(onOpenSettings = onOpenSettingsClick)
                }
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading files...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                uiState.errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = uiState.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = onRetryClick) {
                                        Text(stringResource(R.string.action_retry))
                                    }
                                    if (uiState.isPermissionError) {
                                        Button(onClick = onOpenSettingsClick) {
                                            Text(stringResource(R.string.action_open_settings))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (uiState.searchQuery.isNotBlank()) {
                                    "No files match \"${uiState.searchQuery}\""
                                } else {
                                    "This folder is empty"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Try another category shortcut or create a new folder.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = uiState.items,
                            key = { it.path }
                        ) { item ->
                            FileItemRow(
                                item = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        onOpenFolder(item.path)
                                    } else {
                                        onOpenFile(item)
                                    }
                                },
                                onOpen = { onOpenFile(item) },
                                onRename = { onRequestRename(item) },
                                onShare = { onShareFile(item) },
                                onDetails = { onShowDetails(item) },
                                onDelete = { onRequestDelete(item) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(
    breadcrumbs: List<BreadcrumbSegment>,
    canNavigateUp: Boolean,
    onNavigateUp: () -> Unit,
    onBreadcrumbClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onNavigateUp,
            enabled = canNavigateUp,
            modifier = Modifier.testTag("files_up_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Navigate back"
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            breadcrumbs.forEachIndexed { index, segment ->
                val isLast = index == breadcrumbs.lastIndex
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isLast) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = !isLast) { onBreadcrumbClick(segment.path) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
                if (!isLast) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FileItemRow(
    item: FileItem,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val storageColors = LocalStorageColors.current
    val (icon, tint) = if (item.isDirectory) {
        Icons.Rounded.Folder to MaterialTheme.colorScheme.primary
    } else {
        iconForFileCategory(item.category) to storageColors.colorFor(item.category)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("file_row_${item.name}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = tint.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.isDirectory) {
                            "${item.childCount} item(s)"
                        } else {
                            Formatters.formatBytes(item.sizeBytes)
                        },
                        style = MonoNumericStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• ${Formatters.formatShortDate(item.lastModifiedMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "File actions for ${item.name}"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (!item.isDirectory) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open)) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onOpen()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_details)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Info, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onDetails()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortDropdownMenu(
    expanded: Boolean,
    currentField: FileSortField,
    currentDirection: SortDirection,
    onDismiss: () -> Unit,
    onSelect: (FileSortField, SortDirection) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        FileSortField.entries.forEach { field ->
            DropdownMenuItem(
                text = { Text("Sort by ${field.label}") },
                trailingIcon = {
                    if (currentField == field) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                    }
                },
                onClick = { onSelect(field, currentDirection) }
            )
        }
        HorizontalDivider()
        SortDirection.entries.forEach { dir ->
            DropdownMenuItem(
                text = { Text(dir.label) },
                trailingIcon = {
                    if (currentDirection == dir) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                    }
                },
                onClick = { onSelect(currentField, dir) }
            )
        }
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    errorText: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true,
                isError = errorText != null,
                supportingText = errorText?.let { msg -> { Text(msg) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("folder_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                modifier = Modifier.testTag("folder_name_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun FileDetailsDialog(
    item: FileItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("Name", item.name)
                DetailLine("Type", if (item.isDirectory) "Folder" else item.mimeType)
                if (!item.isDirectory) {
                    DetailLine("Size", "${Formatters.formatBytes(item.sizeBytes)} (${item.sizeBytes} B)")
                    DetailLine("Category", item.category.displayName)
                } else {
                    DetailLine("Contains", "${item.childCount} item(s)")
                }
                DetailLine("Modified", Formatters.formatTimestamp(item.lastModifiedMillis))
                DetailLine("Path", item.path)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun iconForFileCategory(category: StorageCategory): ImageVector = when (category) {
    StorageCategory.IMAGES -> Icons.Rounded.Image
    StorageCategory.VIDEOS -> Icons.Rounded.Videocam
    StorageCategory.AUDIO -> Icons.Rounded.AudioFile
    StorageCategory.DOCUMENTS -> Icons.Rounded.Description
    StorageCategory.OTHER -> Icons.Rounded.InsertDriveFile
}

@Preview(showBackground = true)
@Composable
private fun FileManagerScreenPreview() {
    StorageLensTheme {
        FileManagerScreen(
            uiState = FileManagerUiState(
                isLoading = false,
                hasFullStorageAccess = true,
                currentPath = "/storage/emulated/0/Download",
                rootPath = "/storage/emulated/0",
                breadcrumbs = listOf(
                    BreadcrumbSegment("Internal Storage", "/storage/emulated/0"),
                    BreadcrumbSegment("Download", "/storage/emulated/0/Download")
                ),
                items = listOf(
                    FileItem(
                        name = "Invoices",
                        path = "/storage/emulated/0/Download/Invoices",
                        isDirectory = true,
                        sizeBytes = 0L,
                        lastModifiedMillis = 1763884000000L,
                        extension = "",
                        mimeType = "inode/directory",
                        category = StorageCategory.OTHER,
                        childCount = 8
                    ),
                    FileItem(
                        name = "Q4_Report.pdf",
                        path = "/storage/emulated/0/Download/Q4_Report.pdf",
                        isDirectory = false,
                        sizeBytes = 3_480_000L,
                        lastModifiedMillis = 1763884000000L,
                        extension = "pdf",
                        mimeType = "application/pdf",
                        category = StorageCategory.DOCUMENTS
                    ),
                    FileItem(
                        name = "sunset_beach.jpg",
                        path = "/storage/emulated/0/Download/sunset_beach.jpg",
                        isDirectory = false,
                        sizeBytes = 6_120_000L,
                        lastModifiedMillis = 1763884000000L,
                        extension = "jpg",
                        mimeType = "image/jpeg",
                        category = StorageCategory.IMAGES
                    )
                )
            ),
            onShortcutSelect = {},
            onOpenFolder = {},
            onNavigateUp = {},
            onBreadcrumbClick = {},
            onSearchQueryChange = {},
            onSortChange = { _, _ -> },
            onRetryClick = {},
            onOpenSettingsClick = {},
            onShowNewFolderDialog = {},
            onDismissNewFolderDialog = {},
            onCreateFolder = {},
            onOpenFile = {},
            onShareFile = {},
            onRequestRename = {},
            onDismissRenameDialog = {},
            onConfirmRename = {},
            onRequestDelete = {},
            onDismissDeleteDialog = {},
            onConfirmDelete = {},
            onShowDetails = {},
            onDismissDetails = {},
            onClearBanner = {}
        )
    }
}
