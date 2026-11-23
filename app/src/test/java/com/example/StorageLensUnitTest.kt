package com.example

import com.example.data.model.CategoryBreakdown
import com.example.data.model.StorageCategory
import com.example.data.model.StorageScanResult
import com.example.util.Formatters
import com.example.util.NameValidationResult
import com.example.util.SafeFileValidator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StorageLensUnitTest {

    @Test
    fun formatBytes_scalesUnitsCorrectly() {
        assertEquals("0 B", Formatters.formatBytes(0L))
        assertEquals("512 B", Formatters.formatBytes(512L))
        assertEquals("1 KB", Formatters.formatBytes(1024L))
        assertEquals("1.0 MB", Formatters.formatBytes(1024L * 1024L))
        assertEquals("2.5 GB", Formatters.formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun categorizeExtension_mapsCommonFileTypes() {
        assertEquals(StorageCategory.IMAGES, Formatters.categorizeExtension("JPG"))
        assertEquals(StorageCategory.VIDEOS, Formatters.categorizeExtension("mp4"))
        assertEquals(StorageCategory.AUDIO, Formatters.categorizeExtension("flac"))
        assertEquals(StorageCategory.DOCUMENTS, Formatters.categorizeExtension("pdf"))
        assertEquals(StorageCategory.OTHER, Formatters.categorizeExtension("apk"))
    }

    @Test
    fun safeFileValidator_rejectsEmptyDuplicateAndInvalidNames() {
        val existing = setOf("Documents", "notes.txt")
        assertTrue(SafeFileValidator.validateNewName("", existing) is NameValidationResult.Invalid)
        assertTrue(SafeFileValidator.validateNewName("   ", existing) is NameValidationResult.Invalid)
        assertTrue(SafeFileValidator.validateNewName("..", existing) is NameValidationResult.Invalid)
        assertTrue(SafeFileValidator.validateNewName("a/b", existing) is NameValidationResult.Invalid)
        assertTrue(SafeFileValidator.validateNewName("documents", existing) is NameValidationResult.Invalid)
        assertTrue(SafeFileValidator.validateNewName("Archives", existing) is NameValidationResult.Valid)
    }

    @Test
    fun safeFileValidator_protectsSystemPaths() {
        assertFalse(SafeFileValidator.isSafeToModifyOrDelete(File("/")))
        assertFalse(SafeFileValidator.isSafeToModifyOrDelete(File("/system/bin")))
        assertFalse(SafeFileValidator.isSafeToModifyOrDelete(File("/proc/1")))
    }

    @Test
    fun storageScanResult_jsonRoundTripPreservesData() {
        val json = Json { ignoreUnknownKeys = true }
        val original = StorageScanResult(
            totalBytes = 128_000_000_000L,
            usedBytes = 58_400_000_000L,
            freeBytes = 69_600_000_000L,
            categories = listOf(
                CategoryBreakdown(StorageCategory.IMAGES, 12_000_000_000L, 420),
                CategoryBreakdown(StorageCategory.VIDEOS, 30_000_000_000L, 45)
            ),
            scannedAtMillis = 1763884000000L,
            isLimitedMode = false
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<StorageScanResult>(encoded)
        assertEquals(original, decoded)
    }
}
