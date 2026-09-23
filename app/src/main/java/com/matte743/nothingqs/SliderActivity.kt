package com.matte743.nothingqs

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Floating panel with draggable brightness and volume sliders.
 *
 * Home screen widgets cannot follow a dragging finger (they only receive taps),
 * so tapping a bar on the widget opens this panel right above it.
 */
class SliderActivity : Activity() {

    companion object {
        const val EXTRA_TARGET = "target"
        const val TARGET_BRIGHTNESS = 1
        const val TARGET_VOLUME = 2
        private const val AUTO_CLOSE_MS = 5000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { finish() }
    private lateinit var card: LinearLayout
    private var brightnessSlider: DotSliderView? = null
    private var volumeSlider: DotSliderView? = null

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A tap on the widget sets that level right away; dragging refines it.
        applyTappedLevel(intent)

        val root = FrameLayout(this).apply {
            setOnClickListener { finish() }
        }
        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.slider_card_bg)
            val p = dp(12f).toInt()
            setPadding(p, p, p, p)
            isClickable = true // keeps taps on the card from closing the panel
            elevation = dp(8f)
            alpha = 0f // shown once it sits over the widget
        }
        root.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            val m = dp(12f).toInt()
            setMargins(m, 0, m, 0)
            gravity = Gravity.TOP
        })
        setContentView(root)

        buildBrightness()
        buildVolume()

        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            card.post { positionCard(root, bars.top, bars.bottom) }
            insets
        }
        scheduleAutoClose()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyTappedLevel(intent)
        refreshSliders()
        scheduleAutoClose()
    }

    override fun onResume() {
        super.onResume()
        refreshSliders()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoClose)
        WidgetRenderer.updateAllDelayed(applicationContext)
        // The panel is a transient overlay: close it when the user leaves.
        if (!isChangingConfigurations) finish()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scheduleAutoClose()
        return super.dispatchTouchEvent(ev)
    }

    private fun scheduleAutoClose() {
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, AUTO_CLOSE_MS)
    }

    private fun applyTappedLevel(intent: Intent?) {
        val target = intent?.getIntExtra(EXTRA_TARGET, 0) ?: 0
        val level = intent?.getIntExtra(Actions.EXTRA_LEVEL, 0) ?: 0
        if (level <= 0) return
        when (target) {
            TARGET_BRIGHTNESS -> if (Brightness.setSegment(this, level)) Haptics.perform(this, Haptics.Kind.TICK)
            TARGET_VOLUME -> {
                SystemControls.setVolumeSegment(this, level)
                Haptics.perform(this, Haptics.Kind.TICK)
            }
        }
    }

    /** Places the card over the widget bar that was tapped, when the launcher tells us where. */
    private fun positionCard(root: View, insetTop: Int, insetBottom: Int) {
        val bounds: Rect? = intent?.sourceBounds
        val location = IntArray(2).also { root.getLocationOnScreen(it) }
        val minY = insetTop + dp(8f)
        val maxY = root.height - insetBottom - card.height - dp(8f)
        val wanted = if (bounds != null) {
            // Centre the card on the tapped bar.
            bounds.centerY() - location[1] - card.height / 2f
        } else {
            maxY - dp(24f)
        }
        card.translationY = wanted.coerceIn(minY, maxY.coerceAtLeast(minY))
        card.animate().alpha(1f).setDuration(120).start()
    }

    private fun sliderParams(top: Float) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top).toInt() }

    private fun buildBrightness() {
        if (!Settings.System.canWrite(this)) {
            val button = TextView(this).apply {
                text = getString(R.string.slider_grant_brightness)
                setTextColor(getColor(R.color.widget_text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER_VERTICAL
                val p = dp(18f).toInt()
                setPadding(p, p, p, p)
                setBackgroundResource(R.drawable.slider_row_bg)
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                    finish()
                }
            }
            card.addView(button, sliderParams(0f))
            return
        }
        brightnessSlider = DotSliderView(
            this,
            R.drawable.ic_brightness,
            label = { "${(it * 100).roundToInt()}%" },
            onLevel = { level, _ -> Brightness.setLevel(this, level) },
        ).apply { onTouchActivity = { scheduleAutoClose() } }
        card.addView(brightnessSlider, sliderParams(0f))
    }

    private fun buildVolume() {
        volumeSlider = DotSliderView(
            this,
            R.drawable.ic_volume,
            label = { "${(it * 100).roundToInt()}%" },
            onLevel = { level, _ -> SystemControls.setVolumeFraction(this, level) },
        ).apply { onTouchActivity = { scheduleAutoClose() } }
        card.addView(volumeSlider, sliderParams(10f))
    }

    private fun refreshSliders() {
        brightnessSlider?.let {
            it.level = Brightness.get(this)
            it.overrideLabel = if (Brightness.isAuto(this)) "AUTO" else null
        }
        volumeSlider?.level = SystemControls.volumeFraction(this)
    }
}
