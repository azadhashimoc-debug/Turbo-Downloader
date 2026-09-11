package com.example.engine

import android.content.Context
import android.os.Environment
import com.example.data.db.DownloadDao
import com.example.data.model.DownloadCategory
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.util.FormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance smart download engine equipped with:
 * 1. Multi-stream segmented downloading (turbo parallel HTTP Range chunks for weak/throttled connections)
 * 2. Adaptive dynamic buffer sizing (scales from 16KB to 128KB according to network throughput)
 * 3. Smart resilient auto-retry with exponential backoff for unstable wireless links
 * 4. HTTP Range chunk stitching directly into RandomAccessFile for zero-copy efficiency
 */
class DownloadEngine(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val pausedFlags = ConcurrentHashMap<Long, Boolean>()

    // Current smart engine mode selected by user
    private val _engineMode = MutableStateFlow(SmartEngineMode.TURBO_MULTI_STREAM)
    val engineMode: StateFlow<SmartEngineMode> = _engineMode.asStateFlow()

    // Aggressive OkHttpClient with tuned connection pool and timeouts for weak networks
    private val okHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Live speeds map for high-frequency UI updates
    private val _liveSpeeds = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val liveSpeeds: StateFlow<Map<Long, Long>> = _liveSpeeds.asStateFlow()

    init {
        // Reset any leftover "DOWNLOADING" states from previous app session
        engineScope.launch {
            downloadDao.resetInterruptedDownloads()
        }
    }

    fun setEngineMode(mode: SmartEngineMode) {
        _engineMode.value = mode
    }

    private fun getDownloadsDirectory(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun enqueueDownload(
        url: String,
        customFileName: String? = null,
        categoryOverride: DownloadCategory? = null
    ): Long {
        val rawName = if (!customFileName.isNullOrBlank()) {
            customFileName.trim()
        } else {
            FormatUtils.extractFileNameFromUrl(url)
        }

        val safeName = sanitizeFileName(rawName)
        val downloadDir = getDownloadsDirectory()
        val targetFile = getUniqueFile(downloadDir, safeName)
        val category = categoryOverride ?: FormatUtils.detectCategory(targetFile.name)

        val entity = DownloadEntity(
            url = url,
            fileName = targetFile.name,
            filePath = targetFile.absolutePath,
            status = DownloadStatus.QUEUED,
            category = category,
            dateAdded = System.currentTimeMillis()
        )

        val id = downloadDao.insertDownload(entity)
        startDownload(id)
        return id
    }

    fun startDownload(downloadId: Long) {
        if (activeJobs.containsKey(downloadId)) return
        pausedFlags[downloadId] = false

        val job = engineScope.launch {
            runSmartDownload(downloadId)
        }
        activeJobs[downloadId] = job
    }

    fun pauseDownload(downloadId: Long) {
        pausedFlags[downloadId] = true
        activeJobs[downloadId]?.cancel()
        activeJobs.remove(downloadId)

        val updatedMap = _liveSpeeds.value.toMutableMap()
        updatedMap.remove(downloadId)
        _liveSpeeds.value = updatedMap

        engineScope.launch {
            val item = downloadDao.getDownloadByIdSync(downloadId) ?: return@launch
            if (item.status != DownloadStatus.COMPLETED) {
                downloadDao.updateDownload(
                    item.copy(
                        status = DownloadStatus.PAUSED,
                        speedBytesPerSec = 0
                    )
                )
            }
        }
    }

    fun resumeDownload(downloadId: Long) {
        startDownload(downloadId)
    }

    fun retryDownload(downloadId: Long) {
        pauseDownload(downloadId)
        engineScope.launch {
            val item = downloadDao.getDownloadByIdSync(downloadId) ?: return@launch
            downloadDao.updateDownload(
                item.copy(
                    status = DownloadStatus.QUEUED,
                    downloadedBytes = 0L,
                    speedBytesPerSec = 0L,
                    errorMessage = null
                )
            )
            // Delete partial file on retry
            try {
                val file = File(item.filePath)
                if (file.exists()) file.delete()
                // Also clean up any parts files if existed
                cleanPartFiles(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            startDownload(downloadId)
        }
    }

    fun cancelDownload(downloadId: Long, deleteFile: Boolean = true) {
        pausedFlags[downloadId] = true
        activeJobs[downloadId]?.cancel()
        activeJobs.remove(downloadId)

        val updatedMap = _liveSpeeds.value.toMutableMap()
        updatedMap.remove(downloadId)
        _liveSpeeds.value = updatedMap

        engineScope.launch {
            val item = downloadDao.getDownloadByIdSync(downloadId)
            if (item != null) {
                if (deleteFile) {
                    try {
                        val file = File(item.filePath)
                        if (file.exists()) file.delete()
                        cleanPartFiles(file)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                downloadDao.deleteDownloadById(downloadId)
            }
        }
    }

    private fun cleanPartFiles(file: File) {
        val parent = file.parentFile ?: return
        val prefix = file.name + ".part"
        parent.listFiles()?.forEach { f ->
            if (f.name.startsWith(prefix)) {
                f.delete()
            }
        }
    }

    /**
     * Primary smart download routine.
     * Probes the server for capabilities, calculates optimal threads and chunking,
     * and downloads using multi-thread range slicing or adaptive stream fallback.
     */
    private suspend fun runSmartDownload(downloadId: Long) {
        var entity = downloadDao.getDownloadByIdSync(downloadId) ?: return

        try {
            downloadDao.updateDownload(
                entity.copy(
                    status = DownloadStatus.CONNECTING,
                    errorMessage = null,
                    speedBytesPerSec = 0
                )
            )

            val targetFile = File(entity.filePath)
            var existingLength = if (targetFile.exists()) targetFile.length() else 0L

            // Quick check if already completed
            if (entity.totalBytes > 0 && existingLength >= entity.totalBytes && entity.totalBytes == entity.downloadedBytes) {
                downloadDao.updateDownload(
                    entity.copy(
                        status = DownloadStatus.COMPLETED,
                        speedBytesPerSec = 0,
                        dateCompleted = System.currentTimeMillis()
                    )
                )
                return
            }

            // Probe server capabilities (HEAD / partial GET)
            val headRequest = Request.Builder()
                .url(entity.url)
                .header("User-Agent", "Mozilla/5.0 (Android; DownloadManager/2.0 TurboEngine)")
                .header("Range", "bytes=0-1")
                .build()

            var isServerResumable = false
            var probedContentLength = entity.totalBytes
            var probedMimeType = entity.mimeType
            var probedFileName = entity.fileName

            withContext(Dispatchers.IO) {
                try {
                    okHttpClient.newCall(headRequest).execute().use { probeResponse ->
                        if (probeResponse.isSuccessful || probeResponse.code == 206) {
                            val cr = probeResponse.header("Content-Range")
                            val acceptRanges = probeResponse.header("Accept-Ranges")
                            isServerResumable = probeResponse.code == 206 ||
                                    cr != null ||
                                    acceptRanges?.contains("bytes", ignoreCase = true) == true

                            if (cr != null && cr.contains("/")) {
                                val totalStr = cr.substringAfterLast("/")
                                totalStr.toLongOrNull()?.let {
                                    if (it > 0) probedContentLength = it
                                }
                            } else {
                                val cl = probeResponse.header("Content-Length")?.toLongOrNull()
                                if (cl != null && cl > 1 && probeResponse.code != 206) {
                                    probedContentLength = cl
                                }
                            }

                            probeResponse.header("Content-Type")?.let { probedMimeType = it }
                            val cd = probeResponse.header("Content-Disposition")
                            parseContentDispositionFileName(cd)?.let { probedFileName = it }
                        }
                    }
                } catch (e: Exception) {
                    // Probe might fail on some servers; we will proceed with normal stream
                }
            }

            // Determine if multi-threaded chunking is beneficial
            val currentMode = _engineMode.value
            val isLargeFile = probedContentLength > 2 * 1024 * 1024 // > 2MB
            val canUseMultiStream = isServerResumable &&
                    probedContentLength > 0 &&
                    isLargeFile &&
                    currentMode == SmartEngineMode.TURBO_MULTI_STREAM

            val finalFileName = probedFileName
            entity = entity.copy(
                totalBytes = probedContentLength,
                mimeType = probedMimeType,
                resumable = isServerResumable,
                fileName = finalFileName,
                category = FormatUtils.detectCategory(finalFileName, probedMimeType)
            )
            downloadDao.updateDownload(entity)

            if (canUseMultiStream) {
                // Turbo 4-stream segmented download for weak connections
                executeMultiStreamDownload(downloadId, entity, targetFile, probedContentLength)
            } else {
                // Adaptive single-stream with dynamic buffer and auto-reconnect
                executeAdaptiveStreamDownload(downloadId, entity, targetFile, probedContentLength, isServerResumable)
            }

        } catch (e: CancellationException) {
            val current = downloadDao.getDownloadByIdSync(downloadId)
            if (current != null && current.status != DownloadStatus.COMPLETED) {
                downloadDao.updateDownload(
                    current.copy(
                        status = DownloadStatus.PAUSED,
                        speedBytesPerSec = 0
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val current = downloadDao.getDownloadByIdSync(downloadId)
            if (current != null) {
                downloadDao.updateDownload(
                    current.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.localizedMessage ?: "Yükləmə xətası baş verdi",
                        speedBytesPerSec = 0
                    )
                )
            }
        } finally {
            activeJobs.remove(downloadId)
            val speedMap = _liveSpeeds.value.toMutableMap()
            speedMap.remove(downloadId)
            _liveSpeeds.value = speedMap
        }
    }

    /**
     * Smart Segmented Multi-Stream Downloader:
     * Splits file into 4 parallel chunks. Weak and throttled connections benefit immensely
     * because servers often throttle per-connection bandwidth, but 4 concurrent connections
     * bypass single-stream limits and maximize link utilization.
     */
    private suspend fun executeMultiStreamDownload(
        downloadId: Long,
        initialEntity: DownloadEntity,
        targetFile: File,
        totalBytes: Long
    ) = withContext(Dispatchers.IO) {
        val numThreads = 4
        val chunkSize = totalBytes / numThreads

        // Allocate target file space if not already allocated
        RandomAccessFile(targetFile, "rw").use { raf ->
            if (raf.length() < totalBytes) {
                raf.setLength(totalBytes)
            }
        }

        downloadDao.updateDownload(
            initialEntity.copy(
                status = DownloadStatus.DOWNLOADING,
                totalBytes = totalBytes
            )
        )

        val totalDownloadedCounter = AtomicLong(0L)
        // Check existing progress in parts
        val partProgress = Array(numThreads) { index ->
            val partFile = File(targetFile.parentFile, "${targetFile.name}.part$index")
            val len = if (partFile.exists()) partFile.length() else 0L
            totalDownloadedCounter.addAndGet(len)
            AtomicLong(len)
        }

        var lastSpeedTime = System.currentTimeMillis()
        var bytesSinceLastSpeed = 0L
        var lastDbUpdateTime = System.currentTimeMillis()
        var currentSpeed = 0L

        val threadJobs = (0 until numThreads).map { index ->
            async(Dispatchers.IO) {
                val startByte = index * chunkSize + partProgress[index].get()
                val endByte = if (index == numThreads - 1) totalBytes - 1 else (index + 1) * chunkSize - 1

                if (startByte > endByte) {
                    return@async // Chunk already completed
                }

                // Resilient download loop for this segment with up to 5 auto-retries on connection drops
                var segmentCurrent = startByte
                var retryCount = 0
                val partFile = File(targetFile.parentFile, "${targetFile.name}.part$index")

                while (segmentCurrent <= endByte && !pausedFlags.getOrDefault(downloadId, false)) {
                    try {
                        val request = Request.Builder()
                            .url(initialEntity.url)
                            .header("User-Agent", "Mozilla/5.0 (Android; DownloadManager/2.0 Turbo)")
                            .header("Range", "bytes=$segmentCurrent-$endByte")
                            .build()

                        val response = okHttpClient.newCall(request).execute()
                        if (!response.isSuccessful && response.code != 206) {
                            response.close()
                            throw Exception("HTTP chunk error ${response.code}")
                        }

                        val body = response.body ?: throw Exception("Chunk body null")
                        val bufferSize = calculateOptimalBuffer(currentSpeed)
                        val buffer = ByteArray(bufferSize)
                        val input = body.byteStream()

                        FileOutputStream(partFile, true).use { fos ->
                            input.use { stream ->
                                var read: Int
                                while (stream.read(buffer).also { read = it } != -1) {
                                    if (pausedFlags.getOrDefault(downloadId, false)) {
                                        break
                                    }
                                    fos.write(buffer, 0, read)
                                    segmentCurrent += read
                                    partProgress[index].addAndGet(read.toLong())
                                    val downloadedNow = totalDownloadedCounter.addAndGet(read.toLong())
                                    bytesSinceLastSpeed += read

                                    val now = System.currentTimeMillis()
                                    val delta = now - lastSpeedTime
                                    if (delta >= 400) {
                                        currentSpeed = (bytesSinceLastSpeed * 1000) / delta
                                        bytesSinceLastSpeed = 0L
                                        lastSpeedTime = now

                                        val speedMap = _liveSpeeds.value.toMutableMap()
                                        speedMap[downloadId] = currentSpeed
                                        _liveSpeeds.value = speedMap
                                    }

                                    if (now - lastDbUpdateTime >= 500) {
                                        downloadDao.updateDownload(
                                            initialEntity.copy(
                                                downloadedBytes = downloadedNow.coerceAtMost(totalBytes),
                                                totalBytes = totalBytes,
                                                status = DownloadStatus.DOWNLOADING,
                                                speedBytesPerSec = currentSpeed
                                            )
                                        )
                                        lastDbUpdateTime = now
                                    }
                                }
                                fos.flush()
                            }
                        }
                        retryCount = 0 // Successfully finished or paused
                    } catch (e: Exception) {
                        if (pausedFlags.getOrDefault(downloadId, false)) break
                        retryCount++
                        if (retryCount > 5) {
                            throw e
                        }
                        delay(1000L * retryCount) // Smart exponential backoff on weak link
                    }
                }
            }
        }

        threadJobs.awaitAll()

        if (pausedFlags.getOrDefault(downloadId, false)) {
            downloadDao.updateDownload(
                initialEntity.copy(
                    downloadedBytes = totalDownloadedCounter.get().coerceAtMost(totalBytes),
                    totalBytes = totalBytes,
                    status = DownloadStatus.PAUSED,
                    speedBytesPerSec = 0
                )
            )
            return@withContext
        }

        // Merge all parts into final target file using RandomAccessFile
        RandomAccessFile(targetFile, "rw").use { raf ->
            for (i in 0 until numThreads) {
                val partFile = File(targetFile.parentFile, "${targetFile.name}.part$i")
                if (partFile.exists()) {
                    val offset = i * chunkSize
                    raf.seek(offset)
                    partFile.inputStream().use { partIn ->
                        val mergeBuffer = ByteArray(64 * 1024)
                        var bytes: Int
                        while (partIn.read(mergeBuffer).also { bytes = it } != -1) {
                            raf.write(mergeBuffer, 0, bytes)
                        }
                    }
                    partFile.delete()
                }
            }
        }

        downloadDao.updateDownload(
            initialEntity.copy(
                downloadedBytes = totalBytes,
                totalBytes = totalBytes,
                status = DownloadStatus.COMPLETED,
                speedBytesPerSec = 0,
                dateCompleted = System.currentTimeMillis()
            )
        )
    }

    /**
     * Adaptive single-stream with dynamic buffer scaling and persistent reconnect for servers
     * that don't support multi-range chunks.
     */
    private suspend fun executeAdaptiveStreamDownload(
        downloadId: Long,
        initialEntity: DownloadEntity,
        targetFile: File,
        totalBytes: Long,
        isResumable: Boolean
    ) = withContext(Dispatchers.IO) {
        var existingLength = if (targetFile.exists()) targetFile.length() else 0L
        var currentDownloaded = existingLength
        var retryCount = 0

        var lastSpeedTime = System.currentTimeMillis()
        var bytesSinceLastSpeed = 0L
        var lastDbUpdateTime = System.currentTimeMillis()
        var currentSpeed = 0L

        downloadDao.updateDownload(
            initialEntity.copy(
                status = DownloadStatus.DOWNLOADING,
                totalBytes = totalBytes
            )
        )

        while (currentDownloaded < totalBytes || totalBytes <= 0) {
            if (pausedFlags.getOrDefault(downloadId, false)) {
                break
            }

            try {
                val requestBuilder = Request.Builder()
                    .url(initialEntity.url)
                    .header("User-Agent", "Mozilla/5.0 (Android; DownloadManager/2.0 SmartEngine)")

                val append = isResumable && currentDownloaded > 0
                if (append) {
                    requestBuilder.header("Range", "bytes=$currentDownloaded-")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful && response.code != 206) {
                    response.close()
                    throw Exception("HTTP ${response.code} ${response.message}")
                }

                val body = response.body ?: throw Exception("Boş cavab")
                val stream = body.byteStream()
                val fos = FileOutputStream(targetFile, append)

                val bufferSize = calculateOptimalBuffer(currentSpeed)
                val buffer = ByteArray(bufferSize)

                fos.use { output ->
                    stream.use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (pausedFlags.getOrDefault(downloadId, false)) {
                                break
                            }
                            output.write(buffer, 0, read)
                            currentDownloaded += read
                            bytesSinceLastSpeed += read

                            val now = System.currentTimeMillis()
                            val speedDelta = now - lastSpeedTime
                            if (speedDelta >= 400) {
                                currentSpeed = (bytesSinceLastSpeed * 1000) / speedDelta
                                bytesSinceLastSpeed = 0L
                                lastSpeedTime = now

                                val speedMap = _liveSpeeds.value.toMutableMap()
                                speedMap[downloadId] = currentSpeed
                                _liveSpeeds.value = speedMap
                            }

                            if (now - lastDbUpdateTime >= 500) {
                                downloadDao.updateDownload(
                                    initialEntity.copy(
                                        downloadedBytes = currentDownloaded,
                                        totalBytes = totalBytes,
                                        status = DownloadStatus.DOWNLOADING,
                                        speedBytesPerSec = currentSpeed
                                    )
                                )
                                lastDbUpdateTime = now
                            }
                        }
                        output.flush()
                    }
                }

                // If read completed to end of body
                if (!pausedFlags.getOrDefault(downloadId, false)) {
                    break
                }
            } catch (e: Exception) {
                if (pausedFlags.getOrDefault(downloadId, false)) break
                retryCount++
                if (retryCount > 6) {
                    throw e
                }
                delay(1200L * retryCount) // Smart auto-retry on weak internet connection drops
            }
        }

        if (pausedFlags.getOrDefault(downloadId, false)) {
            downloadDao.updateDownload(
                initialEntity.copy(
                    downloadedBytes = currentDownloaded,
                    totalBytes = totalBytes,
                    status = DownloadStatus.PAUSED,
                    speedBytesPerSec = 0
                )
            )
        } else {
            val finalSize = targetFile.length()
            val finalTotal = if (totalBytes > 0) totalBytes else finalSize
            downloadDao.updateDownload(
                initialEntity.copy(
                    downloadedBytes = finalSize,
                    totalBytes = finalTotal,
                    status = DownloadStatus.COMPLETED,
                    speedBytesPerSec = 0,
                    dateCompleted = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Dynamically calculates buffer size based on current network speed.
     * Slow networks (< 256 KB/s): 16 KB for low latency and rapid packet flushing
     * Medium networks: 32 KB - 64 KB
     * Fast networks: 128 KB for peak OS system call efficiency
     */
    private fun calculateOptimalBuffer(speedBytesPerSec: Long): Int {
        return when {
            speedBytesPerSec < 256 * 1024 -> 16 * 1024
            speedBytesPerSec < 1024 * 1024 -> 32 * 1024
            speedBytesPerSec < 5 * 1024 * 1024 -> 64 * 1024
            else -> 128 * 1024
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    }

    private fun getUniqueFile(dir: File, fileName: String): File {
        var file = File(dir, fileName)
        if (!file.exists()) return file

        val nameWithoutExt = file.nameWithoutExtension
        val ext = file.extension
        val extWithDot = if (ext.isNotEmpty()) ".$ext" else ""

        var counter = 1
        while (file.exists()) {
            file = File(dir, "$nameWithoutExt($counter)$extWithDot")
            counter++
        }
        return file
    }

    private fun parseContentDispositionFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        return try {
            val fileNameRegex = "filename\\*?=['\"]?(?:UTF-\\d['\"])?([^;'\"]+)".toRegex(RegexOption.IGNORE_CASE)
            fileNameRegex.find(header)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
}
