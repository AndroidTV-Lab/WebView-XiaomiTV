package com.tvlaunchers.webviewinstaller

import androidx.annotation.StringRes

enum class Screen { Status, Default }

sealed interface DialogState {
    data class NonMulan(val device: String) : DialogState
    data class Error(val title: String, val message: String, val log: String?) : DialogState
    data class ConfirmDownload(val asset: WebViewAsset) : DialogState
    data class Manage(val packageName: String) : DialogState
    data object ConfirmOverlay : DialogState
    data object ConfirmRestore : DialogState
    data class ConfirmUninstall(val packageName: String) : DialogState
    data class Reboot(val message: String) : DialogState
    data class DeleteDownload(
        val packageName: String,
        val publicName: String,
        val privatePath: String,
    ) : DialogState
    data class SetDefaultAfterInstall(val packageName: String) : DialogState
    data class OverlayRequired(val packageName: String) : DialogState
}

data class KnownTarget(val id: String, val packageName: String, @StringRes val nameRes: Int)

object Known {
    const val OVERLAY_PACKAGE = "com.android.webviewoverlay"
    /** Android 9's standard location for preinstalled RROs. */
    const val OVERLAY_DIR = "/product/overlay"
    const val OVERLAY_PATH = "$OVERLAY_DIR/WebViewOverlay.apk"
    const val AOSP_PACKAGE = "com.android.webview"

    val TARGETS = listOf(
        KnownTarget("thorium", "com.thorium.webview", R.string.name_thorium),
        KnownTarget("aosmium", "org.axpos.aosmium_wv", R.string.name_aosmium),
        KnownTarget("google", "com.google.android.webview", R.string.name_google),
    )

    val ALL_PACKAGES = TARGETS.map { it.packageName } + AOSP_PACKAGE

    fun targetFor(packageName: String): KnownTarget? =
        TARGETS.firstOrNull { it.packageName == packageName }
}

data class WebViewAsset(
    val id: String,
    val packageName: String,
    val fileName: String,
    val sha256: String,
    val size: Long,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val abi: String?,
)

data class InstalledPackage(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val enabled: Boolean,
)

data class DeviceStatus(
    val device: String,
    val model: String,
    val sdk: Int,
    val androidRelease: String,
    val rootAvailable: Boolean,
    val rootUid: String?,
    val rootError: String?,
    val storageDir: String,
    val webviewSetting: String?,
    val currentProvider: String?,
    val currentVersion: String?,
    val validProviders: List<String>,
    val overlayExists: Boolean,
    val overlayRecognized: Boolean,
    val overlayHash: String?,
    val bundledOverlayHash: String?,
    val pendingOverlayReboot: Boolean,
    val pendingProvider: String?,
    val installed: Map<String, InstalledPackage>,
    val assets: List<WebViewAsset>,
    val manifestError: String?,
)

data class DownloadState(
    val active: Boolean = false,
    val assetId: String? = null,
    val displayName: String? = null,
    val bytes: Long = 0,
    val total: Long = -1,
    val source: String? = null,
    val verifying: Boolean = false,
    val done: Boolean = false,
    val path: String? = null,
    val error: String? = null,
    val cancelled: Boolean = false,
)

data class UiState(
    val screen: Screen = Screen.Status,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val rootChecking: Boolean = true,
    val status: DeviceStatus? = null,
    val manifestLoading: Boolean = false,
    val dialog: DialogState? = null,
    val busyLabel: String? = null,
    val download: DownloadState = DownloadState(),
    val exitRequested: Boolean = false,
    val toast: String? = null,
    val selectedProvider: String? = null,
)
