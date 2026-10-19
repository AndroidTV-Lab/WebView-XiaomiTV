package com.tvlaunchers.webviewinstaller

import android.content.Context
import java.io.File

/**
 * The overlay APK ships inside the installer (`assets/WebViewOverlay.apk`) and that copy is what
 * gets deployed. The file published on the release is only an archive of the same build.
 */
object BundledOverlay {
    const val ASSET = "WebViewOverlay.apk"

    /**
     * Copies the bundled overlay into app-private storage. Root cannot read the installer's
     * asset directory directly, and [RootPaths.downloadDir] is a path the TvService process can
     * resolve.
     */
    fun extract(context: Context): File {
        val dest = File(RootPaths.downloadDir(context), ASSET)
        context.assets.open(ASSET).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    /** SHA-256 of the bundled copy, or null when this build has no overlay asset. */
    fun hash(context: Context): String? = runCatching {
        context.assets.open(ASSET).use { Http.sha256(it) }
    }.getOrNull()
}
