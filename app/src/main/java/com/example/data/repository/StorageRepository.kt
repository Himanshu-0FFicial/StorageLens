package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.example.data.model.CategoryBreakdown
import com.example.data.model.CleanableItem
import com.example.data.model.CleanerGroup
import com.example.data.model.CleanerGroupType
import com.example.data.model.CleanupExecutionResult
import com.example.data.model.FileCategoryShortcut
import com.example.data.model.FileItem
import com.example.data.model.FileSortField
import com.example.data.model.SortDirection
import com.example.data.model.StorageCategory
import com.example.data.model.StorageScanResult
import com.example.util.Formatters
import com.example.util.NameValidationResult
import com.example.util.PermissionHelper
import com.example.util.SafeFileValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

sealed class FileOperationResult<out T> {
    data class Success<T>(val data: T) : FileOperationResult<T>()
    data class Error(val message: String, val isPermissionError: Boolean = false) :
        FileOperationResult<Nothing>()
}

data class DeviceStorageStats(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long
)

class StorageRepository(private val context: Context) {

    private val protectedStandardFolderNames = setOf(
        "android", "dcim", "pictures", "music", "movies", "documents",
        "download", "downloads", "alarms", "notifications", "podcasts", "ringtones"
    )

    fun getRootDirectoryPath(): String {
        return if (PermissionHelper.hasFullStorageAccess(context)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath
        }
    }

    fun getExternalRootPath(): String = Environment.getExternalStorageDirectory().absolutePath

    fun getDownloadsPath(): String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

    fun getDeviceStorageStats(): DeviceStorageStats {
        return runCatching {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = (total - free).coerceAtLeast(0L)
            DeviceStorageStats(totalBytes = total, usedBytes = used, freeBytes = free)
        }.getOrElse {
            val stat = StatFs(context.filesDir.path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = (total - free).coerceAtLeast(0L)
            DeviceStorageStats(totalBytes = total, usedBytes = used, freeBytes = free)
        }
    }

    suspend fun scanStorage(
        onProgress: suspend (progress: Float, currentLocation: String) -> Unit
    ): StorageScanResult = withContext(Dispatchers.IO) {
        val hasAccess = PermissionHelper.hasFullStorageAccess(context)
        val stats = getDeviceStorageStats()

        val bytesByCategory = mutableMapOf<StorageCategory, Long>()
        val countsByCategory = mutableMapOf<StorageCategory, Int>()
        StorageCategory.entries.forEach {
            bytesByCategory[it] = 0L
            countsByCategory[it] = 0
        }

        val scanRoots: List<File> = if (hasAccess) {
            listOf(Environment.getExternalStorageDirectory())
        } else {
            listOfNotNull(
                context.getExternalFilesDir(null),
                context.externalCacheDir,
                context.filesDir,
                context.cacheDir
            ).distinctBy { it.absolutePath }
        }

        val stack = ArrayDeque<File>()
        scanRoots.filter { it.exists() && it.canRead() }.forEach { stack.addLast(it) }

        var processedDirs = 0
        var lastProgressUpdateTime = System.currentTimeMillis()

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = stack.removeLast()
            if (SafeFileValidator.isRestrictedAndroidSystemDir(dir)) continue

            processedDirs++
            val now = System.currentTimeMillis()
            if (now - lastProgressUpdateTime >= 80L) {
                lastProgressUpdateTime = now
                // Smooth asymptotic progress while discovering unknown folder depth
                val estimatedProgress = (1f - 1f / (1f + processedDirs / 35f)).coerceIn(0.05f, 0.94f)
                onProgress(estimatedProgress, dir.name.ifBlank { "Internal Storage" })
            }

            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (child.isDirectory) {
                    if (!isRestrictedAndroidDataSubdir(child)) {
                        stack.addLast(child)
                    }
                } else if (child.isFile) {
                    val ext = child.extension.lowercase(Locale.ROOT)
                    val category = Formatters.categorizeExtension(ext)
                    val size = child.length().coerceAtLeast(0L)
                    bytesByCategory[category] = (bytesByCategory[category] ?: 0L) + size
                    countsByCategory[category] = (countsByCategory[category] ?: 0) + 1
                }
            }
        }

        onProgress(1f, "Finalizing results")

        val categories = StorageCategory.entries.map { category ->
            CategoryBreakdown(
                category = category,
                bytes = bytesByCategory[category] ?: 0L,
                fileCount = countsByCategory[category] ?: 0
            )
        }

        StorageScanResult(
            totalBytes = stats.totalBytes,
            usedBytes = stats.usedBytes,
            freeBytes = stats.freeBytes,
            categories = categories,
            scannedAtMillis = System.currentTimeMillis(),
            isLimitedMode = !hasAccess
        )
    }

    suspend fun scanCleanerItems(
        largeFileThresholdMb: Long,
        onProgress: suspend (progress: Float, stage: String) -> Unit
    ): List<CleanerGroup> = withContext(Dispatchers.IO) {
        val hasAccess = PermissionHelper.hasFullStorageAccess(context)
        val thresholdBytes = largeFileThresholdMb.coerceAtLeast(1L) * 1024L * 1024L

        val appCacheItems = mutableListOf<CleanableItem>()
        val tempItems = mutableListOf<CleanableItem>()
        val emptyFolderItems = mutableListOf<CleanableItem>()
        val apkItems = mutableListOf<CleanableItem>()
        val largeFileItems = mutableListOf<CleanableItem>()

        onProgress(0.1f, "Checking StorageLens app cache")
        val cacheRoots = listOfNotNull(
            context.cacheDir,
            context.codeCacheDir,
            context.externalCacheDir
        ).distinctBy { it.absolutePath }

        for (cacheRoot in cacheRoots) {
            currentCoroutineContext().ensureActive()
            val cacheFiles = collectNestedFiles(cacheRoot)
            for (file in cacheFiles) {
                currentCoroutineContext().ensureActive()
                appCacheItems.add(
                    CleanableItem(
                        path = file.absolutePath,
                        name = file.name,
                        sizeBytes = file.length().coerceAtLeast(0L),
                        lastModifiedMillis = file.lastModified(),
                        groupType = CleanerGroupType.APP_CACHE,
                        isSelected = true
                    )
                )
            }
        }

        val scanRoots = if (hasAccess) {
            listOf(Environment.getExternalStorageDirectory())
        } else {
            listOfNotNull(context.getExternalFilesDir(null)).filter { it.exists() && it.canRead() }
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val downloadsCanonical = runCatching { downloadsDir.canonicalPath }.getOrNull()

        val stack = ArrayDeque<File>()
        scanRoots.filter { it.exists() && it.canRead() }.forEach { stack.addLast(it) }

        var dirsScanned = 0
        var lastUpdate = System.currentTimeMillis()
        val externalRootCanonical = runCatching {
            Environment.getExternalStorageDirectory().canonicalPath
        }.getOrNull()

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = stack.removeLast()
            if (SafeFileValidator.isRestrictedAndroidSystemDir(dir) || isRestrictedAndroidDataSubdir(dir)) {
                continue
            }

            dirsScanned++
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 80L) {
                lastUpdate = now
                val progress = (0.15f + 0.8f * (1f - 1f / (1f + dirsScanned / 40f)))
                    .coerceIn(0.15f, 0.95f)
                onProgress(progress, "Scanning ${dir.name.ifBlank { "storage" }}")
            }

            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            val dirCanonical = runCatching { dir.canonicalPath }.getOrNull()
            if (children.isEmpty() &&
                dirCanonical != externalRootCanonical &&
                !isProtectedRootSubfolder(dir) &&
                SafeFileValidator.isSafeToModifyOrDelete(dir, cacheRoots)
            ) {
                emptyFolderItems.add(
                    CleanableItem(
                        path = dir.absolutePath,
                        name = dir.name,
                        sizeBytes = 0L,
                        lastModifiedMillis = dir.lastModified(),
                        groupType = CleanerGroupType.EMPTY_FOLDERS,
                        isDirectory = true,
                        isSelected = true
                    )
                )
                continue
            }

            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (child.isDirectory) {
                    if (!isRestrictedAndroidDataSubdir(child)) {
                        stack.addLast(child)
                    }
                } else if (child.isFile) {
                    val ext = child.extension.lowercase(Locale.ROOT)
                    val size = child.length().coerceAtLeast(0L)
                    val parentCanonical = runCatching { child.parentFile?.canonicalPath }.getOrNull()

                    if (ext == "tmp" || ext == "log") {
                        tempItems.add(
                            CleanableItem(
                                path = child.absolutePath,
                                name = child.name,
                                sizeBytes = size,
                                lastModifiedMillis = child.lastModified(),
                                groupType = CleanerGroupType.TEMP_FILES,
                                isSelected = true
                            )
                        )
                    }

                    if (ext == "apk" && downloadsCanonical != null &&
                        (parentCanonical == downloadsCanonical ||
                            parentCanonical?.startsWith("$downloadsCanonical/") == true)
                    ) {
                        apkItems.add(
                            CleanableItem(
                                path = child.absolutePath,
                                name = child.name,
                                sizeBytes = size,
                                lastModifiedMillis = child.lastModified(),
                                groupType = CleanerGroupType.DOWNLOAD_APKS,
                                isSelected = false
                            )
                        )
                    }

                    if (size >= thresholdBytes) {
                        largeFileItems.add(
                            CleanableItem(
                                path = child.absolutePath,
                                name = child.name,
                                sizeBytes = size,
                                lastModifiedMillis = child.lastModified(),
                                groupType = CleanerGroupType.LARGE_FILES,
                                isSelected = false
                            )
                        )
                    }
                }
            }
        }

        onProgress(1f, "Done")

        listOf(
            CleanerGroup(CleanerGroupType.APP_CACHE, appCacheItems.sortedByDescending { it.sizeBytes }),
            CleanerGroup(CleanerGroupType.TEMP_FILES, tempItems.sortedByDescending { it.sizeBytes }),
            CleanerGroup(CleanerGroupType.EMPTY_FOLDERS, emptyFolderItems.sortedBy { it.name.lowercase() }),
            CleanerGroup(CleanerGroupType.DOWNLOAD_APKS, apkItems.sortedByDescending { it.sizeBytes }),
            CleanerGroup(CleanerGroupType.LARGE_FILES, largeFileItems.sortedByDescending { it.sizeBytes })
        )
    }

    suspend fun deleteCleanableItems(items: List<CleanableItem>): CleanupExecutionResult =
        withContext(Dispatchers.IO) {
            val cacheRoots = listOfNotNull(
                context.cacheDir,
                context.codeCacheDir,
                context.externalCacheDir
            )
            var freedBytes = 0L
            var deletedCount = 0
            val failedNames = mutableListOf<String>()

            // Delete files first, then directories deepest-first
            val sortedItems = items.sortedByDescending { it.path.length }
            for (item in sortedItems) {
                val target = File(item.path)
                if (!target.exists()) {
                    continue
                }
                if (!SafeFileValidator.isSafeToModifyOrDelete(target, cacheRoots)) {
                    failedNames.add(item.name)
                    continue
                }

                val sizeBefore = if (target.isFile) target.length().coerceAtLeast(0L) else 0L
                val deleted = runCatching {
                    if (target.isDirectory) {
                        if (target.listFiles()?.isEmpty() == true) target.delete() else false
                    } else {
                        target.delete()
                    }
                }.getOrDefault(false)

                if (deleted) {
                    freedBytes += sizeBefore
                    deletedCount++
                } else {
                    failedNames.add(item.name)
                }
            }

            CleanupExecutionResult(
                freedBytes = freedBytes,
                deletedCount = deletedCount,
                failedNames = failedNames
            )
        }

    suspend fun listDirectory(
        path: String,
        sortField: FileSortField,
        sortDirection: SortDirection,
        query: String
    ): FileOperationResult<List<FileItem>> = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists()) {
            return@withContext FileOperationResult.Error("Folder not found: ${dir.name}")
        }
        if (!dir.isDirectory) {
            return@withContext FileOperationResult.Error("Selected path is not a folder.")
        }
        if (!dir.canRead()) {
            return@withContext FileOperationResult.Error(
                "Permission denied for ${dir.name}. Grant full storage access in Settings.",
                isPermissionError = true
            )
        }

        val rawChildren = runCatching { dir.listFiles() }.getOrNull()
        if (rawChildren == null) {
            return@withContext FileOperationResult.Error(
                "Unable to read ${dir.name}. Storage permission may be required.",
                isPermissionError = !PermissionHelper.hasFullStorageAccess(context)
            )
        }

        val mapped = rawChildren.map { file -> file.toFileItem() }
        val filtered = if (query.isBlank()) {
            mapped
        } else {
            val q = query.trim().lowercase(Locale.ROOT)
            mapped.filter { it.name.lowercase(Locale.ROOT).contains(q) }
        }

        FileOperationResult.Success(sortItems(filtered, sortField, sortDirection))
    }

    suspend fun listCategoryFiles(
        shortcut: FileCategoryShortcut,
        sortField: FileSortField,
        sortDirection: SortDirection,
        query: String
    ): FileOperationResult<List<FileItem>> = withContext(Dispatchers.IO) {
        if (shortcut == FileCategoryShortcut.ALL) {
            return@withContext listDirectory(getRootDirectoryPath(), sortField, sortDirection, query)
        }

        if (shortcut == FileCategoryShortcut.DOWNLOADS) {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists() || !downloads.canRead()) {
                return@withContext FileOperationResult.Error(
                    "Cannot access Downloads without storage permission.",
                    isPermissionError = !PermissionHelper.hasFullStorageAccess(context)
                )
            }
            return@withContext listDirectory(downloads.absolutePath, sortField, sortDirection, query)
        }

        val targetCategory = when (shortcut) {
            FileCategoryShortcut.IMAGES -> StorageCategory.IMAGES
            FileCategoryShortcut.VIDEOS -> StorageCategory.VIDEOS
            FileCategoryShortcut.AUDIO -> StorageCategory.AUDIO
            FileCategoryShortcut.DOCUMENTS -> StorageCategory.DOCUMENTS
            else -> StorageCategory.OTHER
        }

        val hasAccess = PermissionHelper.hasFullStorageAccess(context)
        val roots = if (hasAccess) {
            listOf(Environment.getExternalStorageDirectory())
        } else {
            listOfNotNull(
                context.getExternalFilesDir(null),
                context.filesDir
            ).distinctBy { it.absolutePath }
        }

        val results = mutableListOf<FileItem>()
        val stack = ArrayDeque<File>()
        roots.filter { it.exists() && it.canRead() }.forEach { stack.addLast(it) }

        val q = query.trim().lowercase(Locale.ROOT)
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = stack.removeLast()
            if (SafeFileValidator.isRestrictedAndroidSystemDir(dir) || isRestrictedAndroidDataSubdir(dir)) {
                continue
            }
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (child.isDirectory) {
                    if (!isRestrictedAndroidDataSubdir(child)) {
                        stack.addLast(child)
                    }
                } else if (child.isFile) {
                    val ext = child.extension.lowercase(Locale.ROOT)
                    if (Formatters.categorizeExtension(ext) == targetCategory) {
                        val item = child.toFileItem()
                        if (q.isEmpty() || item.name.lowercase(Locale.ROOT).contains(q)) {
                            results.add(item)
                        }
                    }
                }
            }
        }

        FileOperationResult.Success(sortItems(results, sortField, sortDirection))
    }

    fun filterAndSortItems(
        items: List<FileItem>,
        sortField: FileSortField,
        direction: SortDirection,
        query: String
    ): List<FileItem> {
        val filtered = if (query.isBlank()) {
            items
        } else {
            val q = query.trim().lowercase(Locale.ROOT)
            items.filter { it.name.lowercase(Locale.ROOT).contains(q) }
        }
        return sortItems(filtered, sortField, direction)
    }

    suspend fun createFolder(parentPath: String, folderName: String): FileOperationResult<FileItem> =
        withContext(Dispatchers.IO) {
            val parent = File(parentPath)
            if (!parent.exists() || !parent.isDirectory) {
                return@withContext FileOperationResult.Error("Parent folder no longer exists.")
            }
            if (!parent.canWrite()) {
                return@withContext FileOperationResult.Error(
                    "Permission denied: cannot create folders here.",
                    isPermissionError = !PermissionHelper.hasFullStorageAccess(context)
                )
            }

            val existingNames = parent.listFiles()?.map { it.name }?.toSet().orEmpty()
            when (val validation = SafeFileValidator.validateNewName(folderName, existingNames)) {
                is NameValidationResult.Invalid -> {
                    return@withContext FileOperationResult.Error(validation.message)
                }
                NameValidationResult.Valid -> Unit
            }

            val newDir = File(parent, folderName.trim())
            val created = runCatching { newDir.mkdir() }.getOrDefault(false)
            if (!created) {
                return@withContext FileOperationResult.Error(
                    "Could not create folder. Check permissions or available storage."
                )
            }
            FileOperationResult.Success(newDir.toFileItem())
        }

    suspend fun renameFileOrFolder(path: String, newName: String): FileOperationResult<FileItem> =
        withContext(Dispatchers.IO) {
            val target = File(path)
            if (!target.exists()) {
                return@withContext FileOperationResult.Error("File not found.")
            }
            if (!SafeFileValidator.isSafeToModifyOrDelete(target)) {
                return@withContext FileOperationResult.Error("System path cannot be renamed.")
            }
            val parent = target.parentFile
                ?: return@withContext FileOperationResult.Error("Cannot determine parent folder.")

            val siblingNames = parent.listFiles()
                ?.filter { it.absolutePath != target.absolutePath }
                ?.map { it.name }
                ?.toSet()
                .orEmpty()

            when (val validation = SafeFileValidator.validateNewName(newName, siblingNames)) {
                is NameValidationResult.Invalid -> {
                    return@withContext FileOperationResult.Error(validation.message)
                }
                NameValidationResult.Valid -> Unit
            }

            val destination = File(parent, newName.trim())
            val renamed = runCatching { target.renameTo(destination) }.getOrDefault(false)
            if (!renamed) {
                return@withContext FileOperationResult.Error(
                    "Rename failed. The file may be in use or write permission is restricted."
                )
            }
            FileOperationResult.Success(destination.toFileItem())
        }

    suspend fun deleteFileOrFolder(path: String): FileOperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val target = File(path)
            if (!target.exists()) {
                return@withContext FileOperationResult.Error("File not found.")
            }
            if (!SafeFileValidator.isSafeToModifyOrDelete(target)) {
                return@withContext FileOperationResult.Error("Protected system locations cannot be deleted.")
            }
            val deleted = runCatching {
                if (target.isDirectory) target.deleteRecursively() else target.delete()
            }.getOrDefault(false)

            if (!deleted) {
                return@withContext FileOperationResult.Error(
                    "Could not delete \"${target.name}\". It may be in use or protected."
                )
            }
            FileOperationResult.Success(Unit)
        }

    private suspend fun collectNestedFiles(root: File): List<File> {
        if (!root.exists() || !root.canRead()) return emptyList()
        val files = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else if (child.isFile) {
                    files.add(child)
                }
            }
        }
        return files
    }

    private fun isProtectedRootSubfolder(dir: File): Boolean {
        val externalRoot = Environment.getExternalStorageDirectory()
        val isDirectChildOfRoot = dir.parentFile?.absolutePath == externalRoot.absolutePath
        return isDirectChildOfRoot && dir.name.lowercase(Locale.ROOT) in protectedStandardFolderNames
    }

    private fun isRestrictedAndroidDataSubdir(dir: File): Boolean {
        val path = dir.absolutePath
        return path.contains("/Android/data") || path.contains("/Android/obb")
    }

    private fun File.toFileItem(): FileItem {
        val isDir = isDirectory
        val ext = if (isDir) "" else extension.lowercase(Locale.ROOT)
        val childCount = if (isDir) runCatching { list()?.size ?: 0 }.getOrDefault(0) else 0
        return FileItem(
            name = name,
            path = absolutePath,
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else length().coerceAtLeast(0L),
            lastModifiedMillis = lastModified(),
            extension = ext,
            mimeType = Formatters.resolveMimeType(name, isDir),
            category = Formatters.categorizeExtension(ext),
            childCount = childCount
        )
    }

    private fun sortItems(
        items: List<FileItem>,
        sortField: FileSortField,
        direction: SortDirection
    ): List<FileItem> {
        val comparator = when (sortField) {
            FileSortField.NAME -> compareBy<FileItem> { it.name.lowercase(Locale.ROOT) }
            FileSortField.SIZE -> compareBy<FileItem> { it.sizeBytes }.thenBy { it.name.lowercase(Locale.ROOT) }
            FileSortField.DATE -> compareBy<FileItem> { it.lastModifiedMillis }.thenBy { it.name.lowercase(Locale.ROOT) }
        }
        val ordered = if (direction == SortDirection.ASCENDING) {
            items.sortedWith(comparator)
        } else {
            items.sortedWith(comparator.reversed())
        }
        // Keep folders grouped ahead of files for intuitive browsing
        return ordered.sortedByDescending { it.isDirectory }
    }
}
