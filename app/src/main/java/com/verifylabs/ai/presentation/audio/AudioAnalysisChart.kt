package com.verifylabs.ai.presentation.audio

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.verifylabs.ai.R

class AudioAnalysisChart
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        View(context, attrs, defStyleAttr) {

    private var averageScore: Double = 0.0
    private var dataPoints: List<Double> = emptyList()

    private val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.audio_chart_background)
                style = Paint.Style.FILL
            }

    private val gridPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#BDBDBD")
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }

    private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.secondary_text)
                textSize = 24f
                textAlign = Paint.Align.RIGHT
            }

    private val badgePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#81C784") // Default green
                style = Paint.Style.FILL
            }

    private val badgeTextPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 22f
                textAlign = Paint.Align.CENTER
            }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 4f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }

    private val labels = listOf("1.00", "0.95", "0.85", "0.65", "0.50", "0.00")
    private val labelValues = listOf(1.0, 0.95, 0.85, 0.65, 0.50, 0.0)

    fun setScore(score: Double) {
        this.averageScore = score
        // iOS Parity: Use actual data only, no fake noise
        this.dataPoints = listOf(score)

        // Update badge color based on score
        badgePaint.color = getScoreColor(score)
        invalidate()
    }

    fun setChronologicalScores(scores: List<Double>) {
        if (scores.isEmpty()) return
        this.dataPoints = scores
        this.averageScore = scores.average()

        // Update badge color based on average
        badgePaint.color = getScoreColor(this.averageScore)
        invalidate()
    }

    private fun getScoreColor(score: Double): Int {
        return when {
            score <= 0.5 -> Color.parseColor("#4CAF50") // Human - Green
            score <= 0.65 -> Color.parseColor("#8BC34A") // Likely Human - Light Green
            score <= 0.85 -> Color.parseColor("#9E9E9E") // Unsure - Gray
            score <= 0.95 -> Color.parseColor("#FF5252") // Likely AI - Light Red
            else -> Color.parseColor("#D32F2F") // AI - Red
        }
    }

    fun reset() {
        this.dataPoints = emptyList()
        this.averageScore = 0.0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 40f
        val chartLeft = padding + 20f
        val chartRight = w - padding - 100f
        val chartTop = padding + 60f
        val chartBottom = h - padding - 80f
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // Draw background
        val rect = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(rect, 24f, 24f, backgroundPaint)

        // Only draw chart elements if we have data
        if (dataPoints.isNotEmpty()) {
            textPaint.textSize = 24f
            textPaint.color = ContextCompat.getColor(context, R.color.secondary_text) // Axis labels
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.isFakeBoldText = false

            for (i in labels.indices) {
                val y = chartBottom - (labelValues[i].toFloat() * chartHeight)
                canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
                // Draw labels on the right side of the chart area
                canvas.drawText(labels[i], chartRight + 15f, y + 8f, textPaint)
            }

            // Left vertical axis line
            canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, gridPaint)
            // Right vertical axis line
            canvas.drawLine(chartRight, chartTop, chartRight, chartBottom, gridPaint)

            // Draw average line
            val avgY = chartBottom - (averageScore.toFloat() * chartHeight)
            linePaint.color = getScoreColor(averageScore)
            canvas.drawLine(chartLeft, avgY, chartRight, avgY, linePaint)

            // Draw Avg Badge
            val badgeWidth = 100f
            val badgeHeight = 40f
            val badgeX = chartLeft + 10f
            val badgeY = avgY - badgeHeight - 10f
            val badgeRect = RectF(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight)
            canvas.drawRoundRect(badgeRect, 8f, 8f, badgePaint)
            badgeTextPaint.color = Color.WHITE
            canvas.drawText(
                "Avg: %.2f".format(averageScore),
                badgeX + badgeWidth / 2,
                badgeY + badgeHeight / 2 + 8f,
                badgeTextPaint
            )

            // Draw Data Points and connecting lines (Smooth Curve)
            val stepX = if (dataPoints.size > 1) chartWidth / (dataPoints.size - 1) else 0f
            val path = Path()

            val points =
                dataPoints.mapIndexed { i, score ->
                    PointF(
                        chartLeft + (i * stepX),
                        chartBottom - (score.toFloat() * chartHeight)
                    )
                }

            if (points.isNotEmpty()) {
                path.moveTo(points[0].x, points[0].y)

                if (points.size == 1) {
                    // Single point: just draw a small line or dot
                    path.lineTo(points[0].x + 1, points[0].y)
                } else {
                    // Catmull-Rom spline interpolation logic translated to Cubic Bezier
                    for (i in 0 until points.size - 1) {
                        val p0 = points[if (i > 0) i - 1 else 0]
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = points[if (i + 2 < points.size) i + 2 else points.size - 1]

                        val cp1x = p1.x + (p2.x - p0.x) * 0.2f
                        val cp1y = p1.y + (p2.y - p0.y) * 0.2f
                        val cp2x = p2.x - (p3.x - p1.x) * 0.2f
                        val cp2y = p2.y - (p3.y - p1.y) * 0.2f

                        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                    }
                }
            }

            // Draw trend line
            val trendPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    strokeWidth = 4f
                    style = Paint.Style.STROKE
                    alpha = 200
                    // strokeJoin = Paint.Join.ROUND
                    // strokeCap = Paint.Cap.ROUND
                }
            canvas.drawPath(path, trendPaint)

            // Draw points on top
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 20f
            textPaint.color = Color.LTGRAY

            for ((i, p) in points.withIndex()) {
                val score = (chartBottom - p.y) / chartHeight
                pointPaint.color = getScoreColor(score.toDouble())
                canvas.drawCircle(p.x, p.y, 8f, pointPaint)

                // Draw X-axis label (1, 2, 3...)
                canvas.drawText((i + 1).toString(), p.x, chartBottom + 30f, textPaint)
            }

            // Legend
            val legendY = h - 30f
            val legendItemWidth = (w - padding * 2) / 5
            val legendLabels = listOf("Human", "Likely...", "Unsure", "Likely...", "AI")
            val legendColors = listOf("#4CAF50", "#8BC34A", "#9E9E9E", "#FF5252", "#D32F2F")

            for (i in legendLabels.indices) {
                val lx = padding + (i * legendItemWidth)
                pointPaint.color = Color.parseColor(legendColors[i])
                canvas.drawCircle(lx + 10f, legendY - 10f, 8f, pointPaint)
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.textSize = 20f
                textPaint.color = ContextCompat.getColor(context, R.color.secondary_text)
                canvas.drawText(legendLabels[i], lx + 25f, legendY - 2f, textPaint)
            }
        }
    }
}
