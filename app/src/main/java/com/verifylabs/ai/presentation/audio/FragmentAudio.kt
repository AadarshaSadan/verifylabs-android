package com.verifylabs.ai.presentation.audio

import android.Manifest
import android.content.res.ColorStateList
import android.graphics.Color
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
import com.verifylabs.ai.presentation.MainActivity
import com.verifylabs.ai.presentation.media.GuidelinesDialogFragment
import com.verifylabs.ai.presentation.media.MediaType
import com.verifylabs.ai.presentation.media.VerificationResponse
import com.verifylabs.ai.presentation.viewmodel.MediaViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.text.NumberFormat
import java.util.ArrayList
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var startTimestampPosition: Long = 0
    private var currentRecordedFile: File? = null
    private var isQuickRecording = false
    private var isVerificationCompleted = false // Flag to prevent multiple results/API glitches
    private var quickRecordingJob: Job? = null
    private val quickRecordStopHandler = Handler(Looper.getMainLooper())
    private val quickRecordStopRunnable = Runnable { stopRecording() }
    private var quickMicBlinkAnimator: android.animation.ValueAnimator? = null
    private var currentQuickDuration: Int = 40

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
    private val silenceCheckRunnable =
            object : Runnable {
                override fun run() {
                    if (isRecording) {
                        val amplitude = recorder?.maxAmplitude ?: 0
                        if (amplitude < SILENCE_THRESHOLD) {
                            silenceDurationMs += 500
                        } else {
                            silenceDurationMs = 0
                        }

                        val maxAllowedSilence =
                                if (isQuickRecording) QUICK_RECORD_MAX_SILENCE
                                else LONG_RECORD_MAX_SILENCE
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
                    if (isQuickRecording) {
                        val elapsedSeconds = elapsed / 1000
                        val remaining = (currentQuickDuration - elapsedSeconds).coerceAtLeast(0)
                        binding.txtTimer.text = "Recording $remaining seconds"
                    } else {
                        val minutes = (elapsed / 1000) / 60
                        val seconds = (elapsed / 1000) % 60
                        val millis = (elapsed % 1000) / 10
                        binding.txtTimer.text =
                                String.format("%02d:%02d:%02d", minutes, seconds, millis)
                    }
                    handler.postDelayed(this, 100) // Update slightly less frequently for text
                }
            }

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    Log.d(TAG, "Microphone permission granted")
                    startRecording()
                } else {
                    Log.d(TAG, "Microphone permission denied")
                    Log.d(TAG, "Permission denied")
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
            Log.d(
                    TAG,
                    "Quick mic button clicked | isRecording: $isRecording, isQuickRecording: $isQuickRecording"
            )
            if (isRecording && !isQuickRecording) {
                Toast.makeText(requireContext(), "Manual recording in progress", Toast.LENGTH_SHORT)
                        .show()
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

        binding.btnGuidelines.setOnClickListener {
            GuidelinesDialogFragment.newInstance()
                    .show(parentFragmentManager, GuidelinesDialogFragment.TAG)
        }
        // Underline the guidelines text
        val guidelinesTv = binding.btnGuidelines.getChildAt(1) as? android.widget.TextView
        guidelinesTv?.paintFlags =
                guidelinesTv?.paintFlags?.or(android.graphics.Paint.UNDERLINE_TEXT_FLAG) ?: 0

        // Initial Local Load
        loadLocalCredits()

        // Click to Refresh
        binding.llCreditsInfo.root.setOnClickListener { checkCredits() }

        observeCredits()
        observeUploadAndVerify()

        // New Result Buttons Logic
        binding.btnReset.setOnClickListener { resetMicButton() }

        binding.btnShowAnalysis.setOnClickListener {
            // Show Analysis & Stats
            binding.cardAudioAnalysis.visibility = View.VISIBLE
            binding.layoutStatsRow.visibility = View.VISIBLE
            binding.audioAnalysisChart.visibility = View.VISIBLE
            binding.layoutAnalysisPlaceholder.visibility = View.GONE

            // Hide the button itself as per requirement
            binding.btnShowAnalysis.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.updateStatusBarColor(R.color.app_background)
        // Restore standard elevation
        (activity as? MainActivity)?.updateBottomNavColor(R.color.app_background_3, 8f)
        (activity as? MainActivity)?.updateAppBarColor(R.color.app_background)
        (activity as? MainActivity)?.updateMainBackgroundColor(R.color.app_background)
    }

    private fun loadLocalCredits() {
        val storeCredits = preferenceHelper.getCreditRemaining()
        val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(storeCredits)
        binding.llCreditsInfo.tvCreditsRemaining.text =
                getString(R.string.credits_remaining, formattedCredits)
        binding.llCreditsInfo.progressCredits.visibility = View.GONE
        binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
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
        currentQuickDuration = durationSeconds
        Log.d(TAG, "startQuickRecording() - Duration: ${durationSeconds}s")

        isQuickRecording = true
        startRecording()

        // Visual feedback for quick record button
        binding.micTimer.setImageResource(R.drawable.ic_audio)

        // Scale animation
        binding.micPulsebtn.startAnimation(
                ScaleAnimation(
                                1f,
                                1.2f,
                                1f,
                                1.2f,
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
        )

        // Color blinking animation (Red and White)
        quickMicBlinkAnimator =
                android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 600
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    addUpdateListener { animator ->
                        val fraction = animator.animatedValue as Float
                        val color =
                                android.graphics.Color.argb(
                                        255,
                                        (255 * fraction + 252 * (1 - fraction))
                                                .toInt(), // Red channel (252 is close to white-ish
                                        // red in some themes, but let's go pure
                                        // red vs white)
                                        (255 * (1 - fraction)).toInt(), // Green
                                        (255 * (1 - fraction)).toInt() // Blue
                                )
                        // Color transition from White (255,255,255) to Red (255,0,0)
                        binding.micPulsebtn.background.colorFilter =
                                android.graphics.PorterDuffColorFilter(
                                        color,
                                        android.graphics.PorterDuff.Mode.SRC_IN
                                )
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
        binding.llCreditsInfo.progressCredits.visibility = View.VISIBLE
        binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
        binding.llCreditsInfo.tvCreditsRemaining.text = "Loading..."
        binding.layoutNoCreditStatus.visibility = View.GONE
        binding.llCreditsInfo.root.visibility = View.VISIBLE

        viewModel.checkCredits(username, apiKey)
    }

    private fun showInvalidCredentials() {
        Log.d(TAG, "showInvalidCredentials()")
        binding.llCreditsInfo.progressCredits.visibility = View.GONE
        binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
        binding.llCreditsInfo.tvCreditsRemaining.text = "Invalid credentials"
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
                    binding.llCreditsInfo.progressCredits.visibility = View.VISIBLE
                    binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
                    binding.llCreditsInfo.tvCreditsRemaining.text = "Loading..."
                    binding.layoutNoCreditStatus.visibility = View.GONE
                    Log.d(TAG, "Checking credits...")
                    binding.micButton.isEnabled = false
                }
                Status.SUCCESS -> {
                    Log.d(TAG, "Credits: SUCCESS")
                    binding.llCreditsInfo.progressCredits.visibility = View.GONE
                    binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE

                    val json = resource.data as? JsonObject
                    val credits = json?.get("credits")?.asInt ?: 0
                    val monthly = json?.get("credits_monthly")?.asInt ?: 0
                    val total = credits + monthly

                    Log.d(TAG, "Credits parsed: total=$total (credits=$credits, monthly=$monthly)")
                    preferenceHelper.setCreditReamaining(total)
                    val storeCredits = preferenceHelper.getCreditRemaining()
                    val formattedCredits =
                            NumberFormat.getNumberInstance(Locale.US).format(storeCredits)
                    binding.llCreditsInfo.tvCreditsRemaining.text =
                            getString(R.string.credits_remaining, formattedCredits)

                    binding.micButton.isEnabled = total > 0
                    binding.micButton.alpha = if (total > 0) 1f else 0.5f

                    if (total <= 0) {
                        Log.d(TAG, "No credits - showing banner")
                        binding.layoutNoCreditStatus.visibility = View.VISIBLE
                        // binding.txtStatus.text = "No credits remaining"
                    } else {
                        Log.d(TAG, "Credits available - ready to record")
                        binding.layoutNoCreditStatus.visibility = View.GONE
                        Log.d(TAG, "Tap to record")
                    }
                }
                Status.ERROR -> {
                    Log.e(TAG, "Credits: ERROR - ${resource.message}")
                    binding.llCreditsInfo.progressCredits.visibility = View.GONE
                    binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
                    binding.llCreditsInfo.tvCreditsRemaining.text = "Credit check failed"
                    binding.micButton.isEnabled = false
                    binding.layoutNoCreditStatus.visibility = View.GONE
                    Log.e(TAG, "Failed to check credits")
                }
                Status.INSUFFICIENT_CREDITS -> {
                    binding.llCreditsInfo.progressCredits.visibility = View.GONE
                    binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
                    binding.micButton.isEnabled = false
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
            Log.d(TAG, "Insufficient credits")
            Toast.makeText(requireContext(), "You need at least 1 credit", Toast.LENGTH_SHORT)
                    .show()
            return
        }

        try {
            // Use .aac extension for ADTS format (allows concatenation)
            val fileName = "audio_chunk_${allChunkFiles.size}_${System.currentTimeMillis()}.aac"
            val outputFile = File(requireContext().externalCacheDir, fileName)
            currentRecordedFile = outputFile
            allChunkFiles.add(outputFile)
            Log.d(TAG, "Recording chunk ${allChunkFiles.size} to: ${outputFile.absolutePath}")

            recorder =
                    MediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        // Use AAC_ADTS container which supports binary concatenation
                        setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(outputFile.absolutePath)
                        prepare()
                        start()
                    }

            Log.d(TAG, "Recording started")
            binding.micButton.setImageResource(R.drawable.ic_audio)
            binding.txtTimer.visibility = View.VISIBLE
            Log.d(
                    TAG,
                    if (isQuickRecording) "Status: Quick Recording..."
                    else "Status: Long Recording..."
            )
            isRecording = true

            if (allChunkFiles.size == 1) { // First chunk
                startTime = System.currentTimeMillis()
                handler.post(timerRunnable)
                startPulseAnimation()
                silenceDurationMs = 0
                silenceCheckHandler.postDelayed(silenceCheckRunnable, 500)

                // Show Analysis Placeholder on start
                binding.audioAnalysisChart.reset()
                binding.audioAnalysisChart.visibility = View.VISIBLE // Ensure visible but empty
                binding.cardAudioAnalysis.visibility = View.VISIBLE
                binding.layoutAnalysisPlaceholder.visibility = View.VISIBLE
            }

            if (isLongRecording) {
                currentChunkStartTime = System.currentTimeMillis()
                segmentationHandler.postDelayed({ rotateChunk() }, 10000) // Rotate every 10s
            }
        } catch (e: IOException) {
            Log.e(TAG, "Recording failed", e)
            Log.e(TAG, "Recording failed msg")
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rotateChunk() {
        if (!isLongRecording || !isRecording) return

        val segmentDuration = System.currentTimeMillis() - currentChunkStartTime
        Log.d(TAG, "Rotating recording chunk. Duration: ${segmentDuration}ms")

        val oldFile = currentRecordedFile

        // Stop current and start new immediately
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping chunk", e)
        }

        if (oldFile != null) {
            // Security Check (iOS Parity): Minimum 5 seconds for quality verification
            if (segmentDuration < 5000) {
                Log.w(TAG, "Chunk too short ($segmentDuration ms). Skipping verification.")
                allChunkFiles.remove(oldFile)
                oldFile.delete()
            } else if (silenceDurationMs >= 10000L && isLongRecording
            ) { // Silence Detection (Android Optimization)
                Log.d(TAG, "Chunk is silent. Skipping verification to save credits.")
                temporalScores.add(0.0)
                binding.audioAnalysisChart.setChronologicalScores(temporalScores)
            } else {
                uploadAudio(oldFile) // Parallel verification start
            }
        }

        currentChunkStartTime = System.currentTimeMillis()
        startRecording() // Start next chunk
    }

    private fun handleSilenceFailure() {
        Log.w(TAG, "Silence threshold reached. Stopping recording.")
        stopRecording()
        Log.d(TAG, "Failure: Long silence detected")
        Toast.makeText(requireContext(), "Recording stopped due to silence", Toast.LENGTH_LONG)
                .show()
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
            val segmentDuration = System.currentTimeMillis() - currentChunkStartTime
            if (file != null && file.exists()) {
                if (segmentDuration < 5000 && allChunkFiles.size > 1) {
                    Log.w(
                            TAG,
                            "Final chunk too short ($segmentDuration ms). Skipping verification."
                    )
                    allChunkFiles.remove(file)
                    file.delete()
                } else {
                    Log.d(
                            TAG,
                            "Final chunk ready: ${file.length()} bytes, Duration: ${segmentDuration}ms"
                    )
                    uploadAudio(file)
                }
            }

            if (isLongRecording) {
                // Post-process: Merge all chunks into one for history
                isLongRecording = false
                mergeAndSaveChunks()
            } else if (currentRecordedFile == null) {
                Log.w(TAG, "Audio file not found after recording")
                Log.e(TAG, "File not found")
            } else {
                // Final Verification State for regular/quick recording
                Log.d(TAG, "Starting Quick/Regular Verification Flow")
                binding.root.post { showAnalyzingState() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            if (_binding != null) {
                Log.e(TAG, "Stop failed")
            }
        }
    }

    private fun showAnalyzingState() {
        Log.d(TAG, "showAnalyzingState() - Showing Blue Circle Layout")
        // Hide Mic & Pulse
        binding.micButton.visibility = View.INVISIBLE
        binding.micPulse.visibility = View.INVISIBLE

        // Show Blue Analyzing Circle
        binding.layoutAnalyzing.visibility = View.VISIBLE
        binding.layoutAnalyzing.bringToFront() // Ensure it's on top
        
        // Show Analyzing Card (Behind Blue Circle)
        binding.cardAudioAnalysis.visibility = View.VISIBLE
        binding.layoutAnalyzingStatus.visibility = View.VISIBLE
        binding.layoutAnalysisPlaceholder.visibility = View.VISIBLE
        binding.audioAnalysisChart.visibility = View.GONE
        
        Log.d(TAG, "Analyzing...")
        stopPulseAnimation()
    }

    // ========== UPLOAD ==========
    private fun uploadAudio(file: File) {
        Log.d(TAG, "uploadAudio() - Uploading: ${file.name} (${file.length()} bytes)")
        viewModel.uploadMedia(file.absolutePath, MediaType.AUDIO)
    }

    // ========== VERIFY (LOG-ONLY RESULT) ==========
    private fun observeUploadAndVerify() {
        viewModel.getUploadResponse().observe(viewLifecycleOwner) { resource ->
            Log.d(TAG, "Upload response: ${resource.status}, data: ${resource.data}")

            when (resource.status) {
                Status.LOADING -> {
                    Log.d(TAG, "Upload: LOADING")
                    //                    binding.txtStatus.text = "Uploading audio..."
                    Log.d(TAG, "Uploading audio...")
                }
                Status.SUCCESS -> {
                    val url = resource.data?.get("uploadedUrl")?.asString.orEmpty()
                    Log.d(TAG, "Upload SUCCESS - URL: $url")
                    //                    binding.txtStatus.text = "Verifying audio..."
                    Log.d(TAG, "Verifying audio...")
                    viewModel.verifyMedia(
                            username = preferenceHelper.getUserName().orEmpty(),
                            apiKey = preferenceHelper.getApiKey().orEmpty(),
                            mediaType = MediaType.AUDIO.value,
                            mediaUrl = url
                    )
                }
                Status.ERROR -> {
                    Log.e(TAG, "Upload FAILED: ${resource.message}")
                    showErrorResult(resource.message ?: "Upload failed")
                }
                Status.INSUFFICIENT_CREDITS -> {
                    Log.e(TAG, "Upload FAILED (Credits): ${resource.message}")
                    //                    binding.txtStatus.text = "Insufficient credits for upload"
                    Log.d(TAG, "Insufficient credits for upload")
                    resetMicButton()
                }
            }
        }

        // Parallel Verification Results Handling (iOS Parity)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.verifyResponseFlow.collect { resource ->
                Log.d(TAG, "Verify response collected: ${resource.status}")

                when (resource.status) {
                    Status.LOADING -> {
                        Log.d(TAG, "Verify: LOADING")
                        // binding.txtStatus.text = "Verifying audio..."
                    }
                    Status.SUCCESS -> {
                        if (isVerificationCompleted) return@collect

                        Log.d(TAG, "Verify: SUCCESS")
                        val response =
                                Gson().fromJson(
                                                resource.data.toString(),
                                                VerificationResponse::class.java
                                        )

                        Log.d(TAG, "Band: ${response.band}, Score: ${response.score}")

                        // Thread-safe update to temporal scores
                        synchronized(temporalScores) {
                            if (isLongRecording || temporalScores.isNotEmpty()) {
                                temporalScores.add(response.score)
                                temporalScores.add(response.score)
                                // binding.cardAudioAnalysis.visibility = View.VISIBLE // Don't
                                // auto-show
                                // binding.audioAnalysisChart.visibility = View.VISIBLE // Don't
                                // auto-show
                                binding.layoutAnalysisPlaceholder.visibility =
                                        View.GONE // Hide placeholder
                                binding.audioAnalysisChart.setChronologicalScores(
                                        ArrayList(temporalScores)
                                )

                                if (!isRecording && !isLongRecording) {
                                    finalizeAndSaveHistory(response)
                                }
                            } else {
                                // binding.cardAudioAnalysis.visibility = View.VISIBLE // Don't
                                // auto-show
                                // binding.audioAnalysisChart.visibility = View.VISIBLE // Don't
                                // auto-show
                                binding.layoutAnalysisPlaceholder.visibility =
                                        View.GONE // Hide placeholder
                                binding.audioAnalysisChart.setScore(response.score)
                                finalizeAndSaveHistory(response)
                            }
                        }

                        // if (!isRecording) {
                        //     resetMicButton()
                        // }

                        // Auto-refresh credits
                        checkCredits()
                    }
                    Status.ERROR -> {
                        Log.e(TAG, "Verify FAILED: ${resource.message}")
                        showErrorResult(resource.message ?: "Verification failed")
                    }
                    Status.INSUFFICIENT_CREDITS -> {
                        Log.w(TAG, "Verify: INSUFFICIENT_CREDITS")
                        //                        binding.txtStatus.text = "Insufficient Credits"
                        Log.d(TAG, "Insufficient Credits")
                        Toast.makeText(
                                        requireContext(),
                                        "Top up your credits to continue verifying audio",
                                        Toast.LENGTH_LONG
                                )
                                .show()
                        resetMicButton()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.creditConsumedFlow.collect {
                val currentCredits = preferenceHelper.getCreditRemaining()
                val newCredits = (currentCredits - 1).coerceAtLeast(0)
                preferenceHelper.setCreditReamaining(newCredits)
                binding.llCreditsInfo.tvCreditsRemaining.text = "Credits Remaining: $newCredits"
                Log.d(TAG, "Credit consumed. New balance: $newCredits")
            }
        }
    }

    private fun finalizeAndSaveHistory(response: VerificationResponse) {
        val finalScore =
                if (temporalScores.isNotEmpty()) temporalScores.average() else response.score

        displayResult(response)

        viewLifecycleOwner.lifecycleScope.launch(exceptionHandler) {
            try {
                val context = context ?: return@launch

                // Use HistoryFileManager to save persistently
                val sourceFile =
                        if (allChunkFiles.size > 1) {
                            val mergedFile = File(context.externalCacheDir, "merged_audio.aac")
                            if (mergedFile.exists()) mergedFile else currentRecordedFile
                        } else {
                            currentRecordedFile
                        }

                val savedPath =
                        sourceFile?.let { file ->
                            com.verifylabs.ai.core.util.HistoryFileManager.saveMedia(
                                    context,
                                    android.net.Uri.fromFile(file),
                                    "audio"
                            )
                        }

                val finalUriString =
                        savedPath?.let { android.net.Uri.fromFile(File(it)).toString() }
                                ?: sourceFile?.absolutePath // Fallback

                val entity =
                        VerificationEntity(
                                mediaType = "Audio",
                                mediaUri = finalUriString,
                                mediaThumbnail = null,
                                band = response.band,
                                bandName = response.bandName,
                                bandDescription = response.bandDescription,
                                aiScore = finalScore,
                                fileSizeKb = (sourceFile?.length() ?: 0) / 1024,
                                resolution = null,
                                quality = null,
                                timestamp = System.currentTimeMillis(),
                                username = preferenceHelper.getUserName() ?: "",
                                temporalScoresJson =
                                        if (temporalScores.isNotEmpty())
                                                Gson().toJson(temporalScores)
                                        else null
                        )
                verificationRepository.saveVerification(entity)
                Log.d(TAG, "Full session saved to history with ID: ${entity.id}")

                // Cleanup
                allChunkFiles.forEach { it.delete() }
                File(context.externalCacheDir, "merged_audio.aac").delete()
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
        // Let's implement a basic byte concatenation which works for some MPEG-4 ADTS/AAC
        // structures but is risky.
        // Better: Just use the last chunk or inform that we'd need a muxer library for perfect
        // merge.
        // PROPER WAY: Use MediaMuxer to extract tracks and append.

        lifecycleScope.launch(exceptionHandler) {
            try {
                val context = context ?: return@launch
                val mergedFile = File(context.externalCacheDir, "merged_audio.aac")
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
                // fos.close() was already called above
                Log.d(TAG, "Merged file created at: ${mergedFile.absolutePath}")

                // Final Verification for Long Recording
                withContext(Dispatchers.Main) {
                    uploadAudio(mergedFile)
                    showAnalyzingState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to merge chunks", e)
                withContext(Dispatchers.Main) {
                    //                    binding.txtStatus.text = "Merge failed"
                    Log.e(TAG, "Merge failed")
                    resetMicButton()
                }
            }
        }
    }

    private fun resetMicButton() {
        Log.d(TAG, "resetMicButton()")

        // Restore Mic UI
        showMicControls(true)
        binding.micButton.setImageResource(R.drawable.ic_mic)
        binding.micButton.isEnabled = true
        binding.txtTimer.visibility = View.GONE

        // Reset Status Text
        //        binding.txtStatus.text = ""
        //        binding.txtStatus.visibility = View.VISIBLE
        Log.d(TAG, "Resetting Mic Button Status")

        // Hide Analysis/Results
        binding.layoutAnalyzing.visibility = View.GONE
        binding.layoutResultsContainer.visibility = View.GONE
        binding.cardAudioAnalysis.visibility = View.GONE
        binding.layoutStatsRow.visibility = View.GONE // Reset stats visibility
        binding.layoutAnalysisPlaceholder.visibility =
                View.VISIBLE // Should be visible if card were visible, but card is GONE.
        // Actually initial state has placeholder VISIBLE inside hierarchy.
        
        binding.layoutAnalyzingStatus.visibility = View.GONE

        // Reset Logic State
        binding.audioAnalysisChart.reset()
        isLongRecording = false
        isQuickRecording = false
        isVerificationCompleted = false
        allChunkFiles.clear()
        temporalScores.clear()
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

    private fun showErrorResult(message: String) {
        Log.d(TAG, "showErrorResult: $message")
        binding.layoutAnalyzing.visibility = View.GONE
        binding.layoutResultsContainer.visibility = View.VISIBLE
        binding.cardAudioAnalysis.visibility = View.GONE
        binding.layoutAnalyzingStatus.visibility = View.GONE

        // Hide controls and status similar to success state
        Log.d(TAG, "Status: ")
        binding.txtTimer.visibility = View.GONE
        showMicControls(false)

        binding.cardResultStatus.background =
                ContextCompat.getDrawable(requireContext(), R.drawable.bg_result_card_ai)
        binding.imgResultIcon.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_warning)
        )
        //        binding.imgResultIcon.imageTintList = ColorStateList.valueOf(Color.GREEN)

        binding.txtResultTitle.text = "Verification failed"
        binding.txtResultTitle.visibility = View.VISIBLE

        binding.txtResultMessage.text =
                "This usually means the audio couldn't be analyzed (too short, wrong format, or poor quality)."
        binding.txtResultMessage.setTextColor(Color.WHITE)

        binding.btnReset.visibility = View.VISIBLE
        binding.btnReset.visibility = View.VISIBLE
        binding.btnShowAnalysis.visibility = View.VISIBLE
        binding.btnShowAnalysis.text = getString(R.string.show_analysis)

        // Hide charts/stats initially
        binding.cardAudioAnalysis.visibility = View.GONE
        binding.layoutStatsRow.visibility = View.GONE
    }

    private fun displayResult(response: VerificationResponse) {
        if (isVerificationCompleted) return // Prevent duplicate calls
        isVerificationCompleted = true

        Log.d(TAG, "displayResult(band=${response.band})")

        binding.layoutAnalyzing.visibility = View.GONE // Ensure analyzing spinner is gone
        binding.layoutResultsContainer.visibility = View.VISIBLE
        // binding.txtStatus.text = "" // Clear status text
        // binding.txtStatus.visibility = View.INVISIBLE // User requested to hide it
        binding.txtTimer.visibility = View.GONE // User requested to hide timer

        // Default to Success Green Style
        binding.cardResultStatus.background =
                ContextCompat.getDrawable(requireContext(), R.drawable.bg_result_card_human)
        binding.imgResultIcon.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_circle)
        )
        binding.txtResultMessage.setTextColor(Color.parseColor("#4CAF50")) // Green text
        binding.txtResultTitle.visibility = View.GONE

        binding.txtResultMessage.text =
                response.bandDescription ?: getBandDescription(response.band)

        showMicControls(false)

        // Calculate Stats
        val scoresToUse =
                if (temporalScores.isNotEmpty()) temporalScores else listOf(response.score)
        val avg = scoresToUse.average()
        val min = scoresToUse.minOrNull() ?: 0.0
        val max = scoresToUse.maxOrNull() ?: 0.0

        binding.txtAvgValue.text = String.format("%.2f", avg)
        binding.txtMinValue.text = String.format("%.2f", min)
        binding.txtMaxValue.text = String.format("%.2f", max)

        // Ensure stats and chart are HIDDEN initially as per user request
        // They will be shown only when "Show analysis" is clicked.
        binding.layoutStatsRow.visibility = View.GONE
        binding.cardAudioAnalysis.visibility = View.GONE
        binding.layoutAnalyzingStatus.visibility = View.GONE

        // Ensure button is VISIBLE
        binding.btnShowAnalysis.visibility = View.VISIBLE
        binding.btnShowAnalysis.text = "Show analysis"

        when (response.band) {
            0 ->{
                showErrorResult("short recording")

            }

            1, 2 -> { // Human - Green
                binding.cardResultStatus.background =
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_result_card_human)
                binding.imgResultIcon.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.verifylabs_smile_icon_green_white)
                )
//                binding.imgResultIcon.imageTintList =
//                        ColorStateList.valueOf(Color.parseColor("#4CAF50"))

                binding.imgResultIcon.imageTintList =null
                binding.txtResultMessage.setTextColor(Color.parseColor("#4CAF50"))
            }
            3 -> { // Unsure - Gray? (Requirement only specified Success/Error layouts, but handling
                // 3 safely)
                // Keeping Green/Success style or maybe switch to a Neutral Gray card if we had one.
                // For now, let's treat it as a "Result" but maybe with question mark.
                binding.imgResultIcon.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_question_circle)
                )
                binding.txtResultMessage.setTextColor(Color.LTGRAY)
            }
            4, 5 -> { // AI - Red (Error Style)
                binding.cardResultStatus.background =
                        ContextCompat.getDrawable(requireContext(), R.drawable.bg_result_card_human)
                binding.imgResultIcon.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.verifylabs_robot_icon_red_white)
                )
//                binding.imgResultIcon.imageTintList =
//                        ColorStateList.valueOf(Color.parseColor("#FF5252"))


                binding.imgResultIcon.imageTintList =null
                binding.txtResultMessage.setTextColor(
                        Color.WHITE
                ) // White text on Red card usually? Or Red text?
                // The requirements say: "Title says 'Verification failed.' The text below
                // explains..."
                // However, Band 4/5 is "AI Detected", NOT "Verification failed" (technical error).
                // "Verification failed" usually means analysis couldn't happen (too short, etc).
                // BUT, if the user considers "AI" as the "Error Page" from the description?
                // The description "Image 2: The 'Error' / Failed Page... usually means audio
                // couldn't be analyzed".
                // So Band 4/5 is a VALID result (AI), not an error.
                // I should check where "Verification Failed" (technical error) happens.
                // It happens in Status.ERROR blocks usually.

                // So for Band 4/5 (AI), I should probably use the Red styling but with "AI
                // Detected" text?
                // The prompt says "Image 1: The 'Success' Result Page... passed the check and is
                // considered authentic human".
                // "Image 2: The 'Error' / Failed Page... verification failed... too short, wrong
                // format".

                // So Band 4/5 should still use the Red Card but maybe with AI text.
                // Let's stick to the Red Card style for AI results for now as it matches "Bad"
                // result.
                binding.txtResultMessage.setTextColor(Color.parseColor("#FF5252"))
            }
        }
    }

    private fun showMicControls(show: Boolean) {
        val visibility = if (show) View.VISIBLE else View.GONE

        binding.micButton.visibility = visibility
        binding.micTimer.visibility = visibility
        binding.micPulse.visibility = visibility
        binding.micPulsebtn.visibility = visibility
    }

    private fun getBandDescription(band: Int): String {
        return when (band) {
            1 ->
                    "There’s a high probability that this was created by a human and has not been altered by AI."
            2 ->
                    "This was likely created by a human, but may have been improved by photo apps or a phone's automated software."
            3 ->
                    "This result can't be determined due to quality or testing suitability. This could be because it is partly machine-made, low resolution or too dark. Check FAQs on VerifyLabs.AI for more information."
            4 ->
                    "This was likely created by a machine. Partly AI-generated content or deepfakes can often give these results."
            5 -> "There’s a high probability that this is deepfake or AI-generated."
            else -> ""
        }
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
                            duration = 1200
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
