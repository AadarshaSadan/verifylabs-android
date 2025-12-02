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
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.FragmentAudioBinding
import com.verifylabs.ai.presentation.media.MediaType
import com.verifylabs.ai.presentation.media.VerificationResponse
import com.verifylabs.ai.presentation.viewmodel.MediaViewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class FragmentAudio : Fragment() {

    private var _binding: FragmentAudioBinding? = null
    private val binding get() = _binding!!

    private val TAG = "FragmentAudio"  // Log TAG

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private lateinit var viewModel: MediaViewModel
    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var startTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - startTime
            val minutes = (elapsed / 1000) / 60
            val seconds = (elapsed / 1000) % 60
            val millis = (elapsed % 1000) / 10
            binding.txtTimer.text = String.format("%02d:%02d:%02d", minutes, seconds, millis)
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
                Toast.makeText(requireContext(), "Microphone permission required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
            if (isRecording) stopRecording() else requestRecordPermission()
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
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "hasRecordPermission() -> $granted")
        return granted
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
                    val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(storeCredits)
                    binding.tvCreditsRemaining.text = getString(R.string.credits_remaining, formattedCredits)

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
            Toast.makeText(requireContext(), "You need at least 1 credit", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val outputFile = File(requireContext().externalCacheDir, "recorded_audio.mp3")
            Log.d(TAG, "Recording to: ${outputFile.absolutePath}")

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            Log.d(TAG, "Recording started")
            binding.micButton.setImageResource(R.drawable.ic_audio)
            binding.txtStatus.text = "Recording..."
            isRecording = true
            startTime = System.currentTimeMillis()
            handler.post(timerRunnable)
            startPulseAnimation()

        } catch (e: IOException) {
            Log.e(TAG, "Recording failed", e)
            binding.txtStatus.text = "Recording failed"
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        Log.d(TAG, "stopRecording()")

        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false
            handler.removeCallbacks(timerRunnable)
            binding.micButton.setImageResource(R.drawable.ic_mic)
            stopPulseAnimation()

            val file = File(requireContext().externalCacheDir, "recorded_audio.mp3")
            if (file.exists()) {
                Log.d(TAG, "Audio file ready: ${file.length()} bytes")
                uploadAudio(file)
            } else {
                Log.w(TAG, "Audio file not found after recording")
                binding.txtStatus.text = "File not found"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            binding.txtStatus.text = "Stop failed"
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
                    val response = Gson().fromJson(resource.data.toString(), VerificationResponse::class.java)

                    Log.d(TAG, "Band: ${response.band}, Description: ${response.bandDescription}")
                    if (response.error != null) {
                        Log.e(TAG, "Verification error: ${response.error}")
                    }

                    // Show final result in status text
                    val result = getBandResult(response.band)
                    binding.txtStatus.text = "Done: $result"

                    // Reset mic button
                    resetMicButton()

                    // Delete file
                    File(requireContext().externalCacheDir, "recorded_audio.mp3").delete()
                }

                Status.ERROR -> {
                    Log.e(TAG, "Verify FAILED: ${resource.message}")
                    binding.txtStatus.text = "Failed: ${resource.message}"
                    resetMicButton()
                }
            }
        }
    }

    private fun resetMicButton() {
        Log.d(TAG, "resetMicButton()")
        binding.micButton.setImageResource(R.drawable.ic_mic)
        binding.micButton.isEnabled = true
        binding.micButton.setOnClickListener { requestRecordPermission() }
    }

    private fun getBandResult(band: Int?): String {
        return when (band) {
            1 -> "Human Made"
            2 -> "Likely Human Made"
            3 -> "Inconclusive"
            4 -> "Likely AI"
            5 -> "AI-generated"
            else -> "Unknown"
        }.also { result ->
            Log.d(TAG, "getBandResult($band) → $result")
        }
    }

    // ========== ANIMATION ==========
    private fun startPulseAnimation() {
        Log.d(TAG, "startPulseAnimation()")
        val anim = ScaleAnimation(1f, 1.3f, 1f, 1.3f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f).apply {
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
        super.onDestroyView()
        handler.removeCallbacks(timerRunnable)
        recorder?.release()
        _binding = null
    }
}