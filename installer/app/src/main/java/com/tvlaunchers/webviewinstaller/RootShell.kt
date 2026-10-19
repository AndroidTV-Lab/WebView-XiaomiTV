package com.tvlaunchers.webviewinstaller

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs shell scripts as uid 0 through the Xiaomi TvService diagnostic channel:
 *
 *     service call TvService 4400 s16 s s16 <script>
 *
 * The call is asynchronous and has no stdout, so every task writes a log and an .exit status file
 * that this object polls.
 */
object RootShell {
    data class Probe(val available: Boolean, val uid: String?, val error: String?)
    data class Result(val ok: Boolean, val message: String?, val log: String?)

    private const val SERVICE_NAME = "TvService"
    private const val SERVICE_INTERFACE = "mitv.internal.ITvService"
    private const val TRANSACTION = "4400"
    private const val CALL_SPACING_MS = 5_000L

    /**
     * TvService copies the script with `cat <script> > /data/diagnosis/command.sh` in a fixed
     * 99-character buffer. Longer paths truncate the destination (`/data/diagnosis/command.s`),
     * so the file is never created and the script silently does not run.
     */
    private const val MAX_SCRIPT_PATH = 68

    private val mutex = Mutex()
    private val sequence = AtomicInteger(0)
    private var lastCallAt = 0L

    /** Two base36 characters keep script names unique across app restarts. */
    private val runToken: String = (System.nanoTime() % 1296).toString(36).padStart(2, '0')

    suspend fun probe(workDir: File): Probe = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Do not pre-check with `service list`: SELinux denies the service list dump to the
            // app domain on some builds even though `service call` works. The probe script is the
            // authoritative test.
            val id = newId()
            val out = File(workDir, "probe_$id.out")
            val script = File(RootPaths.scriptDir(workDir), scriptName())
            try {
                out.delete()
                out.createNewFile()
                out.setReadable(true, false)
                out.setWritable(true, false)
                script.writeText(
                    """
                    #!/system/bin/sh
                    id -u > "${out.absolutePath}"
                    cat /proc/self/attr/current >> "${out.absolutePath}"
                    """.trimIndent() + "\n"
                )
                script.setReadable(true, false)
                val call = try {
                    callService(script)
                } catch (e: Throwable) {
                    script.delete()
                    out.delete()
                    return@withContext Probe(false, null, "service call failed: ${e.message}")
                }
                var text = ""
                val deadline = SystemClock.elapsedRealtime() + 10_000
                while (text.isBlank() && SystemClock.elapsedRealtime() < deadline) {
                    text = try {
                        out.readText().trim()
                    } catch (e: Throwable) {
                        ""
                    }
                    if (text.isBlank()) delay(100)
                }
                val lines = text.lines()
                val uid = lines.firstOrNull()?.trim()
                if (uid == "0") {
                    Probe(true, "0", null)
                } else {
                    Probe(false, uid, if (text.isBlank()) "probe timed out ($call)" else text.take(300))
                }
            } finally {
                script.delete()
                out.delete()
            }
        }
    }

    suspend fun run(
        workDir: File,
        scriptBody: String,
        timeoutMs: Long,
        readLogChars: Int = 16_000,
    ): Result = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val id = newId()
                val log = File(RootPaths.logDir(workDir), "$id.log")
                val exit = File(workDir, "$id.exit")
                val script = File(RootPaths.scriptDir(workDir), scriptName())
                log.delete()
                log.createNewFile()
                log.setReadable(true, false)
                log.setWritable(true, false)
                exit.delete()
                script.writeText(buildPreamble(log, exit) + scriptBody + "\n")
                script.setReadable(true, false)

                val call = try {
                    callService(script)
                } catch (e: Throwable) {
                    script.delete()
                    return@withContext Result(
                        false,
                        "service call failed: ${e.message}",
                        readTail(log, readLogChars),
                    )
                }

                val deadline = SystemClock.elapsedRealtime() + timeoutMs
                while (SystemClock.elapsedRealtime() < deadline) {
                    if (exit.exists() && exit.length() > 0) {
                        val status = exit.readText().trim()
                        val text = readTail(log, readLogChars)
                        script.delete()
                        exit.delete()
                        prune(workDir)
                        val ok = status == "OK" || status.startsWith("OK")
                        return@withContext Result(
                            ok,
                            if (ok) null else status.removePrefix("FAIL:").ifBlank { "unknown error" },
                            text,
                        )
                    }
                    delay(250)
                }
                val text = readTail(log, readLogChars)
                script.delete()
                prune(workDir)
                Result(false, "timeout after ${timeoutMs / 1000}s ($call)", text)
            } catch (e: Throwable) {
                Result(false, e.message ?: e.javaClass.simpleName, null)
            }
        }
    }

    /** Fire-and-forget; used for reboot where the device may disappear mid-script. */
    suspend fun fire(workDir: File, scriptBody: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val id = newId()
                    val log = File(RootPaths.logDir(workDir), "$id.log")
                    val exit = File(workDir, "$id.exit")
                    val script = File(RootPaths.scriptDir(workDir), scriptName())
                    log.createNewFile()
                    log.setReadable(true, false)
                    log.setWritable(true, false)
                    script.writeText(buildPreamble(log, exit) + scriptBody + "\n")
                    script.setReadable(true, false)
                    callService(script)
                } catch (_: Throwable) {
                    // Nothing useful to report when the device is going down.
                }
            }
        }
    }

    private fun buildPreamble(log: File, exit: File): String = """
        #!/system/bin/sh
        export PATH=/system/bin:/vendor/bin:/vendor/xbin
        LOG='${log.absolutePath}'
        EXIT='${exit.absolutePath}'
        run() { "${'$'}@" >> "${'$'}LOG" 2>&1; }
        fail() {
          echo "FAIL:${'$'}1" >> "${'$'}LOG"
          echo "FAIL:${'$'}1" > "${'$'}EXIT"
          exit 1
        }
    """.trimIndent() + "\n"

    private fun newId(): String =
        "wv${System.currentTimeMillis()}_${sequence.incrementAndGet()}"

    /**
     * Short rotating name so the absolute path stays under [MAX_SCRIPT_PATH]. The timestamped
     * names used for logs and status files are longer, but those paths only appear inside the
     * script body, where TvService's buffer limit does not apply.
     */
    private fun scriptName(): String = "w$runToken${sequence.incrementAndGet() % 1000}.sh"

    private fun callService(script: File): String {
        val path = script.absolutePath
        require(path.length <= MAX_SCRIPT_PATH) {
            "script path is ${path.length} chars, TvService truncates anything over " +
                "$MAX_SCRIPT_PATH: $path"
        }
        // TvService drops requests that arrive too soon after the previous one. Calls are
        // serialized by [mutex], but the probe can follow another task immediately.
        val sinceLast = SystemClock.elapsedRealtime() - lastCallAt
        if (lastCallAt > 0 && sinceLast < CALL_SPACING_MS) {
            Thread.sleep(CALL_SPACING_MS - sinceLast)
        }
        lastCallAt = SystemClock.elapsedRealtime()
        return exec(
            "call", SERVICE_NAME, TRANSACTION, "s16", "s", "s16", script.absolutePath,
        )
    }

    private fun exec(vararg args: String): String {
        val process = ProcessBuilder("/system/bin/service", *args)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            return "service call timed out"
        }
        return process.inputStream.bufferedReader().use { it.readText() }.trim()
    }

    private fun readTail(file: File, maxChars: Int): String = try {
        val text = file.readText()
        if (text.length <= maxChars) text else text.takeLast(maxChars)
    } catch (e: Throwable) {
        ""
    }

    private fun prune(workDir: File) {
        try {
            RootPaths.logDir(workDir).listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(40)
                ?.forEach { it.delete() }
            RootPaths.scriptDir(workDir).listFiles()
                ?.filter { it.name.startsWith("w") && it.name.endsWith(".sh") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(20)
                ?.forEach { it.delete() }
        } catch (_: Throwable) {
        }
    }
}
