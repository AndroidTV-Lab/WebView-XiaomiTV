package com.tvlaunchers.webviewinstaller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.tvlaunchers.webviewinstaller.DeviceStatus
import com.tvlaunchers.webviewinstaller.InstallerViewModel
import com.tvlaunchers.webviewinstaller.Known
import com.tvlaunchers.webviewinstaller.R
import com.tvlaunchers.webviewinstaller.UiState
import kotlinx.coroutines.delay

@Composable
fun DefaultScreen(vm: InstallerViewModel, state: UiState) {
    val status = state.status
    val validProviders = status?.validProviders ?: emptyList()
    val installedKnown = Known.ALL_PACKAGES.filter {
        status?.installed?.containsKey(it) == true
    }
    val unusable = installedKnown.filter { it !in validProviders }
    val selected = state.selectedProvider ?: status?.pendingProvider
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
                title = stringResource(R.string.default_title),
                subtitle = stringResource(R.string.default_hint),
                onBack = { vm.back() },
                backModifier = Modifier.focusRequester(backFocus),
            )
        }

        item {
            Section(title = "") {
                if (validProviders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.default_no_providers),
                        color = Muted,
                        fontSize = BodySize,
                    )
                }
                for (packageName in validProviders) {
                    ProviderButton(
                        vm = vm,
                        status = status,
                        packageName = packageName,
                        selected = packageName == selected,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                if (unusable.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    for (packageName in unusable) {
                        UnusableProviderRow(vm, status, packageName)
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { selected?.let { vm.applyDefault(it) } },
                    enabled = selected != null &&
                        status?.rootAvailable == true &&
                        state.busyLabel == null,
                ) {
                    Text(stringResource(R.string.default_apply))
                }
            }
        }
    }
}

@Composable
private fun ProviderButton(
    vm: InstallerViewModel,
    status: DeviceStatus?,
    packageName: String,
    selected: Boolean,
) {
    val info = status?.installed?.get(packageName)
    val isCurrent = status?.currentProvider == packageName

    Button(
        onClick = { vm.selectProvider(packageName) },
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) {
            ButtonDefaults.colors(
                containerColor = Color(0xFF1E4658),
                contentColor = Color.White,
                focusedContainerColor = Accent,
                focusedContentColor = Color(0xFF00202E),
            )
        } else {
            ButtonDefaults.colors()
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = vm.label(packageName),
                color = if (selected) Color.White else Muted,
            )
            Spacer(Modifier.weight(1f))
            info?.versionName?.let {
                Text(text = it, color = Muted, fontSize = SmallSize)
            }
            if (isCurrent) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.default_current),
                    color = Ok,
                    fontSize = SmallSize,
                )
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Text(text = "✓", color = Accent)
            }
        }
    }
}

@Composable
private fun UnusableProviderRow(
    vm: InstallerViewModel,
    status: DeviceStatus?,
    packageName: String,
) {
    val info = status?.installed?.get(packageName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackgroundMuted, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = vm.label(packageName), color = Muted, fontSize = BodySize)
            Text(
                text = packageName + (info?.versionName?.let { " · $it" } ?: ""),
                color = Muted,
                fontSize = 12.sp,
            )
        }
        Text(
            text = stringResource(R.string.default_needs_overlay),
            color = Warn,
            fontSize = SmallSize,
        )
    }
}
