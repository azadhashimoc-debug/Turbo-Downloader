package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class DownloadCategory {
    DOCUMENT,
    VIDEO,
    AUDIO,
    IMAGE,
    ARCHIVE,
    APK,
    OTHER
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String = "application/octet-stream",
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val speedBytesPerSec: Long = 0L,
    val errorMessage: String? = null,
    val resumable: Boolean = true,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateCompleted: Long? = null,
    val category: DownloadCategory = DownloadCategory.OTHER
) {
    val progress: Float
        get() = when {
            totalBytes > 0 -> (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            status == DownloadStatus.COMPLETED -> 1f
            else -> 0f
        }

    val progressPercent: Int
        get() = (progress * 100).toInt()
}
