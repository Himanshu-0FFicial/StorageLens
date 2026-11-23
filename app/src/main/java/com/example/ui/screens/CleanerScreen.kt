package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CleanableItem
import com.example.data.model.CleanerGroup
import com.example.data.model.CleanerGroupType
import com.example.data.model.CleanupExecutionResult
import com.example.data.model.CleanupHistoryEntry
import com.example.ui.components.LimitedModeBanner
import com.example.ui.theme.MonoNumericStyle
import com.example.ui.theme.StorageLensTheme
import com.example.util.Formatters
import com.example.viewmodel.CleanerUiState

@Composable
fun CleanerScreen(
    uiState: CleanerUiState,
    onRescanClick: () -> Unit,
    onCancelScanClick: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleGroup: (CleanerGroupType) -> Unit,
    onToggleSelectAll: () -> Unit,
    onUpdateThreshold: (String) -> Boolean,
    onClearThresholdError: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissSummary: () -> Unit,
    onOpenSystemStorageSettings: () -> Unit,
    onOpenAppPermissionSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showThresholdDialog by remember { mutableStateOf(false) }
    val expandedGroups = remember {
        mutableStateMapOf(
            CleanerGroupType.APP_CACHE to true,
            CleanerGroupType.TEMP_FILES to true,
            CleanerGroupType.EMPTY_FOLDERS to false,
            CleanerGroupType.DOWNLOAD_APKS to true,
            CleanerGroupType.LARGE_FILES to true
        )
    }

    if (showThresholdDialog) {
        ThresholdDialog(
            currentThresholdMb = uiState.largeFileThresholdMb,
            errorText = uiState.thresholdInputError,
            onDismiss = {
                onClearThresholdError()
                showThresholdDialog = false
            },
            onSave = { raw ->
                if (onUpdateThreshold(raw)) {
                    showThresholdDialog = false
                }
            }
        )
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Confirm Cleanup") },
            text = {
                Text(
                    "Permanently delete ${uiState.totalSelectedCount} selected item(s) totaling ${
                        Formatters.formatBytes(uiState.totalSelectedBytes)
                    }? This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_cleaner_delete_button")
                ) {
                    Text("Delete (${Formatters.formatBytes(uiState.totalSelectedBytes)})")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteDialog) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    uiState.lastCleanupResult?.let { summary ->
        CleanupSummaryDialog(
            result = summary,
            onDismiss = onDismissSummary
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cleaner",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Junk, temp files, APKs & large files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (uiState.isScanning) {
                            FilledTonalButton(
                                onClick = onCancelScanClick,
                                modifier = Modifier.testTag("cleaner_cancel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_cancel_scan))
                            }
                        } else {
                            FilledTonalButton(
                                onClick = onRescanClick,
                                modifier = Modifier.testTag("cleaner_rescan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan")
                            }
                        }
                    }
                }

                if (!uiState.hasFullStorageAccess) {
                    item {
                        LimitedModeBanner(onOpenSettings = onOpenAppPermissionSettings)
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { showThresholdDialog = true },
                            label = {
                                Text("Large file threshold: ${uiState.largeFileThresholdMb} MB")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("threshold_chip")
                        )

                        if (uiState.totalItemsCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(onClick = onToggleSelectAll)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("cleaner_select_all")
                            ) {
                                Checkbox(
                                    checked = uiState.allSelected,
                                    onCheckedChange = { onToggleSelectAll() }
                                )
                                Text(
                                    text = "Select all",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                if (uiState.isScanning || uiState.isDeleting) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = {
                                        if (uiState.isDeleting) 0.5f else uiState.scanProgress.coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                )
                                Text(
                                    text = if (uiState.isDeleting) {
                                        "Cleaning selected files..."
                                    } else {
                                        uiState.scanStage.ifBlank { "Scanning..." }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (uiState.errorMessage != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uiState.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onRescanClick) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }
                }

                if (uiState.hasScannedOnce && !uiState.isScanning && uiState.totalItemsCount == 0) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "No junk or large files found \uD83C\uDF89",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Your scanned folders look clean at the ${uiState.largeFileThresholdMb} MB threshold.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(
                    items = uiState.groups,
                    key = { it.type.name }
                ) { group ->
                    val isExpanded = expandedGroups[group.type] ?: true
                    CleanerGroupCard(
                        group = group,
                        isExpanded = isExpanded,
                        onExpandToggle = {
                            expandedGroups[group.type] = !isExpanded
                        },
                        onToggleGroup = { onToggleGroup(group.type) },
                        onToggleItem = onToggleItem
                    )
                }

                item {
                    SystemStorageCacheCard(onOpenSystemStorageSettings = onOpenSystemStorageSettings)
                }

                if (uiState.cleanupHistory.isNotEmpty()) {
                    item {
                        CleanupHistoryCard(history = uiState.cleanupHistory)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            AnimatedVisibility(visible = uiState.totalSelectedCount > 0) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${uiState.totalSelectedCount} item(s) selected",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = Formatters.formatBytes(uiState.totalSelectedBytes),
                                style = MonoNumericStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = onRequestDelete,
                            enabled = !uiState.isDeleting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.testTag("cleaner_delete_button")
                        ) {
                            if (uiState.isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.CleaningServices,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clean Now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanerGroupCard(
    group: CleanerGroup,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onToggleGroup: () -> Unit,
    onToggleItem: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandToggle)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (group.items.isNotEmpty()) {
                    Checkbox(
                        checked = group.allSelected,
                        onCheckedChange = { onToggleGroup() },
                        modifier = Modifier.testTag("group_checkbox_${group.type.name.lowercase()}")
                    )
                } else {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconForGroup(group.type),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = group.type.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (group.items.isEmpty()) {
                            "Nothing found"
                        } else {
                            "${group.items.size} item(s) • ${Formatters.formatBytes(group.totalBytes)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onExpandToggle) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse group" else "Expand group"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded && group.items.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    group.items.forEach { item ->
                        CleanerItemRow(
                            item = item,
                            onToggle = { onToggleItem(item.path) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanerItemRow(
    item: CleanableItem,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isSelected,
            onCheckedChange = { onToggle() }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (item.isDirectory) "Empty" else Formatters.formatBytes(item.sizeBytes),
            style = MonoNumericStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SystemStorageCacheCard(
    onOpenSystemStorageSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Other Apps' Caches",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.system_cache_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onOpenSystemStorageSettings,
                modifier = Modifier.testTag("system_storage_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.open_system_storage))
            }
        }
    }
}

@Composable
private fun CleanupHistoryCard(history: List<CleanupHistoryEntry>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Recent Cleanups",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            history.take(5).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${entry.itemsDeletedCount} item(s) removed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Formatters.formatTimestamp(entry.timestampMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "+${Formatters.formatBytes(entry.freedBytes)}",
                        style = MonoNumericStyle,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun ThresholdDialog(
    currentThresholdMb: Long,
    errorText: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var input by remember { mutableStateOf(currentThresholdMb.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Large File Threshold") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Flag files larger than this size (in MB) during cleanup scans.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Size in MB (1 - 10240)") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { msg -> { Text(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("threshold_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(input) },
                modifier = Modifier.testTag("save_threshold_button")
            ) {
                Text("Save & Scan")
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
private fun CleanupSummaryDialog(
    result: CleanupExecutionResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = { Text("Cleanup Complete") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Freed ${Formatters.formatBytes(result.freedBytes)} across ${result.deletedCount} item(s).",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (result.failedNames.isNotEmpty()) {
                    Text(
                        text = "${result.failedNames.size} item(s) could not be deleted (protected or in use).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private fun iconForGroup(type: CleanerGroupType): ImageVector = when (type) {
    CleanerGroupType.APP_CACHE -> Icons.Rounded.Cached
    CleanerGroupType.TEMP_FILES -> Icons.Rounded.Description
    CleanerGroupType.EMPTY_FOLDERS -> Icons.Rounded.FolderOff
    CleanerGroupType.DOWNLOAD_APKS -> Icons.Rounded.Android
    CleanerGroupType.LARGE_FILES -> Icons.Rounded.InsertDriveFile
}

@Preview(showBackground = true)
@Composable
private fun CleanerScreenPreview() {
    StorageLensTheme {
        CleanerScreen(
            uiState = CleanerUiState(
                hasScannedOnce = true,
                hasFullStorageAccess = true,
                largeFileThresholdMb = 50L,
                groups = listOf(
                    CleanerGroup(
                        type = CleanerGroupType.APP_CACHE,
                        items = listOf(
                            CleanableItem(
                                path = "/data/user/0/com.example/cache/thumb.bin",
                                name = "thumb.bin",
                                sizeBytes = 4_200_000L,
                                lastModifiedMillis = 1763884000000L,
                                groupType = CleanerGroupType.APP_CACHE,
                                isSelected = true
                            )
                        )
                    ),
                    CleanerGroup(
                        type = CleanerGroupType.LARGE_FILES,
                        items = listOf(
                            CleanableItem(
                                path = "/storage/emulated/0/Movies/trip_4k.mp4",
                                name = "trip_4k.mp4",
                                sizeBytes = 482_000_000L,
                                lastModifiedMillis = 1763884000000L,
                                groupType = CleanerGroupType.LARGE_FILES,
                                isSelected = false
                            )
                        )
                    )
                ),
                cleanupHistory = listOf(
                    CleanupHistoryEntry(
                        id = "1",
                        timestampMillis = 1763880000000L,
                        freedBytes = 128_500_000L,
                        itemsDeletedCount = 12,
                        summary = "Freed 122.5 MB"
                    )
                )
            ),
            onRescanClick = {},
            onCancelScanClick = {},
            onToggleItem = {},
            onToggleGroup = {},
            onToggleSelectAll = {},
            onUpdateThreshold = { true },
            onClearThresholdError = {},
            onRequestDelete = {},
            onDismissDeleteDialog = {},
            onConfirmDelete = {},
            onDismissSummary = {},
            onOpenSystemStorageSettings = {},
            onOpenAppPermissionSettings = {}
        )
    }
}
