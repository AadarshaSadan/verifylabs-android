package com.verifylabs.ai.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QualityScore(val rawScore: Int, val maxScore: Int = 100) {
    val percentage: Int
        get() = ((rawScore.toDouble() / maxScore.toDouble()) * 100).toInt()

    val grade: String
        get() =
                when (percentage) {
                    in 90..100 -> "Excellent"
                    in 80 until 90 -> "Very Good"
                    in 70 until 80 -> "Good"
                    in 60 until 70 -> "Fair"
                    in 50 until 60 -> "Poor"
                    else -> "Very Poor"
                }
}

object MediaQualityAnalyzer {

    private const val TAG = "MediaQualityAnalyzer"

    suspend fun analyzeImage(context: Context, uri: Uri): QualityScore =
            withContext(Dispatchers.IO) {
                try {
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input)
                    } ?: throw Exception("Failed to open input stream")

                    // Downscale for analysis if too large (max 1024px) for performance
                    val maxDim = 1024
                    val scaledBitmap =
                            if (bitmap.width > maxDim || bitmap.height > maxDim) {
                                val ratio =
                                        min(
                                                maxDim.toDouble() / bitmap.width,
                                                maxDim.toDouble() / bitmap.height
                                        )
                                Bitmap.createScaledBitmap(
                                        bitmap,
                                        (bitmap.width * ratio).toInt(),
                                        (bitmap.height * ratio).toInt(),
                                        true
                                )
                            } else {
                                bitmap
                            }

                    var score = 0

                    // 1. Resolution Scoring (based on original bitmap)
                    val megapixels = (bitmap.width * bitmap.height) / 1_000_000.0
                    score +=
                            when {
                                megapixels >= 24 -> 25
                                megapixels >= 12 -> 20
                                megapixels >= 8 -> 15
                                megapixels >= 4 -> 10
                                else -> 5
                            }

                    // 2. Brightness & Contrast & Entropy (on scaled bitmap)
                    val (brightness, contrast, entropy, dynamicRange) =
                            calculateMetrics(scaledBitmap)

                    // Contrast Scoring
                    score += min((contrast / 5).toInt(), 15)

                    // Dynamic Range Scoring
                    score += min((dynamicRange / 10).toInt(), 15)

                    // Entropy Scoring
                    score += min((entropy * 2).toInt(), 10)

                    // 3. Sharpness
                    val sharpness = calculateSharpness(scaledBitmap)
                    score += min((sharpness / 10).toInt(), 15)

                    // 4. Bit Depth (Approximation, usually 8-bit per channel on Android Bitmap)
                    // ARGB_8888 is 8 bits per channel
                    val bitDepth = if (bitmap.config == Bitmap.Config.ARGB_8888) 8 else 4
                    score +=
                            when {
                                bitDepth >= 16 -> 15
                                bitDepth >= 8 -> 10
                                else -> 5
                            }

                    // Cap at 100
                    QualityScore(min(score, 100))
                } catch (e: Exception) {
                    Log.e(TAG, "Analysis failed", e)
                    QualityScore(50) // Fallback
                }
            }

    suspend fun analyzeVideo(context: Context, uri: Uri): QualityScore =
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    var score = 0

                    // 1. Resolution
                    val width =
                            retriever
                                    .extractMetadata(
                                            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                                    )
                                    ?.toIntOrNull()
                                    ?: 0
                    val height =
                            retriever
                                    .extractMetadata(
                                            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                                    )
                                    ?.toIntOrNull()
                                    ?: 0
                    val pixels = width * height

                    score +=
                            when {
                                pixels >= 3840 * 2160 -> 30 // 4K
                                pixels >= 1920 * 1080 -> 25 // 1080p
                                pixels >= 1280 * 720 -> 15 // 720p
                                else -> 10
                            }

                    // 2. Bitrate
                    val bitrateWrapper =
                            retriever
                                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                                    ?.toLongOrNull()
                    val bitrate = bitrateWrapper ?: 0L
                    val megapixels = pixels / 1_000_000.0

                    if (megapixels > 0 && bitrate > 0) {
                        val bitrateMbps = bitrate / 1_000_000.0
                        val bitratePerMP = bitrateMbps / megapixels
                        score +=
                                when {
                                    bitratePerMP >= 8 -> 20
                                    bitratePerMP >= 5 -> 15
                                    bitratePerMP >= 3 -> 10
                                    else -> 5
                                }
                    } else {
                        score += 5
                    }

                    // 3. Frame Rate (Approximation, standard is often 30)
                    // Android doesn't always expose FPS easily via MetadataRetriever, assuming 30
                    // for standard
                    score += 15

                    // 4. Codec (Mime Type)
                    val mimeType =
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    score +=
                            when {
                                mimeType?.contains("hevc") == true ||
                                        mimeType?.contains("h265") == true -> 15
                                mimeType?.contains("avc") == true ||
                                        mimeType?.contains("h264") == true -> 12
                                else -> 8
                            }

                    // 5. Audio Presence
                    val hasAudio =
                            retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                            ) != null
                    if (hasAudio) score += 5

                    QualityScore(min(score, 100))
                } catch (e: Exception) {
                    Log.e(TAG, "Video Analysis failed", e)
                    QualityScore(50)
                } finally {
                    retriever.release()
                }
            }

    private fun calculateMetrics(bitmap: Bitmap): Metrics {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sum: Long = 0
        var sumSquares: Long = 0
        val histogram = IntArray(256)
        var minVal = 255
        var maxVal = 0

        // Use stride for performance (sample every 4th pixel)
        val step = 4
        var count = 0

        for (i in pixels.indices step step) {
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val gray = (r + g + b) / 3

            sum += gray
            sumSquares += (gray * gray).toLong()
            histogram[gray]++
            if (gray < minVal) minVal = gray
            if (gray > maxVal) maxVal = gray
            count++
        }

        val brightness = sum.toFloat() / count
        val variance = (sumSquares.toFloat() / count) - (brightness * brightness)
        val contrast = sqrt(max(0f, variance))
        val dynamicRange = (maxVal - minVal).toFloat()

        var entropy = 0f
        for (h in histogram) {
            if (h > 0) {
                val p = h.toFloat() / count
                entropy -= p * log2(p)
            }
        }

        return Metrics(brightness, contrast, entropy, dynamicRange)
    }

    private fun calculateSharpness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var edgeSum: Long = 0
        val step = 4
        var count = 0

        // Simple gradient check
        for (y in 1 until height - 1 step step) {
            for (x in 1 until width - 1 step step) {
                val idx = y * width + x
                val p = pixels[idx]
                val gray = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3

                // Neighbors
                val pLeft = pixels[idx - 1]
                val grayLeft = (Color.red(pLeft) + Color.green(pLeft) + Color.blue(pLeft)) / 3

                val pTop = pixels[idx - width]
                val grayTop = (Color.red(pTop) + Color.green(pTop) + Color.blue(pTop)) / 3

                val gx = kotlin.math.abs(gray - grayLeft)
                val gy = kotlin.math.abs(gray - grayTop)

                edgeSum += (gx + gy)
                count++
            }
        }

        return if (count > 0) edgeSum.toFloat() / count else 0f
    }

    fun getQualityImprovementAdvice(score: Int): String {
        return when (score) {
            in 90..100 ->
                    "This media has excellent quality with great resolution, sharpness, and color depth. It provides the best conditions for accurate AI detection analysis. High-quality media like this gives our system the most detail to work with."
            in 80 until 90 ->
                    "This media has very good quality. It could be slightly improved with better lighting conditions, higher resolution, or reduced motion blur. These factors help our AI detection work more accurately by providing clearer details to analyze."
            in 70 until 80 ->
                    "This media has good quality but could be better. Improved lighting, higher resolution, or capturing with a better camera would help. Media shot in bright natural light with a main camera lens typically provides more detail for accurate AI detection analysis."
            in 60 until 70 ->
                    "This media has fair quality. Better results could be achieved with improved lighting, steadier capture to reduce blur, or higher quality camera settings. Digital zoom appears to have been used which reduces sharpness. Clearer media helps provide more reliable AI detection results."
            in 50 until 60 ->
                    "This media has low quality which may affect detection accuracy. The lighting appears insufficient, there may be lens issues affecting clarity, or heavy compression has been applied. Media captured with better lighting, a cleaner lens, without filters or beauty effects, and at full resolution would provide much better analysis results."
            else ->
                    "This media has very low quality which impacts reliable analysis. Issues include poor lighting, possible lens obstruction, low resolution, or heavy compression. For accurate AI detection, media needs to be captured in bright conditions, with a clean unobstructed lens, at high resolution, and without heavy editing or compression."
        }
    }

    data class Metrics(
            val brightness: Float,
            val contrast: Float,
            val entropy: Float,
            val dynamicRange: Float
    )
}
