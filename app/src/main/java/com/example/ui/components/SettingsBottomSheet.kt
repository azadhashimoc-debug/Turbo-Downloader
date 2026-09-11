package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
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
import com.example.engine.SmartEngineMode
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanLight
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.QuantumViolet
import com.example.ui.theme.TurboAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    currentEngineMode: SmartEngineMode,
    onEngineModeSelected: (SmartEngineMode) -> Unit,
    onDismiss: () -> Unit,
    onClearCompleted: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scrollState = rememberScrollState()

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
                .verticalScroll(scrollState)
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
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Mühərrik və Tənzimləmələr",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Obsidian Kinetic Sürətləndirmə Mərkəzi",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Kinetic Speed Engine Selector
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = TurboAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ağıllı Yükləmə Sürətləndiricisi",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Zəif internet və mobil şəbəkələrdə 4-hissəli paralel axın və dinamik RAM buferləşdirmə ilə maksimal sürət hasil edilir.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Engine Modes Cards
            SmartEngineMode.entries.forEach { mode ->
                val isSelected = mode == currentEngineMode
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable { onEngineModeSelected(mode) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            ElectricCyan.copy(alpha = 0.12f)
                        } else {
                            ObsidianCard
                        }
                    ),
                    border = BorderStroke(
                        1.2.dp,
                        if (isSelected) ElectricCyan.copy(alpha = 0.7f) else ObsidianBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onEngineModeSelected(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = ElectricCyan,
                                unselectedColor = Color(0xFF64748B)
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) ElectricCyanLight else Color.White
                                )
                                if (mode == SmartEngineMode.TURBO_MULTI_STREAM) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = TurboAmber.copy(alpha = 0.2f),
                                        border = BorderStroke(0.6.dp, TurboAmber.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "TÖVSİYƏ EDİLİR",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = TurboAmber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(modifier = Modifier.height(16.dp))

            // Card: Storage Info
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Faylların Saxlanma Yeri",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tətbiqin xüsusi 'Downloads' qovluğunda saxlanılır",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action: Clear Completed
            OutlinedButton(
                onClick = {
                    onClearCompleted()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_clear_completed"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CyberCrimson.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CyberCrimson.copy(alpha = 0.08f),
                    contentColor = CyberCrimson
                )
            ) {
                Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Tamamlanmış Yükləmələri Təmizlə",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
