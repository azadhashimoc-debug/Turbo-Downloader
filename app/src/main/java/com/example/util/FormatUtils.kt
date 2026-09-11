package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.data.model.DownloadCategory
import java.io.File
import java.net.URLDecoder
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    private val decimalFormat = DecimalFormat("#.##")

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return "${decimalFormat.format(value)} ${units[digitGroups]}"
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatEta(downloaded: Long, total: Long, speed: Long): String {
        if (total <= 0 || speed <= 0 || downloaded >= total) return "--"
        val remainingBytes = total - downloaded
        val remainingSeconds = remainingBytes / speed
        return when {
            remainingSeconds < 60 -> "${remainingSeconds}s"
            remainingSeconds < 3600 -> "${remainingSeconds / 60}d ${remainingSeconds % 60}s"
            else -> "${remainingSeconds / 3600}s ${(remainingSeconds % 3600) / 60}d"
        }
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun extractFileNameFromUrl(url: String): String {
        return try {
            val cleanUrl = if (url.contains("?")) url.substring(0, url.indexOf("?")) else url
            val decoded = URLDecoder.decode(cleanUrl, "UTF-8")
            val name = decoded.substringAfterLast("/")
            if (name.isNotBlank() && name.contains(".")) name else "download_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}"
        }
    }

    fun detectCategory(fileName: String, mimeType: String? = null): DownloadCategory {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            extension in listOf("pdf", "doc", "docx", "txt", "xlsx", "xls", "pptx", "epub") -> DownloadCategory.DOCUMENT
            extension in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "3gp") -> DownloadCategory.VIDEO
            extension in listOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "wma") -> DownloadCategory.AUDIO
            extension in listOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "ico") -> DownloadCategory.IMAGE
            extension in listOf("zip", "rar", "7z", "tar", "gz", "bz2") -> DownloadCategory.ARCHIVE
            extension in listOf("apk", "xapk", "apks") -> DownloadCategory.APK
            mimeType?.startsWith("image/") == true -> DownloadCategory.IMAGE
            mimeType?.startsWith("video/") == true -> DownloadCategory.VIDEO
            mimeType?.startsWith("audio/") == true -> DownloadCategory.AUDIO
            mimeType?.contains("pdf") == true -> DownloadCategory.DOCUMENT
            else -> DownloadCategory.OTHER
        }
    }

    // Android's built-in MimeTypeMap doesn't know several common archive/media extensions
    // (rar, 7z, apk on some OEM builds...). Apps like zArchiver register their intent
    // filters against these exact mime types, not against "application/octet-stream" - so
    // an unresolved extension silently falling back to octet-stream is why "no suitable
    // app found" shows up for files a real archive manager can open just fine.
    private val extraMimeTypes = mapOf(
        "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "bz2" to "application/x-bzip2",
        "iso" to "application/x-iso9660-image",
        "apk" to "application/vnd.android.package-archive",
        "xapk" to "application/vnd.android.package-archive",
        "flac" to "audio/flac",
        "epub" to "application/epub+zip"
    )

    fun getMimeType(file: File): String {
        val extension = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: extraMimeTypes[extension]
            ?: "application/octet-stream"
    }

    fun openDownloadedFile(context: Context, filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false

        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Faylı aç"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun shareDownloadedFile(context: Context, filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false

        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Faylı paylaş"))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
