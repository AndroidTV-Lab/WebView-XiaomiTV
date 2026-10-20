package com.androidtvlab.xiaomi.webviewinstaller.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.darkColorScheme

// Deep graphite/navy palette with a cyan accent. TV UIs are viewed from a distance, so contrast
// stays high and focus is always communicated by more than colour (border + glow + scale).
val Background = Color(0xFF0B1118)
val BackgroundTop = Color(0xFF0E1721)
val BackgroundBottom = Color(0xFF080D13)

val CardBackground = Color(0xFF151E28)
val CardBackgroundMuted = Color(0xFF111923)
val CardBorder = Color(0xFF1C2734)

val RowBackground = Color(0xFF1B2633)
val RowBackgroundFocused = Color(0xFF243B50)
val RowBorder = Color(0xFF2A3948)

val Accent = Color(0xFF57C8FF)
val AccentSoft = Color(0xFF1E4A5F)
val Muted = Color(0xFF93A6B8)
val TextPrimary = Color(0xFFF2F7FB)
val Ok = Color(0xFF6FD48B)
val Warn = Color(0xFFFFC15C)
val Danger = Color(0xFFFF6B6B)

// The TV reports 1920x1080 at density 320, i.e. only 960x540 dp. Sizes are tuned so the whole
// status page (root, actions, WebView, overlay, installed providers) fits on one screen.
val ScreenPadding = 20.dp
val SectionSpacing = 6.dp

val TitleSize = 30.sp
val SectionTitleSize = 18.sp
val BodySize = 19.sp
val LabelSize = 16.sp
val SmallSize = 13.sp

val InstallerColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00202E),
    primaryContainer = AccentSoft,
    onPrimaryContainer = Color(0xFFB3E5FC),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00201C),
    background = Background,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = RowBackground,
    onSurfaceVariant = Muted,
    border = CardBorder,
    borderVariant = RowBorder,
    error = Danger,
    onError = Color.White,
)
