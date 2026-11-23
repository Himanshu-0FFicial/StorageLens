package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class StorageCategory(val displayName: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    OTHER("Other");

    fun toShortcut(): FileCategoryShortcut = when (this) {
        IMAGES -> FileCategoryShortcut.IMAGES
        VIDEOS -> FileCategoryShortcut.VIDEOS
        AUDIO -> FileCategoryShortcut.AUDIO
        DOCUMENTS -> FileCategoryShortcut.DOCUMENTS
        OTHER -> FileCategoryShortcut.ALL
    }
}

@Serializable
data class CategoryBreakdown(
    val category: StorageCategory,
    val bytes: Long,
    val fileCount: Int
)

@Serializable
data class StorageScanResult(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val categories: List<CategoryBreakdown> = emptyList(),
    val scannedAtMillis: Long = 0L,
    val isLimitedMode: Boolean = false
)

@Serializable
data class CleanupHistoryEntry(
    val id: String,
    val timestampMillis: Long,
    val freedBytes: Long,
    val itemsDeletedCount: Int,
    val summary: String
)

enum class ChartViewMode {
    DONUT,
    TREEMAP
}

enum class CleanerGroupType(
    val title: String,
    val description: String
) {
    APP_CACHE("App Cache", "Temporary cache created by StorageLens"),
    TEMP_FILES("Temp & Log Files", "Leftover .tmp and .log files"),
    EMPTY_FOLDERS("Empty Folders", "Directories containing no files"),
    DOWNLOAD_APKS("Installer APKs", "Leftover .apk packages in Downloads"),
    LARGE_FILES("Large Files", "Files exceeding your size threshold")
}

data class CleanableItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val groupType: CleanerGroupType,
    val isDirectory: Boolean = false,
    val isSelected: Boolean = false
)

data class CleanerGroup(
    val type: CleanerGroupType,
    val items: List<CleanableItem>
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
    val selectedBytes: Long get() = items.filter { it.isSelected }.sumOf { it.sizeBytes }
    val selectedCount: Int get() = items.count { it.isSelected }
    val allSelected: Boolean get() = items.isNotEmpty() && items.all { it.isSelected }
}

data class CleanupExecutionResult(
    val freedBytes: Long,
    val deletedCount: Int,
    val failedNames: List<String>
)

@Serializable
enum class FileSortField(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Date")
}

@Serializable
enum class SortDirection(val label: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

enum class FileCategoryShortcut(val label: String) {
    ALL("All Files"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    DOWNLOADS("Downloads")
}

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val extension: String,
    val mimeType: String,
    val category: StorageCategory,
    val childCount: Int = 0
)

data class BreadcrumbSegment(
    val label: String,
    val path: String
)
