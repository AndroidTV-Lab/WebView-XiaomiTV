package com.tvlaunchers.webviewinstaller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.tvlaunchers.webviewinstaller.Http
import com.tvlaunchers.webviewinstaller.InstallerViewModel
import com.tvlaunchers.webviewinstaller.Known
import com.tvlaunchers.webviewinstaller.KnownTarget
import com.tvlaunchers.webviewinstaller.R
import com.tvlaunchers.webviewinstaller.UiState
import kotlinx.coroutines.delay

@Composable
fun InstallScreen(vm: InstallerViewModel, state: UiState) {
    val status = state.status
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { backFocus.requestFocus() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        item {
            HeaderRow(
                title = stringResource(R.string.install_title),
                onBack = { vm.back() },
                backModifier = Modifier.focusRequester(backFocus),
            )
        }

        if (state.manifestLoading) {
            item {
                Section(title = "") {
                    Text(
                        text = stringResource(R.string.install_manifest_loading),
                        color = Muted,
                        fontSize = BodySize,
                    )
                }
            }
        }

        val manifestError = status?.manifestError
        if (!state.manifestLoading && manifestError != null) {
            item {
                Section(title = "") {
                    Text(
                        text = stringResource(R.string.install_manifest_error, manifestError),
                        color = Danger,
                        fontSize = BodySize,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.refresh(true) }) {
                        Text(stringResource(R.string.install_manifest_retry))
                    }
                }
            }
        }

        items(Known.TARGETS, key = { it.id }) { target ->
            AssetCard(vm, state, target)
        }

        if (state.download.active || state.download.error != null || state.download.cancelled) {
            item {
                DownloadSection(vm, state)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AssetCard(
    vm: InstallerViewModel,
    state: UiState,
    target: KnownTarget,
) {
    val status = state.status
    val asset = status?.assets?.firstOrNull { it.packageName == target.packageName }
    val installed = status?.installed?.get(target.packageName)
    val busy = state.busyLabel != null || state.download.active
    val canAct = asset != null && status?.rootAvailable == true && !busy

    Section(stringResource(target.nameRes)) {
        Text(
            text = target.packageName,
            color = Muted,
            fontSize = SmallSize,
        )
        Spacer(Modifier.height(8.dp))
        InfoRow(
            label = stringResource(R.string.install_local_version),
            value = installed?.versionName ?: stringResource(R.string.not_installed),
            valueColor = if (installed == null) Muted else Color.White,
        )
        InfoRow(
            label = stringResource(R.string.install_remote_version),
            value = asset?.versionName
                ?: if (state.manifestLoading) "…" else stringResource(R.string.install_asset_missing),
            valueColor = if (asset == null) Muted else Color.White,
        )
        InfoRow(
            label = stringResource(R.string.install_size),
            value = asset?.let { Http.formatSize(it.size) } ?: "—",
            valueColor = if (asset == null) Muted else Color.White,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { asset?.let { vm.requestDownload(it) } },
            enabled = canAct,
        ) {
            Text(
                text = when {
                    asset == null -> stringResource(R.string.install_asset_missing)
                    state.download.active && state.download.assetId == asset.id ->
                        stringResource(R.string.install_prepare)

                    installed == null -> stringResource(R.string.install_button_download)
                    installed.versionName != asset.versionName ->
                        stringResource(R.string.install_button_update)

                    else -> stringResource(R.string.install_button_reinstall)
                }
            )
        }
    }
}
