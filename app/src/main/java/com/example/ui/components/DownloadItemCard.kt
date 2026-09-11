package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadCategory
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanLight
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.QuantumViolet
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TurboAmber
import com.example.util.FormatUtils

@Composable
fun DownloadItemCard(
    download: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = download.progress,
        label = "download_progress"
    )

    val isActive = download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.CONNECTING
    val isCompleted = download.status == DownloadStatus.COMPLETED

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("download_card_${download.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianCard
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) ElectricCyan.copy(alpha = 0.45f)
            else if (isCompleted) NeonEmerald.copy(alpha = 0.25f)
            else ObsidianBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Kinetic Category Glyph, File Name, and Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon
                CategoryBadge(category = download.category)

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Size Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.fileName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = (-0.2).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (download.totalBytes > 0) {
                            "${FormatUtils.formatBytes(download.downloadedBytes)} / ${FormatUtils.formatBytes(download.totalBytes)}"
                        } else {
                            FormatUtils.formatBytes(download.downloadedBytes)
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Chip
                StatusBadge(status = download.status)
            }

            // Progress Bar with Kinetic Glow for active streams
            if (download.status != DownloadStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(14.dp))
                if (download.status == DownloadStatus.CONNECTING || (download.status == DownloadStatus.DOWNLOADING && download.totalBytes <= 0)) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ElectricCyan,
                        trackColor = Color(0xFF0D1626)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = when (download.status) {
                            DownloadStatus.PAUSED -> TurboAmber
                            DownloadStatus.FAILED -> CyberCrimson
                            else -> ElectricCyan
                        },
                        trackColor = Color(0xFF0D1626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-row: Progress stats (Speed, ETA, Percent) and Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left metrics: Speed / ETA / Percent or completion time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (download.status) {
                        DownloadStatus.DOWNLOADING -> {
                            if (download.speedBytesPerSec > 0) {
                                Surface(
                                    color = ElectricCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(0.6.dp, ElectricCyan.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "⚡ ${FormatUtils.formatSpeed(download.speedBytesPerSec)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = ElectricCyanLight,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (download.totalBytes > 0) {
                                Text(
                                    text = "${download.progressPercent}%",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                                val eta = FormatUtils.formatEta(download.downloadedBytes, download.totalBytes, download.speedBytesPerSec)
                                if (eta != "--") {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "⏱ $eta",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NeonEmerald)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tamamlandı • ${FormatUtils.formatBytes(download.downloadedBytes)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = NeonEmerald
                                )
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            Text(
                                text = "Fasilədə • ${download.progressPercent}%",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = TurboAmber
                            )
                        }
                        DownloadStatus.FAILED -> {
                            Text(
                                text = download.errorMessage ?: "Yükləmə uğursuz oldu",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberCrimson,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DownloadStatus.CONNECTING -> {
                            Text(
                                text = "Qoşulur...",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan
                            )
                        }
                        DownloadStatus.QUEUED -> {
                            Text(
                                text = "Növbədə",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        DownloadStatus.CANCELLED -> {
                            Text(
                                text = "Ləğv edildi",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Action Buttons Row (Ensuring minimumInteractiveComponentSize of 48dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (download.status) {
                        DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING, DownloadStatus.QUEUED -> {
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                                    .testTag("btn_pause_${download.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Fasilə et",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                                    .testTag("btn_cancel_${download.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Ləğv et",
                                    tint = CyberCrimson
                                )
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                                    .testTag("btn_resume_${download.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Davam etdir",
                                    tint = ElectricCyan
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                                    .testTag("btn_delete_${download.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Sil",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            IconButton(
                                onClick = onOpen,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                                    .testTag("btn_open_${download.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Launch,
                                    contentDescription = "Faylı aç",
                                    tint = ElectricCyan
                                )
                            }
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                                    .testTag("btn_share_${download.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Paylaş",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                                    .testTag("btn_delete_${download.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Sil",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }
                        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                                    .testTag("btn_retry_${download.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Yenidən cəhd et",
                                    tint = ElectricCyan
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                                    .testTag("btn_delete_${download.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Sil",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBadge(category: DownloadCategory) {
    val (icon: ImageVector, color: Color) = when (category) {
        DownloadCategory.VIDEO -> Icons.Default.Movie to QuantumViolet
        DownloadCategory.AUDIO -> Icons.Default.Audiotrack to ElectricCyan
        DownloadCategory.IMAGE -> Icons.Default.Image to Color(0xFF38BDF8)
        DownloadCategory.DOCUMENT -> Icons.Default.Description to TurboAmber
        DownloadCategory.ARCHIVE -> Icons.Default.FolderZip to Color(0xFFFFCC00)
        DownloadCategory.APK -> Icons.Default.Android to NeonEmerald
        DownloadCategory.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile to Color(0xFF94A3B8)
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(color.copy(alpha = 0.14f))
            .border(0.8.dp, color.copy(alpha = 0.35f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category.name,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun StatusBadge(status: DownloadStatus) {
    val (text, textColor, bgColor, borderColor) = when (status) {
        DownloadStatus.DOWNLOADING -> Quad("Yüklənir", ElectricCyan, ElectricCyan.copy(alpha = 0.15f), ElectricCyan.copy(alpha = 0.4f))
        DownloadStatus.CONNECTING -> Quad("Qoşulur", ElectricCyan, ElectricCyan.copy(alpha = 0.15f), ElectricCyan.copy(alpha = 0.4f))
        DownloadStatus.COMPLETED -> Quad("Hazırdır", NeonEmerald, NeonEmerald.copy(alpha = 0.15f), NeonEmerald.copy(alpha = 0.35f))
        DownloadStatus.PAUSED -> Quad("Fasilə", TurboAmber, TurboAmber.copy(alpha = 0.15f), TurboAmber.copy(alpha = 0.4f))
        DownloadStatus.FAILED -> Quad("Xəta", CyberCrimson, CyberCrimson.copy(alpha = 0.15f), CyberCrimson.copy(alpha = 0.4f))
        DownloadStatus.QUEUED -> Quad("Növbədə", Color(0xFF94A3B8), Color(0xFF94A3B8).copy(alpha = 0.12f), ObsidianBorder)
        DownloadStatus.CANCELLED -> Quad("Ləğv", Color(0xFF94A3B8), Color(0xFF94A3B8).copy(alpha = 0.12f), ObsidianBorder)
    }

    Surface(
        color = bgColor,
        border = BorderStroke(0.6.dp, borderColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
