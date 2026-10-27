package com.app.healthydays.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.app.healthydays.R
import com.app.healthydays.data.dto.DayStat

class DailyChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var days: List<DayStat> = emptyList()

    private val density = resources.displayMetrics.density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stats_chart_line)
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stats_chart_line)
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stats_chart_grid)
        strokeWidth = dp(1f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stats_chart_text)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics
        )
    }

    fun setData(newDays: List<DayStat>) {
        days = newDays
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val yLabelWidth = textPaint.measureText("100%") + dp(6f)
        val left = paddingLeft + yLabelWidth
        val right = width - paddingRight - dp(8f)
        val top = paddingTop + dp(8f)
        val bottom = height - paddingBottom - dp(24f)

        val chartWidth = right - left
        val chartHeight = bottom - top
        if (chartWidth <= 0f || chartHeight <= 0f) return

        // Líneas de referencia y etiquetas del eje Y
        textPaint.textAlign = Paint.Align.RIGHT
        for (value in intArrayOf(0, 50, 100)) {
            val y = bottom - chartHeight * value / 100f
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(
                "$value%",
                left - dp(6f),
                y + textPaint.textSize / 3f,
                textPaint
            )
        }

        val n = days.size
        if (n == 0) return

        fun xFor(index: Int): Float =
            if (n == 1) left + chartWidth / 2f
            else left + chartWidth * index / (n - 1)

        fun yFor(percentage: Float): Float =
            bottom - chartHeight * percentage / 100f

        // Etiquetas del eje X: primer día, día central y último día
        val labelY = bottom + dp(18f)
        if (n == 1) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(shortDate(days[0].date), xFor(0), labelY, textPaint)
        } else {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(shortDate(days.first().date), left, labelY, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(shortDate(days.last().date), right, labelY, textPaint)

            if (n >= 5) {
                val middle = n / 2
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(
                    shortDate(days[middle].date), xFor(middle), labelY, textPaint
                )
            }
        }

        // Segmentos: solo entre días consecutivos que ambos tienen datos
        for (i in 1 until n) {
            val previous = days[i - 1].percentage ?: continue
            val current = days[i].percentage ?: continue
            canvas.drawLine(
                xFor(i - 1), yFor(previous),
                xFor(i), yFor(current),
                linePaint
            )
        }

        // Puntos: uno por cada día con datos (los aislados quedan como punto suelto)
        val radius = if (n > 60) dp(2f) else dp(4f)
        for (i in 0 until n) {
            val percentage = days[i].percentage ?: continue
            canvas.drawCircle(xFor(i), yFor(percentage), radius, pointPaint)
        }
    }

    /** yyyy-MM-dd -> dd/MM */
    private fun shortDate(date: String): String =
        if (date.length >= 10) "${date.substring(8, 10)}/${date.substring(5, 7)}"
        else date

    private fun dp(value: Float): Float = value * density
}