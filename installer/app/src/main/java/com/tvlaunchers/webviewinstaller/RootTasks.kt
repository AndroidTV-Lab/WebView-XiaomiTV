package com.tvlaunchers.webviewinstaller

/**
 * Script bodies executed by [RootShell]. They run after the preamble that defines PATH, LOG,
 * EXIT, the `run` helper (command plus redirection to the log) and `fail`.
 */
object RootTasks {
    fun install(apkPath: String, packageName: String, publicName: String): String = """
        echo "START install $packageName"
        # The app process cannot write public storage on this firmware (WRITE_EXTERNAL_STORAGE
        # maps to no gids), so the user-visible copy is published here, as root.
        run mkdir -p /sdcard/Download
        run cp '$apkPath' '/sdcard/Download/$publicName' || run echo "WARN: publish apk failed"
        run chmod 644 '/sdcard/Download/$publicName'
        TMP='/data/local/tmp/wvi_install.apk'
        run cp '$apkPath' "${'$'}TMP" || fail "copy apk to /data/local/tmp failed"
        run chmod 644 "${'$'}TMP"
        run pm install -r --user 0 "${'$'}TMP"
        RC=${'$'}?
        run rm -f "${'$'}TMP"
        [ "${'$'}RC" -eq 0 ] || fail "pm install failed (rc=${'$'}RC)"
        echo "OK" > "${'$'}EXIT"
    """.trimIndent()

    fun overlay(apkPath: String): String = """
        echo "START overlay"
        # /product/overlay is the standard RRO location on Android 9; /product is mounted ro.
        run mount -o rw,remount /product || fail "remount /product rw failed"
        run mkdir -p ${Known.OVERLAY_DIR} || fail "mkdir ${Known.OVERLAY_DIR} failed"
        run cp '$apkPath' ${Known.OVERLAY_PATH} || fail "copy overlay failed"
        run chmod 644 ${Known.OVERLAY_PATH}
        run chown root:root ${Known.OVERLAY_PATH}
        command -v restorecon >/dev/null 2>&1 && run restorecon ${Known.OVERLAY_PATH}
        run sync
        run mount -o ro,remount /product
        echo "OK" > "${'$'}EXIT"
    """.trimIndent()

    /** The app cannot write public storage, so the published copy is removed as root. */
    fun removeDownload(publicName: String): String = """
        echo "START remove download $publicName"
        run rm -f '/sdcard/Download/$publicName' || fail "remove /sdcard/Download/$publicName failed"
        echo "OK" > "${'$'}EXIT"
    """.trimIndent()

    fun uninstall(packageName: String): String = """
        echo "START uninstall $packageName"
        run pm uninstall --user 0 '$packageName'
        RC=${'$'}?
        [ "${'$'}RC" -eq 0 ] || fail "pm uninstall failed (rc=${'$'}RC)"
        echo "OK" > "${'$'}EXIT"
    """.trimIndent()

    fun setProvider(packageName: String): String = """
        echo "START set provider $packageName"
        run pm enable --user 0 '$packageName' || fail "pm enable $packageName failed"
        run settings put global webview_provider '$packageName' || fail "settings put failed"
        run settings get global webview_provider
        echo "OK" > "${'$'}EXIT"
    """.trimIndent()

    fun restore(): String = """
        echo "START restore"
        run pm enable ${Known.AOSP_PACKAGE}
        run settings put global webview_provider ${Known.AOSP_PACKAGE} || fail "settings put failed"
        for P in com.thorium.webview org.axpos.aosmium_wv com.google.android.webview; do
          run pm uninstall --user 0 "${'$'}P"
        done
        run mount -o rw,remount /product || fail "remount /product rw failed"
        if [ -e ${Known.OVERLAY_PATH} ]; then
          run rm -f ${Known.OVERLAY_PATH} || fail "remove overlay failed"
        fi
        run sync
        run mount -o ro,remount /product
        echo "OK" > "${'$'}EXIT"
    """.trimIndent()

    fun status(): String = """
        echo "START status"
        {
          echo "=== WEBVIEWUPDATE ==="
          dumpsys webviewupdate
          echo "=== OVERLAY ==="
          dumpsys overlay
          echo "=== SETTING ==="
          settings get global webview_provider
          echo "=== END ==="
        } >> "${'$'}LOG" 2>&1
        echo "OK" > "${'$'}EXIT"
    """.trimIndent()

    fun reboot(): String = """
        run svc power reboot
        run reboot
    """.trimIndent()
}
