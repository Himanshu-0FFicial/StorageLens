package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsDataStore
import com.example.data.local.StorageSnapshotStore
import com.example.data.model.CleanerGroup
import com.example.data.model.CleanerGroupType
import com.example.data.model.CleanupExecutionResult
import com.example.data.model.CleanupHistoryEntry
import com.example.data.repository.StorageRepository
import com.example.util.Formatters
import com.example.util.PermissionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class CleanerUiState(
    val isScanning: Boolean = false,
    val isDeleting: Boolean = false,
    val scanProgress: Float = 0f,
    val scanStage: String = "",
    val hasScannedOnce: Boolean = false,
    val groups: List<CleanerGroup> = emptyList(),
    val largeFileThresholdMb: Long = 50L,
    val thresholdInputError: String? = null,
    val hasFullStorageAccess: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val lastCleanupResult: CleanupExecutionResult? = null,
    val cleanupHistory: List<CleanupHistoryEntry> = emptyList(),
    val errorMessage: String? = null
) {
    val totalSelectedBytes: Long
        get() = groups.sumOf { it.selectedBytes }

    val totalSelectedCount: Int
        get() = groups.sumOf { it.selectedCount }

    val totalItemsCount: Int
        get() = groups.sumOf { it.items.size }

    val allSelected: Boolean
        get() = totalItemsCount > 0 && groups.filter { it.items.isNotEmpty() }.all { it.allSelected }
}

class CleanerViewModel(
    private val appContext: Context,
    private val repository: StorageRepository,
    private val snapshotStore: StorageSnapshotStore,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanerUiState())
    val uiState: StateFlow<CleanerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val prefs = settingsDataStore.preferencesFlow.first()
            val history = snapshotStore.loadCleanupHistory()
            val hasAccess = PermissionHelper.hasFullStorageAccess(appContext)
            _uiState.update {
                it.copy(
                    largeFileThresholdMb = prefs.largeFileThresholdMb,
                    cleanupHistory = history,
                    hasFullStorageAccess = hasAccess
                )
            }
            startScan()
        }
    }

    fun refreshPermissionState() {
        val hasAccess = PermissionHelper.hasFullStorageAccess(appContext)
        val changed = hasAccess != _uiState.value.hasFullStorageAccess
        _uiState.update { it.copy(hasFullStorageAccess = hasAccess) }
        if (changed && hasAccess) {
            startScan()
        }
    }

    fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val thresholdMb = _uiState.value.largeFileThresholdMb
            _uiState.update {
                it.copy(
                    isScanning = true,
                    scanProgress = 0.05f,
                    scanStage = "Scanning storage...",
                    errorMessage = null
                )
            }
            try {
                val groups = repository.scanCleanerItems(thresholdMb) { progress, stage ->
                    _uiState.update { state ->
                        state.copy(scanProgress = progress, scanStage = stage)
                    }
                }
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanProgress = 1f,
                        scanStage = "",
                        hasScannedOnce = true,
                        groups = groups,
                        hasFullStorageAccess = PermissionHelper.hasFullStorageAccess(appContext),
                        errorMessage = null
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanProgress = 0f,
                        scanStage = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanProgress = 0f,
                        scanStage = "",
                        errorMessage = e.message ?: "Unable to scan for cleanable files."
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update {
            it.copy(
                isScanning = false,
                scanProgress = 0f,
                scanStage = ""
            )
        }
    }

    fun toggleItem(path: String) {
        _uiState.update { state ->
            val updatedGroups = state.groups.map { group ->
                group.copy(
                    items = group.items.map { item ->
                        if (item.path == path) item.copy(isSelected = !item.isSelected) else item
                    }
                )
            }
            state.copy(groups = updatedGroups)
        }
    }

    fun toggleGroup(type: CleanerGroupType) {
        _uiState.update { state ->
            val targetGroup = state.groups.find { it.type == type } ?: return@update state
            val selectAllInGroup = !targetGroup.allSelected
            val updatedGroups = state.groups.map { group ->
                if (group.type == type) {
                    group.copy(items = group.items.map { it.copy(isSelected = selectAllInGroup) })
                } else {
                    group
                }
            }
            state.copy(groups = updatedGroups)
        }
    }

    fun toggleSelectAll() {
        _uiState.update { state ->
            val targetSelected = !state.allSelected
            val updatedGroups = state.groups.map { group ->
                group.copy(items = group.items.map { it.copy(isSelected = targetSelected) })
            }
            state.copy(groups = updatedGroups)
        }
    }

    fun updateThreshold(rawInput: String): Boolean {
        val parsed = rawInput.trim().toLongOrNull()
        if (parsed == null || parsed < 1L || parsed > 10240L) {
            _uiState.update {
                it.copy(thresholdInputError = "Enter a number between 1 and 10,240 MB.")
            }
            return false
        }
        _uiState.update {
            it.copy(
                largeFileThresholdMb = parsed,
                thresholdInputError = null
            )
        }
        viewModelScope.launch {
            settingsDataStore.setLargeFileThresholdMb(parsed)
            startScan()
        }
        return true
    }

    fun clearThresholdError() {
        _uiState.update { it.copy(thresholdInputError = null) }
    }

    fun requestDeleteConfirmation() {
        if (_uiState.value.totalSelectedCount > 0) {
            _uiState.update { it.copy(showDeleteConfirmation = true) }
        }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun dismissCleanupSummary() {
        _uiState.update { it.copy(lastCleanupResult = null) }
    }

    fun confirmDelete() {
        val selectedItems = _uiState.value.groups.flatMap { it.items }.filter { it.isSelected }
        if (selectedItems.isEmpty()) {
            _uiState.update { it.copy(showDeleteConfirmation = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showDeleteConfirmation = false,
                    isDeleting = true,
                    errorMessage = null
                )
            }
            try {
                val result = repository.deleteCleanableItems(selectedItems)
                val historyEntry = CleanupHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    timestampMillis = System.currentTimeMillis(),
                    freedBytes = result.freedBytes,
                    itemsDeletedCount = result.deletedCount,
                    summary = "Freed ${Formatters.formatBytes(result.freedBytes)} (${result.deletedCount} items)"
                )
                val updatedHistory = if (result.deletedCount > 0) {
                    snapshotStore.appendCleanupHistory(historyEntry)
                } else {
                    _uiState.value.cleanupHistory
                }

                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        lastCleanupResult = result,
                        cleanupHistory = updatedHistory
                    )
                }
                startScan()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = e.message ?: "Failed to delete selected items."
                    )
                }
            }
        }
    }

    class Factory(
        private val appContext: Context,
        private val repository: StorageRepository,
        private val snapshotStore: StorageSnapshotStore,
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CleanerViewModel(appContext, repository, snapshotStore, settingsDataStore) as T
        }
    }
}
