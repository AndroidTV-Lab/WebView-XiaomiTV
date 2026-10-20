package com.tvlaunchers.webviewinstaller

import androidx.annotation.StringRes

enum class Screen { Status, Default }

sealed interface DialogState {
    data class NonMulan(val device: String) : DialogState
    data class Error(val title: String, val message: String, val log: String?) : DialogState
    data class ConfirmDownload(val asset: WebViewAsset) : DialogState
    data class Variants(val packageName: String) : DialogState
    data class ConfirmReplace(
        val asset: WebViewAsset,
        val path: String,
    ) : DialogState
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

data class KnownTarget(
    val id: String,
    val packageName: String,
    @StringRes val nameRes: Int,
    val recommended: Boolean = false,
)

object Known {
    const val OVERLAY_PACKAGE = "com.android.webviewoverlay"
    /** Android 9's standard location for preinstalled RROs. */
    const val OVERLAY_DIR = "/product/overlay"
    const val OVERLAY_PATH = "$OVERLAY_DIR/WebViewOverlay.apk"
    const val AOSP_PACKAGE = "com.android.webview"

    /**
     * SHA-256 (uppercase hex) of the DER certificate of the project signing key
     * (`thorium-overlay.jks`). Every patched WebView variant is signed with that key, and the
     * overlay trusts the same certificate. Keep this in sync with
     * overlay/res/xml/config_webview_packages.xml.
     */
    const val PROJECT_SIGNER_SHA256 =
        "0BD9927722CB322B795FC56BB06FC2FFC6B5F839559B93BEBED68CB4CE2486FD"
    const val SIGNER_PROJECT = "project"
    const val SIGNER_VENDOR = "vendor"

    // Order drives the WebView rows on the overview's right column: the recommended family comes
    // first so the remote lands on it.
    val TARGETS = listOf(
        KnownTarget("aosmium", "org.axpos.aosmium_wv", R.string.name_aosmium, recommended = true),
        KnownTarget("thorium", "com.thorium.webview", R.string.name_thorium),
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
    /** Localized display name, e.g. "Thorium 标准版（关闭 WebGPU）". */
    val title: String? = null,
    val titleEn: String? = null,
    val description: String? = null,
    val descriptionEn: String? = null,
    /** Short machine-readable variant name such as "stock" or "nowebgpu". */
    val variant: String? = null,
    /** null means "not stated by the manifest". */
    val webgpu: Boolean? = null,
    /** [Known.SIGNER_PROJECT] or [Known.SIGNER_VENDOR]. */
    val signer: String? = null,
    val recommended: Boolean = false,
)

data class InstalledPackage(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val enabled: Boolean,
    /** SHA-256 (uppercase hex) of the signing certificate, or null when unavailable. */
    val signerSha256: String? = null,
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
