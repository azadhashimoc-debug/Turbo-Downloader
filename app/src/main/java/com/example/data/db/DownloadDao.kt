package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY dateAdded DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY dateAdded DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getDownloadById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadByIdSync(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Delete
    suspend fun deleteDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun deleteAllCompleted()

    @Query("UPDATE downloads SET status = :newStatus, speedBytesPerSec = 0 WHERE status = :oldStatus")
    suspend fun resetInterruptedDownloads(oldStatus: DownloadStatus = DownloadStatus.DOWNLOADING, newStatus: DownloadStatus = DownloadStatus.PAUSED)
}
