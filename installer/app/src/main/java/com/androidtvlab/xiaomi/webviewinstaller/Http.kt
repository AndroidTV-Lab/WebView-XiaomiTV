package com.androidtvlab.xiaomi.webviewinstaller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

object Http {
    data class TextResult(val text: String, val source: String)

    private const val USER_AGENT = "WebViewInstaller/1.0 (Android)"

    suspend fun fetchText(urls: List<String>, timeoutMs: Int = 8000): TextResult =
        withContext(Dispatchers.IO) {
            var last: Exception? = null
            for (url in urls) {
                try {
                    val conn = open(url, timeoutMs)
                    try {
                        val code = conn.responseCode
                        if (code !in 200..299) throw IOException("HTTP $code")
                        val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        return@withContext TextResult(text, hostOf(url))
                    } finally {
                        conn.disconnect()
                    }
                } catch (e: Exception) {
                    last = e
                }
            }
            throw IOException(
                last?.message?.let { "all sources failed: $it" } ?: "all sources failed",
                last,
            )
        }

    /**
     * Downloads to [dest] (through a .part file), verifying [expectedSha256]. Tries each candidate
     * URL in order; a candidate that yields a hash mismatch is discarded so the next one starts
     * from scratch.
     */
    suspend fun downloadSmall(
        urls: List<String>,
        dest: File,
        expectedSha256: String?,
        timeoutMs: Int = 30000,
    ): String = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val part = File(dest.absolutePath + ".part")
        var last: Exception? = null
        for (url in urls) {
            try {
                if (part.exists()) part.delete()
                val conn = open(url, timeoutMs)
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) throw IOException("HTTP $code")
                    conn.inputStream.use { input ->
                        FileOutputStream(part, false).use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                if (expectedSha256 != null && !sha256(part).equals(expectedSha256, ignoreCase = true)) {
                    part.delete()
                    throw IOException("sha256 mismatch")
                }
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
                return@withContext hostOf(url)
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException(last?.message ?: "all sources failed", last)
    }

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun sha256(bytes: ByteArray): String = sha256(bytes.inputStream())

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(128 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
        return digest.digest().toHex()
    }

    fun parseSha256(text: String): String? =
        Regex("[0-9a-fA-F]{64}").find(text)?.value?.lowercase(Locale.US)

    fun hostOf(url: String): String = try {
        URL(url).host
    } catch (e: Exception) {
        url
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "?"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }

    fun shortHash(hash: String?): String {
        if (hash.isNullOrBlank()) return "—"
        return if (hash.length > 16) hash.take(16) + "…" else hash
    }

    private fun open(url: String, timeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) out.append(String.format(Locale.US, "%02x", b))
        return out.toString()
    }
}
