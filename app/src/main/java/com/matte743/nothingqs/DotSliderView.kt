package com.matte743.nothingqs

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * Horizontal dot-matrix slider in the style of Nothing OS. Drag anywhere on it
 * to change the level; every dot crossed gives a light haptic tick.
 */
@SuppressLint("ViewConstructor")
class DotSliderView(
    context: Context,
    iconRes: Int,
    private val label: (Float) -> String,
    private val onLevel: (level: Float, finished: Boolean) -> Unit,
) : View(context) {

    var level: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** Optional fixed text, such as "AUTO", shown until the user drags. */
    var overrideLabel: String? = null
        set(value) {
            field = value
            invalidate()
        }

    var onTouchActivity: (() -> Unit)? = null

    private val dots = 18
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val icon: Drawable? = context.getDrawable(iconRes)?.mutate()
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_surface) }
    private val iconBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_bg) }
    private val dotOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.dot_on) }
    private val dotOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.dot_off) }
    private val dotRed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.nt_red) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_text) }
    private val rect = RectF()

    private var lastStep = -1
    private var dragging = false

    private val iconSize get() = height - dp(16f)
    private val trackLeft get() = paddingLeft + dp(8f) + iconSize + dp(18f)
    private val trackRight get() = width - paddingRight - dp(66f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64f, resources.displayMetrics).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        rect.set(paddingLeft.toFloat(), 0f, (width - paddingRight).toFloat(), h)
        canvas.drawRoundRect(rect, h / 2, h / 2, pill)

        // Icon in a circle on the left.
        val cx = paddingLeft + dp(8f) + iconSize / 2
        canvas.drawCircle(cx, h / 2, iconSize / 2, iconBg)
        icon?.let {
            val s = (iconSize * 0.5f).toInt()
            it.setBounds((cx - s / 2).toInt(), (h / 2 - s / 2).toInt(), (cx + s / 2).toInt(), (h / 2 + s / 2).toInt())
            it.draw(canvas)
        }

        // Dots.
        val left = trackLeft
        val right = trackRight
        val lit = (level * dots).roundToInt()
        val step = if (dots > 1) (right - left) / (dots - 1) else 0f
        val r = dp(3.2f)
        for (i in 0 until dots) {
            val x = left + i * step
            val paint = when {
                lit > 0 && i == lit - 1 -> dotRed
                i < lit -> dotOn
                else -> dotOff
            }
            canvas.drawCircle(x, h / 2, if (i < lit) r * 1.15f else r, paint)
        }

        // Value in the dot-matrix font.
        val value = overrideLabel ?: label(level)
        val cell = dp(2.3f)
        val textWidth = DotFont.columns(value) * cell
        val tx = width - paddingRight - dp(22f) - textWidth
        DotFont.draw(canvas, value, tx, h / 2 - DotFont.ROWS * cell / 2, cell, text)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                update(event.x, finished = false)
            }

            MotionEvent.ACTION_MOVE -> if (dragging) update(event.x, finished = false)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) update(event.x, finished = true)
                dragging = false
            }
        }
        onTouchActivity?.invoke()
        return true
    }

    private fun update(x: Float, finished: Boolean) {
        val left = trackLeft
        val right = trackRight
        // Snap to dots so each position matches what is drawn.
        val raw = ((x - left) / (right - left)).coerceIn(0f, 1f)
        val stepIndex = (raw * dots).roundToInt()
        overrideLabel = null
        level = stepIndex.toFloat() / dots
        if (stepIndex != lastStep) {
            if (lastStep != -1 && Prefs.haptics(context)) {
                val feedback = if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                else HapticFeedbackConstants.CLOCK_TICK
                performHapticFeedback(feedback)
            }
            lastStep = stepIndex
            onLevel(level, finished)
        } else if (finished) {
            onLevel(level, true)
        }
    }
}
