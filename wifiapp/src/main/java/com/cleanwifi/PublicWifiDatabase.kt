package com.cleanwifi

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the project's opt-in, moderated public-venue Wi-Fi directory.
 * Only approved records with explicit sharing permission are ever returned.
 */
class PublicWifiDatabase {
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
    @Volatile private var cached: List<Hotspot>? = null
    @Volatile private var fetchedAt: Long = 0L
    @Volatile private var loading = false
    private val waiting = mutableListOf<(Result<List<Hotspot>>) -> Unit>()

    fun lookup(ssids: Set<String>, callback: (Result<List<Hotspot>>) -> Unit) {
        val current = cached
        if (current != null && System.currentTimeMillis() - fetchedAt < CACHE_TTL_MS) {
            main.post { callback(Result.success(current.filter { it.ssid in ssids })) }
            return
        }
        synchronized(waiting) {
            waiting.add { result -> callback(result.map { list -> list.filter { it.ssid in ssids } }) }
            if (loading) return
            loading = true
        }
        Thread {
            val result = runCatching { download() }
            synchronized(waiting) {
                if (result.isSuccess) {
                    cached = result.getOrThrow()
                    fetchedAt = System.currentTimeMillis()
                }
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
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 30L * 60L * 1000L
        private const val DATA_URL =
            "https://raw.githubusercontent.com/a529160331/CleanProxy/feature/free-client-foundation/wifiapp/src/main/assets/public-wifi.json"
    }
}
