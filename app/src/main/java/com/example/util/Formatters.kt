package com.example.util

import android.webkit.MimeTypeMap
import com.example.data.model.StorageCategory
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "raw", "dng"
    )
    private val videoExtensions = setOf(
        "mp4", "mkv", "mov", "avi", "wmv", "flv", "webm", "3gp", "m4v"
    )
    private val audioExtensions = setOf(
        "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "opus", "amr"
    )
    private val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv",
        "json", "xml", "rtf", "epub", "odt", "ods"
    )

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        val pattern = if (unitIndex <= 1) "#,##0" else "#,##0.0"
        val formatted = DecimalFormat(pattern).format(value)
        return "$formatted ${units[unitIndex]}"
    }

    fun formatPercent(part: Long, total: Long): String {
        if (total <= 0L) return "0%"
        val pct = (part.toDouble() / total.toDouble()) * 100.0
        return if (pct < 0.1 && part > 0L) "<0.1%" else "${DecimalFormat("0.0").format(pct)}%"
    }

    fun formatTimestamp(millis: Long): String {
        if (millis <= 0L) return "Never scanned"
        val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    fun formatShortDate(millis: Long): String {
        if (millis <= 0L) return "--"
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    fun categorizeExtension(extension: String): StorageCategory {
        val ext = extension.lowercase(Locale.ROOT)
        return when {
            ext in imageExtensions -> StorageCategory.IMAGES
            ext in videoExtensions -> StorageCategory.VIDEOS
            ext in audioExtensions -> StorageCategory.AUDIO
            ext in documentExtensions -> StorageCategory.DOCUMENTS
            else -> StorageCategory.OTHER
        }
    }

    fun resolveMimeType(fileName: String, isDirectory: Boolean): String {
        if (isDirectory) return "inode/directory"
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isEmpty()) return "*/*"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "md", "log", "tmp" -> "text/plain"
            "json" -> "application/json"
            else -> "*/*"
        }
    }
}
