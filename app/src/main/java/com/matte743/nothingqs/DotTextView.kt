package com.matte743.nothingqs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Heading drawn with the dot-matrix font, followed by a red Nothing dot. */
class DotTextView(context: Context, private val text: String, private val cellPx: Float) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.app_text) }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.nt_red) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (DotFont.columns(text) + 3) * cellPx
        val h = DotFont.ROWS * cellPx
        setMeasuredDimension(w.toInt() + paddingLeft + paddingRight, h.toInt() + paddingTop + paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        DotFont.draw(canvas, text, left, top, cellPx, paint)
        val x = left + (DotFont.columns(text) + 2.5f) * cellPx
        canvas.drawCircle(x, top + 0.5f * cellPx, cellPx * 0.55f, red)
    }
}
