package com.tvlaunchers.webviewinstaller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDownload(intent)
            ACTION_CANCEL -> {
                cancelRequested = true
                activeConnection?.disconnect()
                if (job == null) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelRequested = true
        activeConnection?.disconnect()
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startDownload(intent: Intent) {
        val assetId = intent.getStringExtra(EXTRA_ASSET_ID) ?: return
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: assetId
        val destPath = intent.getStringExtra(EXTRA_DEST) ?: return
        val expectedSha = intent.getStringExtra(EXTRA_SHA) ?: return
        val expectedSize = intent.getLongExtra(EXTRA_SIZE, -1)
        val urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: arrayListOf()

        job?.cancel()
        cancelRequested = false
        _state.value = DownloadState(
            active = true,
            assetId = assetId,
            displayName = displayName,
        )
        startForeground(
            NOTIFICATION_ID,
            buildNotification(displayName, 0, -1, indeterminate = true),
        )

        job = scope.launch {
            try {
                download(
                    urls = urls,
                    dest = File(destPath),
                    expectedSha = expectedSha,
                    expectedSize = expectedSize,
                    assetId = assetId,
                    displayName = displayName,
                )
            } catch (e: CancellationException) {
                finish(displayName, success = false, cancelled = true, error = null)
            } catch (e: Exception) {
                if (cancelRequested) {
                    finish(displayName, success = false, cancelled = true, error = null)
                } else {
                    finish(displayName, success = false, cancelled = false, error = e.message)
                }
            }
        }
    }

    private suspend fun download(
        urls: List<String>,
        dest: File,
        expectedSha: String,
        expectedSize: Long,
        assetId: String,
        displayName: String,
    ) {
        dest.parentFile?.mkdirs()

        if (dest.exists()) {
            val existing = runCatching { Http.sha256(dest) }.getOrNull()
            if (existing != null && existing.equals(expectedSha, ignoreCase = true)) {
                _state.value = _state.value.copy(
                    active = false,
                    done = true,
                    path = dest.absolutePath,
                    bytes = dest.length(),
                    total = dest.length(),
                )
                notifyFinal(displayName, success = true)
                stopSelf()
                return
            }
            dest.delete()
        }

        val part = File(dest.absolutePath + ".part")
        var lastError: Exception? = null

        for (url in urls) {
            if (cancelRequested) throw CancellationException("cancelled")
            try {
                downloadFrom(url, part, expectedSize, displayName)
                _state.update { it.copy(verifying = true, total = part.length(), bytes = part.length()) }
                updateNotification(displayName, part.length(), part.length(), indeterminate = true)
                val actual = Http.sha256(part)
                if (!actual.equals(expectedSha, ignoreCase = true)) {
                    part.delete()
                    lastError = IOException("sha256 mismatch from ${Http.hostOf(url)}")
                    continue
                }
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
                _state.update {
                    it.copy(
                        active = false,
                        done = true,
                        verifying = false,
                        path = dest.absolutePath,
                        bytes = dest.length(),
                        total = dest.length(),
                    )
                }
                notifyFinal(displayName, success = true)
                stopSelf()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                // Keep the partial file so the next mirror can resume from it.
            }
        }

        throw lastError ?: IOException("all download sources failed")
    }

    private suspend fun downloadFrom(
        url: String,
        part: File,
        expectedSize: Long,
        displayName: String,
    ) {
        val start = if (part.exists()) part.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WebViewInstaller/1.0 (Android)")
            if (start > 0) setRequestProperty("Range", "bytes=$start-")
        }
        activeConnection = connection
        try {
            val code = connection.responseCode
            if (code == 416) {
                // The partial file already covers the whole resource; let hash verification decide.
                return
            }
            if (code !in 200..299) throw IOException("HTTP $code from ${Http.hostOf(url)}")

            val append = code == HttpURLConnection.HTTP_PARTIAL && start > 0
            if (!append && part.exists()) part.delete()

            val contentLength = connection.contentLengthLong
            var total = if (append) start + contentLength else contentLength
            if (total <= 0 && expectedSize > 0) total = expectedSize

            _state.update {
                it.copy(
                    source = Http.hostOf(url),
                    bytes = if (append) start else 0,
                    total = if (total > 0) total else -1,
                )
            }
            updateNotification(displayName, if (append) start else 0, total, indeterminate = total <= 0)

            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var written = if (append) start else 0L
                    var lastUpdate = 0L
                    while (true) {
                        if (cancelRequested) throw CancellationException("cancelled")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUpdate >= 400) {
                            lastUpdate = now
                            _state.update {
                                it.copy(
                                    bytes = written,
                                    total = if (total > 0) total else -1,
                                )
                            }
                            updateNotification(displayName, written, total, indeterminate = total <= 0)
                        }
                    }
                    output.flush()
                    _state.update {
                        it.copy(bytes = written, total = if (total > 0) total else -1)
                    }
                }
            }
        } finally {
            activeConnection = null
            connection.disconnect()
        }
    }

    private fun finish(
        displayName: String,
        success: Boolean,
        cancelled: Boolean,
        error: String?,
    ) {
        _state.update {
            it.copy(
                active = false,
                done = success,
                cancelled = cancelled,
                error = error,
                verifying = false,
            )
        }
        notifyFinal(displayName, success)
        stopSelf()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        displayName: String,
        bytes: Long,
        total: Long,
        indeterminate: Boolean,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.download_notification_title, displayName))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (indeterminate || total <= 0) {
            builder.setProgress(0, 0, true)
        } else {
            val percent = ((bytes * 100) / total).toInt().coerceIn(0, 100)
            builder.setProgress(100, percent, false)
            builder.setContentText(
                "$percent% · ${Http.formatSize(bytes)} / ${Http.formatSize(total)}"
            )
        }
        return builder.build()
    }

    private fun updateNotification(
        displayName: String,
        bytes: Long,
        total: Long,
        indeterminate: Boolean,
    ) {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(
                NOTIFICATION_ID,
                buildNotification(displayName, bytes, total, indeterminate),
            )
        } catch (_: Exception) {
        }
    }

    private fun notifyFinal(displayName: String, success: Boolean) {
        try {
            stopForeground(STOP_FOREGROUND_DETACH)
            val manager = getSystemService(NotificationManager::class.java)
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(
                    if (success) android.R.drawable.stat_sys_download_done
                    else android.R.drawable.stat_notify_error
                )
                .setContentTitle(displayName)
                .setContentText(
                    getString(
                        if (success) R.string.download_notification_done
                        else R.string.download_notification_failed
                    )
                )
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val ACTION_START = "com.tvlaunchers.webviewinstaller.START_DOWNLOAD"
        const val ACTION_CANCEL = "com.tvlaunchers.webviewinstaller.CANCEL_DOWNLOAD"

        private const val EXTRA_ASSET_ID = "asset_id"
        private const val EXTRA_DISPLAY_NAME = "display_name"
        private const val EXTRA_DEST = "dest"
        private const val EXTRA_SHA = "sha256"
        private const val EXTRA_SIZE = "size"
        private const val EXTRA_URLS = "urls"

        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1001

        private val _state = MutableStateFlow(DownloadState())
        val state: StateFlow<DownloadState> = _state.asStateFlow()

        fun start(
            context: Context,
            asset: WebViewAsset,
            displayName: String,
            dest: File,
            urls: List<String>,
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ASSET_ID, asset.id)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                putExtra(EXTRA_DEST, dest.absolutePath)
                putExtra(EXTRA_SHA, asset.sha256)
                putExtra(EXTRA_SIZE, asset.size)
                putStringArrayListExtra(EXTRA_URLS, ArrayList(urls))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
