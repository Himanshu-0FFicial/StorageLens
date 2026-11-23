package com.example.data.local

import android.content.Context
import com.example.data.model.CleanupHistoryEntry
import com.example.data.model.StorageScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class StorageSnapshotStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val snapshotFile: File
        get() = File(context.filesDir, "storage_snapshot.json")

    private val cleanupHistoryFile: File
        get() = File(context.filesDir, "cleanup_history.json")

    suspend fun loadLastScanResult(): StorageScanResult? = withContext(Dispatchers.IO) {
        runCatching {
            val file = snapshotFile
            if (!file.exists() || file.length() == 0L) return@withContext null
            json.decodeFromString<StorageScanResult>(file.readText())
        }.getOrNull()
    }

    suspend fun saveLastScanResult(result: StorageScanResult) = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = File(context.filesDir, "storage_snapshot.json.tmp")
            tempFile.writeText(json.encodeToString(result))
            if (!tempFile.renameTo(snapshotFile)) {
                snapshotFile.writeText(tempFile.readText())
                tempFile.delete()
            }
        }
    }

    suspend fun loadCleanupHistory(): List<CleanupHistoryEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val file = cleanupHistoryFile
            if (!file.exists() || file.length() == 0L) return@withContext emptyList()
            json.decodeFromString<List<CleanupHistoryEntry>>(file.readText())
        }.getOrDefault(emptyList())
    }

    suspend fun appendCleanupHistory(entry: CleanupHistoryEntry): List<CleanupHistoryEntry> =
        withContext(Dispatchers.IO) {
            val updated = (listOf(entry) + loadCleanupHistory()).take(50)
            runCatching {
                val tempFile = File(context.filesDir, "cleanup_history.json.tmp")
                tempFile.writeText(json.encodeToString(updated))
                if (!tempFile.renameTo(cleanupHistoryFile)) {
                    cleanupHistoryFile.writeText(tempFile.readText())
                    tempFile.delete()
                }
            }
            updated
        }
}
