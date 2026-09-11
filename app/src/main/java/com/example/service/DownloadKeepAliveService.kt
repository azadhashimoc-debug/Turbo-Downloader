package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.util.NotificationHelper

/**
 * A lightweight foreground service whose only job is to raise this process's priority
 * while at least one download is active, so the OS is far less likely to kill the app
 * (and with it, the in-flight download coroutines) while it is backgrounded.
 *
 * The actual download work still happens in [com.example.engine.DownloadEngine]; this
 * service just keeps the process alive and shows the persistent progress notification.
 */
class DownloadKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val activeCount = intent?.getIntExtra(EXTRA_ACTIVE_COUNT, 0) ?: 0
        val totalSpeed = intent?.getLongExtra(EXTRA_TOTAL_SPEED, 0L) ?: 0L

        NotificationHelper.ensureChannels(this)
        val notification = NotificationHelper.buildProgressNotification(this, activeCount.coerceAtLeast(0), totalSpeed)

        // The platform requires startForeground() to be called shortly after a service is
        // started via startForegroundService(), even for a service that is about to stop
        // itself right away - so always promote first, then tear down if there's nothing
        // left to track.
        ServiceCompat.startForeground(
            this,
            NotificationHelper.PROGRESS_NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (activeCount <= 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_ACTIVE_COUNT = "extra_active_count"
        private const val EXTRA_TOTAL_SPEED = "extra_total_speed"

        /** Call whenever the number of active downloads changes (including reaching zero). */
        fun updateState(context: Context, activeCount: Int, totalSpeedBytes: Long) {
            val intent = Intent(context, DownloadKeepAliveService::class.java)
                .putExtra(EXTRA_ACTIVE_COUNT, activeCount)
                .putExtra(EXTRA_TOTAL_SPEED, totalSpeedBytes)
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // App is in a state where it can't start a service (e.g. backgrounded on
                // Android 8+ without an active download yet) - safe to ignore, the service
                // is only meaningful once a download has already begun in the foreground.
                e.printStackTrace()
            }
        }
    }
}
