package com.example.data.repository

import com.example.data.db.DownloadDao
import com.example.data.model.DownloadCategory
import com.example.data.model.DownloadEntity
import com.example.engine.DownloadEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class DownloadRepository(
    private val downloadDao: DownloadDao,
    private val downloadEngine: DownloadEngine
) {
    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    val liveSpeeds: StateFlow<Map<Long, Long>> = downloadEngine.liveSpeeds
    val engineMode: StateFlow<com.example.engine.SmartEngineMode> = downloadEngine.engineMode

    fun setEngineMode(mode: com.example.engine.SmartEngineMode) {
        downloadEngine.setEngineMode(mode)
    }

    suspend fun startNewDownload(
        url: String,
        customName: String? = null,
        categoryOverride: DownloadCategory? = null
    ): Long {
        return downloadEngine.enqueueDownload(url, customName, categoryOverride)
    }

    fun pauseDownload(id: Long) {
        downloadEngine.pauseDownload(id)
    }

    fun resumeDownload(id: Long) {
        downloadEngine.resumeDownload(id)
    }

    fun cancelActiveDownload(id: Long) {
        downloadEngine.cancelActiveDownload(id)
    }

    fun moveToPublicStorage(id: Long, onResult: (Boolean) -> Unit) {
        downloadEngine.moveToPublicStorage(id, onResult)
    }

    fun retryDownload(id: Long) {
        downloadEngine.retryDownload(id)
    }

    fun cancelOrDelete(id: Long, deleteFile: Boolean = true) {
        downloadEngine.cancelDownload(id, deleteFile)
    }

    suspend fun deleteAllCompleted() {
        downloadDao.deleteAllCompleted()
    }
}
