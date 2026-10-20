package com.androidtvlab.xiaomi.webviewinstaller.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.androidtvlab.xiaomi.webviewinstaller.DeviceStatus
import com.androidtvlab.xiaomi.webviewinstaller.InstallerViewModel
import com.androidtvlab.xiaomi.webviewinstaller.Known
import com.androidtvlab.xiaomi.webviewinstaller.R
import com.androidtvlab.xiaomi.webviewinstaller.UiState
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
    val firstProviderFocus = remember { FocusRequester() }

    // Land on the provider list rather than the back arrow: choosing a provider is the whole point
    // of this screen and the list is what the remote should be able to walk immediately.
    LaunchedEffect(validProviders) {
        delay(150)
        runCatching {
            if (validProviders.isEmpty()) {
                backFocus.requestFocus()
            } else {
                firstProviderFocus.requestFocus()
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
                validProviders.forEachIndexed { index, packageName ->
                    ProviderButton(
                        vm = vm,
                        status = status,
                        packageName = packageName,
                        selected = packageName == selected,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstProviderFocus)
                        } else {
                            Modifier
                        },
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
                    colors = primaryButtonColors(),
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
    modifier: Modifier = Modifier,
) {
    val info = status?.installed?.get(packageName)
    val isCurrent = status?.currentProvider == packageName
    val shape = RoundedCornerShape(12.dp)

    // Same TV focus language as the overview rows: accent border, glow and a slight lift, so the
    // selection list reads as one system with the home screen.
    Surface(
        onClick = { vm.selectProvider(packageName) },
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AccentSoft else RowBackground,
            contentColor = TextPrimary,
            focusedContainerColor = if (selected) AccentSoft else RowBackgroundFocused,
            focusedContentColor = TextPrimary,
            pressedContainerColor = AccentSoft,
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f, pressedScale = 0.995f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, RowBorder), 0.dp, shape),
            focusedBorder = Border(BorderStroke(2.dp, Accent), 0.dp, shape),
            pressedBorder = Border(BorderStroke(2.dp, Accent), 0.dp, shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            glow = Glow.None,
            focusedGlow = Glow(Accent.copy(alpha = 0.35f), 14.dp),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = vm.label(packageName),
                color = TextPrimary,
                fontSize = BodySize,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            info?.versionName?.let {
                Text(
                    text = it,
                    color = Muted,
                    fontSize = SmallSize,
                )
            }
            if (isCurrent) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.default_current),
                    color = Ok,
                    fontSize = SmallSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "✓",
                    color = Accent,
                    fontSize = BodySize,
                    fontWeight = FontWeight.Bold,
                )
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
            .background(CardBackgroundMuted, RoundedCornerShape(12.dp))
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
