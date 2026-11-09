package com.cleanwifi

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the project's opt-in, moderated public-venue Wi-Fi directory.
 * Only approved records with explicit sharing permission are ever returned.
 *
 * A private app-storage snapshot is retained so previously downloaded,
 * approved records remain searchable when the device is offline.
 */
class PublicWifiDatabase(context: Context) {
    data class Hotspot(
        val ssid: String,
        val password: String,
        val venue: String,
        val city: String,
        val notes: String,
        val sourceUrl: String,
        val updatedAt: String
    )

    private val main = Handler(Looper.getMainLooper())
    private val cacheFile = File(context.filesDir, "public-wifi-approved-cache.json")
    @Volatile private var cached: List<Hotspot>? = null
    @Volatile private var fetchedAt: Long = 0L
    @Volatile private var loading = false
    @Volatile var lastLookupUsedOfflineCache: Boolean = false
        private set
    private val waiting = mutableListOf<(Result<List<Hotspot>>) -> Unit>()

    fun lookup(ssids: Set<String>, callback: (Result<List<Hotspot>>) -> Unit) {
        val current = cached
        if (current != null && System.currentTimeMillis() - fetchedAt < CACHE_TTL_MS) {
            lastLookupUsedOfflineCache = false
            main.post { callback(Result.success(current.filter { it.ssid in ssids })) }
            return
        }
        synchronized(waiting) {
            waiting.add { result -> callback(result.map { list -> list.filter { it.ssid in ssids } }) }
            if (loading) return
            loading = true
        }
        Thread {
            var usedOffline = false
            val result = runCatching { download() }.recoverCatching {
                usedOffline = true
                readCache()
            }
            synchronized(waiting) {
                if (result.isSuccess) {
                    cached = result.getOrThrow()
                    fetchedAt = if (usedOffline) 0L else System.currentTimeMillis()
                }
                lastLookupUsedOfflineCache = usedOffline
                loading = false
                val callbacks = waiting.toList()
                waiting.clear()
                callbacks.forEach { cb -> main.post { cb(result) } }
            }
        }.start()
    }

    private fun download(): List<Hotspot> {
        val connection = (URL(DATA_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            check(connection.responseCode in 200..299) { "公共数据库暂不可用（HTTP ${connection.responseCode}）" }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            require(body.toByteArray(Charsets.UTF_8).size <= MAX_CACHE_BYTES) { "公共数据库文件超过大小限制" }
            val records = parseApproved(body)
            // Persist only a successfully parsed response; never replace a good
            // offline snapshot with an invalid or partial response.
            cacheFile.writeText(body, Charsets.UTF_8)
            return records
        } finally {
            connection.disconnect()
        }
    }

    private fun readCache(): List<Hotspot> {
        check(cacheFile.isFile) { "没有可用的离线公共热点缓存" }
        require(cacheFile.length() <= MAX_CACHE_BYTES) { "离线缓存文件超过大小限制" }
        return parseApproved(cacheFile.readText(Charsets.UTF_8))
    }

    private fun parseApproved(body: String): List<Hotspot> {
        val root = JSONObject(body)
        val records = root.optJSONArray("hotspots") ?: return emptyList()
        return (0 until records.length()).mapNotNull { index ->
            val item = records.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("status") != "approved" ||
                !item.optBoolean("permissionConfirmed", false)) return@mapNotNull null
            val ssid = item.optString("ssid").trim()
            val password = item.optString("password")
            if (ssid.isBlank() || password.isBlank()) return@mapNotNull null
            Hotspot(
                ssid = ssid,
                password = password,
                venue = item.optString("venue"),
                city = item.optString("city"),
                notes = item.optString("notes"),
                sourceUrl = item.optString("sourceUrl"),
                updatedAt = item.optString("updatedAt")
            )
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 30L * 60L * 1000L
        private const val MAX_CACHE_BYTES = 2 * 1024 * 1024
        private const val DATA_URL =
            "https://raw.githubusercontent.com/a529160331/CleanProxy/feature/free-client-foundation/wifiapp/src/main/assets/public-wifi.json"
    }
}
