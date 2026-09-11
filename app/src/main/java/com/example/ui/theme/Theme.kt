package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Premium Obsidian Kinetic Dark Theme (Default & Primary Experience)
private val ObsidianDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF003F4A),
    onPrimaryContainer = ElectricCyanLight,
    secondary = TurboAmber,
    onSecondary = Color(0xFF381E00),
    secondaryContainer = Color(0xFF573200),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = QuantumViolet,
    background = ObsidianDeep,
    onBackground = Color(0xFFF1F5F9),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1E293B)
)

// Clean Crisp Light Mode with Kinetic Accents
private val ObsidianLightColorScheme = lightColorScheme(
    primary = Color(0xFF007A87),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0FCFF),
    onPrimaryContainer = Color(0xFF002026),
    secondary = Color(0xFFB35900),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8D1),
    onSecondaryContainer = Color(0xFF421E00),
    tertiary = QuantumViolet,
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF0A101D),
    surface = Color.White,
    onSurface = Color(0xFF0A101D),
    surfaceVariant = Color(0xFFE8EFF7),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to stunning Obsidian Kinetic aesthetic
    dynamicColor: Boolean = false, // Keep high-contrast Obsidian Kinetic styling
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ObsidianDarkColorScheme else ObsidianLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
