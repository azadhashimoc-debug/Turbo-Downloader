package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SampleDownloadItem
import com.example.data.SampleDownloads
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanLight
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleLinksBottomSheet(
    onDismiss: () -> Unit,
    onSelectSample: (SampleDownloadItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricCyan.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Sınaq Test Faylları",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Obsidian Kinetic Yükləmə Testləri",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Turbo motorun sürətini və fasilə/davam xüsusiyyətlərini test etmək üçün seçin:",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SampleDownloads.items) { sample ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable {
                                onSelectSample(sample)
                                onDismiss()
                            }
                            .testTag("sample_item_${sample.fileName}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ObsidianCard
                        ),
                        border = BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryBadge(category = sample.category)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sample.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${sample.fileName} • ${sample.sizeText}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Yüklə",
                                    tint = ElectricCyanLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
