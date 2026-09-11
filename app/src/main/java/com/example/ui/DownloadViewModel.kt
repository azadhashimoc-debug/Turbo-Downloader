package com.example.ui

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SampleDownloadItem
import com.example.data.db.AppDatabase
import com.example.data.model.DownloadCategory
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.data.repository.DownloadRepository
import com.example.engine.DownloadEngine
import com.example.util.FormatUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class DownloadFilter(val title: String) {
    ALL("Hamısı"),
    ACTIVE("Aktiv"),
    COMPLETED("Tamamlandı"),
    PAUSED("Fasilə/Xəta")
}

data class DownloadUiState(
    val downloads: List<DownloadEntity> = emptyList(),
    val filteredDownloads: List<DownloadEntity> = emptyList(),
    val filter: DownloadFilter = DownloadFilter.ALL,
    val searchQuery: String = "",
    val totalSpeedBytes: Long = 0L,
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    val pausedCount: Int = 0,
    val freeStorageText: String = "",
    val showAddDialog: Boolean = false,
    val incomingUrl: String? = null,
    val engineMode: com.example.engine.SmartEngineMode = com.example.engine.SmartEngineMode.TURBO_MULTI_STREAM,
    val showSampleSheet: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val selectedDownloadDetails: DownloadEntity? = null
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val engine = DownloadEngine(application, database.downloadDao())
    private val repository = DownloadRepository(database.downloadDao(), engine)

    private val _filter = MutableStateFlow(DownloadFilter.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _showAddDialog = MutableStateFlow(false)
    private val _incomingUrl = MutableStateFlow<String?>(null)
    private val _showSampleSheet = MutableStateFlow(false)
    private val _showSettingsDialog = MutableStateFlow(false)
    private val _selectedDetails = MutableStateFlow<DownloadEntity?>(null)

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    private val rawDownloads = repository.allDownloads
    private val liveSpeeds = repository.liveSpeeds
    private val engineMode = repository.engineMode

    val uiState: StateFlow<DownloadUiState> = combine(
        rawDownloads,
        liveSpeeds,
        _filter,
        _searchQuery,
        _showAddDialog,
        _incomingUrl,
        _showSampleSheet,
        _showSettingsDialog,
        _selectedDetails,
        engineMode
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val downloads = args[0] as List<DownloadEntity>
        @Suppress("UNCHECKED_CAST")
        val speeds = args[1] as Map<Long, Long>
        val filter = args[2] as DownloadFilter
        val search = args[3] as String
        val showAdd = args[4] as Boolean
        val incoming = args[5] as String?
        val showSamples = args[6] as Boolean
        val showSettings = args[7] as Boolean
        val details = args[8] as DownloadEntity?
        val currentEngineMode = args[9] as com.example.engine.SmartEngineMode

        // Merge live high-frequency speeds into entities
        val updatedDownloads = downloads.map { entity ->
            val liveSpeed = speeds[entity.id]
            if (liveSpeed != null && entity.status == DownloadStatus.DOWNLOADING) {
                entity.copy(speedBytesPerSec = liveSpeed)
            } else {
                entity
            }
        }

        var totalSpeed = 0L
        var active = 0
        var completed = 0
        var paused = 0

        for (item in updatedDownloads) {
            when (item.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING, DownloadStatus.QUEUED -> {
                    active++
                    totalSpeed += item.speedBytesPerSec
                }
                DownloadStatus.COMPLETED -> completed++
                DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELLED -> paused++
            }
        }

        val filtered = updatedDownloads.filter { item ->
            val matchesFilter = when (filter) {
                DownloadFilter.ALL -> true
                DownloadFilter.ACTIVE -> item.status in listOf(
                    DownloadStatus.DOWNLOADING,
                    DownloadStatus.CONNECTING,
                    DownloadStatus.QUEUED
                )
                DownloadFilter.COMPLETED -> item.status == DownloadStatus.COMPLETED
                DownloadFilter.PAUSED -> item.status in listOf(
                    DownloadStatus.PAUSED,
                    DownloadStatus.FAILED,
                    DownloadStatus.CANCELLED
                )
            }

            val matchesSearch = if (search.isBlank()) {
                true
            } else {
                item.fileName.contains(search, ignoreCase = true) ||
                        item.url.contains(search, ignoreCase = true)
            }

            matchesFilter && matchesSearch
        }

        DownloadUiState(
            downloads = updatedDownloads,
            filteredDownloads = filtered,
            filter = filter,
            searchQuery = search,
            totalSpeedBytes = totalSpeed,
            activeCount = active,
            completedCount = completed,
            pausedCount = paused,
            freeStorageText = calculateFreeStorage(),
            showAddDialog = showAdd,
            incomingUrl = incoming,
            engineMode = currentEngineMode,
            showSampleSheet = showSamples,
            showSettingsDialog = showSettings,
            selectedDownloadDetails = details
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DownloadUiState()
    )

    fun handleIncomingUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            _incomingUrl.value = trimmed
            _showAddDialog.value = true
        }
    }

    fun clearIncomingUrl() {
        _incomingUrl.value = null
    }

    fun setFilter(filter: DownloadFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setShowAddDialog(show: Boolean) {
        _showAddDialog.value = show
    }

    fun setShowSampleSheet(show: Boolean) {
        _showSampleSheet.value = show
    }

    fun setShowSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun setSelectedDetails(download: DownloadEntity?) {
        _selectedDetails.value = download
    }

    fun setSmartEngineMode(mode: com.example.engine.SmartEngineMode) {
        repository.setEngineMode(mode)
        viewModelScope.launch {
            _snackbarEvent.emit("Mühərrik rejimi dəyişdirildi: ${mode.title}")
        }
    }

    fun startDownload(url: String, customName: String?, category: DownloadCategory? = null) {
        if (url.isBlank()) return
        viewModelScope.launch {
            try {
                repository.startNewDownload(url.trim(), customName?.takeIf { it.isNotBlank() }, category)
                _snackbarEvent.emit("Yükləmə başladıldı")
                _showAddDialog.value = false
            } catch (e: Exception) {
                _snackbarEvent.emit("Xəta: ${e.localizedMessage}")
            }
        }
    }

    fun startSampleDownload(sample: SampleDownloadItem) {
        viewModelScope.launch {
            try {
                repository.startNewDownload(sample.url, sample.fileName, sample.category)
                _snackbarEvent.emit("${sample.title} yükləməyə əlavə edildi")
                _showSampleSheet.value = false
            } catch (e: Exception) {
                _snackbarEvent.emit("Xəta: ${e.localizedMessage}")
            }
        }
    }

    fun pauseDownload(id: Long) {
        repository.pauseDownload(id)
    }

    fun resumeDownload(id: Long) {
        repository.resumeDownload(id)
    }

    fun cancelActiveDownload(id: Long) {
        repository.cancelActiveDownload(id)
        viewModelScope.launch {
            _snackbarEvent.emit("Yükləmə ləğv edildi")
        }
    }

    fun moveToPublicStorage(id: Long) {
        repository.moveToPublicStorage(id) { success ->
            viewModelScope.launch {
                _snackbarEvent.emit(
                    if (success) "Fayl Downloads/TurboLoad qovluğuna köçürüldü"
                    else "Köçürmə uğursuz oldu - 'Bütün fayllara giriş' icazəsini yoxlayın"
                )
            }
        }
    }

    fun retryDownload(id: Long) {
        repository.retryDownload(id)
    }

    fun deleteDownload(id: Long, deleteFile: Boolean = true) {
        repository.cancelOrDelete(id, deleteFile)
        viewModelScope.launch {
            _snackbarEvent.emit("Yükləmə silindi")
        }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            repository.deleteAllCompleted()
            _snackbarEvent.emit("Tamamlanmış yükləmələr siyahıdan təmizləndi")
        }
    }

    private fun calculateFreeStorage(): String {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val availableBlocks = stat.availableBlocksLong
            val blockSize = stat.blockSizeLong
            FormatUtils.formatBytes(availableBlocks * blockSize)
        } catch (e: Exception) {
            "--"
        }
    }
}
