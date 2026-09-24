package com.matte743.nothingqs

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

/** Setup screen: permissions, optional Shizuku, haptics and "add widget". */
class MainActivity : Activity() {

    private val refreshers = mutableListOf<() -> Unit>()
    private lateinit var content: LinearLayout

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) Prefs.setUseShizuku(this, true)
        runOnUiThread { refresh() }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(28f), dp(20f), dp(28f))
        }
        scroll.addView(content)
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        content.addView(DotTextView(this, "QS WIDGET", dp(5f).toFloat()))
        content.addView(text(getString(R.string.subtitle), 14f, R.color.app_text_secondary).apply {
            setPadding(0, dp(12f), 0, dp(20f))
        })

        // ---- Widget
        card(getString(R.string.section_widget), getString(R.string.widget_add_desc)).also { c ->
            c.button.text = getString(R.string.widget_add_button)
            c.button.setOnClickListener { pinWidget() }
        }

        // ---- Brightness
        card(getString(R.string.section_brightness), getString(R.string.brightness_desc)).also { c ->
            c.button.setOnClickListener {
                startSafely(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
            refreshers += { c.setDone(Settings.System.canWrite(this)) }
        }

        // ---- Bluetooth
        card(getString(R.string.section_bluetooth), getString(R.string.bluetooth_desc)).also { c ->
            c.button.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1) }
            refreshers += { c.setDone(SystemControls.hasBluetoothPermission(this)) }
        }

        // ---- Notifications
        card(getString(R.string.section_notifications), getString(R.string.notifications_desc)).also { c ->
            c.button.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2) }
            refreshers += {
                c.setDone(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            }
        }

        // ---- Glyph torch
        card(getString(R.string.section_glyph), "").also { c ->
            c.button.text = getString(R.string.glyph_test)
            c.button.setOnClickListener {
                startForegroundService(
                    Intent(this, GlyphTorchService::class.java).setAction(GlyphTorchService.ACTION_TOGGLE)
                )
            }
            refreshers += {
                val supported = GlyphTorchService.isSupported()
                c.desc.text = getString(if (supported) R.string.glyph_supported else R.string.glyph_unsupported)
                c.button.visibility = if (supported) View.VISIBLE else View.GONE
            }
        }

        // ---- Glyph clock toy
        card(getString(R.string.section_clock), "").also { c ->
            c.button.text = getString(R.string.clock_open_toys)
            c.button.setOnClickListener { openGlyphToysManager() }
            refreshers += {
                val supported = GlyphTorchService.isSupported()
                c.desc.text = getString(if (supported) R.string.clock_supported else R.string.clock_unsupported)
                c.button.visibility = if (supported) View.VISIBLE else View.GONE
            }
        }

        // ---- Shizuku
        card(getString(R.string.section_shizuku), getString(R.string.shizuku_desc)).also { c ->
            val status = text("", 14f, R.color.app_text).apply { setPadding(0, dp(10f), 0, 0) }
            c.root.addView(status, c.root.indexOfChild(c.button))
            val toggle = switch(getString(R.string.shizuku_use), Prefs.useShizuku(this)) { checked ->
                if (checked != Prefs.useShizuku(this)) {
                    Prefs.setUseShizuku(this, checked)
                    updateWidget()
                }
            }
            c.root.addView(toggle)
            refreshers += {
                val s = ShizukuShell.status(this)
                status.text = getString(
                    when (s) {
                        ShizukuShell.Status.NOT_INSTALLED -> R.string.shizuku_not_installed
                        ShizukuShell.Status.NOT_RUNNING -> R.string.shizuku_not_running
                        ShizukuShell.Status.NO_PERMISSION -> R.string.shizuku_needs_permission
                        ShizukuShell.Status.READY -> R.string.shizuku_ready
                    }
                )
                toggle.visibility = if (s == ShizukuShell.Status.READY) View.VISIBLE else View.GONE
                toggle.isChecked = Prefs.useShizuku(this)
                when (s) {
                    ShizukuShell.Status.READY -> {
                        c.button.visibility = View.GONE
                    }
                    ShizukuShell.Status.NO_PERMISSION -> {
                        c.button.visibility = View.VISIBLE
                        c.setDone(false)
                        c.button.setOnClickListener {
                            try {
                                Shizuku.requestPermission(3)
                            } catch (_: Throwable) {
                            }
                        }
                    }

                    else -> {
                        c.button.visibility = View.VISIBLE
                        c.button.isEnabled = true
                        c.button.text = getString(R.string.open)
                        c.button.setBackgroundResource(R.drawable.button_bg)
                        c.button.setTextColor(getColor(R.color.app_button_text))
                        c.button.setOnClickListener { openShizuku(s == ShizukuShell.Status.NOT_INSTALLED) }
                    }
                }
            }
        }

        // ---- Haptics
        card(getString(R.string.section_haptics), getString(R.string.haptics_desc)).also { c ->
            c.button.visibility = View.GONE
            val toggle = switch(getString(R.string.section_haptics), Prefs.haptics(this)) { checked ->
                Prefs.setHaptics(this, checked)
                if (checked) Haptics.perform(this, Haptics.Kind.ON)
            }
            c.root.addView(toggle)
        }

        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        updateWidget()
    }

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
        updateWidget()
    }

    private fun refresh() = refreshers.forEach { it() }

    private fun updateWidget() {
        val app = applicationContext
        Thread {
            StateSyncJob.schedule(app)
            WidgetRenderer.updateAll(app)
        }.start()
    }

    private fun pinWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager != null && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, QsWidgetProvider::class.java), null, null)
        } else {
            Toast.makeText(this, R.string.widget_pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    private fun openShizuku(notInstalled: Boolean) {
        if (!notInstalled) {
            packageManager.getLaunchIntentForPackage(ShizukuShell.PACKAGE)?.let {
                startSafely(it)
                return
            }
        }
        if (!startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuShell.PACKAGE}")))) {
            startSafely(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${ShizukuShell.PACKAGE}"))
            )
        }
    }

    /** Nothing's "Manage Glyph Toys" screen, where the user adds the clock to the toy list. */
    private fun openGlyphToysManager() {
        val intent = Intent().setComponent(
            ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")
        )
        val opened = try {
            startSafely(intent)
        } catch (_: SecurityException) {
            false
        }
        if (!opened) Toast.makeText(this, R.string.clock_toys_manager_missing, Toast.LENGTH_LONG).show()
    }

    private fun startSafely(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    // ------------------------------------------------------------ UI helpers

    private fun text(value: String, sizeSp: Float, colorRes: Int, mono: Boolean = false) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(getColor(colorRes))
        if (mono) {
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.08f
            isAllCaps = true
        }
    }

    private inner class Card(val root: LinearLayout, val desc: TextView, val button: TextView) {
        fun setDone(done: Boolean) {
            button.text = getString(if (done) R.string.granted else R.string.grant)
            button.isEnabled = !done
            button.setBackgroundResource(if (done) R.drawable.button_done_bg else R.drawable.button_bg)
            button.setTextColor(getColor(if (done) R.color.app_text_secondary else R.color.app_button_text))
        }
    }

    private fun card(title: String, description: String): Card {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(dp(20f), dp(18f), dp(20f), dp(18f))
        }
        root.addView(text(title, 12f, R.color.app_text_secondary, mono = true))
        val desc = text(description, 15f, R.color.app_text).apply {
            setPadding(0, dp(8f), 0, 0)
            setLineSpacing(0f, 1.15f)
        }
        root.addView(desc)
        val button = TextView(this).apply {
            text = getString(R.string.grant)
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            isAllCaps = true
            letterSpacing = 0.08f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(getColor(R.color.app_button_text))
            setBackgroundResource(R.drawable.button_bg)
            setPadding(dp(22f), dp(12f), dp(22f), dp(12f))
        }
        root.addView(button, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14f) })
        content.addView(root, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12f) })
        return Card(root, desc, button)
    }

    @Suppress("DEPRECATION")
    private fun switch(label: String, initial: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(this).apply {
        text = label
        isChecked = initial
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(getColor(R.color.app_text))
        setPadding(0, dp(14f), 0, 0)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(states, intArrayOf(getColor(R.color.nt_red), getColor(R.color.dot_off)))
        trackTintList = ColorStateList(states, intArrayOf(getColor(R.color.nt_red), getColor(R.color.dot_off)))
        setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }
}
