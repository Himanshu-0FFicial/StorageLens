package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CategoryBreakdown
import com.example.data.model.ChartViewMode
import com.example.data.model.StorageCategory
import com.example.data.model.StorageScanResult
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.CategoryTreemapChart
import com.example.ui.components.LimitedModeBanner
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.components.StorageRingGauge
import com.example.ui.theme.LocalStorageColors
import com.example.ui.theme.MonoNumericStyle
import com.example.ui.theme.StorageLensTheme
import com.example.util.Formatters
import com.example.viewmodel.DashboardUiState

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onRescanClick: () -> Unit,
    onCancelScanClick: () -> Unit,
    onChartModeChange: (ChartViewMode) -> Unit,
    onCategoryClick: (StorageCategory) -> Unit,
    onGrantPermissionClick: () -> Unit,
    onDismissRationale: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.showPermissionRationale) {
        PermissionRationaleDialog(
            onGrantClick = onGrantPermissionClick,
            onContinueLimited = onDismissRationale
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DashboardHeader(hasFullAccess = uiState.hasFullStorageAccess)
            }

            if (!uiState.hasFullStorageAccess) {
                item {
                    LimitedModeBanner(onOpenSettings = onOpenSettingsClick)
                }
            }

            if (uiState.errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
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

            item {
                StorageOverviewCard(
                    scanResult = uiState.scanResult,
                    lastScanTimeMillis = uiState.lastScanTimeMillis,
                    isScanning = uiState.isScanning,
                    scanProgress = uiState.scanProgress,
                    scanningLocation = uiState.scanningLocation,
                    onRescanClick = onRescanClick,
                    onCancelScanClick = onCancelScanClick
                )
            }

            item {
                CategoryBreakdownCard(
                    categories = uiState.scanResult.categories,
                    chartMode = uiState.chartMode,
                    onChartModeChange = onChartModeChange,
                    onCategoryClick = onCategoryClick
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DashboardHeader(hasFullAccess: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "StorageLens",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Internal storage telemetry",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            color = if (hasFullAccess) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (hasFullAccess) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = if (hasFullAccess) "Full Access" else "Limited",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasFullAccess) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(
    scanResult: StorageScanResult,
    lastScanTimeMillis: Long,
    isScanning: Boolean,
    scanProgress: Float,
    scanningLocation: String,
    onRescanClick: () -> Unit,
    onCancelScanClick: () -> Unit
) {
    val semanticColors = LocalStorageColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            StorageRingGauge(
                usedBytes = scanResult.usedBytes,
                totalBytes = scanResult.totalBytes,
                freeBytes = scanResult.freeBytes
            )

            Text(
                text = "${Formatters.formatBytes(scanResult.usedBytes)} used of ${Formatters.formatBytes(scanResult.totalBytes)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricPill(
                    label = "Used",
                    value = Formatters.formatBytes(scanResult.usedBytes),
                    dotColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    label = "Free",
                    value = Formatters.formatBytes(scanResult.freeBytes),
                    dotColor = semanticColors.freeSpace,
                    modifier = Modifier.weight(1f)
                )
                MetricPill(
                    label = "Total",
                    value = Formatters.formatBytes(scanResult.totalBytes),
                    dotColor = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = isScanning) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { scanProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = if (scanningLocation.isNotBlank()) {
                            "Scanning: $scanningLocation"
                        } else {
                            "Scanning storage..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Last scanned: ${Formatters.formatTimestamp(lastScanTimeMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isScanning) {
                    FilledTonalButton(
                        onClick = onCancelScanClick,
                        modifier = Modifier.testTag("cancel_scan_button")
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
                    Button(
                        onClick = onRescanClick,
                        modifier = Modifier.testTag("rescan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_rescan))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    dotColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MonoNumericStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    categories: List<CategoryBreakdown>,
    chartMode: ChartViewMode,
    onChartModeChange: (ChartViewMode) -> Unit,
    onCategoryClick: (StorageCategory) -> Unit
) {
    val totalMeasured = categories.sumOf { it.bytes }
    val storageColors = LocalStorageColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Storage by Category",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap any segment to inspect files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = chartMode == ChartViewMode.DONUT,
                        onClick = { onChartModeChange(ChartViewMode.DONUT) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("chart_mode_donut"),
                        icon = {}
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DonutLarge,
                            contentDescription = "Donut chart",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    SegmentedButton(
                        selected = chartMode == ChartViewMode.TREEMAP,
                        onClick = { onChartModeChange(ChartViewMode.TREEMAP) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("chart_mode_treemap"),
                        icon = {}
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = "Treemap chart",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (chartMode == ChartViewMode.DONUT) {
                CategoryDonutChart(
                    categories = categories,
                    onCategoryTap = onCategoryClick
                )
            } else {
                CategoryTreemapChart(
                    categories = categories,
                    onCategoryTap = onCategoryClick
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val ordered = categories.sortedByDescending { it.bytes }
                ordered.forEach { breakdown ->
                    val color = storageColors.colorFor(breakdown.category)
                    CategoryLegendRow(
                        breakdown = breakdown,
                        totalBytes = totalMeasured,
                        dotColor = color,
                        icon = iconForCategory(breakdown.category),
                        onClick = { onCategoryClick(breakdown.category) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryLegendRow(
    breakdown: CategoryBreakdown,
    totalBytes: Long,
    dotColor: androidx.compose.ui.graphics.Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag("category_row_${breakdown.category.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = dotColor.copy(alpha = 0.16f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = dotColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = breakdown.category.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${breakdown.fileCount} files • ${Formatters.formatPercent(breakdown.bytes, totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = Formatters.formatBytes(breakdown.bytes),
            style = MonoNumericStyle,
            color = MaterialTheme.colorScheme.onSurface
        )

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "Open ${breakdown.category.displayName}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun iconForCategory(category: StorageCategory): ImageVector = when (category) {
    StorageCategory.IMAGES -> Icons.Rounded.Image
    StorageCategory.VIDEOS -> Icons.Rounded.Videocam
    StorageCategory.AUDIO -> Icons.Rounded.AudioFile
    StorageCategory.DOCUMENTS -> Icons.Rounded.Description
    StorageCategory.OTHER -> Icons.Rounded.FolderZip
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    StorageLensTheme(darkTheme = false) {
        DashboardScreen(
            uiState = DashboardUiState(
                isInitialLoading = false,
                hasFullStorageAccess = true,
                lastScanTimeMillis = 1763884000000L,
                scanResult = StorageScanResult(
                    totalBytes = 128L * 1024 * 1024 * 1024,
                    usedBytes = 58_400_000_000L,
                    freeBytes = 79_038_953_472L,
                    categories = listOf(
                        CategoryBreakdown(StorageCategory.VIDEOS, 24_200_000_000L, 148),
                        CategoryBreakdown(StorageCategory.IMAGES, 14_800_000_000L, 2840),
                        CategoryBreakdown(StorageCategory.DOCUMENTS, 4_200_000_000L, 412),
                        CategoryBreakdown(StorageCategory.AUDIO, 2_900_000_000L, 320),
                        CategoryBreakdown(StorageCategory.OTHER, 12_300_000_000L, 910)
                    )
                )
            ),
            onRescanClick = {},
            onCancelScanClick = {},
            onChartModeChange = {},
            onCategoryClick = {},
            onGrantPermissionClick = {},
            onDismissRationale = {},
            onOpenSettingsClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardTreemapDarkPreview() {
    StorageLensTheme(darkTheme = true) {
        DashboardScreen(
            uiState = DashboardUiState(
                isInitialLoading = false,
                hasFullStorageAccess = false,
                chartMode = ChartViewMode.TREEMAP,
                scanResult = StorageScanResult(
                    totalBytes = 128L * 1024 * 1024 * 1024,
                    usedBytes = 58_400_000_000L,
                    freeBytes = 79_038_953_472L,
                    categories = listOf(
                        CategoryBreakdown(StorageCategory.VIDEOS, 24_200_000_000L, 148),
                        CategoryBreakdown(StorageCategory.IMAGES, 14_800_000_000L, 2840),
                        CategoryBreakdown(StorageCategory.DOCUMENTS, 4_200_000_000L, 412),
                        CategoryBreakdown(StorageCategory.AUDIO, 2_900_000_000L, 320),
                        CategoryBreakdown(StorageCategory.OTHER, 12_300_000_000L, 910)
                    )
                )
            ),
            onRescanClick = {},
            onCancelScanClick = {},
            onChartModeChange = {},
            onCategoryClick = {},
            onGrantPermissionClick = {},
            onDismissRationale = {},
            onOpenSettingsClick = {}
        )
    }
}
