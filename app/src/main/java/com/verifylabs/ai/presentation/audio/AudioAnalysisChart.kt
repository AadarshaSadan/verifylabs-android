package com.verifylabs.ai.presentation.audio

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.verifylabs.ai.R

class AudioAnalysisChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var averageScore: Double = 0.0
    private var dataPoints: List<Double> = emptyList()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575")
        textSize = 24f
        textAlign = Paint.Align.RIGHT
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#81C784") // Default green
        style = Paint.Style.FILL
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val labels = listOf("1.00", "0.95", "0.85", "0.65", "0.50", "0.00")
    private val labelValues = listOf(1.0, 0.95, 0.85, 0.65, 0.50, 0.0)

    fun setScore(score: Double) {
        this.averageScore = score
        // Generate representative points around the score for single result compatibility
        val points = mutableListOf<Double>()
        for (i in 0 until 20) {
            val variance = (Math.random() - 0.5) * 0.05
            points.add((score + variance).coerceIn(0.0, 1.0))
        }
        this.dataPoints = points
        
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
            score < 0.2 -> Color.parseColor("#4CAF50") // Human - Green
            score < 0.4 -> Color.parseColor("#8BC34A") // Likely Human - Light Green
            score < 0.6 -> Color.parseColor("#9E9E9E") // Unsure - Gray
            score < 0.8 -> Color.parseColor("#FF5252") // Likely AI - Light Red
            else -> Color.parseColor("#D32F2F") // AI - Red
        }
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

        // Title
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = 32f
        textPaint.isFakeBoldText = true
        canvas.drawText("Audio Analysis", padding, padding + 30f, textPaint)

        textPaint.textSize = 24f
        textPaint.color = Color.parseColor("#E0E0E0")
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

        if (dataPoints.isNotEmpty()) {
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
            canvas.drawText("Avg: %.2f".format(averageScore), badgeX + badgeWidth / 2, badgeY + badgeHeight / 2 + 8f, badgeTextPaint)

            // Draw Data Points and connecting lines
            val stepX = chartWidth / (dataPoints.size - 1).coerceAtLeast(1)
            val path = Path()
            for (i in dataPoints.indices) {
                val px = chartLeft + (i * stepX)
                val py = chartBottom - (dataPoints[i].toFloat() * chartHeight)
                
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                
                pointPaint.color = getScoreColor(dataPoints[i])
                canvas.drawCircle(px, py, 6f, pointPaint)
            }
            
            // Draw the trend line connecting points
            val trendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                strokeWidth = 2f
                style = Paint.Style.STROKE
                alpha = 150
            }
            canvas.drawPath(path, trendPaint)
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
            textPaint.color = Color.WHITE
            canvas.drawText(legendLabels[i], lx + 25f, legendY - 2f, textPaint)
        }

        // Bottom label '0'
        textPaint.color = Color.LTGRAY
        canvas.drawText("0", chartLeft, chartBottom + 30f, textPaint)
    }
}
