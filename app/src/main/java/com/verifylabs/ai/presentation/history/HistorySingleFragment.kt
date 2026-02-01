package com.verifylabs.ai.presentation.history

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.data.database.VerificationEntity
import com.verifylabs.ai.data.repository.VerificationRepository
import com.verifylabs.ai.databinding.FragmentHistorySingleBinding
import com.verifylabs.ai.presentation.MainActivity
import com.verifylabs.ai.presentation.media.MediaType
import com.verifylabs.ai.presentation.viewmodel.MediaViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistorySingleFragment : Fragment() {

    private var _binding: FragmentHistorySingleBinding? = null
    private val binding
        get() = _binding!!

    @Inject lateinit var repository: VerificationRepository

    @Inject lateinit var preferenceHelper: PreferenceHelper

    private lateinit var viewModel: MediaViewModel

    private var historyId: Long = 0
    private var currentEntity: VerificationEntity? = null
    private var latestReverificationScore: Double = 0.0
    private var latestReverificationBand: Int = 1
    private var latestCredits: Int = 0
    private var latestMonthlyCredits: Int = 0
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isAudioPlaying = false

    private val updateSeekBar =
            object : Runnable {
                override fun run() {
                    mediaPlayer?.let {
                        binding.audioSeekBar.progress = it.currentPosition
                        binding.tvCurrentTime.text = formatTime(it.currentPosition)
                        handler.postDelayed(this, 1000)
                    }
                }
            }

    companion object {
        private const val TAG = "HistorySingleFragment"
        private const val ARG_HISTORY_ID = "history_id"

        fun newInstance(historyId: Long): HistorySingleFragment {
            return HistorySingleFragment().apply {
                arguments = Bundle().apply { putLong(ARG_HISTORY_ID, historyId) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyId = arguments?.getLong(ARG_HISTORY_ID) ?: 0
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistorySingleBinding.inflate(inflater, container, false)
        // Force recompile for ViewBinding change
        Log.d(TAG, "onCreateView: Inflated binding")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[MediaViewModel::class.java]

        setupClickListeners()
        setupObservers()
        loadHistoryDetails()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.verifyResponseFlow.collectLatest { resource ->
                    when (resource.status) {
                        Status.SUCCESS -> {
                            val response = resource.data
                            if (response != null) {
                                if (response.has("error") && !response.get("error").isJsonNull) {
                                     val errorMsg = response.get("error").asString
                                     handleReverificationError(errorMsg)
                                } else if (response.has("score")) {
                                    val score = response.get("score").asDouble
                                    val band = response.get("band")?.asInt ?: 1
                                    val bandName = response.get("band_name")?.asString ?: "Result"
                                    val bandDescription =
                                            response.get("band_description")?.asString ?: ""
                                    val credits = response.get("credits")?.asInt ?: 0
                                    val creditsMonthly = response.get("credits_monthly")?.asInt ?: 0
                                    handleReverificationSuccess(
                                            score,
                                            band,
                                            bandName,
                                            bandDescription,
                                            credits,
                                            creditsMonthly
                                    )
                                } else {
                                    handleReverificationError("Invalid response from server (Missing score)")
                                }
                            } else {
                                handleReverificationError("Empty response from server")
                            }
                        }
                        Status.ERROR -> {
                            handleReverificationError(resource.message ?: "Verification failed")
                        }
                        Status.LOADING -> {
                            binding.tvReverificationStatus.text = "Verifying..."
                        }
                        Status.INSUFFICIENT_CREDITS -> {
                            handleReverificationError("Insufficient credits")
                        }
                    }
                }
            }
        }

        viewModel.getUploadResponse().observe(viewLifecycleOwner) { resource ->
            if (resource.status == Status.SUCCESS) {
                val uploadedUrl = resource.data?.get("uploadedUrl")?.asString
                if (uploadedUrl != null) {
                    binding.tvReverificationStatus.text = "Verifying..."
                    val username = preferenceHelper.getUserName() ?: ""
                    val apiKey = preferenceHelper.getApiKey() ?: ""
                    val type = (currentEntity?.mediaType ?: "image").lowercase()
                    viewModel.verifyMedia(username, apiKey, type, uploadedUrl)
                }
            } else if (resource.status == Status.ERROR) {
                handleReverificationError(resource.message ?: "Upload failed")
            } else if (resource.status == Status.LOADING) {
                binding.tvReverificationStatus.text = "Uploading..."
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.btnVerifyAgain.setOnClickListener { performReverification() }

        binding.btnSaveNew.setOnClickListener { saveAsNewEntry() }
    }

    private fun performReverification() {
        val entity = currentEntity ?: return
        val filePath = entity.mediaUri
        if (filePath.isNullOrEmpty()) {
            handleReverificationError("Media file path not found")
            return
        }

        binding.btnVerifyAgain.isVisible = false
        binding.layoutReverifying.isVisible = true
        binding.layoutNewResult.isVisible = false
        binding.tvReverificationError.isVisible = false
        binding.tvReverificationStatus.text = "Uploading..."

        val mediaType =
                when (entity.mediaType.lowercase()) {
                    "video" -> MediaType.VIDEO
                    "audio" -> MediaType.AUDIO
                    else -> MediaType.IMAGE
                }

        viewModel.uploadMedia(filePath, mediaType)
    }

    private fun handleReverificationSuccess(
            score: Double,
            band: Int,
            bandName: String,
            bandDescription: String,
            credits: Int,
            monthlyCredits: Int
    ) {
        latestReverificationScore = score
        latestReverificationBand = band
        latestCredits = credits
        latestMonthlyCredits = monthlyCredits

        // Sync credits to preferences
        preferenceHelper.setCreditReamaining(credits + monthlyCredits)

        binding.layoutReverifying.isVisible = false
        binding.layoutNewResult.isVisible = true
        binding.btnVerifyAgain.isVisible = true
        binding.tvReverificationError.isVisible = false

        updateNewResultDisplay(score, bandName, bandDescription)
    }

    private fun handleReverificationError(error: String) {
        binding.layoutReverifying.isVisible = false
        binding.btnVerifyAgain.isVisible = true
        binding.tvReverificationError.text = error
        binding.tvReverificationError.isVisible = true
        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
    }

    private fun updateNewResultDisplay(score: Double, bandName: String, bandDescription: String) {
        binding.tvNewBandName.text = bandName
        binding.tvNewDescription.text = bandDescription

        val colorRes =
                when {
                    score > 0.95 -> R.color.vl_red
                    score > 0.85 -> R.color.system_red
                    score > 0.65 -> R.color.system_gray
                    score > 0.50 -> R.color.system_green
                    else -> R.color.vl_green
                }

        val baseColor = androidx.core.content.ContextCompat.getColor(requireContext(), colorRes)
        val backgroundColor =
                androidx.core.graphics.ColorUtils.setAlphaComponent(baseColor, (0.1 * 255).toInt())
        val strokeColor =
                androidx.core.graphics.ColorUtils.setAlphaComponent(baseColor, (0.3 * 255).toInt())

        val shape =
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 16 * resources.displayMetrics.density
                    setColor(backgroundColor)
                    setStroke((1 * resources.displayMetrics.density).toInt(), strokeColor)
                }
        binding.cardNewResult.background = shape

        val originalScore = currentEntity?.aiScore ?: 0.0
        val diff = Math.abs(score - originalScore)
        if (diff > 0.01) {
            val percent = (diff * 100).toInt()
            val text = "Changed by $percent%"
            binding.tvComparison.text = text
            binding.tvComparison.setTextColor(
                    if (score > originalScore) android.graphics.Color.RED
                    else android.graphics.Color.GREEN
            )
        } else {
            binding.tvComparison.text = "Same result as original"
            binding.tvComparison.setTextColor(resources.getColor(R.color.secondary_text))
        }

        when {
            score > 0.85 ->
                    binding.imgNewIcon.setImageResource(
                            R.drawable.verifylabs_robot_icon_light_grey_rgb_1__traced_
                    )
            score > 0.65 -> binding.imgNewIcon.setImageResource(R.drawable.ic_question_circle)
            else ->
                    binding.imgNewIcon.setImageResource(
                            R.drawable.verifylabs_smile_icon_light_grey_rgb_1__traced_
                    )
        }
    }

    private fun saveAsNewEntry() {
        val entity = currentEntity ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val newEntity =
                    entity.copy(
                            id = 0,
                            timestamp = System.currentTimeMillis(),
                            aiScore = latestReverificationScore,
                            band = latestReverificationBand,
                            bandName = binding.tvNewBandName.text.toString(),
                            bandDescription = binding.tvNewDescription.text.toString()
                    )
            repository.saveVerification(newEntity)
            Toast.makeText(requireContext(), "Saved to history", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadHistoryDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val entity = repository.getById(historyId)
                currentEntity = entity
                if (entity == null) {
                    Toast.makeText(requireContext(), "History item not found", Toast.LENGTH_SHORT)
                            .show()
                    parentFragmentManager.popBackStack()
                    return@launch
                }

                // binding.tvTypeValue.text = entity.mediaType.uppercase() // Hidden per user request 
                
                when (entity.mediaType) {
                    "Image" -> {
                        binding.imageView.isVisible = true
                        binding.btnPlayVideo.isVisible = false
                        binding.videoView.isVisible = false
                        binding.audioPlayerLayout.isVisible = false
                        binding.ivAudioIcon.setImageResource(R.drawable.ic_mic)

                        entity.mediaUri?.let { uriString ->
                            try {
                                binding.imageView.setImageURI(Uri.parse(uriString))
                            } catch (e: Exception) {
                                binding.imageView.setImageResource(R.drawable.verifylabs_logo)
                            }
                        }
                                ?: run {
                                    binding.imageView.setImageResource(R.drawable.verifylabs_logo)
                                }
                    }
                    "Video" -> {
                        binding.imageView.isVisible = true
                        binding.btnPlayVideo.isVisible = true
                        binding.videoView.isVisible = false
                        binding.audioPlayerLayout.isVisible = false

                        entity.mediaUri?.let { uriString ->
                            val uri = Uri.parse(uriString)
                            try {
                                val frame = extractVideoFrame(uri)
                                if (frame != null) binding.imageView.setImageBitmap(frame)
                                else
                                        binding.imageView.setImageResource(
                                                android.R.drawable.ic_menu_slideshow
                                        )
                            } catch (e: Exception) {
                                binding.imageView.setImageResource(
                                        android.R.drawable.ic_menu_slideshow
                                )
                            }

                            binding.btnPlayVideo.setOnClickListener {
                                binding.imageView.isVisible = false
                                binding.btnPlayVideo.isVisible = false
                                binding.videoView.isVisible = true
                                binding.videoView.setVideoURI(uri)
                                val mc = MediaController(requireContext())
                                mc.setAnchorView(binding.videoView)
                                binding.videoView.setMediaController(mc)
                                binding.videoView.setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    binding.videoView.start()
                                }
                            }
                        }
                    }
                    "Audio" -> {
                        binding.imageView.isVisible = false
                        binding.btnPlayVideo.isVisible = false
                        binding.videoView.isVisible = false
                        binding.audioPlayerLayout.isVisible = true
                        binding.imageOverlay.isVisible = false

                        // Match iOS icons for audio types
                        val iconRes =
                                if (entity.mediaThumbnail != null) R.drawable.ic_quick_record
                                else R.drawable.ic_mic
                        binding.ivAudioIcon.setImageResource(iconRes)
                        binding.tvAudioLabel.text =
                                if (entity.mediaThumbnail != null) "Quick record audio"
                                else "Manual recording"

                        entity.mediaUri?.let { setupAudioPlayer(Uri.parse(it)) }
                    }
                }

                // Hidden per user request
                // binding.tvResolutionValue.text = entity.resolution ?: "--"
                val uriStr = entity.mediaUri ?: ""
                // binding.tvSizeValue.text =
                //        if (uriStr.contains("/")) uriStr.substringAfterLast("/")
                //        else if (uriStr.isNotEmpty()) uriStr else "--"
                // binding.tvRobotValue.text = entity.bandName

                updateResultDisplay(entity.aiScore, entity.bandName, entity.bandDescription)

                val dateFormat = SimpleDateFormat("dd MMM yyyy 'at' h:mm a", Locale.getDefault())
                binding.tvVerifiedDate.text =
                        "Verified on ${dateFormat.format(Date(entity.timestamp))}"
            } catch (e: Exception) {
                Log.e(TAG, "Error loading history details", e)
            }
        }
    }

    private fun setupAudioPlayer(uri: Uri) {
        mediaPlayer =
                MediaPlayer().apply {
                    try {
                        setDataSource(requireContext(), uri)
                        prepare()
                        binding.tvTotalTime.text = formatTime(duration)
                        binding.audioSeekBar.max = duration
                    } catch (e: Exception) {
                        Log.e(TAG, "Audio player setup failed", e)
                    }
                }

        binding.btnPlayPauseAudio.setOnClickListener {
            if (isAudioPlaying) {
                mediaPlayer?.pause()
                binding.btnPlayPauseAudio.setImageResource(android.R.drawable.ic_media_play)
                handler.removeCallbacks(updateSeekBar)
            } else {
                mediaPlayer?.start()
                binding.btnPlayPauseAudio.setImageResource(android.R.drawable.ic_media_pause)
                handler.post(updateSeekBar)
            }
            isAudioPlaying = !isAudioPlaying
        }

        binding.audioSeekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                    ) {
                        if (fromUser) mediaPlayer?.seekTo(progress)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
        )

        mediaPlayer?.setOnCompletionListener {
            isAudioPlaying = false
            binding.btnPlayPauseAudio.setImageResource(android.R.drawable.ic_media_play)
            binding.audioSeekBar.progress = 0
            binding.tvCurrentTime.text = "00:00"
            handler.removeCallbacks(updateSeekBar)
        }
    }

    private fun formatTime(millis: Int): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun extractVideoFrame(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(requireContext(), uri)
            retriever.getFrameAtTime(0)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun updateResultDisplay(score: Double, bandName: String, bandDescription: String) {
        binding.tvBandName.text = bandName
        binding.tvDescription.text = bandDescription

        val colorRes =
                when {
                    score > 0.95 -> R.color.vl_red
                    score > 0.85 -> R.color.system_red
                    score > 0.65 -> R.color.system_gray
                    score > 0.50 -> R.color.system_green
                    else -> R.color.vl_green
                }

        val baseColor = androidx.core.content.ContextCompat.getColor(requireContext(), colorRes)
        val backgroundColor =
                androidx.core.graphics.ColorUtils.setAlphaComponent(baseColor, (0.1 * 255).toInt())
        val strokeColor =
                androidx.core.graphics.ColorUtils.setAlphaComponent(baseColor, (0.3 * 255).toInt())

        val resultDrawable =
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(backgroundColor)
                    setStroke((1 * resources.displayMetrics.density).toInt(), strokeColor)
                    cornerRadius = 16 * resources.displayMetrics.density
                }

        binding.layoutResult.background = resultDrawable
        binding.tvBandName.setTextColor(baseColor)

        when {
            score > 0.85 -> {
                binding.imgIcon.setImageResource(
                        R.drawable.verifylabs_robot_icon_light_grey_rgb_1__traced_
                )
                binding.imageOverlay.setImageResource(R.drawable.ic_red_cross_tranparent)
                binding.imageOverlay.isVisible = currentEntity?.mediaType != "Audio"
            }
            score > 0.65 -> {
                binding.imgIcon.setImageResource(R.drawable.ic_question_circle)
                binding.imageOverlay.setImageResource(R.drawable.ic_gray_area)
                binding.imageOverlay.isVisible = currentEntity?.mediaType != "Audio"
            }
            else -> {
                binding.imgIcon.setImageResource(
                        R.drawable.verifylabs_smile_icon_light_grey_rgb_1__traced_
                )
                binding.imageOverlay.setImageResource(R.drawable.ic_tick_icon)
                binding.imageOverlay.isVisible = currentEntity?.mediaType != "Audio"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setBottomNavVisibility(false)
        (activity as? MainActivity)?.setAppBarVisibility(false)

        // Sync Status Bar, System Nav Bar, and Root Background to match this fragment's @color/app_background
        (activity as? MainActivity)?.updateStatusBarColor(R.color.app_background)
        (activity as? MainActivity)?.updateMainBackgroundColor(R.color.app_background)
    }

    override fun onPause() {
        super.onPause()
        (activity as? MainActivity)?.setBottomNavVisibility(true)
        (activity as? MainActivity)?.setAppBarVisibility(true)
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        handler.removeCallbacks(updateSeekBar)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
