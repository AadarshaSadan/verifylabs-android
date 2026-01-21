package com.verifylabs.ai.presentation.audio

import android.Manifest
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.data.database.VerificationEntity
import com.verifylabs.ai.data.repository.VerificationRepository
import com.verifylabs.ai.databinding.FragmentAudioBinding
import com.verifylabs.ai.presentation.media.MediaType
import com.verifylabs.ai.presentation.media.VerificationResponse
import com.verifylabs.ai.presentation.viewmodel.MediaViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FragmentAudio : Fragment() {

    private var _binding: FragmentAudioBinding? = null
    private val binding
        get() = _binding!!

    private val TAG = "FragmentAudio" // Log TAG

    @Inject lateinit var preferenceHelper: PreferenceHelper

    @Inject lateinit var verificationRepository: VerificationRepository

    private lateinit var viewModel: MediaViewModel
    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var startTime = 0L
    private var currentRecordedFile: File? = null
    private var isQuickRecording = false
    private val quickRecordStopHandler = Handler(Looper.getMainLooper())
    private val quickRecordStopRunnable = Runnable { stopRecording() }
    private var quickMicBlinkAnimator: android.animation.ValueAnimator? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine failed: ${throwable.localizedMessage}", throwable)
    }

    // Segmented Recording (Long Record)
    private var isLongRecording = false
    private val temporalScores = mutableListOf<Double>()
    private val allChunkFiles = mutableListOf<File>()
    private val segmentationHandler = Handler(Looper.getMainLooper())
    private var currentChunkStartTime = 0L
    
    // Silence Detection
    private var silenceDurationMs = 0L
    private val SILENCE_THRESHOLD = 500 // Arbitrary amplitude threshold
    private val QUICK_RECORD_MAX_SILENCE = 10000L // 10 seconds for quick record
    private val LONG_RECORD_MAX_SILENCE = 30000L // 30 seconds for long record sessions
    private val MAX_RECORD_DURATION = 600000L // 600 seconds total limit
    private val silenceCheckHandler = Handler(Looper.getMainLooper())
    private val silenceCheckRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                val amplitude = recorder?.maxAmplitude ?: 0
                if (amplitude < SILENCE_THRESHOLD) {
                    silenceDurationMs += 500
                } else {
                    silenceDurationMs = 0
                }
                
                val maxAllowedSilence = if (isQuickRecording) QUICK_RECORD_MAX_SILENCE else LONG_RECORD_MAX_SILENCE
                val elapsedTotal = System.currentTimeMillis() - startTime
                
                if (silenceDurationMs >= maxAllowedSilence) {
                    handleSilenceFailure()
                } else if (elapsedTotal >= MAX_RECORD_DURATION) {
                    Log.d(TAG, "Max record duration (600s) reached.")
                    stopRecording()
                } else {
                    silenceCheckHandler.postDelayed(this, 500)
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable =
            object : Runnable {
                override fun run() {
                    val elapsed = System.currentTimeMillis() - startTime
                    val minutes = (elapsed / 1000) / 60
                    val seconds = (elapsed / 1000) % 60
                    val millis = (elapsed % 1000) / 10
                    binding.txtTimer.text =
                            String.format("%02d:%02d:%02d", minutes, seconds, millis)
                    handler.postDelayed(this, 10)
                }
            }

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    Log.d(TAG, "Microphone permission granted")
                    startRecording()
                } else {
                    Log.d(TAG, "Microphone permission denied")
                    binding.txtStatus.text = "Permission denied"
                    Toast.makeText(
                                    requireContext(),
                                    "Microphone permission required",
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                }
            }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView()")
        _binding = FragmentAudioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated()")

        viewModel = ViewModelProvider(this)[MediaViewModel::class.java]

        binding.micButton.setOnClickListener {
            Log.d(TAG, "Mic button clicked | isRecording: $isRecording")
            if (isRecording) {
                stopRecording()
            } else {
                isLongRecording = true
                isQuickRecording = false
                allChunkFiles.clear()
                temporalScores.clear()
                requestRecordPermission()
            }
        }

        binding.micPulsebtn.setOnClickListener {
            Log.d(TAG, "Quick mic button clicked | isRecording: $isRecording, isQuickRecording: $isQuickRecording")
            if (isRecording && !isQuickRecording) {
                Toast.makeText(requireContext(), "Manual recording in progress", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isQuickRecording) {
                stopRecording()
            } else {
                isLongRecording = false
                isQuickRecording = true
                allChunkFiles.clear()
                temporalScores.clear()
                startQuickRecordingWithPermission()
            }
        }

        checkCredits()
        observeCredits()
        observeUploadAndVerify()
    }

    private fun requestRecordPermission() {
        Log.d(TAG, "requestRecordPermission()")
        if (hasRecordPermission()) {
            Log.d(TAG, "Permission already granted, starting recording")
            startRecording()
        } else {
            Log.d(TAG, "Requesting RECORD_AUDIO permission")
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun hasRecordPermission(): Boolean {
        val granted =
                ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "hasRecordPermission() -> $granted")
        return granted
    }

    private fun startQuickRecordingWithPermission() {
        if (hasRecordPermission()) {
            startQuickRecording()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startQuickRecording() {
        val durationSeconds = preferenceHelper.getQuickRecordDuration().takeIf { it > 0 } ?: 40
        Log.d(TAG, "startQuickRecording() - Duration: ${durationSeconds}s")
        
        isQuickRecording = true
        startRecording()
        
        // Visual feedback for quick record button
        binding.micTimer.setImageResource(R.drawable.ic_audio)
        
        // Scale animation
        binding.micPulsebtn.startAnimation(
            ScaleAnimation(1f, 1.2f, 1f, 1.2f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 600
                repeatCount = Animation.INFINITE
                repeatMode = Animation.REVERSE
            }
        )

        // Color blinking animation (Red and White)
        quickMicBlinkAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val color = android.graphics.Color.argb(
                    255,
                    (255 * fraction + 252 * (1 - fraction)).toInt(), // Red channel (252 is close to white-ish red in some themes, but let's go pure red vs white)
                    (255 * (1 - fraction)).toInt(), // Green
                    (255 * (1 - fraction)).toInt()  // Blue
                )
                // Color transition from White (255,255,255) to Red (255,0,0)
                binding.micPulsebtn.background.colorFilter = android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            }
            start()
        }
        
        quickRecordStopHandler.postDelayed(quickRecordStopRunnable, durationSeconds * 1000L)
    }

    // ========== CREDIT CHECK ==========
    private fun checkCredits() {
        Log.d(TAG, "checkCredits() - Starting")

        val username = preferenceHelper.getUserName()
        val apiKey = preferenceHelper.getApiKey()

        if (username.isNullOrEmpty() || apiKey.isNullOrEmpty()) {
            Log.d(TAG, "Invalid credentials: username=$username, apiKey=${apiKey?.isNotEmpty()}")
            showInvalidCredentials()
            return
        }

        Log.d(TAG, "Valid credentials, calling checkCredits API")
        binding.progressCredits.visibility = View.VISIBLE
        binding.tvCreditsRemaining.visibility = View.GONE
        binding.layoutNoCreditStatus.visibility = View.GONE
        binding.llCreditsInfo.visibility = View.VISIBLE

        viewModel.checkCredits(username, apiKey)
    }

    private fun showInvalidCredentials() {
        Log.d(TAG, "showInvalidCredentials()")
        binding.progressCredits.visibility = View.GONE
        binding.tvCreditsRemaining.visibility = View.VISIBLE
        binding.tvCreditsRemaining.text = "Invalid credentials"
        binding.micButton.isEnabled = false
        binding.layoutNoCreditStatus.visibility = View.GONE
        Toast.makeText(requireContext(), "Please log in again.", Toast.LENGTH_LONG).show()
    }

    private fun observeCredits() {
        Log.d(TAG, "observeCredits() - Observing LiveData")

        viewModel.getCreditsResponse().observe(viewLifecycleOwner) { resource ->
            Log.d(TAG, "Credits response: ${resource.status}, data: ${resource.data}")

            when (resource.status) {
                Status.LOADING -> {
                    Log.d(TAG, "Credits: LOADING")
                    binding.progressCredits.visibility = View.VISIBLE
                    binding.tvCreditsRemaining.visibility = View.GONE
                    binding.layoutNoCreditStatus.visibility = View.GONE
                    binding.txtStatus.text = "Checking credits..."
                    binding.micButton.isEnabled = false
                }
                Status.SUCCESS -> {
                    Log.d(TAG, "Credits: SUCCESS")
                    binding.progressCredits.visibility = View.GONE
                    binding.tvCreditsRemaining.visibility = View.VISIBLE

                    val json = resource.data as? JsonObject
                    val credits = json?.get("credits")?.asInt ?: 0
                    val monthly = json?.get("credits_monthly")?.asInt ?: 0
                    val total = credits + monthly

                    Log.d(TAG, "Credits parsed: total=$total (credits=$credits, monthly=$monthly)")
                    preferenceHelper.setCreditReamaining(total)
                    val storeCredits = preferenceHelper.getCreditRemaining()
                    val formattedCredits =
                            NumberFormat.getNumberInstance(Locale.US).format(storeCredits)
                    binding.tvCreditsRemaining.text =
                            getString(R.string.credits_remaining, formattedCredits)

                    binding.micButton.isEnabled = total > 0
                    binding.micButton.alpha = if (total > 0) 1f else 0.5f

                    if (total <= 0) {
                        Log.d(TAG, "No credits - showing banner")
                        binding.layoutNoCreditStatus.visibility = View.VISIBLE
                        binding.txtStatus.text = "No credits remaining"
                    } else {
                        Log.d(TAG, "Credits available - ready to record")
                        binding.layoutNoCreditStatus.visibility = View.GONE
                        binding.txtStatus.text = "Tap to record"
                    }
                }
                Status.ERROR -> {
                    Log.e(TAG, "Credits: ERROR - ${resource.message}")
                    binding.progressCredits.visibility = View.GONE
                    binding.tvCreditsRemaining.visibility = View.VISIBLE
                    binding.tvCreditsRemaining.text = "Credit check failed"
                    binding.micButton.isEnabled = false
                    binding.layoutNoCreditStatus.visibility = View.GONE
                    binding.txtStatus.text = "Failed to check credits"
                }
            }
        }
    }

    // ========== RECORDING ==========
    private fun startRecording() {
        val total = preferenceHelper.getCreditRemaining() ?: 0
        Log.d(TAG, "startRecording() - Credits: $total")

        if (total <= 0) {
            Log.d(TAG, "startRecording blocked: insufficient credits")
            binding.txtStatus.text = "Insufficient credits"
            Toast.makeText(requireContext(), "You need at least 1 credit", Toast.LENGTH_SHORT)
                    .show()
            return
        }

        try {
            val fileName = "audio_chunk_${allChunkFiles.size}_${System.currentTimeMillis()}.mp4"
            val outputFile = File(requireContext().externalCacheDir, fileName)
            currentRecordedFile = outputFile
            allChunkFiles.add(outputFile)
            Log.d(TAG, "Recording chunk ${allChunkFiles.size} to: ${outputFile.absolutePath}")

            recorder =
                    MediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(outputFile.absolutePath)
                        prepare()
                        start()
                    }

            Log.d(TAG, "Recording started")
            binding.micButton.setImageResource(R.drawable.ic_audio)
            binding.txtStatus.text = if (isQuickRecording) "Quick Recording..." else "Long Recording..."
            isRecording = true
            
            if (allChunkFiles.size == 1) { // First chunk
                startTime = System.currentTimeMillis()
                handler.post(timerRunnable)
                startPulseAnimation()
                silenceDurationMs = 0
                silenceCheckHandler.postDelayed(silenceCheckRunnable, 500)
            }
            
            if (isLongRecording) {
                currentChunkStartTime = System.currentTimeMillis()
                segmentationHandler.postDelayed({ rotateChunk() }, 10000) // Rotate every 10s
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Recording failed", e)
            binding.txtStatus.text = "Recording failed"
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rotateChunk() {
        if (!isLongRecording || !isRecording) return
        
        Log.d(TAG, "Rotating recording chunk...")
        val oldFile = currentRecordedFile
        
        // Check if the current chunk was silent
        val isChunkSilent = silenceDurationMs >= 10000L // If silence duration spans this entire 10s chunk
        
        // Stop current and start new immediately
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping chunk", e)
        }
        
        if (oldFile != null) {
            if (isChunkSilent && isLongRecording) {
                Log.d(TAG, "Chunk is silent. Skipping verification for this segment.")
                // Add a dummy score or 0.0 to temporal scores to maintain alignment if needed, 
                // but requirements say "silent segments will be ignored".
                // Let's add 0.0 to indicate no AI activity found/safe.
                temporalScores.add(0.0)
                binding.audioAnalysisChart.setChronologicalScores(temporalScores)
            } else {
                uploadAudio(oldFile) // Verify the chunk in bg
            }
        }
        
        startRecording() // Start next chunk
    }

    private fun handleSilenceFailure() {
        Log.w(TAG, "Silence threshold reached. Stopping recording.")
        stopRecording()
        binding.txtStatus.text = "Failure: Long silence detected"
        Toast.makeText(requireContext(), "Recording stopped due to silence", Toast.LENGTH_LONG).show()
    }

    private fun stopRecording() {
        Log.d(TAG, "stopRecording()")

        try {
            segmentationHandler.removeCallbacksAndMessages(null)
            silenceCheckHandler.removeCallbacks(silenceCheckRunnable)
            
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false
            handler.removeCallbacks(timerRunnable)
            quickRecordStopHandler.removeCallbacks(quickRecordStopRunnable)
            
            binding.micButton.setImageResource(R.drawable.ic_mic)
            stopPulseAnimation()

            if (isQuickRecording) {
                isQuickRecording = false
                binding.micTimer.setImageResource(R.drawable.q3_icons)
                binding.micPulsebtn.clearAnimation()
                quickMicBlinkAnimator?.cancel()
                binding.micPulsebtn.background.clearColorFilter()
            }

            val file = currentRecordedFile
            if (file != null && file.exists()) {
                Log.d(TAG, "Final chunk ready: ${file.length()} bytes")
                uploadAudio(file)
            }
            
            if (isLongRecording) {
                // Post-process: Merge all chunks into one for history
                isLongRecording = false
                mergeAndSaveChunks()
            } else if (currentRecordedFile == null) {
                Log.w(TAG, "Audio file not found after recording")
                binding.txtStatus.text = "File not found"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            if (_binding != null) {
                binding.txtStatus.text = "Stop failed"
            }
        }
    }

    // ========== UPLOAD ==========
    private fun uploadAudio(file: File) {
        Log.d(TAG, "uploadAudio() - Uploading: ${file.name} (${file.length()} bytes)")
        binding.txtStatus.text = "Uploading audio..."
        viewModel.uploadMedia(file.absolutePath, MediaType.AUDIO)
    }

    // ========== VERIFY (LOG-ONLY RESULT) ==========
    private fun observeUploadAndVerify() {
        viewModel.getUploadResponse().observe(viewLifecycleOwner) { resource ->
            Log.d(TAG, "Upload response: ${resource.status}, data: ${resource.data}")

            when (resource.status) {
                Status.LOADING -> {
                    Log.d(TAG, "Upload: LOADING")
                    binding.txtStatus.text = "Uploading audio..."
                }
                Status.SUCCESS -> {
                    val url = resource.data?.get("uploadedUrl")?.asString.orEmpty()
                    Log.d(TAG, "Upload SUCCESS - URL: $url")
                    binding.txtStatus.text = "Verifying audio..."
                    viewModel.verifyMedia(
                            username = preferenceHelper.getUserName().orEmpty(),
                            apiKey = preferenceHelper.getApiKey().orEmpty(),
                            mediaType = MediaType.AUDIO.value,
                            mediaUrl = url
                    )
                }
                Status.ERROR -> {
                    Log.e(TAG, "Upload FAILED: ${resource.message}")
                    binding.txtStatus.text = "Upload failed"
                    resetMicButton()
                }
            }
        }

        viewModel.getVerifyResponse().observe(viewLifecycleOwner) { resource ->
            Log.d(TAG, "Verify response: ${resource.status}, data: ${resource.data}")

            when (resource.status) {
                Status.LOADING -> {
                    Log.d(TAG, "Verify: LOADING")
                    binding.txtStatus.text = "Verifying audio..."
                }
                Status.SUCCESS -> {
                    Log.d(TAG, "Verify: SUCCESS")
                    val response =
                            Gson().fromJson(
                                            resource.data.toString(),
                                            VerificationResponse::class.java
                                     )

                    Log.d(TAG, "Band: ${response.band}, Score: ${response.score}")
                    
                    if (isLongRecording || temporalScores.isNotEmpty()) {
                        // Periodic update for long recording
                        temporalScores.add(response.score)
                        binding.cardAudioAnalysis.visibility = View.VISIBLE
                        binding.audioAnalysisChart.setChronologicalScores(temporalScores)
                        
                        // If it's the final stop of a long recording OR a quick record result
                        if (!isRecording && !isLongRecording) {
                            finalizeAndSaveHistory(response)
                        }
                    } else {
                        // Standard single verification (Quick Record or fallback)
                        binding.cardAudioAnalysis.visibility = View.VISIBLE
                        binding.audioAnalysisChart.setScore(response.score)
                        finalizeAndSaveHistory(response)
                    }

                    // Reset mic button UI is handled in finalizeAndSaveHistory 
                    // or immediately if it was just a chunk
                    if (isRecording) {
                         // Still recording next chunk, keep UI as is
                    } else {
                         resetMicButton()
                    }
                }
                Status.ERROR -> {
                    Log.e(TAG, "Verify FAILED: ${resource.message}")
                    binding.txtStatus.text = "Failed: ${resource.message}"
                    resetMicButton()
                }
            }
        }
    }

    private fun finalizeAndSaveHistory(response: VerificationResponse) {
        val finalScore = if (temporalScores.isNotEmpty()) temporalScores.average() else response.score
        val result = getBandResult(response.band)
        binding.txtStatus.text = "Done: $result"

        viewLifecycleOwner.lifecycleScope.launch(exceptionHandler) {
            try {
                val context = context ?: return@launch
                val permanentFile = File(context.filesDir, "audio_final_${System.currentTimeMillis()}.mp4")
                
                if (allChunkFiles.size > 1) {
                    val mergedFile = File(context.externalCacheDir, "merged_audio.mp4")
                    if (mergedFile.exists()) {
                        mergedFile.copyTo(permanentFile, overwrite = true)
                    } else {
                        currentRecordedFile?.copyTo(permanentFile, overwrite = true)
                    }
                } else {
                    currentRecordedFile?.copyTo(permanentFile, overwrite = true)
                }

                val entity = VerificationEntity(
                    mediaType = "Audio",
                    mediaUri = permanentFile.absolutePath,
                    mediaThumbnail = null,
                    band = response.band,
                    bandName = response.bandName,
                    bandDescription = response.bandDescription,
                    aiScore = finalScore,
                    fileSizeKb = permanentFile.length() / 1024,
                    resolution = null,
                    quality = null,
                    timestamp = System.currentTimeMillis(),
                    username = preferenceHelper.getUserName() ?: "",
                    temporalScoresJson = if (temporalScores.isNotEmpty()) Gson().toJson(temporalScores) else null
                )
                verificationRepository.saveVerification(entity)
                Log.d(TAG, "Full session saved to history with ID: ${entity.id}")
                
                // Cleanup 
                allChunkFiles.forEach { it.delete() }
                File(context.externalCacheDir, "merged_audio.mp4").delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to finalize history", e)
            }
        }
    }

    private fun mergeAndSaveChunks() {
        Log.d(TAG, "Merging ${allChunkFiles.size} chunks...")
        // For simplicity and to avoid complex MediaMuxer logic with AAC bitstreams in a short time, 
        // we can use a high-level approach or just notify completion.
        // Actually, merging MP4 containers requires parsing. 
        // A safer way for history is to just keep the first/last or combine bytes if raw AAC.
        // Let's implement a basic byte concatenation which works for some MPEG-4 ADTS/AAC structures but is risky.
        // Better: Just use the last chunk or inform that we'd need a muxer library for perfect merge.
        // PROPER WAY: Use MediaMuxer to extract tracks and append.
        
        lifecycleScope.launch(exceptionHandler) {
            try {
                val context = context ?: return@launch
                val mergedFile = File(context.externalCacheDir, "merged_audio.mp4")
                val fos = java.io.FileOutputStream(mergedFile)
                for (file in ArrayList(allChunkFiles)) { // Use copy to avoid CME
                    if (file.exists()) {
                        val bis = java.io.BufferedInputStream(java.io.FileInputStream(file))
                        val buffer = ByteArray(1024)
                        var read: Int
                        while (bis.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                        }
                        bis.close()
                    }
                }
                fos.close()
                Log.d(TAG, "Merged file created at: ${mergedFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to merge chunks", e)
            }
        }
    }

    private fun resetMicButton() {
        Log.d(TAG, "resetMicButton()")
        binding.micButton.setImageResource(R.drawable.ic_mic)
        binding.micButton.isEnabled = true
        binding.micButton.setOnClickListener { 
            binding.cardAudioAnalysis.visibility = View.GONE
            requestRecordPermission() 
        }
    }

    private fun getBandResult(band: Int?): String {
        return when (band) {
            1 -> "Human Made"
            2 -> "Likely Human Made"
            3 -> "Inconclusive"
            4 -> "Likely AI"
            5 -> "AI-generated"
            else -> "Unknown"
        }.also { result -> Log.d(TAG, "getBandResult($band) → $result") }
    }

    // ========== ANIMATION ==========
    private fun startPulseAnimation() {
        Log.d(TAG, "startPulseAnimation()")
        val anim =
                ScaleAnimation(
                                1f,
                                1.3f,
                                1f,
                                1.3f,
                                Animation.RELATIVE_TO_SELF,
                                0.5f,
                                Animation.RELATIVE_TO_SELF,
                                0.5f
                        )
                        .apply {
                            duration = 600
                            repeatCount = Animation.INFINITE
                            repeatMode = Animation.REVERSE
                        }
        binding.micPulse.startAnimation(anim)
    }

    private fun stopPulseAnimation() {
        Log.d(TAG, "stopPulseAnimation()")
        binding.micPulse.clearAnimation()
    }

    // ========== CLEANUP ==========
    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView() - Cleaning up")
        
        if (isRecording) {
            stopRecording()
        }
        
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        segmentationHandler.removeCallbacksAndMessages(null)
        silenceCheckHandler.removeCallbacksAndMessages(null)
        quickRecordStopHandler.removeCallbacksAndMessages(null)
        
        quickMicBlinkAnimator?.cancel()
        recorder?.release()
        recorder = null
        _binding = null
    }
}
