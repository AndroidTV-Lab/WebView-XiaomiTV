package com.tvlaunchers.webviewinstaller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.tvlaunchers.webviewinstaller.DialogState
import com.tvlaunchers.webviewinstaller.InstallerViewModel
import com.tvlaunchers.webviewinstaller.R
import com.tvlaunchers.webviewinstaller.UiState
import kotlinx.coroutines.delay

@Composable
fun DialogHost(vm: InstallerViewModel, state: UiState, dialog: DialogState) {
    Dialog(
        onDismissRequest = {
            when (dialog) {
                is DialogState.NonMulan -> vm.exitApp()
                is DialogState.DeleteDownload -> vm.keepDownloadedApk()
                else -> vm.dismissDialog()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(720.dp)
                    .background(CardBackground, RoundedCornerShape(18.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                    .padding(28.dp),
            ) {
                when (dialog) {
                    is DialogState.NonMulan -> NonMulanDialog(vm, dialog)
                    is DialogState.Error -> ErrorDialog(vm, dialog)
                    is DialogState.ConfirmDownload -> ConfirmDownloadDialog(vm, dialog)
                    is DialogState.Manage -> ManageDialog(vm, state, dialog)
                    is DialogState.ConfirmOverlay -> ConfirmOverlayDialog(vm)
                    is DialogState.ConfirmRestore -> ConfirmRestoreDialog(vm)
                    is DialogState.ConfirmUninstall -> ConfirmUninstallDialog(vm, dialog)
                    is DialogState.Reboot -> RebootDialog(vm, dialog)
                    is DialogState.DeleteDownload -> DeleteDownloadDialog(vm, dialog)
                    is DialogState.SetDefaultAfterInstall -> SetDefaultAfterInstallDialog(vm, dialog)
                    is DialogState.OverlayRequired -> OverlayRequiredDialog(vm, dialog)
                }
            }
        }
    }
}

@Composable
private fun NonMulanDialog(vm: InstallerViewModel, dialog: DialogState.NonMulan) {
    Column {
        DialogTitle(stringResource(R.string.non_mulan_title))
        DialogBody(stringResource(R.string.non_mulan_message, dialog.device))
        DialogButtons(
            cancelText = stringResource(R.string.non_mulan_exit),
            confirmText = stringResource(R.string.non_mulan_continue),
            onCancel = { vm.exitApp() },
            onConfirm = { vm.continueOnNonMulan() },
            focusConfirm = false,
        )
    }
}

@Composable
private fun ConfirmDownloadDialog(vm: InstallerViewModel, dialog: DialogState.ConfirmDownload) {
    val asset = dialog.asset
    Column {
        DialogTitle(stringResource(R.string.download_confirm_title))
        DialogBody(
            stringResource(
                R.string.download_confirm_message,
                vm.label(asset.packageName),
                asset.versionName ?: stringResource(R.string.unknown),
                com.tvlaunchers.webviewinstaller.Http.formatSize(asset.size),
            )
        )
        DialogButtons(
            cancelText = stringResource(R.string.close),
            confirmText = stringResource(R.string.download_start),
            onCancel = { vm.dismissDialog() },
            onConfirm = { vm.confirmDownload(asset) },
            focusConfirm = true,
        )
    }
}

@Composable
private fun ManageDialog(
    vm: InstallerViewModel,
    state: UiState,
    dialog: DialogState.Manage,
) {
    val installed = state.status?.installed?.get(dialog.packageName)
    val asset = state.status?.assets?.firstOrNull { it.packageName == dialog.packageName }
    val hasUpdate = installed != null && asset?.versionName != null &&
        asset.versionName != installed.versionName
    val hasAsset = asset != null
    val primaryFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { primaryFocus.requestFocus() }
    }

    Column {
        DialogTitle(vm.label(dialog.packageName))
        DialogBody(
            stringResource(
                R.string.manage_message,
                installed?.versionName ?: stringResource(R.string.not_installed),
                asset?.versionName ?: stringResource(R.string.install_asset_missing),
            )
        )
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = { vm.dismissDialog() }, colors = secondaryButtonColors()) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { vm.requestUninstall(dialog.packageName) },
                colors = dangerButtonColors(),
                modifier = if (!hasAsset) Modifier.focusRequester(primaryFocus) else Modifier,
            ) {
                Text(stringResource(R.string.action_uninstall))
            }
            if (hasAsset) {
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { asset?.let { vm.confirmDownload(it) } },
                    colors = if (hasUpdate) secondaryButtonColors() else primaryButtonColors(),
                    modifier = if (hasUpdate) Modifier else Modifier.focusRequester(primaryFocus),
                ) {
                    Text(stringResource(R.string.install_button_reinstall))
                }
                if (hasUpdate) {
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { asset?.let { vm.confirmDownload(it) } },
                        colors = primaryButtonColors(),
                        modifier = Modifier.focusRequester(primaryFocus),
                    ) {
                        Text(stringResource(R.string.install_button_update))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmOverlayDialog(vm: InstallerViewModel) {
    Column {
        DialogTitle(stringResource(R.string.overlay_confirm_title))
        DialogBody(stringResource(R.string.overlay_confirm_message))
        DialogButtons(
            cancelText = stringResource(R.string.close),
            confirmText = stringResource(R.string.overlay_confirm_yes),
            onCancel = { vm.dismissDialog() },
            onConfirm = { vm.confirmOverlayDeploy() },
            focusConfirm = true,
        )
    }
}

@Composable
private fun ConfirmRestoreDialog(vm: InstallerViewModel) {
    Column {
        DialogTitle(stringResource(R.string.restore_title))
        DialogBody(stringResource(R.string.restore_message))
        DialogButtons(
            cancelText = stringResource(R.string.restore_no),
            confirmText = stringResource(R.string.restore_yes),
            onCancel = { vm.dismissDialog() },
            onConfirm = { vm.confirmRestore() },
            focusConfirm = false,
            confirmColors = ButtonDefaults.colors(
                containerColor = Color(0xFF5A1F1F),
                contentColor = Color(0xFFFFCDD2),
                focusedContainerColor = Danger,
                focusedContentColor = Color.White,
            ),
        )
    }
}

@Composable
private fun ConfirmUninstallDialog(
    vm: InstallerViewModel,
    dialog: DialogState.ConfirmUninstall,
) {
    val name = vm.label(dialog.packageName)
    Column {
        DialogTitle(stringResource(R.string.uninstall_title, name))
        DialogBody(stringResource(R.string.uninstall_message, name))
        DialogButtons(
            cancelText = stringResource(R.string.uninstall_no),
            confirmText = stringResource(R.string.uninstall_yes),
            onCancel = { vm.dismissDialog() },
            onConfirm = { vm.confirmUninstall(dialog.packageName) },
            focusConfirm = false,
            confirmColors = dangerButtonColors(),
        )
    }
}

@Composable
private fun RebootDialog(vm: InstallerViewModel, dialog: DialogState.Reboot) {
    Column {
        DialogTitle(stringResource(R.string.reboot_title))
        DialogBody(dialog.message)
        DialogButtons(
            cancelText = stringResource(R.string.reboot_later),
            confirmText = stringResource(R.string.reboot_now),
            onCancel = { vm.dismissDialog() },
            onConfirm = { vm.rebootNow() },
            focusConfirm = false,
        )
    }
}

@Composable
private fun DeleteDownloadDialog(vm: InstallerViewModel, dialog: DialogState.DeleteDownload) {
    Column {
        DialogTitle(stringResource(R.string.delete_download_title))
        DialogBody(
            stringResource(
                R.string.delete_download_message,
                vm.label(dialog.packageName),
                dialog.publicName,
            )
        )
        DialogButtons(
            cancelText = stringResource(R.string.delete_download_no),
            confirmText = stringResource(R.string.delete_download_yes),
            onCancel = { vm.keepDownloadedApk() },
            onConfirm = { vm.deleteDownloadedApk() },
            focusConfirm = false,
            confirmColors = dangerButtonColors(),
        )
    }
}

@Composable
private fun SetDefaultAfterInstallDialog(
    vm: InstallerViewModel,
    dialog: DialogState.SetDefaultAfterInstall,
) {
    Column {
        DialogTitle(stringResource(R.string.set_default_after_install_title))
        DialogBody(
            stringResource(
                R.string.set_default_after_install_message,
                vm.label(dialog.packageName),
            )
        )
        DialogButtons(
            cancelText = stringResource(R.string.set_default_after_install_no),
            confirmText = stringResource(R.string.set_default_after_install_yes),
            onCancel = { vm.dismissDialog() },
            onConfirm = { vm.setDefaultFromDialog(dialog.packageName) },
            focusConfirm = false,
        )
    }
}

@Composable
private fun OverlayRequiredDialog(vm: InstallerViewModel, dialog: DialogState.OverlayRequired) {
    Column {
        DialogTitle(stringResource(R.string.overlay_confirm_title))
        DialogBody(stringResource(R.string.install_overlay_hint, vm.label(dialog.packageName)))
        DialogButtons(
            cancelText = stringResource(R.string.reboot_later),
            confirmText = stringResource(R.string.install_overlay_go),
            onCancel = { vm.dismissDialog() },
            onConfirm = {
                vm.dismissDialog()
                vm.requestOverlayDeploy()
            },
            focusConfirm = true,
        )
    }
}

@Composable
private fun ErrorDialog(vm: InstallerViewModel, dialog: DialogState.Error) {
    var expanded by remember { mutableStateOf(false) }
    val okFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { okFocus.requestFocus() }
    }

    Column {
        DialogTitle(dialog.title)
        if (dialog.message.isNotBlank()) {
            DialogBody(dialog.message)
        }

        val log = dialog.log
        if (!log.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            if (!expanded) {
                Button(onClick = { expanded = true }, colors = secondaryButtonColors()) {
                    Text(stringResource(R.string.error_log))
                }
            } else {
                Text(
                    text = stringResource(R.string.log_title),
                    color = Accent,
                    fontSize = SectionTitleSize,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .background(Background, RoundedCornerShape(8.dp))
                        .focusable()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text = log.takeLast(MAX_LOG_CHARS),
                        color = Muted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = { vm.dismissDialog() },
                modifier = Modifier.focusRequester(okFocus),
                colors = primaryButtonColors(),
            ) {
                Text(stringResource(R.string.error_ok))
            }
        }
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun DialogBody(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(
        text = text,
        color = TextPrimary,
        fontSize = BodySize,
        lineHeight = 25.sp,
    )
}

@Composable
private fun DialogButtons(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    focusConfirm: Boolean,
    confirmColors: ButtonColors = primaryButtonColors(),
) {
    val cancelFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching {
            if (focusConfirm) confirmFocus.requestFocus() else cancelFocus.requestFocus()
        }
    }

    Spacer(Modifier.height(22.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(
            onClick = onCancel,
            modifier = Modifier.focusRequester(cancelFocus),
            colors = secondaryButtonColors(),
        ) {
            Text(cancelText)
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.focusRequester(confirmFocus),
            colors = confirmColors,
        ) {
            Text(confirmText)
        }
    }
}

private const val MAX_LOG_CHARS = 20000

/** Confirm/primary action: accent fill on focus, dark text for contrast against the bright accent. */
@Composable
fun primaryButtonColors(): ButtonColors = ButtonDefaults.colors(
    containerColor = AccentSoft,
    contentColor = TextPrimary,
    focusedContainerColor = Accent,
    focusedContentColor = Color(0xFF00202E),
)

/** Destructive action: muted red fill, bright red focus, still obvious on a TV. */
@Composable
fun dangerButtonColors(): ButtonColors = ButtonDefaults.colors(
    containerColor = Color(0xFF5A1F1F),
    contentColor = Color(0xFFFFCDD2),
    focusedContainerColor = Danger,
    focusedContentColor = Color.White,
)

/** Cancel/secondary action: neutral fill, same accent focus treatment so D-pad focus is obvious. */
@Composable
fun secondaryButtonColors(): ButtonColors = ButtonDefaults.colors(
    containerColor = RowBackground,
    contentColor = TextPrimary,
    focusedContainerColor = Accent,
    focusedContentColor = Color(0xFF00202E),
)
