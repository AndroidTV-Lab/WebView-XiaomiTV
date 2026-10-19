package com.tvlaunchers.webviewinstaller

import org.json.JSONArray
import org.json.JSONObject

object Releases {
    const val REPO = "TVLaunchers/WebView-XiaomiTV"
    private const val BASE = "https://github.com/$REPO/releases/download"

    const val LATEST_TAG = "latest"
    const val ASSETS_TAG = "webview-assets"

    const val OVERLAY_URL = "$BASE/$LATEST_TAG/WebViewOverlay.apk"
    const val OVERLAY_SHA_URL = "$BASE/$LATEST_TAG/WebViewOverlay.apk.sha256"
    const val MANIFEST_URL = "$BASE/$ASSETS_TAG/manifest.json"

    /** Mirrors are prepended to the GitHub URL; the first working source wins. */
    private val MIRRORS = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
    )

    fun candidates(url: String): List<String> = listOf(url) + MIRRORS.map { it + url }

    fun assetUrl(fileName: String): String = "$BASE/$ASSETS_TAG/$fileName"

    fun parseManifest(text: String): List<WebViewAsset> {
        val root = JSONObject(text)
        val array: JSONArray = root.optJSONArray("packages") ?: return emptyList()
        val result = ArrayList<WebViewAsset>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val packageName = obj.optString("package").trim()
            val fileName = obj.optString("file").trim()
            val sha = obj.optString("sha256").trim().lowercase()
            if (packageName.isEmpty() || fileName.isEmpty()) continue
            if (!sha.matches(Regex("[0-9a-f]{64}"))) continue
            val id = obj.optString("id").trim().ifEmpty {
                Known.targetFor(packageName)?.id ?: packageName
            }
            result.add(
                WebViewAsset(
                    id = id,
                    packageName = packageName,
                    fileName = fileName,
                    sha256 = sha,
                    size = if (obj.has("size")) obj.optLong("size", -1) else -1,
                    versionName = obj.optString("versionName").trim().ifEmpty { null },
                    versionCode = if (obj.has("versionCode")) obj.optLong("versionCode") else null,
                    minSdk = if (obj.has("minSdk")) obj.optInt("minSdk") else null,
                    abi = obj.optString("abi").trim().ifEmpty { null },
                )
            )
        }
        return result
    }
}
