package com.verifylabs.ai.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.ActivityShareReceiverBinding
import com.verifylabs.ai.presentation.auth.login.LoginViewModel
import com.verifylabs.ai.presentation.media.MediaType
import com.verifylabs.ai.presentation.media.VerificationResponse
import com.verifylabs.ai.presentation.viewmodel.MediaViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareReceiverBinding
    private lateinit var mediaViewModel: MediaViewModel
    private lateinit var loginViewModel: LoginViewModel

    private var selectedMediaUri: Uri? = null
    private var mediaType = MediaType.IMAGE

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private val TAG = "ShareReceiverActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowAppearance()
        initViewModels()
        handleIncomingIntent(intent)
        observeMediaViewModel()
        observeLoginViewModel()
        apiCheckCredits()

        binding.btnDone.setOnClickListener {
            finish() // Closes the activity
        }
    }

    // ---------------------------
    // Window setup
    // ---------------------------
    private fun setupWindowAppearance() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Handle system bars insets (status bar padding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        // Ensure status bar icons are correct based on theme (backward compatible)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        windowInsetsController.isAppearanceLightStatusBars = !isDarkMode
    }

    // ---------------------------
    // Init ViewModels
    // ---------------------------
    private fun initViewModels() {
        mediaViewModel = ViewModelProvider(this)[MediaViewModel::class.java]
        loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]
    }

    // ---------------------------
    // Credit check
    // ---------------------------
    private fun apiCheckCredits() {
        val username = preferenceHelper.getUserName()
        val apiKey = preferenceHelper.getApiKey()
        if (username.isNullOrEmpty() || apiKey.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid credentials. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }
        loginViewModel.checkCredits(username, apiKey)
    }

    private fun observeLoginViewModel() {
        loginViewModel.getCreditsResponse().observe(this) { response ->
            when (response.status) {
                Status.SUCCESS -> {
                    val credits = response.data?.get("credits")?.asIntSafe() ?: 0
                    val creditsMonthly = response.data?.get("credits_monthly")?.asIntSafe() ?: 0
                    val totalCredits = credits + creditsMonthly
                    preferenceHelper.setCreditReamaining(totalCredits)

                    val formattedCredits = NumberFormat.getNumberInstance(Locale.US)
                        .format(preferenceHelper.getCreditRemaining())

                    binding.progressCredits.visibility = View.GONE
                    binding.tvCreditsRemaining.visibility = View.VISIBLE
                    binding.tvCreditsRemaining.text = getString(R.string.credits_remaining, formattedCredits)

                    // Show no credit layout if zero
                    if (totalCredits <= 0) {
                        binding.layoutNoCreditStatus.visibility = View.VISIBLE
                    } else {
                        binding.layoutNoCreditStatus.visibility = View.GONE
                    }
                }
                Status.ERROR -> {
                    binding.progressCredits.visibility = View.GONE
                    binding.tvCreditsRemaining.visibility = View.VISIBLE
                    Toast.makeText(this, "Failed to check credits", Toast.LENGTH_SHORT).show()
                }
                Status.LOADING -> {
                    binding.progressCredits.visibility = View.VISIBLE
                    binding.tvCreditsRemaining.visibility = View.GONE
                }
            }
        }
    }

    // ---------------------------
    // Handle media
    // ---------------------------
    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }

        if (uri != null) {
            selectedMediaUri = uri
            mediaType = getMediaType(uri)
            showPreview(uri)
            startVerification()
        } else {
            Toast.makeText(this, "No media received", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun getMediaType(uri: Uri): MediaType {
        return when (contentResolver.getType(uri)?.substringBefore("/")) {
            "video" -> MediaType.VIDEO
            "audio" -> MediaType.AUDIO
            else -> MediaType.IMAGE
        }
    }

    private fun showPreview(uri: Uri) {
        when (mediaType) {
            MediaType.IMAGE -> {
                binding.imageViewMedia.setImageURI(uri)
                binding.imageViewMedia.visibility = android.view.View.VISIBLE
                binding.videoViewMedia.visibility = android.view.View.GONE
                binding.audioContainer.visibility = android.view.View.GONE
            }
            MediaType.VIDEO -> {
                binding.videoViewMedia.setVideoURI(uri)
                val mediaController = MediaController(this)
                mediaController.setAnchorView(binding.videoViewMedia)
                binding.videoViewMedia.setMediaController(mediaController)
                binding.videoViewMedia.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    binding.videoViewMedia.start()
                }
                binding.videoViewMedia.visibility = android.view.View.VISIBLE
                binding.imageViewMedia.visibility = android.view.View.GONE
                binding.audioContainer.visibility = android.view.View.GONE
            }
            MediaType.AUDIO -> {
                binding.audioContainer.visibility = android.view.View.VISIBLE
                binding.imageViewMedia.visibility = android.view.View.GONE
                binding.videoViewMedia.visibility = android.view.View.GONE
            }
        }

        binding.imageOverlay.visibility = android.view.View.GONE
        binding.layoutInfoStatus.visibility = android.view.View.GONE
        binding.lottieAnimationView.visibility = android.view.View.GONE
    }

    private fun startVerification() {
        selectedMediaUri?.let { uri ->
            val file = getFileFromUri(uri)
            if (file != null) {
                binding.textStatusMessage.text = "Uploading media..."
                binding.lottieAnimationView.visibility = android.view.View.VISIBLE
                mediaViewModel.uploadMedia(file.absolutePath, mediaType)
            } else {
                binding.textStatusMessage.text = "Failed to read media file"
            }
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val fileName = uri.lastPathSegment ?: "temp_file"
            val tempFile = File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.d(TAG, "Error copying file: ${e.message}")
            null
        }
    }

    private fun observeMediaViewModel() {
        mediaViewModel.getUploadResponse().observe(this) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    binding.textStatusMessage.text = "Uploading..."
                    binding.lottieAnimationView.visibility = android.view.View.VISIBLE
                }
                Status.SUCCESS -> {
                    binding.textStatusMessage.text = "Verifying..."
                    val uploadedUrl = resource.data?.get("uploadedUrl")?.asString ?: ""
                    mediaViewModel.verifyMedia(
                        username = preferenceHelper.getUserName().toString(),
                        apiKey = preferenceHelper.getApiKey().toString(),
                        mediaType = mediaType.value,
                        mediaUrl = uploadedUrl
                    )
                }
                Status.ERROR -> {
                    binding.textStatusMessage.text = "Upload failed: ${resource.message}"
                    binding.lottieAnimationView.visibility = android.view.View.GONE
                    binding.btnDone.visibility = View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            mediaViewModel.verifyResponseFlow.collect { resource ->
                when (resource.status) {
                    Status.LOADING -> {
                        binding.textStatusMessage.text = "Verifying..."
                        binding.lottieAnimationView.visibility = android.view.View.VISIBLE
                    }
                    Status.SUCCESS -> {
                        val response = Gson().fromJson(resource.data.toString(), VerificationResponse::class.java)

                        if(response.error != null) {
                            binding.layoutInfoStatus.visibility = android.view.View.VISIBLE
                            binding.textStatusMessage.text = "An error occurred during verification."
                            binding.txtIdentifixation.text = "${response.error}"
                            binding.lottieAnimationView.visibility = android.view.View.GONE
                            binding.btnDone.visibility = View.VISIBLE
                        } else {
                            binding.lottieAnimationView.visibility = android.view.View.GONE
                            displayVerificationResult(response)
                        }
                    }
                    Status.ERROR -> {
                        binding.textStatusMessage.text = "Verification failed: ${resource.message}"
                        binding.lottieAnimationView.visibility = android.view.View.GONE
                    }
                }
            }
        }

        lifecycleScope.launch {
            mediaViewModel.creditConsumedFlow.collect {
                val currentCredits = preferenceHelper.getCreditRemaining()
                val newCredits = (currentCredits - 1).coerceAtLeast(0)
                preferenceHelper.setCreditReamaining(newCredits)
                val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(newCredits)
                binding.tvCreditsRemaining.text = getString(R.string.credits_remaining, formattedCredits)
                Log.d(TAG, "Credit consumed from share. New balance: $newCredits")
            }
        }
    }

    private fun displayVerificationResult(response: VerificationResponse) {
        binding.layoutInfoStatus.visibility = android.view.View.VISIBLE
        binding.textStatusMessage.text = response.bandDescription
        binding.imageOverlay.visibility = android.view.View.VISIBLE
        binding.btnDone.visibility = View.GONE

        binding.txtIdentifixation.text = getBandResult(response.band)

        when (response.band) {
            1, 2 -> {
                binding.imageOverlay.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.verifylabs_tick_icon_light_grey_rgb_2__traced___1_)
                )
                binding.txtIdentifixation.background =
                    ContextCompat.getDrawable(this, R.drawable.drawable_verify_background_green)
                binding.imgIdentification.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.verifylabs_smile_icon_light_grey_rgb_1__traced_)
                )
                binding.btnDone.visibility = View.VISIBLE
            }

            3 -> {
                binding.imageOverlay.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.ic_gray_area)
                )
                binding.txtIdentifixation.background =
                    ContextCompat.getDrawable(this, R.drawable.drawable_verify_background_btn_failed_likely_gray)
                binding.imgIdentification.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.ic_question_circle)
                )
                binding.btnDone.visibility = View.VISIBLE
            }
            4, 5 -> {
                binding.imageOverlay.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.ic_red_cross_tranparent)
                )
                binding.txtIdentifixation.background =
                    ContextCompat.getDrawable(this, R.drawable.drawable_verify_background_btn_failed_likely_red_without_radius)
                binding.imgIdentification.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.verifylabs_robot_icon_light_grey_rgb_1__traced_)
                )

                binding.btnDone.visibility = View.VISIBLE
            }

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
        }
    }

    override fun onPause() {
        super.onPause()
        binding.videoViewMedia.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.videoViewMedia.stopPlayback()
    }
}

// ---------------------------
// Safe Int extension
// ---------------------------
private fun JsonElement.asIntSafe(): Int {
    return try {
        if (this.isJsonPrimitive && this.asJsonPrimitive.isNumber) this.asInt else 0
    } catch (e: Exception) {
        0
    }
}
