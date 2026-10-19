package com.tvlaunchers.webviewinstaller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.tvlaunchers.webviewinstaller.Http
import com.tvlaunchers.webviewinstaller.InstallerViewModel
import com.tvlaunchers.webviewinstaller.R
import com.tvlaunchers.webviewinstaller.UiState

@Composable
fun Section(
    title: String,
    focusable: Boolean = false,
    spacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusable) {
                    Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                } else {
                    Modifier
                },
            )
            .background(CardBackground, shape)
            // Read-only cards take focus so D-pad can scroll them into view on a TV.
            .border(2.dp, if (focused) Accent else CardBorder, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                color = Accent,
                fontSize = SectionTitleSize,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    labelColor: Color = Muted,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = BodySize,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = BodySize,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.6f),
        )
    }
}

@Composable
fun Note(
    text: String,
    color: Color = Muted,
) {
    Text(
        text = text,
        color = color,
        fontSize = SmallSize,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
fun ProgressBar(fraction: Float?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color(0xFF233140), RoundedCornerShape(4.dp)),
    ) {
        if (fraction != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(Accent, Color(0xFF8FE3FF))),
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}

@Composable
fun HeaderRow(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = true,
    backModifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (showBack && onBack != null) {
            Button(
                onClick = onBack,
                modifier = backModifier,
                colors = secondaryButtonColors(),
            ) {
                Text("←")
            }
        }
        // Accent spine: a cheap, readable way to anchor the header from across the room.
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(38.dp)
                .background(Accent, RoundedCornerShape(2.dp)),
        )
        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = TitleSize,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Text(text = subtitle, color = Muted, fontSize = SmallSize)
            }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun DownloadSection(vm: InstallerViewModel, state: UiState) {
    val download = state.download
    if (!download.active && download.error == null && !download.cancelled) return

    Section(stringResource(R.string.download_section)) {
        val name = download.displayName ?: ""
        val total = download.total
        val percent = if (total > 0) {
            ((download.bytes * 100) / total).toInt().coerceIn(0, 100)
        } else {
            null
        }
        Text(
            text = if (percent != null) {
                stringResource(R.string.download_progress, name, percent)
            } else {
                stringResource(R.string.download_progress_unknown, name)
            },
            color = TextPrimary,
            fontSize = BodySize,
        )
        Spacer(Modifier.height(10.dp))
        ProgressBar(percent?.let { it / 100f })
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildString {
                append(Http.formatSize(download.bytes))
                if (total > 0) {
                    append(" / ")
                    append(Http.formatSize(total))
                }
            },
            color = Muted,
            fontSize = SmallSize,
        )
        download.source?.let { Note(stringResource(R.string.download_source, it)) }
        if (download.verifying) Note(stringResource(R.string.download_verifying), Warn)
        download.error?.let { Note(stringResource(R.string.install_download_failed, it), Danger) }
        if (download.cancelled) Note(stringResource(R.string.download_cancelled), Warn)
        Spacer(Modifier.height(10.dp))
        if (download.active) {
            Button(
                onClick = { vm.cancelDownload() },
                colors = secondaryButtonColors(),
            ) {
                Text(stringResource(R.string.download_cancel))
            }
        } else {
            Button(
                onClick = { vm.retryDownload() },
                enabled = state.status?.rootAvailable == true,
                colors = primaryButtonColors(),
            ) {
                Text(stringResource(R.string.download_retry))
            }
        }
    }
}
