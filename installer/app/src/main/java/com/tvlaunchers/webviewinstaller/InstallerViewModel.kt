package com.tvlaunchers.webviewinstaller

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InstallerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = StatusRepository(application)
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var started = false
    private var exitAfterDialog = false
    private var activeAsset: WebViewAsset? = null
    private var lastFailedAsset: WebViewAsset? = null
    private var handledDownloadPath: String? = null
    private var toastSequence = 0

    init {
        viewModelScope.launch {
            DownloadService.state.collect { download ->
                _ui.update { it.copy(download = download) }
                val asset = activeAsset
                if (asset != null && download.assetId == asset.id) {
                    if (download.done && download.path != null) {
                        if (handledDownloadPath != download.path) {
                            handledDownloadPath = download.path
                            activeAsset = null
                            onDownloaded(asset, download.path)
                        }
                    } else if (download.error != null) {
                        activeAsset = null
                        lastFailedAsset = asset
                        showError(
                            getString(R.string.error_title),
                            getString(R.string.install_download_failed, download.error),
                        )
                    } else if (download.cancelled) {
                        activeAsset = null
                        toast(getString(R.string.download_cancelled))
                    }
                }
            }
        }

        val device = Build.DEVICE ?: ""
        if (device.equals("mulan", ignoreCase = true)) {
            start()
        } else {
            _ui.update {
                it.copy(
                    loading = false,
                    rootChecking = false,
                    dialog = DialogState.NonMulan(
                        device.ifBlank { getString(R.string.unknown) }
                    ),
                )
            }
        }
    }

    fun refresh(remote: Boolean = true) {
        if (_ui.value.refreshing || _ui.value.busyLabel != null) return
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    refreshing = true,
                    manifestLoading = remote && it.status == null,
                )
            }
            val status = try {
                repo.load(remote)
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        rootChecking = false,
                        manifestLoading = false,
                    )
                }
                showError(
                    getString(R.string.error_title),
                    e.message ?: e.javaClass.simpleName,
                )
                return@launch
            }
            _ui.update {
                it.copy(
                    status = status,
                    loading = false,
                    refreshing = false,
                    rootChecking = false,
                    manifestLoading = false,
                )
            }
        }
    }

    fun onStoragePermissionChanged() {
        refresh(remote = false)
    }

    fun openScreen(screen: Screen) {
        _ui.update { it.copy(screen = screen) }
    }

    fun back() {
        _ui.update { it.copy(screen = Screen.Status) }
    }

    fun dismissDialog() {
        _ui.update { it.copy(dialog = null) }
        if (exitAfterDialog) {
            exitAfterDialog = false
            _ui.update { it.copy(exitRequested = true) }
        }
    }

    fun exitApp() {
        _ui.update { it.copy(exitRequested = true) }
    }

    fun continueOnNonMulan() {
        if (!File("/vendor/overlay").isDirectory) {
            _ui.update {
                it.copy(
                    dialog = DialogState.Error(
                        getString(R.string.vendor_missing_title),
                        getString(R.string.vendor_missing_message),
                        null,
                    )
                )
            }
            exitAfterDialog = true
            return
        }
        _ui.update { it.copy(dialog = null) }
        start()
    }

    fun selectProvider(packageName: String) {
        _ui.update { it.copy(selectedProvider = packageName) }
    }

    fun requestDownload(asset: WebViewAsset) {
        if (_ui.value.download.active) return
        if (!requireRoot()) return
        _ui.update { it.copy(dialog = DialogState.ConfirmDownload(asset)) }
    }

    fun confirmDownload(asset: WebViewAsset) {
        val context = getApplication<Application>()
        if (!requireRoot()) return
        activeAsset = asset
        lastFailedAsset = asset
        handledDownloadPath = null
        val dest = File(RootPaths.downloadDir(context), "${asset.id}.apk")
        DownloadService.start(
            context = context,
            asset = asset,
            displayName = label(asset.packageName),
            dest = dest,
            urls = Releases.candidates(Releases.assetUrl(asset.fileName)),
        )
        _ui.update { it.copy(dialog = null) }
    }

    fun retryDownload() {
        val asset = lastFailedAsset ?: return
        if (!requireRoot()) return
        confirmDownload(asset)
    }

    fun cancelDownload() {
        DownloadService.cancel(getApplication())
    }

    fun requestOverlayDeploy() {
        if (!requireRoot()) return
        _ui.update { it.copy(dialog = DialogState.ConfirmOverlay) }
    }

    fun confirmOverlayDeploy() {
        _ui.update { it.copy(dialog = null) }
        viewModelScope.launch {
            _ui.update { it.copy(busyLabel = getString(R.string.overlay_running)) }
            try {
                val context = getApplication<Application>()
                val dest = File(RootPaths.downloadDir(context), "WebViewOverlay.apk")
                val expected = _ui.value.status?.remoteOverlayHash
                    ?: Http.fetchText(
                        Releases.candidates(Releases.OVERLAY_SHA_URL),
                        8000,
                    ).text.let { Http.parseSha256(it) }
                if (expected == null) throw IOException("overlay sha256 is unavailable")
                Http.downloadSmall(
                    Releases.candidates(Releases.OVERLAY_URL),
                    dest,
                    expected,
                )
                val info = context.packageManager.getPackageArchiveInfo(dest.absolutePath, 0)
                if (info?.packageName != Known.OVERLAY_PACKAGE) {
                    throw IOException(
                        getString(
                            R.string.overlay_package_mismatch,
                            info?.packageName ?: getString(R.string.unknown),
                        )
                    )
                }
                val result = RootShell.run(
                    workDir = RootPaths.workDir(context),
                    scriptBody = RootTasks.overlay(dest.absolutePath),
                    timeoutMs = 120_000,
                )
                if (!result.ok) {
                    _ui.update { it.copy(busyLabel = null) }
                    showError(
                        title = getString(R.string.error_title),
                        message = getString(R.string.overlay_failed, result.message ?: ""),
                        log = result.log,
                    )
                    return@launch
                }
                repo.setPendingOverlay()
                _ui.update { it.copy(busyLabel = null) }
                toast(getString(R.string.overlay_success))
                refresh(remote = false)
                _ui.update {
                    it.copy(dialog = DialogState.Reboot(getString(R.string.reboot_message)))
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busyLabel = null) }
                showError(
                    title = getString(R.string.error_title),
                    message = getString(
                        R.string.overlay_failed,
                        e.message ?: e.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    fun applyDefault(packageName: String) {
        if (!requireRoot()) return
        if (!PACKAGE_NAME.matches(packageName)) {
            showError(
                title = getString(R.string.error_title),
                message = getString(R.string.default_failed, packageName),
            )
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busyLabel = getString(R.string.default_running)) }
            val result = RootShell.run(
                workDir = RootPaths.workDir(getApplication()),
                scriptBody = RootTasks.setProvider(packageName),
                timeoutMs = 60_000,
            )
            if (!result.ok) {
                _ui.update { it.copy(busyLabel = null) }
                showError(
                    title = getString(R.string.error_title),
                    message = getString(R.string.default_failed, result.message ?: ""),
                    log = result.log,
                )
                return@launch
            }
            repo.setPendingProvider(packageName)
            _ui.update { it.copy(busyLabel = null) }
            toast(getString(R.string.default_applied, label(packageName)))
            refresh(remote = false)
            _ui.update {
                it.copy(
                    screen = Screen.Status,
                    dialog = DialogState.Reboot(getString(R.string.reboot_message)),
                )
            }
        }
    }

    fun setDefaultFromDialog(packageName: String) {
        _ui.update { it.copy(dialog = null) }
        applyDefault(packageName)
    }

    fun requestRestore() {
        if (!requireRoot()) return
        _ui.update { it.copy(dialog = DialogState.ConfirmRestore) }
    }

    fun confirmRestore() {
        _ui.update { it.copy(dialog = null) }
        viewModelScope.launch {
            _ui.update { it.copy(busyLabel = getString(R.string.restore_running)) }
            val result = RootShell.run(
                workDir = RootPaths.workDir(getApplication()),
                scriptBody = RootTasks.restore(),
                timeoutMs = 240_000,
            )
            if (!result.ok) {
                _ui.update { it.copy(busyLabel = null) }
                showError(
                    title = getString(R.string.error_title),
                    message = getString(R.string.restore_failed, result.message ?: ""),
                    log = result.log,
                )
                return@launch
            }
            repo.clearPending()
            _ui.update { it.copy(busyLabel = null) }
            toast(getString(R.string.restore_success))
            refresh(remote = false)
            _ui.update {
                it.copy(
                    screen = Screen.Status,
                    dialog = DialogState.Reboot(getString(R.string.reboot_message)),
                )
            }
        }
    }

    fun rebootNow() {
        _ui.update { it.copy(dialog = null) }
        viewModelScope.launch {
            RootShell.fire(RootPaths.workDir(getApplication()), RootTasks.reboot())
            toast(getString(R.string.reboot_sent))
        }
    }

    fun label(packageName: String): String {
        val target = Known.targetFor(packageName)
        if (target != null) return getString(target.nameRes)
        if (packageName == Known.AOSP_PACKAGE) return getString(R.string.name_aosp)
        return packageName
    }

    private fun start() {
        if (started) return
        started = true
        if (!File("/vendor/overlay").isDirectory) {
            _ui.update {
                it.copy(
                    loading = false,
                    rootChecking = false,
                    dialog = DialogState.Error(
                        getString(R.string.vendor_missing_title),
                        getString(R.string.vendor_missing_message),
                        null,
                    ),
                )
            }
            exitAfterDialog = true
            return
        }
        refresh(remote = true)
    }

    private fun onDownloaded(asset: WebViewAsset, path: String) {
        val context = getApplication<Application>()
        val info = context.packageManager.getPackageArchiveInfo(path, 0)
        if (info?.packageName != asset.packageName) {
            showError(
                getString(R.string.error_title),
                getString(
                    R.string.overlay_package_mismatch,
                    info?.packageName ?: getString(R.string.unknown),
                ),
            )
            return
        }
        installApk(asset, path)
    }

    private fun installApk(asset: WebViewAsset, path: String) {
        viewModelScope.launch {
            _ui.update {
                it.copy(busyLabel = getString(R.string.install_running, label(asset.packageName)))
            }
            val result = RootShell.run(
                workDir = RootPaths.workDir(getApplication()),
                scriptBody = RootTasks.install(path, asset.packageName),
                timeoutMs = 300_000,
            )
            _ui.update { it.copy(busyLabel = null) }
            if (!result.ok) {
                showError(
                    title = getString(R.string.error_title),
                    message = getString(R.string.install_failed, result.message ?: ""),
                    log = result.log,
                )
                return@launch
            }
            toast(getString(R.string.install_success, label(asset.packageName)))
            refresh(remote = false)
            val overlayReady = _ui.value.status?.overlayRecognized == true
            _ui.update {
                it.copy(
                    dialog = if (overlayReady) {
                        DialogState.SetDefaultAfterInstall(asset.packageName)
                    } else {
                        DialogState.OverlayRequired(asset.packageName)
                    },
                )
            }
        }
    }

    private fun requireRoot(): Boolean {
        if (_ui.value.status?.rootAvailable == true) return true
        _ui.update {
            it.copy(
                dialog = DialogState.Error(
                    getString(R.string.need_root_title),
                    getString(R.string.need_root_message),
                    null,
                )
            )
        }
        return false
    }

    private fun showError(title: String, message: String, log: String? = null) {
        _ui.update { it.copy(dialog = DialogState.Error(title, message, log)) }
    }

    private fun toast(message: String) {
        val sequence = ++toastSequence
        _ui.update { it.copy(toast = message) }
        viewModelScope.launch {
            delay(4000)
            if (sequence == toastSequence) {
                _ui.update { it.copy(toast = null) }
            }
        }
    }

    private fun getString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    companion object {
        private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    }
}
