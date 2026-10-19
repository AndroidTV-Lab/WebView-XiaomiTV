package com.tvlaunchers.webviewinstaller.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
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
        // default-provider screen, the overlay row redeploys, a WebView row starts its download,
        // and the restore row runs the full restore.
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
    val device = listOfNotNull(
        status.model.takeIf { it.isNotBlank() },
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
    Section(title = "") {
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
        OverviewRow(
            label = stringResource(R.string.overview_root),
            value = rootValue,
            valueColor = if (status.rootAvailable) Ok else Danger,
        )

        val provider = status.currentProvider
        val providerValue = provider?.let {
            buildString {
                append(vm.label(it))
                status.currentVersion?.takeIf { v -> v.isNotBlank() }?.let { v ->
                    append(" · ").append(v)
                }
            }
        } ?: stringResource(R.string.unknown)
        OverviewRow(
            label = stringResource(R.string.current_provider),
            value = providerValue,
            valueColor = if (provider == null) Muted else Color.White,
            onClick = { vm.openScreen(Screen.Default) },
            enabled = canAct,
        )

        val local = status.overlayHash
        val remote = status.remoteOverlayHash
        val same = local != null && remote != null && local.equals(remote, ignoreCase = true)
        val overlayValue = buildString {
            append(
                stringResource(
                    if (status.overlayExists) R.string.overlay_present else R.string.overlay_missing
                )
            )
            append(" · ")
            append(
                stringResource(
                    if (status.overlayRecognized) {
                        R.string.overlay_recognized
                    } else {
                        R.string.overlay_not_recognized
                    }
                )
            )
            if (local != null && remote != null) {
                append(" · ")
                append(
                    stringResource(
                        if (same) R.string.overlay_up_to_date else R.string.overlay_outdated
                    )
                )
            }
        }
        val overlayColor = when {
            !status.overlayExists -> Danger
            !status.overlayRecognized -> Warn
            local != null && remote != null && !same -> Warn
            else -> Ok
        }
        OverviewRow(
            label = stringResource(R.string.overview_overlay),
            value = overlayValue,
            valueColor = overlayColor,
            onClick = { vm.requestOverlayDeploy() },
            enabled = canAct,
        )

        Known.TARGETS.forEachIndexed { index, target ->
            val info = status.installed[target.packageName]
            val asset = status.assets.firstOrNull { it.packageName == target.packageName }
            OverviewRow(
                label = vm.label(target.packageName),
                value = installedValue(info),
                valueColor = installedColor(info),
                onClick = {
                    if (asset != null) {
                        vm.requestDownload(asset)
                    } else {
                        vm.openScreen(Screen.Install)
                    }
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
            valueColor = installedColor(aosp),
        )

        Spacer(Modifier.height(4.dp))
        OverviewRow(
            label = stringResource(R.string.action_restore),
            value = stringResource(R.string.restore_row_hint),
            valueColor = Muted,
            onClick = { vm.requestRestore() },
            enabled = canAct,
            danger = true,
        )

        if (!status.rootAvailable && !status.rootError.isNullOrBlank()) {
            Note(stringResource(R.string.read_only_banner), Warn)
            Note(status.rootError, Danger)
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
    !info.enabled -> listOfNotNull(
        info.versionName,
        stringResource(R.string.installed_disabled),
    ).joinToString(" · ")
    else -> info.versionName ?: stringResource(R.string.yes)
}

private fun installedColor(info: InstalledPackage?): Color = when {
    info == null -> Muted
    !info.enabled -> Warn
    else -> Color.White
}

/**
 * One piece of information per row. Rows with [onClick] are TV surfaces: they take D-pad focus and
 * the row itself is the button for that information, so no separate action panel is needed.
 */
@Composable
private fun OverviewRow(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val accent = if (danger) Danger else Accent
    val shape = RoundedCornerShape(8.dp)

    if (onClick == null || !enabled) {
        OverviewRowContent(
            label = label,
            value = value,
            valueColor = valueColor,
            danger = danger,
            focused = false,
            showsChevron = onClick != null,
            modifier = modifier,
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // TV surfacing handles D-pad Enter/center clicks, focus scale and focus border for us.
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = true,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = CardBackgroundMuted,
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF2E3A46),
            pressedContentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Muted,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(2.dp, accent), 0.dp, shape),
            pressedBorder = Border(BorderStroke(2.dp, accent), 0.dp, shape),
        ),
        interactionSource = interactionSource,
    ) {
        OverviewRowContent(
            label = label,
            value = value,
            valueColor = valueColor,
            danger = danger,
            focused = focused,
            showsChevron = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OverviewRowContent(
    label: String,
    value: String,
    valueColor: Color,
    danger: Boolean,
    focused: Boolean,
    showsChevron: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (danger) Danger else Muted,
            fontSize = BodySize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = BodySize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
        if (showsChevron) {
            Text(
                text = "›",
                color = if (focused) (if (danger) Danger else Accent) else Muted,
                fontSize = SectionTitleSize,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
