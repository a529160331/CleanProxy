package com.cleanproxy

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var configInfo: TextView
    private lateinit var connectButton: Button
    private var configFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configFile = File(filesDir, "proxy-config.json")
        buildScreen()
        showSavedConfig()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 48, 28, 32)
            setBackgroundColor(Color.rgb(12, 18, 32))
        }
        val title = TextView(this).apply {
            text = "CleanProxy"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "免费优先 · 本地配置 · 一键连接"
            textSize = 14f
            setTextColor(Color.rgb(157, 174, 199))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 28)
        }
        status = TextView(this).apply {
            text = "尚未连接"
            textSize = 17f
            setTextColor(Color.rgb(255, 205, 105))
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
        }
        configInfo = TextView(this).apply {
            text = "尚未导入节点配置"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(12, 14, 12, 14)
        }
        val importButton = Button(this).apply {
            text = "导入节点配置"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, REQUEST_IMPORT)
            }
        }
        connectButton = Button(this).apply {
            text = "连接功能开发中"
            isEnabled = false
        }
        val note = TextView(this).apply {
            text = "节点配置仅保存在本机。免费节点需经过来源与安全检查；当前版本尚未接入 VPN 核心。"
            textSize = 12f
            setTextColor(Color.rgb(135, 150, 174))
            gravity = Gravity.CENTER
            setPadding(12, 28, 12, 0)
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(configInfo)
        root.addView(importButton, matchWidth())
        root.addView(connectButton, matchWidth())
        root.addView(note)
        setContentView(root)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = 10 }

    private fun showSavedConfig() {
        val file = configFile ?: return
        if (!file.exists()) return
        try {
            validateConfig(file.readText())
            configInfo.text = "已保存配置：" + file.name + "\n基础结构检查通过（未验证节点连通性）"
            status.text = "配置已就绪 · 尚未连接"
        } catch (_: Exception) {
            file.delete()
            configInfo.text = "本地配置无效，请重新导入"
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            val content = contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytes(MAX_CONFIG_BYTES + 1)
                require(bytes.size <= MAX_CONFIG_BYTES) { "配置文件超过 2 MiB 限制" }
                String(bytes, Charsets.UTF_8)
            } ?: throw IllegalArgumentException("无法读取文件")
            val json = validateConfig(content)
            configFile?.writeText(json.toString(2))
            configInfo.text = "节点配置已导入并保存在本机"
            status.text = "配置已就绪 · 尚未连接"
        } catch (e: Exception) {
            configInfo.text = "导入失败：" + (e.message ?: "配置格式不正确")
            status.text = "等待有效配置"
        }
    }

    private fun validateConfig(content: String): JSONObject {
        val json = JSONObject(content)
        val outbounds = json.optJSONArray("outbounds")
            ?: throw IllegalArgumentException("缺少 outbounds 数组")
        require(outbounds.length() > 0) { "outbounds 不能为空" }
        val tags = mutableSetOf<String>()
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index)
                ?: throw IllegalArgumentException("第 ${index + 1} 个 outbound 不是对象")
            val type = outbound.optString("type").trim()
            require(type.isNotEmpty()) { "第 ${index + 1} 个 outbound 缺少 type" }
            val tag = outbound.optString("tag").trim()
            if (tag.isNotEmpty()) require(tags.add(tag)) { "outbound tag 重复：$tag" }
        }
        return json
    }

    companion object {
        private const val REQUEST_IMPORT = 1001
        private const val MAX_CONFIG_BYTES = 2 * 1024 * 1024
    }
}
