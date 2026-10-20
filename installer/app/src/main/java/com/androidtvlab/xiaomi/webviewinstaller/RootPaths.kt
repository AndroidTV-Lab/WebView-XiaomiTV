package com.androidtvlab.xiaomi.webviewinstaller

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * The APK is downloaded into the app-private directory, which the app can always write. This
 * firmware's `platform.xml` maps WRITE_EXTERNAL_STORAGE to no supplementary gids, so an app
 * process cannot write (or read) `/sdcard/Download` even when the permission is granted. The
 * root task that installs the APK copies it from here to the public Downloads directory.
 *
 * Scripts and logs stay in the same app-private root: the TvService diagnostic process (uid 0)
 * reads them there, and the app can always read its own logs back.
 */
object RootPaths {
    /** Where the user finds the APKs; written by the root task, not by the app process. */
    fun publicDownloadDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

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

    /** Working directory for in-flight downloads; only the app and the root shell need it. */
    fun downloadDir(context: Context): File =
        File(filesDir(context), "downloads").apply { mkdirs() }
}
