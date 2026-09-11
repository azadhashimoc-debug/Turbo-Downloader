package com.example.engine

import android.content.Context
import android.os.Environment
import com.example.data.db.DownloadDao
import com.example.data.model.DownloadCategory
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.service.DownloadKeepAliveService
import com.example.util.FormatUtils
import com.example.util.NotificationHelper
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
        NotificationHelper.ensureChannels(context)
        // Reset any leftover "DOWNLOADING"/"CONNECTING" states from a previous app session
        // that was killed mid-download - otherwise those items would stay stuck showing a
        // spinner forever, with no coroutine left running to ever finish them.
        engineScope.launch {
            downloadDao.resetInterruptedDownloads(
                oldStatus = DownloadStatus.DOWNLOADING,
                newStatus = DownloadStatus.PAUSED
            )
            downloadDao.resetInterruptedDownloads(
                oldStatus = DownloadStatus.CONNECTING,
                newStatus = DownloadStatus.PAUSED
            )
        }
    }

    /**
     * Keeps the persistent "downloads in progress" notification (and the foreground
     * service backing it) in sync with how many jobs are actually running, so the
     * process is protected from being killed while a download is active.
     */
    private fun refreshKeepAliveState() {
        val activeCount = activeJobs.size
        val totalSpeed = _liveSpeeds.value.values.sum()
        DownloadKeepAliveService.updateState(context, activeCount, totalSpeed)
    }

    fun setEngineMode(mode: SmartEngineMode) {
        _engineMode.value = mode
    }

    /**
     * When "All files access" has been granted, downloads go into the real, publicly
     * browsable Downloads folder (visible to any file manager or archive app). Without it,
     * fall back to this app's own sandboxed external-files dir, which - since Android 11 -
     * no other app (zArchiver included) can browse into at all, even though the OS still
     * lets us read/write it ourselves.
     */
    private fun getDownloadsDirectory(): File {
        val dir = if (hasAllFilesAccess()) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TurboLoad")
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "Downloads")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun hasAllFilesAccess(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
                Environment.isExternalStorageManager()
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
        refreshKeepAliveState()
    }

    fun pauseDownload(downloadId: Long) {
        pausedFlags[downloadId] = true
        activeJobs[downloadId]?.cancel()
        activeJobs.remove(downloadId)

        val updatedMap = _liveSpeeds.value.toMutableMap()
        updatedMap.remove(downloadId)
        _liveSpeeds.value = updatedMap
        refreshKeepAliveState()

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

    /**
     * Cancels an in-progress download but - unlike [cancelDownload] - keeps its row so it
     * shows up as CANCELLED (retryable from the list), instead of vanishing entirely. This
     * is what the UI's "cancel" (X) action on an active download actually calls; the trash
     * icon on a finished/failed/cancelled item still uses [cancelDownload] to remove it
     * for good.
     */
    fun cancelActiveDownload(downloadId: Long) {
        pausedFlags[downloadId] = true
        activeJobs[downloadId]?.cancel()
        activeJobs.remove(downloadId)

        val updatedMap = _liveSpeeds.value.toMutableMap()
        updatedMap.remove(downloadId)
        _liveSpeeds.value = updatedMap
        refreshKeepAliveState()

        engineScope.launch {
            val item = downloadDao.getDownloadByIdSync(downloadId) ?: return@launch
            if (item.status == DownloadStatus.COMPLETED) return@launch

            try {
                val file = File(item.filePath)
                if (file.exists()) file.delete()
                cleanPartFiles(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            downloadDao.updateDownload(
                item.copy(
                    status = DownloadStatus.CANCELLED,
                    downloadedBytes = 0L,
                    speedBytesPerSec = 0
                )
            )
        }
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
        refreshKeepAliveState()

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

    /**
     * Each [SmartEngineMode] genuinely changes how the download runs, not just its label -
     * see [SmartEngineMode.streamCount] for the canonical per-mode stream count.
     */
    private fun numThreadsForMode(mode: SmartEngineMode): Int = mode.streamCount

    /** Removes the small per-segment resume-progress marker files (see [executeMultiStreamDownload]). */
    private fun cleanPartFiles(file: File) {
        val parent = file.parentFile ?: return
        val prefix = file.name + ".progress"
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

            // Determine if multi-threaded chunking is beneficial, and how many streams
            // this mode actually uses - each mode genuinely behaves differently now.
            val currentMode = _engineMode.value
            val numThreads = numThreadsForMode(currentMode)
            val isLargeFile = probedContentLength > 2 * 1024 * 1024 // > 2MB
            val canUseMultiStream = isServerResumable &&
                    probedContentLength > 0 &&
                    isLargeFile &&
                    numThreads > 1

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
                // Segmented download across `numThreads` parallel HTTP Range streams
                executeMultiStreamDownload(downloadId, entity, targetFile, probedContentLength, numThreads, currentMode)
            } else {
                // Adaptive single-stream with dynamic buffer and auto-reconnect
                executeAdaptiveStreamDownload(downloadId, entity, targetFile, probedContentLength, isServerResumable, currentMode)
            }

            val finished = downloadDao.getDownloadByIdSync(downloadId)
            if (finished != null && finished.status == DownloadStatus.COMPLETED) {
                NotificationHelper.notifyCompleted(context, finished)
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
                val failed = current.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.localizedMessage ?: "Yükləmə xətası baş verdi",
                    speedBytesPerSec = 0
                )
                downloadDao.updateDownload(failed)
                NotificationHelper.notifyFailed(context, failed)
            }
        } finally {
            activeJobs.remove(downloadId)
            val speedMap = _liveSpeeds.value.toMutableMap()
            speedMap.remove(downloadId)
            _liveSpeeds.value = speedMap
            refreshKeepAliveState()
        }
    }

    /**
     * Smart Segmented Multi-Stream Downloader:
     * Splits the file into [numThreads] parallel chunks. Weak and throttled connections
     * benefit immensely because servers often throttle per-connection bandwidth, but several
     * concurrent connections bypass single-stream limits and maximize link utilization.
     */
    private suspend fun executeMultiStreamDownload(
        downloadId: Long,
        initialEntity: DownloadEntity,
        targetFile: File,
        totalBytes: Long,
        numThreads: Int,
        mode: SmartEngineMode
    ) = withContext(Dispatchers.IO) {
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
        // Each segment's progress is tracked in a tiny marker file (just an 8-byte long),
        // not a full copy of the downloaded bytes - the segment writes straight into
        // targetFile at its own offset, so there is nothing left to merge afterwards.
        val partProgress = Array(numThreads) { index ->
            val len = readSegmentProgress(targetFile, index)
            totalDownloadedCounter.addAndGet(len)
            AtomicLong(len)
        }

        var lastSpeedTime = System.currentTimeMillis()
        var bytesSinceLastSpeed = 0L
        var lastDbUpdateTime = System.currentTimeMillis()
        var currentSpeed = 0L

        val threadJobs = (0 until numThreads).map { index ->
            async(Dispatchers.IO) {
                val chunkStart = index * chunkSize
                val startByte = chunkStart + partProgress[index].get()
                val endByte = if (index == numThreads - 1) totalBytes - 1 else (index + 1) * chunkSize - 1

                if (startByte > endByte) {
                    return@async // Chunk already completed
                }

                // Resilient download loop for this segment with up to 5 auto-retries on connection drops
                var segmentCurrent = startByte
                var retryCount = 0

                // One RandomAccessFile per thread, seeked to this segment's offset - each
                // thread only ever touches its own byte range, so concurrent writes are safe.
                RandomAccessFile(targetFile, "rw").use { raf ->
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
                            val bufferSize = calculateOptimalBuffer(currentSpeed, mode)
                            val buffer = ByteArray(bufferSize)

                            raf.seek(segmentCurrent)
                            body.byteStream().use { stream ->
                                var read: Int
                                while (stream.read(buffer).also { read = it } != -1) {
                                    if (pausedFlags.getOrDefault(downloadId, false)) {
                                        break
                                    }
                                    raf.write(buffer, 0, read)
                                    segmentCurrent += read
                                    val segmentDone = partProgress[index].addAndGet(read.toLong())
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
                                        writeSegmentProgress(targetFile, index, segmentDone)
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
                            }
                            writeSegmentProgress(targetFile, index, partProgress[index].get())
                            retryCount = 0 // Successfully finished or paused
                        } catch (e: Exception) {
                            if (pausedFlags.getOrDefault(downloadId, false)) break
                            retryCount++
                            if (retryCount > maxRetriesForMode(mode)) {
                                throw e
                            }
                            delay(1000L * retryCount) // Smart exponential backoff on weak link
                        }
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

        // Nothing to merge - every segment already wrote directly into targetFile.
        cleanPartFiles(targetFile)

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

    private fun segmentProgressFile(targetFile: File, index: Int): File =
        File(targetFile.parentFile, "${targetFile.name}.progress$index")

    private fun readSegmentProgress(targetFile: File, index: Int): Long {
        val file = segmentProgressFile(targetFile, index)
        if (!file.exists()) return 0L
        return try {
            RandomAccessFile(file, "r").use { it.readLong() }
        } catch (e: Exception) {
            0L
        }
    }

    private fun writeSegmentProgress(targetFile: File, index: Int, bytesDone: Long) {
        try {
            RandomAccessFile(segmentProgressFile(targetFile, index), "rw").use { it.writeLong(bytesDone) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        isResumable: Boolean,
        mode: SmartEngineMode
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

                val bufferSize = calculateOptimalBuffer(currentSpeed, mode)
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
                if (retryCount > maxRetriesForMode(mode)) {
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
     *
     * LOW_LATENCY_ECO caps the buffer at 32 KB regardless of measured speed - smaller
     * writes flush to disk more often, so a connection that drops mid-buffer loses less
     * progress, matching what that mode promises ("small buffers against interruption").
     */
    private fun calculateOptimalBuffer(speedBytesPerSec: Long, mode: SmartEngineMode): Int {
        val uncapped = when {
            speedBytesPerSec < 256 * 1024 -> 16 * 1024
            speedBytesPerSec < 1024 * 1024 -> 32 * 1024
            speedBytesPerSec < 5 * 1024 * 1024 -> 64 * 1024
            else -> 128 * 1024
        }
        val cap = if (mode == SmartEngineMode.LOW_LATENCY_ECO) 32 * 1024 else Int.MAX_VALUE
        return uncapped.coerceAtMost(cap)
    }

    /**
     * LOW_LATENCY_ECO tolerates more consecutive drops before giving up, matching its
     * "resilient against a flaky connection" description; the other modes fail a bit
     * faster since they assume a more usable link.
     */
    private fun maxRetriesForMode(mode: SmartEngineMode): Int = when (mode) {
        SmartEngineMode.LOW_LATENCY_ECO -> 10
        else -> 6
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
