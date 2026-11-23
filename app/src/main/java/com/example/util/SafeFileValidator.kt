package com.example.util

import android.os.Environment
import java.io.File

sealed class NameValidationResult {
    data object Valid : NameValidationResult()
    data class Invalid(val message: String) : NameValidationResult()
}

object SafeFileValidator {
    private val protectedPrefixes = listOf(
        "/system",
        "/vendor",
        "/product",
        "/apex",
        "/proc",
        "/sys",
        "/dev",
        "/bin",
        "/sbin",
        "/etc",
        "/data/system"
    )

    fun validateNewName(name: String, siblingNames: Set<String>): NameValidationResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return NameValidationResult.Invalid("Name cannot be empty.")
        }
        if (trimmed == "." || trimmed == "..") {
            return NameValidationResult.Invalid("Reserved folder name is not allowed.")
        }
        if (trimmed.length > 255) {
            return NameValidationResult.Invalid("Name is too long (max 255 characters).")
        }
        if (trimmed.any { it == '/' || it == '\\' || it == '\u0000' }) {
            return NameValidationResult.Invalid("Name cannot contain slashes or invalid characters.")
        }
        if (siblingNames.any { it.equals(trimmed, ignoreCase = true) }) {
            return NameValidationResult.Invalid("An item named \"$trimmed\" already exists here.")
        }
        return NameValidationResult.Valid
    }

    fun isSafeToModifyOrDelete(file: File, appCacheDirs: List<File> = emptyList()): Boolean {
        val canonical = runCatching { file.canonicalPath }.getOrNull() ?: return false
        if (canonical == "/" || canonical.isBlank()) return false

        val externalRoot = runCatching {
            Environment.getExternalStorageDirectory().canonicalPath
        }.getOrNull()
        if (externalRoot != null && canonical == externalRoot) return false

        if (protectedPrefixes.any { prefix ->
                canonical == prefix || canonical.startsWith("$prefix/")
            }
        ) {
            return false
        }

        // Prevent deleting the app's own root cache directory itself (only its children)
        if (appCacheDirs.any { runCatching { it.canonicalPath }.getOrNull() == canonical }) {
            return false
        }

        return true
    }

    fun isRestrictedAndroidSystemDir(file: File): Boolean {
        val path = file.absolutePath
        if (protectedPrefixes.any { path == it || path.startsWith("$it/") }) {
            return true
        }
        return false
    }
}
