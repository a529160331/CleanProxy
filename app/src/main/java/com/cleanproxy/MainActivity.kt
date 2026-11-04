package com.cleanproxy

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        val title = TextView(this).apply {
            text = "CleanProxy"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val status = TextView(this).apply {
            text = "尚未连接"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        val importButton = Button(this).apply { text = "导入节点配置" }
        val connectButton = Button(this).apply { text = "连接（内核尚未接入）"; isEnabled = false }
        root.addView(title)
        root.addView(status)
        root.addView(importButton)
        root.addView(connectButton)
        setContentView(root)
    }
}
