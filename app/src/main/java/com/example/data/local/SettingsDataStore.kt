package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.FileSortField
import com.example.data.model.SortDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.storageLensDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "storagelens_settings"
)

data class UserPreferences(
    val largeFileThresholdMb: Long = 50L,
    val sortField: FileSortField = FileSortField.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val lastScanTimeMillis: Long = 0L,
    val hasPromptedStoragePermission: Boolean = false
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val LARGE_FILE_THRESHOLD_MB = longPreferencesKey("large_file_threshold_mb")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_DIRECTION = stringPreferencesKey("sort_direction")
        val LAST_SCAN_TIME_MILLIS = longPreferencesKey("last_scan_time_millis")
        val HAS_PROMPTED_PERMISSION = booleanPreferencesKey("has_prompted_permission")
    }

    val preferencesFlow: Flow<UserPreferences> = context.storageLensDataStore.data.map { prefs ->
        val threshold = (prefs[Keys.LARGE_FILE_THRESHOLD_MB] ?: 50L).coerceIn(1L, 10240L)
        val sortField = runCatching {
            FileSortField.valueOf(prefs[Keys.SORT_FIELD] ?: FileSortField.NAME.name)
        }.getOrDefault(FileSortField.NAME)
        val sortDirection = runCatching {
            SortDirection.valueOf(prefs[Keys.SORT_DIRECTION] ?: SortDirection.ASCENDING.name)
        }.getOrDefault(SortDirection.ASCENDING)
        val lastScan = prefs[Keys.LAST_SCAN_TIME_MILLIS] ?: 0L
        val prompted = prefs[Keys.HAS_PROMPTED_PERMISSION] ?: false

        UserPreferences(
            largeFileThresholdMb = threshold,
            sortField = sortField,
            sortDirection = sortDirection,
            lastScanTimeMillis = lastScan,
            hasPromptedStoragePermission = prompted
        )
    }

    suspend fun setLargeFileThresholdMb(thresholdMb: Long) {
        context.storageLensDataStore.edit { prefs ->
            prefs[Keys.LARGE_FILE_THRESHOLD_MB] = thresholdMb.coerceIn(1L, 10240L)
        }
    }

    suspend fun setSortOrder(field: FileSortField, direction: SortDirection) {
        context.storageLensDataStore.edit { prefs ->
            prefs[Keys.SORT_FIELD] = field.name
            prefs[Keys.SORT_DIRECTION] = direction.name
        }
    }

    suspend fun setLastScanTimeMillis(timestampMillis: Long) {
        context.storageLensDataStore.edit { prefs ->
            prefs[Keys.LAST_SCAN_TIME_MILLIS] = timestampMillis
        }
    }

    suspend fun markPermissionPrompted() {
        context.storageLensDataStore.edit { prefs ->
            prefs[Keys.HAS_PROMPTED_PERMISSION] = true
        }
    }
}
