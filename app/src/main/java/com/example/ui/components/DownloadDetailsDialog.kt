package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadStatus
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TurboAmber
import com.example.util.FormatUtils

@Composable
fun DownloadDetailsDialog(
    download: DownloadEntity,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: (deleteFile: Boolean) -> Unit,
    canMoveToPublicStorage: Boolean = false,
    onMoveToPublicStorage: () -> Unit = {}
) {
    val context = LocalContext.current

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label kopyalandı", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(category = download.category)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = download.fileName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Fayl Parametrləri & Telemetriya",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                DetailRow(label = "Vəziyyət") {
                    StatusBadge(status = download.status)
                }

                DetailItem(
                    label = "Həcm",
                    value = if (download.totalBytes > 0) {
                        "${FormatUtils.formatBytes(download.downloadedBytes)} / ${FormatUtils.formatBytes(download.totalBytes)} (${download.progressPercent}%)"
                    } else {
                        FormatUtils.formatBytes(download.downloadedBytes)
                    }
                )

                DetailItem(
                    label = "Fasilə dəstəyi (HTTP Range)",
                    value = if (download.resumable) "Bəli (Çoxaxınlı davam dəstəklənir)" else "Xeyr"
                )

                DetailItem(
                    label = "Tarix",
                    value = FormatUtils.formatDate(download.dateAdded)
                )

                download.dateCompleted?.let {
                    DetailItem(
                        label = "Tamamlanma vaxtı",
                        value = FormatUtils.formatDate(it)
                    )
                }

                download.errorMessage?.let {
                    DetailItem(
                        label = "Xəta səbəbi",
                        value = it,
                        isError = true
                    )
                }

                HorizontalDivider(color = ObsidianBorder, modifier = Modifier.padding(vertical = 10.dp))

                // Storage Path
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Saxlanma Yolu (Lokal):",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = download.filePath,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            modifier = Modifier.padding(10.dp),
                            color = Color(0xFFE2E8F0)
                        )
                    }

                    if (canMoveToPublicStorage) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bu qovluğu digər proqramlar (zArchiver və s.) görə bilmir.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TurboAmber
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onMoveToPublicStorage,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                        ) {
                            Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Downloads qovluğuna köçür")
                        }
                    }
                }

                // URL with copy button
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mənbə URL:",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF94A3B8)
                        )
                        IconButton(
                            onClick = { copyToClipboard(download.url, "URL") },
                            modifier = Modifier.testTag("btn_copy_url")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "URL kopyala",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Surface(
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = download.url,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            modifier = Modifier.padding(10.dp),
                            maxLines = 3,
                            color = Color(0xFFCBD5E1),
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (download.status == DownloadStatus.COMPLETED) {
                    OutlinedButton(
                        onClick = onShare,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paylaş", color = Color.White)
                    }
                    Button(
                        onClick = onOpen,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF002026))
                    ) {
                        Icon(imageVector = Icons.Default.Launch, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aç", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF002026))
                    ) {
                        Text("Bağla", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDelete(true)
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = CyberCrimson)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Faylı Sil")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    isError: Boolean = false
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF94A3B8)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isError) CyberCrimson else Color.White
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF94A3B8)
        )
        content()
    }
}
