package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsDataStore
import com.example.data.local.StorageSnapshotStore
import com.example.data.model.CategoryBreakdown
import com.example.data.model.ChartViewMode
import com.example.data.model.StorageCategory
import com.example.data.model.StorageScanResult
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

data class DashboardUiState(
    val isInitialLoading: Boolean = true,
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val scanningLocation: String = "",
    val scanResult: StorageScanResult = StorageScanResult(),
    val lastScanTimeMillis: Long = 0L,
    val chartMode: ChartViewMode = ChartViewMode.DONUT,
    val selectedCategory: StorageCategory? = null,
    val hasFullStorageAccess: Boolean = false,
    val showPermissionRationale: Boolean = false,
    val errorMessage: String? = null
)

class DashboardViewModel(
    private val appContext: Context,
    private val repository: StorageRepository,
    private val snapshotStore: StorageSnapshotStore,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var activeScanJob: Job? = null

    init {
        loadInitialSnapshot()
    }

    private fun loadInitialSnapshot() {
        viewModelScope.launch {
            val hasAccess = PermissionHelper.hasFullStorageAccess(appContext)
            val prefs = settingsDataStore.preferencesFlow.first()
            val savedSnapshot = snapshotStore.loadLastScanResult()
            val liveStats = repository.getDeviceStorageStats()

            val initialResult = if (savedSnapshot != null) {
                savedSnapshot.copy(
                    totalBytes = liveStats.totalBytes,
                    usedBytes = liveStats.usedBytes,
                    freeBytes = liveStats.freeBytes,
                    isLimitedMode = !hasAccess
                )
            } else {
                StorageScanResult(
                    totalBytes = liveStats.totalBytes,
                    usedBytes = liveStats.usedBytes,
                    freeBytes = liveStats.freeBytes,
                    categories = StorageCategory.entries.map { CategoryBreakdown(it, 0L, 0) },
                    scannedAtMillis = 0L,
                    isLimitedMode = !hasAccess
                )
            }

            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    scanResult = initialResult,
                    lastScanTimeMillis = prefs.lastScanTimeMillis.takeIf { ts -> ts > 0L }
                        ?: (savedSnapshot?.scannedAtMillis ?: 0L),
                    hasFullStorageAccess = hasAccess,
                    showPermissionRationale = !hasAccess && !prefs.hasPromptedStoragePermission
                )
            }

            // If no previous scan exists and permission is already granted, run an automatic first scan
            if (savedSnapshot == null && hasAccess) {
                startScan()
            }
        }
    }

    fun refreshPermissionState(triggerScanIfNewlyGranted: Boolean = true) {
        val hasAccess = PermissionHelper.hasFullStorageAccess(appContext)
        val wasGrantedBefore = _uiState.value.hasFullStorageAccess
        val liveStats = repository.getDeviceStorageStats()

        _uiState.update { state ->
            state.copy(
                hasFullStorageAccess = hasAccess,
                showPermissionRationale = if (hasAccess) false else state.showPermissionRationale,
                scanResult = state.scanResult.copy(
                    totalBytes = liveStats.totalBytes,
                    usedBytes = liveStats.usedBytes,
                    freeBytes = liveStats.freeBytes,
                    isLimitedMode = !hasAccess
                )
            )
        }

        if (triggerScanIfNewlyGranted && !wasGrantedBefore && hasAccess) {
            startScan()
        }
    }

    fun onPermissionPromptHandled() {
        viewModelScope.launch {
            settingsDataStore.markPermissionPrompted()
            _uiState.update { it.copy(showPermissionRationale = false) }
        }
    }

    fun showRationaleSheet() {
        _uiState.update { it.copy(showPermissionRationale = true) }
    }

    fun dismissRationaleSheet() {
        viewModelScope.launch {
            settingsDataStore.markPermissionPrompted()
            _uiState.update { it.copy(showPermissionRationale = false) }
        }
    }

    fun setChartMode(mode: ChartViewMode) {
        _uiState.update { it.copy(chartMode = mode) }
    }

    fun highlightCategory(category: StorageCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun startScan() {
        activeScanJob?.cancel()
        activeScanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    scanProgress = 0.05f,
                    scanningLocation = "Preparing scan...",
                    errorMessage = null
                )
            }
            try {
                val result = repository.scanStorage { progress, location ->
                    _uiState.update { state ->
                        state.copy(
                            scanProgress = progress,
                            scanningLocation = location
                        )
                    }
                }
                snapshotStore.saveLastScanResult(result)
                settingsDataStore.setLastScanTimeMillis(result.scannedAtMillis)

                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanProgress = 1f,
                        scanningLocation = "",
                        scanResult = result,
                        lastScanTimeMillis = result.scannedAtMillis,
                        hasFullStorageAccess = !result.isLimitedMode,
                        errorMessage = null
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanProgress = 0f,
                        scanningLocation = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanProgress = 0f,
                        scanningLocation = "",
                        errorMessage = e.message ?: "Scan failed. Please try again."
                    )
                }
            }
        }
    }

    fun cancelScan() {
        activeScanJob?.cancel()
        activeScanJob = null
        _uiState.update {
            it.copy(
                isScanning = false,
                scanProgress = 0f,
                scanningLocation = ""
            )
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
            return DashboardViewModel(appContext, repository, snapshotStore, settingsDataStore) as T
        }
    }
}
