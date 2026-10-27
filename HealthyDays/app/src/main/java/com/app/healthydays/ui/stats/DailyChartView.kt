package com.app.healthydays.ui.stats

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import com.app.healthydays.R
import com.app.healthydays.data.dto.DayStat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DailyChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        /** Días que caben exactamente en el ancho visible de la gráfica. */
        const val WINDOW_DAYS = 30
    }

    private val density = resources.displayMetrics.density

    private var days: List<DayStat> = emptyList()

    // Geometría calculada en updateLayout()
    private var plotLeft = 0f
    private var plotRight = 0f
    private var plotTop = 0f
    private var plotBottom = 0f
    private var spacing = 0f          // distancia fija entre días
    private var maxScroll = 0f
    private var scrollOffset = 0f     // px desplazados desde el día más antiguo
    private val edgePadding = dp(6f)

    // Selección (cuadrito)
    private var selectedIndex = -1
    private var isSelecting = false

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

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stats_chart_line)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
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

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stats_tooltip_bg)
        style = Paint.Style.FILL
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stats_tooltip_text)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics
        )
        textAlign = Paint.Align.LEFT
    }

    private val scroller = OverScroller(context)

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean {
                scroller.forceFinished(true)
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // Tocar en cualquier parte descarta el cuadrito.
                if (selectedIndex != -1) {
                    selectedIndex = -1
                    invalidate()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val index = pointNear(e.x, e.y, dp(32f))
                if (index == -1) return

                isSelecting = true
                selectedIndex = index
                parent?.requestDisallowInterceptTouchEvent(true)
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (maxScroll <= 0f) return false
                // Si el gesto es horizontal, que la pantalla no haga scroll vertical.
                if (abs(distanceX) > abs(distanceY)) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                scrollOffset = (scrollOffset + distanceX).coerceIn(0f, maxScroll)
                invalidate()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (maxScroll <= 0f) return false
                scroller.fling(
                    scrollOffset.toInt(), 0,
                    (-velocityX).toInt(), 0,
                    0, maxScroll.toInt(),
                    0, 0
                )
                postInvalidateOnAnimation()
                return true
            }
        }
    )

    fun setData(newDays: List<DayStat>) {
        days = newDays
        selectedIndex = -1
        isSelecting = false
        scrollOffset = Float.MAX_VALUE // se recorta a maxScroll: empieza en el día más reciente
        scroller.forceFinished(true)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (isSelecting) {
                    // Mientras se mantiene pulsado, el cuadrito sigue al dedo.
                    val index = nearestByX(event.x)
                    if (index != -1 && index != selectedIndex) {
                        selectedIndex = index
                        invalidate()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> isSelecting = false
        }

        return handled || super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currX.toFloat().coerceIn(0f, maxScroll)
            postInvalidateOnAnimation()
        }
    }

    private fun updateLayout() {
        val yLabelWidth = textPaint.measureText("100%") + dp(6f)
        plotLeft = paddingLeft + yLabelWidth
        plotRight = width - paddingRight - dp(12f)
        plotTop = paddingTop + dp(8f)
        plotBottom = height - paddingBottom - dp(24f)

        val n = days.size
        val visible = min(n, WINDOW_DAYS)
        val usable = plotRight - plotLeft - 2 * edgePadding

        spacing = if (visible > 1 && usable > 0f) usable / (visible - 1) else 0f
        val contentWidth = if (n > 1) spacing * (n - 1) else 0f
        maxScroll = max(0f, contentWidth - usable)
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
    }

    private fun xFor(index: Int): Float =
        if (days.size == 1) (plotLeft + plotRight) / 2f
        else plotLeft + edgePadding + spacing * index - scrollOffset

    private fun yFor(percentage: Float): Float =
        plotBottom - (plotBottom - plotTop) * percentage / 100f

    /** Punto con datos, visible, a menos de maxDistance del toque. -1 si no hay. */
    private fun pointNear(x: Float, y: Float, maxDistance: Float): Int {
        var best = -1
        var bestDistance = maxDistance
        for (i in days.indices) {
            val percentage = days[i].percentage ?: continue
            val px = xFor(i)
            if (px < plotLeft || px > plotRight) continue
            val distance = hypot(px - x, yFor(percentage) - y)
            if (distance <= bestDistance) {
                best = i
                bestDistance = distance
            }
        }
        return best
    }

    /** Punto con datos, visible, más cercano en horizontal. -1 si no hay. */
    private fun nearestByX(x: Float): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in days.indices) {
            if (days[i].percentage == null) continue
            val px = xFor(i)
            if (px < plotLeft || px > plotRight) continue
            val distance = abs(px - x)
            if (distance < bestDistance) {
                best = i
                bestDistance = distance
            }
        }
        return best
    }

    /** Cada cuántos días se pone etiqueta en el eje X. */
    private fun labelStep(visibleDays: Int): Int = when {
        visibleDays <= 7 -> 1
        visibleDays <= 14 -> 2
        else -> 5
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateLayout()

        val chartWidth = plotRight - plotLeft
        val chartHeight = plotBottom - plotTop
        if (chartWidth <= 0f || chartHeight <= 0f) return

        // Eje Y fijo: líneas de referencia y etiquetas
        textPaint.textAlign = Paint.Align.RIGHT
        for (value in intArrayOf(0, 50, 100)) {
            val y = plotBottom - chartHeight * value / 100f
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
            canvas.drawText(
                "$value%",
                plotLeft - dp(6f),
                y + textPaint.textSize / 3f,
                textPaint
            )
        }

        val n = days.size
        if (n == 0) return

        // Etiquetas del eje X, contando desde el día más reciente
        val labelY = plotBottom + dp(18f)
        val step = labelStep(min(n, WINDOW_DAYS))
        textPaint.textAlign = Paint.Align.CENTER
        for (i in 0 until n) {
            if ((n - 1 - i) % step != 0) continue
            val x = xFor(i)
            if (x < plotLeft || x > plotRight) continue
            canvas.drawText(shortDate(days[i].date), x, labelY, textPaint)
        }

        // Línea y puntos, recortados al área de la gráfica
        canvas.save()
        canvas.clipRect(
            plotLeft, 0f,
            (width - paddingRight).toFloat(), height.toFloat()
        )

        // Segmentos: solo entre días consecutivos que ambos tienen datos
        for (i in 1 until n) {
            val previous = days[i - 1].percentage ?: continue
            val current = days[i].percentage ?: continue
            val x0 = xFor(i - 1)
            val x1 = xFor(i)
            if (x1 < plotLeft || x0 > plotRight) continue
            canvas.drawLine(x0, yFor(previous), x1, yFor(current), linePaint)
        }

        // Puntos: uno por cada día con datos
        val radius = dp(4f)
        for (i in 0 until n) {
            val percentage = days[i].percentage ?: continue
            val x = xFor(i)
            if (x < plotLeft - radius || x > plotRight + radius) continue
            canvas.drawCircle(x, yFor(percentage), radius, pointPaint)
        }

        canvas.restore()

        drawTooltip(canvas)
    }

    private fun drawTooltip(canvas: Canvas) {
        val index = selectedIndex
        if (index !in days.indices) return

        val day = days[index]
        val percentage = day.percentage ?: return

        val px = xFor(index)
        if (px < plotLeft || px > plotRight) return // el punto salió de la vista al deslizar
        val py = yFor(percentage)

        // Aro alrededor del punto seleccionado
        canvas.drawCircle(px, py, dp(7f), ringPaint)

        val text = "${fullDate(day.date)} · ${percentage.roundToInt()}% " +
                "(${day.completed}/${day.planned})"

        val paddingH = dp(10f)
        val paddingV = dp(6f)
        val boxWidth = tooltipTextPaint.measureText(text) + 2 * paddingH
        val boxHeight = tooltipTextPaint.textSize + 2 * paddingV

        val margin = dp(2f)
        val maxLeft = max(margin, width - boxWidth - margin)
        val left = (px - boxWidth / 2f).coerceIn(margin, maxLeft)

        // Encima del punto; si no cabe (por ejemplo al 100 %), debajo.
        var top = py - dp(12f) - boxHeight
        if (top < 0f) top = py + dp(12f)

        canvas.drawRoundRect(
            left, top, left + boxWidth, top + boxHeight,
            dp(6f), dp(6f), tooltipBgPaint
        )

        val metrics = tooltipTextPaint.fontMetrics
        val baseline = top + boxHeight / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, left + paddingH, baseline, tooltipTextPaint)
    }

    /** yyyy-MM-dd -> dd/MM */
    private fun shortDate(date: String): String =
        if (date.length >= 10) "${date.substring(8, 10)}/${date.substring(5, 7)}"
        else date

    /** yyyy-MM-dd -> dd/MM/yyyy */
    private fun fullDate(date: String): String =
        if (date.length >= 10) {
            "${date.substring(8, 10)}/${date.substring(5, 7)}/${date.substring(0, 4)}"
        } else date

    private fun dp(value: Float): Float = value * density
}