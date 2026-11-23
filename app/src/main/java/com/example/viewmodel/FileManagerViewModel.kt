package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsDataStore
import com.example.data.model.BreadcrumbSegment
import com.example.data.model.FileCategoryShortcut
import com.example.data.model.FileItem
import com.example.data.model.FileSortField
import com.example.data.model.SortDirection
import com.example.data.repository.FileOperationResult
import com.example.data.repository.StorageRepository
import com.example.util.PermissionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class FileManagerUiState(
    val isLoading: Boolean = true,
    val currentPath: String = "",
    val rootPath: String = "",
    val breadcrumbs: List<BreadcrumbSegment> = emptyList(),
    val activeShortcut: FileCategoryShortcut = FileCategoryShortcut.ALL,
    val items: List<FileItem> = emptyList(),
    val searchQuery: String = "",
    val sortField: FileSortField = FileSortField.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val hasFullStorageAccess: Boolean = false,
    val errorMessage: String? = null,
    val isPermissionError: Boolean = false,
    val actionBannerMessage: String? = null,
    val pendingDeleteItem: FileItem? = null,
    val pendingRenameItem: FileItem? = null,
    val detailsItem: FileItem? = null,
    val showNewFolderDialog: Boolean = false,
    val dialogValidationError: String? = null
) {
    val canNavigateUp: Boolean
        get() = activeShortcut != FileCategoryShortcut.ALL ||
            (currentPath.isNotBlank() && currentPath != rootPath)
}

class FileManagerViewModel(
    private val appContext: Context,
    private val repository: StorageRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileManagerUiState())
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    // Session cache keyed by "DIR:<path>" or "CAT:<shortcut>" so revisiting folders
    // or returning from external apps renders instantaneously without re-scanning.
    private val sessionCache = mutableMapOf<String, List<FileItem>>()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            val prefs = settingsDataStore.preferencesFlow.first()
            val root = repository.getRootDirectoryPath()
            val hasAccess = PermissionHelper.hasFullStorageAccess(appContext)
            _uiState.update { state ->
                val targetPath = state.currentPath.ifBlank { root }
                state.copy(
                    rootPath = root,
                    currentPath = targetPath,
                    breadcrumbs = if (state.activeShortcut == FileCategoryShortcut.ALL) {
                        buildBreadcrumbs(targetPath, root)
                    } else {
                        state.breadcrumbs
                    },
                    sortField = prefs.sortField,
                    sortDirection = prefs.sortDirection,
                    hasFullStorageAccess = hasAccess
                )
            }
            loadCurrentView(forceRefresh = false)
        }
    }

    fun refreshPermissionAndReload() {
        val currentState = _uiState.value
        val hasAccess = PermissionHelper.hasFullStorageAccess(appContext)

        // Avoid reloading when resuming from another app (e.g. viewing video/image/audio)
        // if storage permission state has not changed.
        if (currentState.rootPath.isNotBlank() &&
            hasAccess == currentState.hasFullStorageAccess &&
            !currentState.isPermissionError
        ) {
            return
        }

        sessionCache.clear()
        val newRoot = repository.getRootDirectoryPath()
        val oldRoot = currentState.rootPath
        val current = currentState.currentPath
        val updatedPath = if (current.isBlank() || current == oldRoot) newRoot else current

        _uiState.update {
            it.copy(
                hasFullStorageAccess = hasAccess,
                rootPath = newRoot,
                currentPath = updatedPath,
                breadcrumbs = if (it.activeShortcut == FileCategoryShortcut.ALL) {
                    buildBreadcrumbs(updatedPath, newRoot)
                } else {
                    it.breadcrumbs
                }
            )
        }
        loadCurrentView(forceRefresh = true)
    }

    fun applyCategoryFilter(shortcut: FileCategoryShortcut) {
        val targetPath = when (shortcut) {
            FileCategoryShortcut.DOWNLOADS -> repository.getDownloadsPath()
            FileCategoryShortcut.ALL -> repository.getRootDirectoryPath()
            else -> _uiState.value.currentPath
        }
        val root = repository.getRootDirectoryPath()
        _uiState.update {
            it.copy(
                activeShortcut = shortcut,
                currentPath = targetPath,
                breadcrumbs = if (shortcut == FileCategoryShortcut.ALL) {
                    buildBreadcrumbs(targetPath, root)
                } else {
                    listOf(BreadcrumbSegment(shortcut.label, targetPath))
                }
            )
        }
        loadCurrentView(forceRefresh = false)
    }

    fun openFolder(path: String) {
        val root = repository.getRootDirectoryPath()
        _uiState.update {
            it.copy(
                activeShortcut = FileCategoryShortcut.ALL,
                currentPath = path,
                breadcrumbs = buildBreadcrumbs(path, root)
            )
        }
        loadCurrentView(forceRefresh = false)
    }

    fun navigateUp(): Boolean {
        val state = _uiState.value
        if (state.activeShortcut != FileCategoryShortcut.ALL) {
            applyCategoryFilter(FileCategoryShortcut.ALL)
            return true
        }
        val root = state.rootPath
        if (state.currentPath.isBlank() || state.currentPath == root) {
            return false
        }
        val parent = File(state.currentPath).parentFile
        val nextPath = if (parent != null && parent.absolutePath.startsWith(root)) {
            parent.absolutePath
        } else {
            root
        }
        openFolder(nextPath)
        return true
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadCurrentView(forceRefresh = false)
    }

    fun updateSort(field: FileSortField, direction: SortDirection) {
        _uiState.update { it.copy(sortField = field, sortDirection = direction) }
        viewModelScope.launch {
            settingsDataStore.setSortOrder(field, direction)
        }
        loadCurrentView(forceRefresh = false)
    }

    fun forceRefresh() {
        loadCurrentView(forceRefresh = true)
    }

    fun invalidateSessionCache() {
        sessionCache.clear()
    }

    fun loadCurrentView(forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (state.currentPath.isBlank() && state.activeShortcut == FileCategoryShortcut.ALL) {
            return
        }

        val key = cacheKey(state.activeShortcut, state.currentPath)
        val cachedItems = if (!forceRefresh) sessionCache[key] else null
        if (cachedItems != null) {
            loadJob?.cancel()
            val prepared = repository.filterAndSortItems(
                items = cachedItems,
                sortField = state.sortField,
                direction = state.sortDirection,
                query = state.searchQuery
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    items = prepared,
                    errorMessage = null,
                    isPermissionError = false
                )
            }
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val latest = _uiState.value
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isPermissionError = false) }
            try {
                // Load unfiltered list into sessionCache so search, sort, and revisits are instant
                val result = if (latest.activeShortcut == FileCategoryShortcut.ALL) {
                    repository.listDirectory(
                        path = latest.currentPath,
                        sortField = latest.sortField,
                        sortDirection = latest.sortDirection,
                        query = ""
                    )
                } else {
                    repository.listCategoryFiles(
                        shortcut = latest.activeShortcut,
                        sortField = latest.sortField,
                        sortDirection = latest.sortDirection,
                        query = ""
                    )
                }

                when (result) {
                    is FileOperationResult.Success -> {
                        sessionCache[key] = result.data
                        val current = _uiState.value
                        val prepared = repository.filterAndSortItems(
                            items = result.data,
                            sortField = current.sortField,
                            direction = current.sortDirection,
                            query = current.searchQuery
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                items = prepared,
                                errorMessage = null,
                                isPermissionError = false
                            )
                        }
                    }
                    is FileOperationResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                items = emptyList(),
                                errorMessage = result.message,
                                isPermissionError = result.isPermissionError
                            )
                        }
                    }
                }
            } catch (_: CancellationException) {
                // Ignore superseded loads
            }
        }
    }

    fun showNewFolderDialog() {
        _uiState.update { it.copy(showNewFolderDialog = true, dialogValidationError = null) }
    }

    fun dismissNewFolderDialog() {
        _uiState.update { it.copy(showNewFolderDialog = false, dialogValidationError = null) }
    }

    fun createFolder(name: String) {
        val parentPath = _uiState.value.currentPath
        viewModelScope.launch {
            when (val res = repository.createFolder(parentPath, name)) {
                is FileOperationResult.Success -> {
                    invalidateAffectedCache(parentPath)
                    _uiState.update {
                        it.copy(
                            showNewFolderDialog = false,
                            dialogValidationError = null,
                            actionBannerMessage = "Created folder \"${res.data.name}\""
                        )
                    }
                    loadCurrentView(forceRefresh = true)
                }
                is FileOperationResult.Error -> {
                    _uiState.update { it.copy(dialogValidationError = res.message) }
                }
            }
        }
    }

    fun requestRename(item: FileItem) {
        _uiState.update { it.copy(pendingRenameItem = item, dialogValidationError = null) }
    }

    fun dismissRenameDialog() {
        _uiState.update { it.copy(pendingRenameItem = null, dialogValidationError = null) }
    }

    fun confirmRename(newName: String) {
        val target = _uiState.value.pendingRenameItem ?: return
        viewModelScope.launch {
            when (val res = repository.renameFileOrFolder(target.path, newName)) {
                is FileOperationResult.Success -> {
                    invalidateAffectedCache(File(target.path).parent ?: _uiState.value.currentPath, target.path)
                    _uiState.update {
                        it.copy(
                            pendingRenameItem = null,
                            dialogValidationError = null,
                            actionBannerMessage = "Renamed to \"${res.data.name}\""
                        )
                    }
                    loadCurrentView(forceRefresh = true)
                }
                is FileOperationResult.Error -> {
                    _uiState.update { it.copy(dialogValidationError = res.message) }
                }
            }
        }
    }

    fun requestDelete(item: FileItem) {
        _uiState.update { it.copy(pendingDeleteItem = item) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(pendingDeleteItem = null) }
    }

    fun confirmDelete() {
        val target = _uiState.value.pendingDeleteItem ?: return
        viewModelScope.launch {
            when (val res = repository.deleteFileOrFolder(target.path)) {
                is FileOperationResult.Success -> {
                    invalidateAffectedCache(File(target.path).parent ?: _uiState.value.currentPath, target.path)
                    _uiState.update {
                        it.copy(
                            pendingDeleteItem = null,
                            actionBannerMessage = "Deleted \"${target.name}\""
                        )
                    }
                    loadCurrentView(forceRefresh = true)
                }
                is FileOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            pendingDeleteItem = null,
                            actionBannerMessage = res.message
                        )
                    }
                }
            }
        }
    }

    fun showDetails(item: FileItem) {
        _uiState.update { it.copy(detailsItem = item) }
    }

    fun dismissDetails() {
        _uiState.update { it.copy(detailsItem = null) }
    }

    fun clearBannerMessage() {
        _uiState.update { it.copy(actionBannerMessage = null) }
    }

    private fun cacheKey(shortcut: FileCategoryShortcut, path: String): String {
        return if (shortcut == FileCategoryShortcut.ALL) "DIR:$path" else "CAT:${shortcut.name}"
    }

    private fun invalidateAffectedCache(folderPath: String, affectedItemPath: String? = null) {
        sessionCache.remove("DIR:$folderPath")
        sessionCache.keys.removeAll { it.startsWith("CAT:") }
        if (affectedItemPath != null) {
            sessionCache.keys.removeAll {
                it == "DIR:$affectedItemPath" || it.startsWith("DIR:$affectedItemPath/")
            }
        }
    }

    private fun buildBreadcrumbs(currentPath: String, rootPath: String): List<BreadcrumbSegment> {
        val rootLabel = if (PermissionHelper.hasFullStorageAccess(appContext)) {
            "Internal Storage"
        } else {
            "App Storage"
        }
        val segments = mutableListOf(BreadcrumbSegment(rootLabel, rootPath))
        if (currentPath == rootPath || !currentPath.startsWith(rootPath)) {
            return segments
        }
        val relative = currentPath.removePrefix(rootPath).trimStart('/')
        if (relative.isEmpty()) return segments

        var cumulative = rootPath
        for (part in relative.split('/')) {
            if (part.isNotBlank()) {
                cumulative = "$cumulative/$part"
                segments.add(BreadcrumbSegment(part, cumulative))
            }
        }
        return segments
    }

    class Factory(
        private val appContext: Context,
        private val repository: StorageRepository,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FileManagerViewModel(appContext, repository, settingsDataStore) as T
        }
    }
}
