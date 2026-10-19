package com.tvlaunchers.webviewinstaller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Downloads go to shared storage when possible. Scripts and logs stay in the app-private
 * directory: the TvService diagnostic process (uid 0) reads them there, and the app can always
 * read its own logs back regardless of the storage permission.
 */
object RootPaths {
    fun downloadBaseDir(context: Context): File {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        val root = if (granted) {
            File(Environment.getExternalStorageDirectory(), "Download")
        } else {
            context.getExternalFilesDir(null) ?: context.filesDir
        }
        return File(root, "WebViewInstaller").apply { mkdirs() }
    }

    fun workDir(context: Context): File = File(filesDir(context), "wvinstaller").apply { mkdirs() }

    /**
     * [Context.getFilesDir] reports `/data/user/0/<pkg>/files`, but the TvService diagnostic
     * process (`misysdiagnose`) cannot resolve `/data/user/0` in its mount namespace. The
     * `/data/data` spelling points at the same directory and is readable there.
     */
    private fun filesDir(context: Context): File {
        val direct = File("/data/data/${context.packageName}/files")
        return if (direct.isDirectory) direct else context.filesDir
    }

    /**
     * Directory that holds the scripts handed to TvService.
     *
     * TvService builds `cat <script> > /data/diagnosis/command.sh` inside a fixed 99-character
     * buffer, so any script path longer than 68 characters gets its destination truncated and the
     * script silently never runs. The app-private files root
     * (`/data/data/<pkg>/files`, about 50 characters) leaves enough room for [RootShell]'s short
     * script names, unlike the nested work directory.
     */
    fun scriptDir(workDir: File): File = workDir.parentFile ?: workDir

    fun logDir(workDir: File): File = File(workDir, "logs").apply { mkdirs() }

    fun downloadDir(context: Context): File =
        File(downloadBaseDir(context), "downloads").apply { mkdirs() }
}
