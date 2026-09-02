package com.necmat.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Updater {

    private const val REPO = "CristianCasapu/necmat"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    data class UpdateInfo(
        val versionName: String,
        val apkUrl: String,
        val notes: String
    )

    /**
     * Compară două versiuni de forma "1.2" / "v1.2.3".
     * Întoarce >0 dacă a e mai nouă decât b, 0 dacă egale, <0 dacă mai veche.
     */
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toInt() }
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    /** Întoarce info despre o versiune mai nouă decât cea instalată, sau null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_LATEST).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val o = JSONObject(body)
            val tag = o.getString("tag_name")
            if (compareVersions(tag, BuildConfig.VERSION_NAME) <= 0) return@withContext null

            val assets = o.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    apkUrl = a.getString("browser_download_url")
                    break
                }
            }
            if (apkUrl == null) return@withContext null
            UpdateInfo(
                versionName = tag.removePrefix("v"),
                apkUrl = apkUrl,
                notes = o.optString("body", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Descarcă APK-ul și pornește instalarea (sistemul cere confirmarea utilizatorului). */
    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "NecMat-v${info.versionName}.apk")
                val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = true
                conn.inputStream.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                conn.disconnect()

                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                false
            }
        }
}
