package com.tvlaunchers.webviewinstaller

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StatusRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun load(fetchRemote: Boolean): DeviceStatus = withContext(Dispatchers.IO) {
        val workDir = RootPaths.workDir(context)
        val probe = RootShell.probe(workDir)
        var rootAvailable = probe.available
        var rootError = probe.error

        val installed = readInstalled()

        var currentProvider: String? = null
        var currentVersion: String? = null
        var validProviders: List<String> = emptyList()
        var overlayRecognized = false

        if (rootAvailable) {
            val result = RootShell.run(
                workDir = workDir,
                scriptBody = RootTasks.status(),
                timeoutMs = 90_000,
                readLogChars = 1_000_000,
            )
            if (result.ok) {
                val log = result.log ?: ""
                val webviewBlock = section(log, "=== WEBVIEWUPDATE ===", "=== OVERLAY ===")
                val overlayBlock = section(log, "=== OVERLAY ===", "=== SETTING ===")
                val currentMatch = Regex(
                    "Current WebView package \\(name, version\\):\\s*\\(([^,]+),\\s*([^)]+)\\)"
                ).find(webviewBlock)
                currentProvider = currentMatch?.groupValues?.get(1)?.trim()
                currentVersion = currentMatch?.groupValues?.get(2)?.trim()
                validProviders = Regex("Valid package\\s+([A-Za-z0-9_.]+)")
                    .findAll(webviewBlock)
                    .map { it.groupValues[1] }
                    .distinct()
                    .toList()
                overlayRecognized = isOverlayEnabled(overlayBlock)
            } else {
                rootError = result.message
                rootAvailable = true
            }
        }

        if (validProviders.isEmpty()) {
            validProviders = Known.ALL_PACKAGES.filter { installed[it]?.enabled == true }
        }

        val setting = Settings.Global.getString(
            context.contentResolver,
            "webview_provider",
        )?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

        val overlayFile = File(Known.OVERLAY_PATH)
        val overlayExists = overlayFile.exists()
        val overlayHash = if (overlayExists) {
            runCatching { Http.sha256(overlayFile) }.getOrNull()
        } else {
            null
        }

        // The overlay travels inside the installer, so "the version to deploy" is this build's
        // bundled asset, not a remote file: compare the deployed copy against it.
        val bundledOverlayHash = BundledOverlay.hash(context)
        var assets: List<WebViewAsset> = emptyList()
        var manifestError: String? = null

        if (fetchRemote) {
            val manifestResult = runCatching {
                Http.fetchText(Releases.candidates(Releases.MANIFEST_URL), 6000)
            }
            if (manifestResult.isSuccess) {
                runCatching { Releases.parseManifest(manifestResult.getOrThrow().text) }
                    .onSuccess { assets = it }
                    .onFailure { manifestError = it.message ?: "manifest parse error" }
            } else {
                manifestError = manifestResult.exceptionOrNull()?.message ?: "manifest fetch failed"
            }
        } else {
            assets = emptyList()
        }

        var pendingOverlay = prefs.getBoolean(KEY_OVERLAY, false)
        var pendingProvider = prefs.getString(KEY_PROVIDER, null)
        if (pendingOverlay && overlayRecognized) {
            prefs.edit().putBoolean(KEY_OVERLAY, false).apply()
            pendingOverlay = false
        }
        if (pendingProvider != null && currentProvider == pendingProvider) {
            prefs.edit().remove(KEY_PROVIDER).apply()
            pendingProvider = null
        }

        DeviceStatus(
            device = Build.DEVICE ?: "",
            model = Build.MODEL ?: "",
            sdk = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE ?: "",
            rootAvailable = rootAvailable,
            rootUid = probe.uid,
            rootError = rootError,
            storageDir = RootPaths.publicDownloadDir().absolutePath,
            webviewSetting = setting,
            currentProvider = currentProvider,
            currentVersion = currentVersion,
            validProviders = validProviders,
            overlayExists = overlayExists,
            overlayRecognized = overlayRecognized,
            overlayHash = overlayHash,
            bundledOverlayHash = bundledOverlayHash,
            pendingOverlayReboot = pendingOverlay,
            pendingProvider = pendingProvider,
            installed = installed,
            assets = assets,
            manifestError = manifestError,
        )
    }

    fun setPendingOverlay() {
        prefs.edit().putBoolean(KEY_OVERLAY, true).apply()
    }

    fun setPendingProvider(packageName: String) {
        prefs.edit().putString(KEY_PROVIDER, packageName).apply()
    }

    fun clearPending() {
        prefs.edit().remove(KEY_OVERLAY).remove(KEY_PROVIDER).apply()
    }

    private fun readInstalled(): Map<String, InstalledPackage> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, InstalledPackage>()
        for (packageName in Known.ALL_PACKAGES) {
            try {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                val enabledSetting = pm.getApplicationEnabledSetting(packageName)
                val enabled = info.applicationInfo?.enabled != false &&
                    enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                    enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                @Suppress("DEPRECATION")
                val signer = info.signatures?.firstOrNull()?.let { Http.sha256(it.toByteArray()) }
                result[packageName] = InstalledPackage(
                    packageName = packageName,
                    versionName = info.versionName,
                    versionCode = info.longVersionCode,
                    enabled = enabled,
                    signerSha256 = signer,
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed.
            }
        }
        return result
    }

    private fun isOverlayEnabled(overlayDump: String): Boolean {
        val index = overlayDump.indexOf(Known.OVERLAY_PACKAGE)
        if (index < 0) return false
        val window = overlayDump.substring(
            index,
            minOf(overlayDump.length, index + 2000),
        )
        return window.contains("STATE_ENABLED_STATIC") ||
            window.contains("STATE_ENABLED") ||
            window.contains("mEnabled=true")
    }

    private fun section(text: String, start: String, end: String): String {
        val startIndex = text.indexOf(start)
        if (startIndex < 0) return ""
        val bodyStart = startIndex + start.length
        val endIndex = text.indexOf(end, bodyStart)
        return if (endIndex < 0) text.substring(bodyStart) else text.substring(bodyStart, endIndex)
    }

    companion object {
        private const val PREFS = "pending"
        private const val KEY_OVERLAY = "pending_overlay"
        private const val KEY_PROVIDER = "pending_provider"
    }
}
