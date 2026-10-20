package com.tvlaunchers.webviewinstaller

import org.json.JSONArray
import org.json.JSONObject

object Releases {
    const val REPO = "TVLaunchers/WebView-XiaomiTV"
    private const val BASE = "https://github.com/$REPO/releases"

    // WebView APKs live in a fixed assets release; the dated releases only archive builds.
    private const val ASSETS_TAG = "webview-assets"
    private const val ASSETS = "$BASE/download/$ASSETS_TAG"

    const val MANIFEST_URL = "$ASSETS/manifest.json"

    /** Mirrors are prepended to the GitHub URL; the first working source wins. */
    private val MIRRORS = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
    )

    fun candidates(url: String): List<String> = listOf(url) + MIRRORS.map { it + url }

    fun assetUrl(fileName: String): String = "$ASSETS/$fileName"

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
            // One package can now have several variants (stock, patched, ...). Without an
            // explicit id they would all collapse onto the family id, and downloads would
            // overwrite each other, so refuse an ambiguous manifest outright.
            if (result.any { it.id == id }) {
                throw IllegalArgumentException("duplicate asset id in manifest: $id")
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
                    title = obj.optString("title").trim().ifEmpty { null },
                    titleEn = obj.optString("title_en").trim().ifEmpty { null },
                    description = obj.optString("description").trim().ifEmpty { null },
                    descriptionEn = obj.optString("description_en").trim().ifEmpty { null },
                    variant = obj.optString("variant").trim().ifEmpty { null },
                    webgpu = if (obj.has("webgpu")) obj.optBoolean("webgpu", true) else null,
                    signer = obj.optString("signer").trim().lowercase().ifEmpty { null },
                    recommended = obj.optBoolean("recommended", false),
                )
            )
        }
        return result
    }
}
