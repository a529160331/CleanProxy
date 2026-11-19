package com.cleanwifi

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build

/**
 * Hands an explicitly selected, authorized hotspot to Android's Wi-Fi
 * suggestion API. Android remains responsible for approval and connection.
 */
object WifiConnector {
    fun submitSuggestion(
        context: Context,
        ssid: String,
        password: String,
        capabilities: String
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "当前 Android 版本不支持应用提交 Wi-Fi 建议，请在系统 Wi-Fi 设置中手动连接。"
        }
        if (ssid.isBlank()) return "Wi-Fi 名称为空，无法提交。"

        val caps = capabilities.uppercase()
        val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
        // This setter was added after Android 10 (API 29); guard it so the
        // minSdk 26 app does not call a newer platform method on API 29.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setIsAppInteractionRequired(true)
        }

        when {
            caps.contains("WPA3") || caps.contains("SAE") -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    return "这是 WPA3 网络；当前 Android 版本暂不支持本应用提交 WPA3 连接建议。"
                }
                if (password.isBlank()) return "WPA3 网络需要密码。"
                runCatching { builder.setWpa3Passphrase(password) }
                    .getOrElse { return "WPA3 密码格式不符合系统要求：${it.message ?: "无效密码"}" }
            }
            caps.contains("WPA") -> {
                if (password.isBlank()) return "该网络需要密码。"
                runCatching { builder.setWpa2Passphrase(password) }
                    .getOrElse { return "Wi-Fi 密码格式不符合系统要求：${it.message ?: "无效密码"}" }
            }
            caps.contains("WEP") -> {
                return "该网络使用较旧的 WEP 加密，本应用暂不自动提交；请通过系统 Wi-Fi 设置连接。"
            }
            else -> {
                if (password.isNotBlank()) {
                    return "扫描信息显示这是开放网络，但保存记录带有密码。请重新扫描确认网络类型。"
                }
            }
        }

        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val status = runCatching {
            manager.addNetworkSuggestions(listOf(builder.build()))
        }.getOrElse {
            return "提交给 Android 失败：${it.message ?: "系统错误"}"
        }

        return when (status) {
            WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ->
                "已把「$ssid」提交给 Android。请留意系统 Wi-Fi 授权通知；获准后由 Android 决定是否连接。提交成功不代表已经联网。"
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE ->
                "这个网络建议已存在。请检查 Android 的 Wi-Fi 授权通知或系统 Wi-Fi 设置。"
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED ->
                "Android 尚未允许 CleanWiFi 提供 Wi-Fi 建议，请先在系统提示中授权。"
            else -> "Android 未接受这条 Wi-Fi 建议（状态码：$status）。请改用系统 Wi-Fi 设置手动连接。"
        }
    }
}
