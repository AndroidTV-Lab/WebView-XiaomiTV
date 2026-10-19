package com.tvlaunchers.webviewinstaller.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.darkColorScheme

val Background = Color(0xFF0F1318)
val CardBackground = Color(0xFF1A2028)
val CardBackgroundMuted = Color(0xFF141A21)
val Accent = Color(0xFF4FC3F7)
val Muted = Color(0xFF9FB0BF)
val Ok = Color(0xFF81C784)
val Warn = Color(0xFFFFB74D)
val Danger = Color(0xFFEF5350)

// The TV reports 1920x1080 at density 320, i.e. only 960x540 dp. Sizes are tuned so the whole
// status page (root, actions, WebView, overlay, installed providers) fits on one screen.
val ScreenPadding = 24.dp
val SectionSpacing = 8.dp

val TitleSize = 26.sp
val SectionTitleSize = 16.sp
val BodySize = 14.sp
val SmallSize = 11.sp

val InstallerColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00202E),
    primaryContainer = Color(0xFF0B3A4D),
    onPrimaryContainer = Color(0xFFB3E5FC),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00201C),
    background = Background,
    onBackground = Color.White,
    surface = CardBackground,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF27313C),
    onSurfaceVariant = Muted,
    border = Color(0xFF3A4753),
    borderVariant = Color(0xFF27313C),
    error = Danger,
    onError = Color.White,
)
