package com.cleanwifi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var wifi: WifiManager
    private lateinit var vault: LocalVault
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var receiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) renderNetworks()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        vault = LocalVault(this)
        buildScreen()
        registerScanReceiver()
        renderNetworks()
        renderVault()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFFF4F7FB.toInt())
        }
        val title = TextView(this).apply {
            text = "CleanWiFi"
            textSize = 28f
            setTextColor(0xFF14243A.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        val subtitle = TextView(this).apply {
            text = "私人 WiFi 助手 · 密码默认只保存在本机"
            textSize = 14f
            setTextColor(0xFF526174.toInt())
            setPadding(0, 8, 0, 16)
        }
        status = TextView(this).apply {
            text = "准备扫描附近网络"
            textSize = 14f
            setTextColor(0xFF315A86.toInt())
            setPadding(0, 8, 0, 12)
        }
        val scan = Button(this).apply {
            text = "扫描附近 WiFi"
            setOnClickListener { requestScan() }
        }
        val add = Button(this).apply {
            text = "添加自己的 WiFi 密码"
            setOnClickListener { showAddDialog() }
        }
        val settings = Button(this).apply {
            text = "打开系统 WiFi 设置"
            setOnClickListener { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        }
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 24)
        }
        scroll.addView(content)
        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(scan)
        root.addView(add)
        root.addView(settings)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun hasScanPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestScan() {
        if (!hasScanPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 41)
            return
        }
        try {
            val started = wifi.startScan()
            status.text = if (started) "正在扫描……系统可能会限制扫描频率" else "系统限制了本次主动扫描，显示最近一次扫描结果"
            renderNetworks()
        } catch (security: SecurityException) {
            status.text = "没有 WiFi 扫描权限，请在系统设置中授权"
        } catch (error: Exception) {
            status.text = "扫描暂不可用：${error.message ?: "未知原因"}"
        }
    }

    private fun registerScanReceiver() {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(scanReceiver, filter)
        }
        receiverRegistered = true
    }

    @Suppress("DEPRECATION")
    private fun renderNetworks() {
        content.removeAllViews()
        addSection("附近 WiFi")
        if (!hasScanPermission()) {
            addLine("需要定位权限才能读取附近 WiFi 扫描结果。")
            return
        }
        try {
            val results = wifi.scanResults
                .filter { it.SSID.isNotBlank() }
                .distinctBy { it.BSSID }
                .sortedByDescending { it.level }
            if (results.isEmpty()) addLine("暂时没有扫描结果。请开启 WiFi 和系统定位，再点扫描。")
            val savedBySsid = runCatching { vault.list().associateBy { it.ssid } }.getOrDefault(emptyMap())
            results.forEach { result ->
                val security = when {
                    result.capabilities.contains("WPA3") -> "WPA3"
                    result.capabilities.contains("WPA") -> "WPA/WPA2"
                    result.capabilities.contains("WEP") -> "WEP（较弱）"
                    else -> "开放网络或未知"
                }
                addLine("${result.SSID}\\n信号：${result.level} dBm · ${result.frequency} MHz · ${security}")
                val saved = savedBySsid[result.SSID]
                if (saved != null) {
                    val matched = Button(this).apply {
                        text = "本机已保存密码 · 查看 / 手动分享"
                        setOnClickListener { confirmRevealOrShare(saved.ssid, saved.password) }
                    }
                    content.addView(matched)
                }
            }
            status.text = "发现 ${results.size} 个可见网络（系统扫描结果）"
        } catch (security: SecurityException) {
            addLine("系统未授予扫描权限。")
        } catch (error: Exception) {
            addLine("读取扫描结果失败：${error.message ?: "未知原因"}")
        }
        addSection("私人密码本")
        renderVaultEntries()
        addSection("共享与更新")
        addLine("自动上传：关闭。家庭 WiFi 和私人密码不会上传。")
        addLine("公开数据库：尚未接入经过许可的数据源；当前不会伪装成已有全国密码库。")
        addLine("陌生网络：本版本不自动连接，连接操作交由 Android 系统确认。")
    }

    private fun renderVault() {
        if (::content.isInitialized) {
            renderNetworks()
        }
    }

    private fun renderVaultEntries() {
        try {
            val entries = vault.list()
            if (entries.isEmpty()) {
                addLine("还没有保存的网络。点上方按钮添加你有权使用的 WiFi。")
            } else {
                entries.forEach { entry ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val label = TextView(this).apply {
                        text = "${entry.ssid} · 密码已加密保存在本机"
                        textSize = 14f
                        setTextColor(0xFF26364A.toInt())
                        setPadding(4, 10, 4, 10)
                    }
                    val delete = Button(this).apply {
                        text = "删除"
                        setOnClickListener {
                            vault.delete(entry.ssid)
                            renderNetworks()
                        }
                    }
                    row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(delete)
                    content.addView(row)
                }
            }
        } catch (error: Exception) {
            addLine("密码本读取失败。为保护数据，没有尝试上传或明文导出。")
        }
    }

    private fun confirmRevealOrShare(ssid: String, password: String) {
        AlertDialog.Builder(this)
            .setTitle("查看已保存的 WiFi 密码")
            .setMessage("网络：$ssid\\n密码仅在本机加密保存。确认后才会在屏幕显示；分享也必须由你再次主动选择。")
            .setNegativeButton("取消", null)
            .setPositiveButton("显示密码") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle(ssid)
                    .setMessage("密码：$password")
                    .setNegativeButton("关闭", null)
                    .setPositiveButton("手动分享") { _, _ ->
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "WiFi：$ssid")
                            putExtra(Intent.EXTRA_TEXT, "WiFi 名称：$ssid\\n密码：$password")
                        }
                        startActivity(Intent.createChooser(share, "选择分享方式"))
                    }
                    .show()
            }
            .show()
    }

    private fun showAddDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }
        val ssid = EditText(this).apply { hint = "WiFi 名称（SSID）" }
        val password = EditText(this).apply { hint = "密码（开放网络可留空）" }
        box.addView(ssid)
        box.addView(password)
        AlertDialog.Builder(this)
            .setTitle("保存到私人密码本")
            .setMessage("仅保存在本机加密存储，不会自动共享。")
            .setView(box)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                try {
                    vault.save(ssid.text.toString(), password.text.toString())
                    Toast.makeText(this, "已加密保存在本机", Toast.LENGTH_SHORT).show()
                    renderNetworks()
                } catch (error: Exception) {
                    Toast.makeText(this, "保存失败：${error.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun addSection(text: String) {
        val label = TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(0xFF14243A.toInt())
            setPadding(0, 18, 0, 6)
        }
        content.addView(label)
    }

    private fun addLine(text: String) {
        val label = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFF405168.toInt())
            setPadding(8, 10, 8, 10)
        }
        content.addView(label)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestScan()
            } else {
                status.text = "未获得定位权限，Android 不允许读取附近 WiFi 扫描结果"
            }
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(scanReceiver)
        super.onDestroy()
    }
}
