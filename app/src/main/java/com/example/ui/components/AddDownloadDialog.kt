package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DownloadCategory
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanLight
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.util.FormatUtils

@Composable
fun AddDownloadDialog(
    initialUrl: String? = null,
    onDismiss: () -> Unit,
    onStartDownload: (url: String, fileName: String?, category: DownloadCategory?) -> Unit
) {
    val context = LocalContext.current
    var urlText by remember(initialUrl) { mutableStateOf(initialUrl ?: "") }
    var fileNameText by remember(initialUrl) {
        mutableStateOf(
            if (!initialUrl.isNullOrBlank()) FormatUtils.extractFileNameFromUrl(initialUrl) else ""
        )
    }
    var selectedCategory by remember(initialUrl) {
        mutableStateOf(
            if (!initialUrl.isNullOrBlank()) FormatUtils.detectCategory(FormatUtils.extractFileNameFromUrl(initialUrl)) else null
        )
    }
    var urlError by remember { mutableStateOf<String?>(null) }

    fun pasteFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                if (text.startsWith("http://") || text.startsWith("https://")) {
                    urlText = text
                    val detected = FormatUtils.extractFileNameFromUrl(text)
                    fileNameText = detected
                    selectedCategory = FormatUtils.detectCategory(detected)
                    urlError = null
                } else if (text.isNotBlank()) {
                    urlText = text
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Auto update file name when url changes if not manually typed
    LaunchedEffect(urlText) {
        if (urlText.startsWith("http://") || urlText.startsWith("https://")) {
            val extracted = FormatUtils.extractFileNameFromUrl(urlText)
            if (fileNameText.isBlank() || fileNameText.startsWith("download_")) {
                fileNameText = extracted
                selectedCategory = FormatUtils.detectCategory(extracted)
            }
            urlError = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricCyan.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Yeni Yükləmə Əlavə Et",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Birbaşa HTTP/HTTPS bağlantısı",
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
                Spacer(modifier = Modifier.height(4.dp))

                // URL Input Field with Obsidian styling
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        if (it.isNotBlank()) urlError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_download_url"),
                    label = { Text("Faylın URL Linki") },
                    placeholder = { Text("https://example.com/file.zip") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard,
                        focusedLabelColor = ElectricCyan,
                        unfocusedLabelColor = Color(0xFF94A3B8),
                        cursorColor = ElectricCyan
                    ),
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = ElectricCyan)
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { pasteFromClipboard() },
                            modifier = Modifier.testTag("btn_paste_url")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Buferdən yapışdır",
                                tint = ElectricCyanLight
                            )
                        }
                    },
                    isError = urlError != null,
                    supportingText = urlError?.let { { Text(it, color = CyberCrimson) } },
                    singleLine = false,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Custom File Name Field
                OutlinedTextField(
                    value = fileNameText,
                    onValueChange = { fileNameText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_file_name"),
                    label = { Text("Faylın adı (ixtiyari)") },
                    placeholder = { Text("Məs: video.mp4") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard,
                        focusedLabelColor = ElectricCyan,
                        unfocusedLabelColor = Color(0xFF94A3B8),
                        cursorColor = ElectricCyan
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (urlText.isNotBlank()) {
                                onStartDownload(urlText, fileNameText, selectedCategory)
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "KATEQORİYA:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf(
                        DownloadCategory.DOCUMENT to "Sənəd",
                        DownloadCategory.VIDEO to "Video",
                        DownloadCategory.AUDIO to "Audio",
                        DownloadCategory.IMAGE to "Şəkil",
                        DownloadCategory.ARCHIVE to "Arxiv"
                    )

                    categories.forEach { (cat, label) ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            },
                            label = { Text(label) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricCyanLight,
                                containerColor = ObsidianCard,
                                labelColor = Color(0xFF94A3B8)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) ElectricCyan else ObsidianBorder
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = urlText.trim()
                    if (trimmed.isBlank() || (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))) {
                        urlError = "Etibarlı HTTP və ya HTTPS linki daxil edin"
                        return@Button
                    }
                    onStartDownload(trimmed, fileNameText.trim().ifBlank { null }, selectedCategory)
                },
                modifier = Modifier.testTag("btn_confirm_download"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Color(0xFF002026)
                )
            ) {
                Text("Yükləməyə Başla", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_add"),
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
            ) {
                Text("Ləğv et")
            }
        },
        shape = RoundedCornerShape(26.dp)
    )
}
