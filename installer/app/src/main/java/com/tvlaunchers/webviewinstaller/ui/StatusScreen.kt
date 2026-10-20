package com.tvlaunchers.webviewinstaller.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tvlaunchers.webviewinstaller.BuildConfig
import com.tvlaunchers.webviewinstaller.DeviceStatus
import com.tvlaunchers.webviewinstaller.InstalledPackage
import com.tvlaunchers.webviewinstaller.InstallerViewModel
import com.tvlaunchers.webviewinstaller.Known
import com.tvlaunchers.webviewinstaller.R
import com.tvlaunchers.webviewinstaller.Screen
import com.tvlaunchers.webviewinstaller.UiState
import kotlinx.coroutines.delay

@Composable
fun StatusScreen(vm: InstallerViewModel, state: UiState) {
    val status = state.status
    val canAct = status?.rootAvailable == true && state.busyLabel == null

    val refreshFocus = remember { FocusRequester() }
    val firstActionFocus = remember { FocusRequester() }
    var focusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(state.loading, state.refreshing, status?.rootAvailable) {
        if (!focusRequested && !state.loading && !state.refreshing) {
            focusRequested = true
            delay(200)
            runCatching {
                if (canAct) {
                    firstActionFocus.requestFocus()
                } else {
                    refreshFocus.requestFocus()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        item {
            HeaderRow(
                title = stringResource(R.string.app_name),
                subtitle = headerSubtitle(status),
                showBack = false,
                trailing = {
                    Button(
                        onClick = { vm.refresh(true) },
                        modifier = Modifier.focusRequester(refreshFocus),
                        enabled = !state.refreshing && state.busyLabel == null,
                        colors = secondaryButtonColors(),
                    ) {
                        Text(
                            text = if (state.refreshing) {
                                stringResource(R.string.status_refreshing)
                            } else {
                                stringResource(R.string.status_refresh)
                            }
                        )
                    }
                },
            )
        }

        // One item per row, and every actionable row *is* the action: the default row opens the
        // default-provider screen, the overlay row redeploys, a WebView row opens its variant
        // picker, and the restore row runs the full restore.
        item {
            OverviewCard(vm, state, status, canAct, firstActionFocus)
        }

        val busyLabel = state.busyLabel
        if (busyLabel != null) {
            item {
                Section(title = "") {
                    Text(
                        text = busyLabel,
                        color = Warn,
                        fontSize = BodySize,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(null)
                }
            }
        }

        if (state.download.active || state.download.error != null || state.download.cancelled) {
            item {
                DownloadSection(vm, state)
            }
        }
    }
}

private fun headerSubtitle(status: DeviceStatus?): String {
    val version = "v${BuildConfig.VERSION_NAME}"
    if (status == null) return version
    // The model is an overview row now, so the subtitle only carries the device code and OS.
    val device = listOfNotNull(
        status.device.takeIf { it.isNotBlank() },
        "Android ${status.androidRelease} (API ${status.sdk})",
    ).joinToString(" · ")
    return if (device.isBlank()) version else "$version · $device"
}

@Composable
private fun OverviewCard(
    vm: InstallerViewModel,
    state: UiState,
    status: DeviceStatus?,
    canAct: Boolean,
    firstActionFocus: FocusRequester,
) {
    Section(title = "", spacing = 2.dp) {
        if (status == null) {
            Text(
                text = if (state.rootChecking) {
                    stringResource(R.string.status_root_checking)
                } else {
                    stringResource(R.string.status_loading)
                },
                color = Muted,
                fontSize = BodySize,
            )
            return@Section
        }

        val rootValue = if (status.rootAvailable) {
            stringResource(R.string.root_available) + " · " +
                stringResource(R.string.root_uid, status.rootUid ?: "?")
        } else {
            stringResource(R.string.root_unavailable)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Left: how the write path and the current system state stand.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OverviewRow(
                    label = stringResource(R.string.overview_root),
                    value = rootValue,
                    valueColor = if (status.rootAvailable) Ok else Danger,
                    dotColor = if (status.rootAvailable) Ok else Danger,
                )

                val provider = status.currentProvider
                OverviewRow(
                    label = stringResource(R.string.current_provider),
                    value = provider?.let { vm.label(it) } ?: stringResource(R.string.unknown),
                    valueColor = if (provider == null) Muted else TextPrimary,
                    dotColor = if (provider == null) Muted else Accent,
                    hint = stringResource(R.string.hint_set_default),
                    onClick = { vm.openScreen(Screen.Default) },
                    enabled = canAct,
                )

                val local = status.overlayHash
                val remote = status.bundledOverlayHash
                val needsUpdate =
                    local != null && remote != null && !local.equals(remote, ignoreCase = true)
                // Two segments at most: "deployed · update available" replaces the recognition
                // state, which would otherwise make the row too wide for its column.
                val overlayValue = when {
                    !status.overlayExists -> stringResource(R.string.overlay_missing) + " · " +
                        stringResource(R.string.overlay_not_recognized)
                    needsUpdate -> stringResource(R.string.overlay_present) + " · " +
                        stringResource(R.string.overlay_outdated)
                    else -> stringResource(R.string.overlay_present) + " · " +
                        stringResource(
                            if (status.overlayRecognized) {
                                R.string.overlay_recognized
                            } else {
                                R.string.overlay_not_recognized
                            }
                        )
                }
                val overlayColor = when {
                    !status.overlayExists -> Danger
                    !status.overlayRecognized -> Warn
                    needsUpdate -> Warn
                    else -> Ok
                }
                OverviewRow(
                    label = stringResource(R.string.overview_overlay),
                    value = overlayValue,
                    valueColor = overlayColor,
                    dotColor = overlayColor,
                    hint = stringResource(R.string.hint_deploy),
                    onClick = { vm.requestOverlayDeploy() },
                    enabled = canAct,
                )

                OverviewRow(
                    label = stringResource(R.string.device_model),
                    value = buildString {
                        append(
                            status.model.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.unknown)
                        )
                        if (status.device.isNotBlank()) {
                            append('(')
                            append(status.device)
                            append(')')
                        }
                    },
                    valueColor = TextPrimary,
                    dotColor = Muted,
                )
            }

            // Right: one row per installable/selectable WebView.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Known.TARGETS.forEachIndexed { index, target ->
                    val info = status.installed[target.packageName]
                    val hasAssets = status.assets.any { it.packageName == target.packageName }
                    val installedAsset =
                        if (info != null) status.assets.firstOrNull { vm.isInstalled(it, info) } else null
                    val familyLabel = vm.label(target.packageName)
                    // A WebView family opens its variant picker, where the user can compare the
                    // packages, install one, switch, or uninstall the family.
                    // The row already says which family this is, so the note only carries the
                    // variant: "Thorium（关闭 WebGPU）" would not fit and reads as an echo.
                    val note = listOfNotNull(
                        installedAsset
                            ?.let { vm.assetTitle(it) }
                            ?.takeIf { it != familyLabel }
                            ?.let(::variantSummary),
                        installedNote(info),
                    ).joinToString(" · ").ifBlank { null }
                    OverviewRow(
                        label = familyLabel,
                        value = installedValue(info),
                        note = note,
                        badge = if (target.recommended) {
                            stringResource(R.string.badge_recommended)
                        } else {
                            null
                        },
                        valueColor = installedColor(info),
                        dotColor = installedColor(info),
                        hint = stringResource(R.string.hint_variants),
                        onClick = if (info != null || hasAssets) {
                            ({ vm.openVariants(target.packageName) })
                        } else {
                            null
                        },
                        enabled = canAct,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstActionFocus)
                        } else {
                            Modifier
                        },
                    )
                }

                val aosp = status.installed[Known.AOSP_PACKAGE]
                OverviewRow(
                    label = vm.label(Known.AOSP_PACKAGE),
                    value = installedValue(aosp),
                    note = installedNote(aosp),
                    valueColor = installedColor(aosp),
                    dotColor = installedColor(aosp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OverviewRow(
            label = stringResource(R.string.action_restore),
            value = stringResource(R.string.restore_row_hint),
            valueColor = Muted,
            dotColor = Danger,
            hint = stringResource(R.string.hint_restore),
            onClick = { vm.requestRestore() },
            enabled = canAct,
            danger = true,
        )

        if (!status.rootAvailable && !status.rootError.isNullOrBlank()) {
            Note(stringResource(R.string.read_only_banner), Warn)
            Note(status.rootError, Danger)
        }
        // The manifest used to be reported on the (removed) install screen; keep the failure
        // visible here, otherwise the download rows just look empty.
        status.manifestError?.let {
            Note(stringResource(R.string.install_manifest_error, it), Danger)
        }
        status.pendingProvider?.let {
            Note(stringResource(R.string.pending_reboot) + " · $it", Warn)
        }
        if (status.pendingOverlayReboot) {
            Note(stringResource(R.string.overlay_pending_reboot), Warn)
        }
    }
}

@Composable
private fun installedValue(info: InstalledPackage?): String = when {
    info == null -> stringResource(R.string.not_installed)
    else -> info.versionName ?: stringResource(R.string.yes)
}

/**
 * "Thorium（关闭 WebGPU）" -> "关闭 WebGPU". Manifest titles repeat the family name that the row
 * label already shows; keeping just the parenthetical part makes the second line fit.
 */
private fun variantSummary(title: String?): String? {
    if (title.isNullOrBlank()) return null
    val open = title.indexOfFirst { it == '（' || it == '(' }
    if (open < 0) return title
    return title.substring(open + 1).trimEnd('）', ')').trim().ifBlank { null }
}

/** Shown under the version: a disabled package needs the extra room of a second line. */
@Composable
private fun installedNote(info: InstalledPackage?): String? =
    if (info != null && !info.enabled) stringResource(R.string.installed_disabled) else null

private fun installedColor(info: InstalledPackage?): Color = when {
    info == null -> Muted
    !info.enabled -> Warn
    else -> TextPrimary
}

/**
 * One piece of information per row, rendered as a TV tile. Rows with [onClick] take D-pad focus and
 * the row itself is the button: focus shows a status-coloured border, an accent glow and a slight
 * scale-up, and the trailing chevron swaps to the action hint so the remote's centre key is obvious.
 */
@Composable
private fun OverviewRow(
    label: String,
    value: String,
    valueColor: Color,
    dotColor: Color,
    modifier: Modifier = Modifier,
    note: String? = null,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    hint: String? = null,
) {
    val accent = if (danger) Danger else Accent
    val shape = RoundedCornerShape(12.dp)

    if (onClick == null || !enabled) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(RowBackground, shape)
                .border(1.dp, RowBorder, shape),
        ) {
            OverviewRowContent(
                label = label,
                value = value,
                note = note,
                badge = badge,
                valueColor = valueColor,
                dotColor = dotColor,
                danger = danger,
                focused = false,
                showsChevron = onClick != null,
                accent = accent,
                hint = null,
            )
        }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // TV surface handles D-pad Enter/center, focus scale, border and glow for us.
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = true,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = RowBackground,
            contentColor = TextPrimary,
            focusedContainerColor = RowBackgroundFocused,
            focusedContentColor = TextPrimary,
            pressedContainerColor = AccentSoft,
            pressedContentColor = Color.White,
            disabledContainerColor = RowBackground,
            disabledContentColor = Muted,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f, pressedScale = 0.995f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, RowBorder), 0.dp, shape),
            focusedBorder = Border(BorderStroke(2.dp, accent), 0.dp, shape),
            pressedBorder = Border(BorderStroke(2.dp, accent), 0.dp, shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            glow = Glow.None,
            focusedGlow = Glow(accent.copy(alpha = 0.35f), 14.dp),
            pressedGlow = Glow(accent.copy(alpha = 0.25f), 10.dp),
        ),
        interactionSource = interactionSource,
    ) {
        OverviewRowContent(
            label = label,
            value = value,
            note = note,
            badge = badge,
            valueColor = valueColor,
            dotColor = dotColor,
            danger = danger,
            focused = focused,
            showsChevron = true,
            accent = accent,
            hint = hint,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OverviewRowContent(
    label: String,
    value: String,
    note: String?,
    badge: String?,
    valueColor: Color,
    dotColor: Color,
    danger: Boolean,
    focused: Boolean,
    showsChevron: Boolean,
    accent: Color,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = if (danger) Danger else TextPrimary,
            fontSize = LabelSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Take the label's own width instead of a fixed share, so a long label such as
            // "Webview Overlay" is never cut off and the value uses whatever is left.
            modifier = Modifier,
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = badge,
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .background(Accent.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = value,
                color = valueColor,
                fontSize = BodySize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
            if (note != null) {
                Text(
                    text = note,
                    color = Warn,
                    fontSize = LabelSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (showsChevron) {
            Spacer(Modifier.width(12.dp))
            Box(
                // Narrow columns: reserve only enough room for a two-character hint, and let
                // longer translations borrow space from the value while focused.
                modifier = Modifier.widthIn(min = 44.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (focused && hint != null) {
                    Text(
                        text = hint,
                        color = accent,
                        fontSize = LabelSize,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = "›",
                        color = if (focused) accent else Muted,
                        fontSize = SectionTitleSize,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
