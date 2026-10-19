package com.tvlaunchers.webviewinstaller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.tvlaunchers.webviewinstaller.BuildConfig
import com.tvlaunchers.webviewinstaller.DeviceStatus
import com.tvlaunchers.webviewinstaller.InstallerViewModel
import com.tvlaunchers.webviewinstaller.Known
import com.tvlaunchers.webviewinstaller.R
import com.tvlaunchers.webviewinstaller.Screen
import com.tvlaunchers.webviewinstaller.UiState
import kotlinx.coroutines.delay

@Composable
fun StatusScreen(vm: InstallerViewModel, state: UiState) {
    val status = state.status
    val canWrite = status?.rootAvailable == true && state.busyLabel == null

    val refreshFocus = remember { FocusRequester() }
    val installFocus = remember { FocusRequester() }
    var focusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(state.loading, state.refreshing, status?.rootAvailable) {
        if (!focusRequested && !state.loading && !state.refreshing) {
            focusRequested = true
            delay(200)
            runCatching {
                if (status?.rootAvailable == true) {
                    installFocus.requestFocus()
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

        // Root, default provider, overlay, every installed provider and the actions share one
        // card, so nothing important sits below the fold on a 540 dp tall TV screen.
        item {
            OverviewCard(vm, state, status, canWrite, installFocus)
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
    canWrite: Boolean,
    installFocus: FocusRequester,
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
        } else {
            OverviewContent(vm, status, canWrite, installFocus)
        }
    }
}

@Composable
private fun OverviewContent(
    vm: InstallerViewModel,
    status: DeviceStatus,
    canWrite: Boolean,
    installFocus: FocusRequester,
) {
    val rootValue = if (status.rootAvailable) {
        stringResource(R.string.root_available) + " · " +
            stringResource(R.string.root_uid, status.rootUid ?: "?")
    } else {
        stringResource(R.string.root_unavailable)
    }
    val rootColor = if (status.rootAvailable) Ok else Danger

    val provider = status.currentProvider
    val providerValue = provider?.let {
        buildString {
            append(vm.label(it))
            status.currentVersion?.takeIf { v -> v.isNotBlank() }?.let { v ->
                append(" · ").append(v)
            }
        }
    } ?: stringResource(R.string.unknown)
    val providerColor = if (provider == null) Muted else Color.White

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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OverviewItem(
            label = stringResource(R.string.overview_root),
            value = rootValue,
            valueColor = rootColor,
            modifier = Modifier.weight(0.9f),
        )
        OverviewActionItem(
            label = stringResource(R.string.current_provider),
            value = providerValue,
            valueColor = providerColor,
            actionLabel = stringResource(R.string.action_set_default),
            onAction = { vm.openScreen(Screen.Default) },
            enabled = canWrite,
            modifier = Modifier.weight(1.3f),
        )
        OverviewActionItem(
            label = stringResource(R.string.overview_overlay),
            value = overlayValue,
            valueColor = overlayColor,
            actionLabel = stringResource(R.string.action_deploy_overlay),
            onAction = { vm.requestOverlayDeploy() },
            enabled = canWrite,
            modifier = Modifier.weight(1.3f),
        )
    }

    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = Modifier.weight(3.5f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (packageName in Known.ALL_PACKAGES) {
                ProviderCell(vm, status, packageName, Modifier.weight(1f))
            }
        }
        Column(
            modifier = Modifier.weight(0.8f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { vm.openScreen(Screen.Install) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(installFocus),
                enabled = canWrite,
            ) {
                Text(stringResource(R.string.action_install_webview))
            }
            Button(
                onClick = { vm.requestRestore() },
                modifier = Modifier.fillMaxWidth(),
                enabled = canWrite,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF5A1F1F),
                    contentColor = Color(0xFFFFCDD2),
                    focusedContainerColor = Danger,
                    focusedContentColor = Color.White,
                ),
            ) {
                Text(stringResource(R.string.action_restore))
            }
        }
    }

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

@Composable
private fun OverviewItem(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            color = Muted,
            fontSize = SmallSize,
            maxLines = 1,
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = BodySize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A status column whose action sits directly under the value it operates on. */
@Composable
private fun OverviewActionItem(
    label: String,
    value: String,
    valueColor: Color,
    actionLabel: String,
    onAction: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            color = Muted,
            fontSize = SmallSize,
            maxLines = 1,
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = BodySize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        ) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun ProviderCell(
    vm: InstallerViewModel,
    status: DeviceStatus,
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val info = status.installed[packageName]
    val value = when {
        info == null -> stringResource(R.string.not_installed)
        !info.enabled -> listOfNotNull(
            info.versionName,
            stringResource(R.string.installed_disabled),
        ).joinToString(" · ")

        else -> info.versionName ?: stringResource(R.string.yes)
    }
    val valueColor = when {
        info == null -> Muted
        !info.enabled -> Warn
        else -> Color.White
    }
    Column(modifier) {
        Text(
            text = vm.label(packageName),
            color = Muted,
            fontSize = SmallSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = BodySize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
