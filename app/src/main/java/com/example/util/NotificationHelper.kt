package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.model.DownloadEntity

/**
 * Centralizes notification-channel setup and the notifications shown for the
 * download lifecycle (ongoing progress, completed, failed).
 */
object NotificationHelper {

    const val PROGRESS_CHANNEL_ID = "turbo_downloads_progress"
    const val EVENTS_CHANNEL_ID = "turbo_downloads_events"

    const val PROGRESS_NOTIFICATION_ID = 1001
    private const val EVENT_NOTIFICATION_ID_BASE = 2000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val progressChannel = NotificationChannel(
            PROGRESS_CHANNEL_ID,
            "Yükləmə fəaliyyəti",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Davam edən yükləmələr üçün fasiləsiz bildiriş"
            setShowBadge(false)
        }

        val eventsChannel = NotificationChannel(
            EVENTS_CHANNEL_ID,
            "Yükləmə nəticələri",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Tamamlanan və uğursuz olan yükləmələr haqqında bildirişlər"
        }

        manager.createNotificationChannel(progressChannel)
        manager.createNotificationChannel(eventsChannel)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /** Builds the persistent low-priority notification shown while any download is active. */
    fun buildProgressNotification(context: Context, activeCount: Int, totalSpeedBytes: Long): android.app.Notification {
        val speedText = if (totalSpeedBytes > 0) {
            "${FormatUtils.formatBytes(totalSpeedBytes)}/s"
        } else {
            "hesablanır..."
        }
        val title = if (activeCount == 1) "1 yükləmə davam edir" else "$activeCount yükləmə davam edir"

        return NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Ümumi sürət: $speedText")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifyCompleted(context: Context, entity: DownloadEntity) {
        if (!hasPostPermission(context)) return
        val notification = NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Yükləmə tamamlandı")
            .setContentText(entity.fileName)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context)
            .notify(EVENT_NOTIFICATION_ID_BASE + entity.id.toInt(), notification)
    }

    fun notifyFailed(context: Context, entity: DownloadEntity) {
        if (!hasPostPermission(context)) return
        val notification = NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Yükləmə uğursuz oldu")
            .setContentText(entity.fileName)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context)
            .notify(EVENT_NOTIFICATION_ID_BASE + entity.id.toInt(), notification)
    }

    private fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
