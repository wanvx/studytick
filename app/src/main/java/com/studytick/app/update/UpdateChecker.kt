package com.studytick.app.update

import com.studytick.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val releaseUrl: String,
    val notes: String,
    val apkUrl: String?,
)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val info: UpdateInfo) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/**
 * 通过 GitHub Releases 检查更新：版本号取 tag（如 v1.1.0）。
 */
object UpdateChecker {

    val repoUrl: String get() = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}"
    val releasePageUrl: String get() = "$repoUrl/releases/latest"

    private fun apiUrl(): String =
        "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(apiUrl()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "StudyTick-Android")
            }
            val code = connection.responseCode
            if (code == 404) return@withContext UpdateResult.UpToDate
            if (code != 200) return@withContext UpdateResult.Failed("网络异常（$code）")

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim()
            if (tag.isEmpty()) return@withContext UpdateResult.Failed("未获取到版本信息")

            val latest = normalize(tag)
            val current = normalize(BuildConfig.VERSION_NAME)
            if (compare(latest, current) <= 0) return@withContext UpdateResult.UpToDate

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            UpdateResult.Available(
                UpdateInfo(
                    versionName = tag,
                    releaseUrl = json.optString("html_url", releasePageUrl),
                    notes = json.optString("body"),
                    apkUrl = apkUrl,
                )
            )
        } catch (t: Throwable) {
            UpdateResult.Failed(t.message ?: "检查更新失败")
        } finally {
            connection?.disconnect()
        }
    }

    private fun normalize(version: String): List<Int> =
        version.removePrefix("v").removePrefix("V")
            .split(".", "-", "+")
            .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
