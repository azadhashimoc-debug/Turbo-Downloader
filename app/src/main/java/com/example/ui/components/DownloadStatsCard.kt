package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.SmartEngineMode
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanLight
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.TurboAmber
import com.example.util.FormatUtils

@Composable
fun DownloadStatsCard(
    totalSpeed: Long,
    activeCount: Int,
    completedCount: Int,
    freeStorage: String,
    engineMode: SmartEngineMode,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Subtle kinetic pulse animation for active speed
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F1B2E),
                            Color(0xFF0A1220)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                ElectricCyan.copy(alpha = if (activeCount > 0) pulseGlow * 0.7f else 0.35f),
                                ObsidianBorder,
                                TurboAmber.copy(alpha = 0.3f)
                            )
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
        ) {
            Column {
                // Top speed display with Kinetic Neon Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (activeCount > 0) ElectricCyan else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ÜMUMİ YÜKLƏMƏ SÜRƏTİ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = ElectricCyanLight.copy(alpha = 0.9f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = FormatUtils.formatSpeed(totalSpeed),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 28.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = Color.White
                            )
                        }
                    }

                    // Cyber Kinetic Speedometer Orb
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        ElectricCyan.copy(alpha = 0.25f),
                                        Color(0xFF0F1B2E)
                                    )
                                )
                            )
                            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Sürət",
                            tint = ElectricCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Stats row: Active, Completed, Free storage with Obsidian kinetic boxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KineticMetricBox(
                        icon = Icons.Default.Download,
                        label = "Aktiv",
                        value = "$activeCount",
                        tint = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )

                    KineticMetricBox(
                        icon = Icons.Default.CheckCircle,
                        label = "Hazır",
                        value = "$completedCount",
                        tint = NeonEmerald,
                        modifier = Modifier.weight(1f)
                    )

                    KineticMetricBox(
                        icon = Icons.Default.SdStorage,
                        label = "Yaddaş",
                        value = freeStorage.ifBlank { "--" },
                        tint = TurboAmber,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Obsidian Kinetic Turbo Engine Status Strip
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenSettings() },
                    color = Color(0xFF142036).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TurboAmber.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = TurboAmber,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(9.dp))
                            Column {
                                Text(
                                    text = "Ağıllı Mühərrik: ${engineMode.title}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = if (engineMode.streamCount > 1) {
                                        "${engineMode.streamCount}-axınlı paralel • dinamik bufer"
                                    } else {
                                        "Tək axın • qırılmaya davamlı"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Tənzimlə",
                            tint = ElectricCyan.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KineticMetricBox(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF142036).copy(alpha = 0.6f))
            .border(0.8.dp, ObsidianBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}
