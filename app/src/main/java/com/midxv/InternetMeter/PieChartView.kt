package com.midxv.InternetMeter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<AppUsageInfo> = emptyList()
    private val colors = listOf(0xFFB366FF.toInt(), 0xFF8A33FF.toInt(), 0xFFE6D5FF.toInt(), 0xFF5C4B75.toInt(), 0xFF2D1B4E.toInt())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun setUsageData(newData: List<AppUsageInfo>) {
        data = newData.take(5)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val total = data.sumOf { it.total }.toFloat()
        if (total == 0f) return

        val size = Math.min(width, height).toFloat() * 0.8f
        val left = (width - size) / 2
        val top = (height - size) / 2
        rect.set(left, top, left + size, top + size)

        var startAngle = 0f
        data.forEachIndexed { index, info ->
            val sweepAngle = (info.total / total) * 360f
            paint.color = colors[index % colors.size]
            canvas.drawArc(rect, startAngle, sweepAngle, true, paint)
            startAngle += sweepAngle
        }
    }
}