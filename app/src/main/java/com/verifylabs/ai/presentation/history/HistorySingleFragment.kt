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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.verifylabs.ai.R
import com.verifylabs.ai.data.repository.VerificationRepository
import com.verifylabs.ai.databinding.FragmentHistorySingleBinding
import com.verifylabs.ai.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class HistorySingleFragment : Fragment() {

    private var _binding: FragmentHistorySingleBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var repository: VerificationRepository

    private var historyId: Long = 0
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isAudioPlaying = false

    private val updateSeekBar = object : Runnable {
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
                arguments = Bundle().apply {
                    putLong(ARG_HISTORY_ID, historyId)
                }
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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        loadHistoryDetails()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnVerifyAgain.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(1) // Navigation back to Media tab
        }
    }

    private fun loadHistoryDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val entity = repository.getById(historyId)
                if (entity == null) {
                    Toast.makeText(requireContext(), "History item not found", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    return@launch
                }

                // Display media based on type
                binding.tvTypeValue.text = entity.mediaType.uppercase()
                when (entity.mediaType) {
                    "Image" -> {
                        binding.imageView.visibility = View.VISIBLE
                        binding.btnPlayVideo.visibility = View.GONE
                        binding.videoView.visibility = View.GONE
                        binding.audioPlayerLayout.visibility = View.GONE
                        binding.ivTypeIcon.setImageResource(android.R.drawable.ic_menu_gallery)
                        binding.cardAudioAnalysis.visibility = View.GONE

                        entity.mediaUri?.let { uriString ->
                            try {
                                val uri = Uri.parse(uriString)
                                binding.imageView.setImageURI(uri)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error loading image", e)
                                binding.imageView.setImageResource(R.drawable.verifylabs_logo)
                            }
                        } ?: run {
                            binding.imageView.setImageResource(R.drawable.verifylabs_logo)
                        }
                    }

                    "Video" -> {
                        binding.imageView.visibility = View.VISIBLE
                        binding.btnPlayVideo.visibility = View.VISIBLE
                        binding.videoView.visibility = View.GONE
                        binding.audioPlayerLayout.visibility = View.GONE
                        binding.ivTypeIcon.setImageResource(android.R.drawable.ic_menu_slideshow)

                        entity.mediaUri?.let { uriString ->
                            val uri = Uri.parse(uriString)
                            // Show thumbnail first
                            try {
                                val frame = extractVideoFrame(uri)
                                if (frame != null) binding.imageView.setImageBitmap(frame)
                                else binding.imageView.setImageResource(android.R.drawable.ic_menu_slideshow)
                            } catch (e: Exception) {
                                binding.imageView.setImageResource(android.R.drawable.ic_menu_slideshow)
                            }

                            // Setup Video Playback
                            binding.btnPlayVideo.setOnClickListener {
                                binding.imageView.visibility = View.GONE
                                binding.btnPlayVideo.visibility = View.GONE
                                binding.videoView.visibility = View.VISIBLE
                                
                                binding.videoView.setVideoURI(uri)
                                val mc = MediaController(requireContext())
                                mc.setAnchorView(binding.videoView)
                                binding.videoView.setMediaController(mc)
                                binding.videoView.setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    binding.videoView.start()
                                }
                            }
                        } ?: run {
                            binding.imageView.setImageResource(android.R.drawable.ic_menu_slideshow)
                            binding.btnPlayVideo.visibility = View.GONE
                        }
                    }

                    "Audio" -> {
                        binding.imageView.visibility = View.GONE
                        binding.btnPlayVideo.visibility = View.GONE
                        binding.videoView.visibility = View.GONE
                        binding.audioPlayerLayout.visibility = View.VISIBLE
                        binding.ivTypeIcon.setImageResource(R.drawable.ic_mic)

                        entity.mediaUri?.let { uriString ->
                            binding.cardAudioAnalysis.visibility = View.VISIBLE
                            
                            val temporalScores = try {
                                if (!entity.temporalScoresJson.isNullOrEmpty()) {
                                    val type = object : com.google.gson.reflect.TypeToken<List<Double>>() {}.type
                                    Gson().fromJson<List<Double>>(entity.temporalScoresJson, type)
                                } else null
                            } catch (e: Exception) {
                                null
                            }

                            if (temporalScores != null) {
                                binding.audioAnalysisChart.setChronologicalScores(temporalScores)
                            } else {
                                binding.audioAnalysisChart.setScore(entity.aiScore)
                            }
                            
                            setupAudioPlayer(Uri.parse(uriString))
                        }
                    }
                }

                // Populate Stats
                entity.quality?.let {
                    binding.tvQualityValue.text = it.toString()
                    binding.qualityProgressBar.progress = it
                } ?: run {
                    binding.tvQualityValue.text = "--"
                    binding.qualityProgressBar.progress = 0
                }

                binding.tvSizeValue.text = entity.fileSizeKb?.toString() ?: "--"
                
                if (entity.resolution != null && entity.resolution.contains("x")) {
                    binding.tvResolutionValue.text = entity.resolution
                    binding.tvResolutionLabel.text = "HD"
                } else {
                    binding.tvResolutionValue.text = entity.resolution ?: "--"
                    binding.tvResolutionLabel.text = ""
                }

                updateResultDisplay(entity.band, entity.bandName, entity.bandDescription)

                val dateFormat = SimpleDateFormat("dd MMM yyyy 'at' h:mma", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(entity.timestamp))
                binding.tvVerifiedDate.text = "Verified on $formattedDate"

            } catch (e: Exception) {
                Log.e(TAG, "Error loading history details", e)
                Toast.makeText(requireContext(), "Error loading details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAudioPlayer(uri: Uri) {
        mediaPlayer = MediaPlayer().apply {
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

        binding.audioSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mediaPlayer?.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

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
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(requireContext(), uri)
            val bitmap = retriever.getFrameAtTime(0)
            retriever.release()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting video frame", e)
            null
        }
    }

    private fun updateResultDisplay(band: Int, bandName: String, bandDescription: String) {
        binding.tvBandName.text = bandName
        binding.tvDescription.text = bandDescription
        when (band) {
            1, 2 -> {
                binding.layoutResult.setBackgroundResource(R.drawable.drawable_verify_background_green)
                binding.tvBandName.setBackgroundResource(R.drawable.drawable_verify_background_green)
                binding.imgIcon.setImageResource(R.drawable.verifylabs_smile_icon_light_grey_rgb_1__traced_)
            }
            3 -> {
                binding.layoutResult.setBackgroundResource(R.drawable.drawable_verify_background_btn_failed_likely_gray)
                binding.tvBandName.setBackgroundResource(R.drawable.drawable_verify_background_btn_failed_likely_gray)
                binding.imgIcon.setImageResource(R.drawable.ic_question_circle)
            }
            4, 5 -> {
                binding.layoutResult.setBackgroundResource(R.drawable.drawable_verify_background_btn_failed_likely_red_without_radius)
                binding.tvBandName.setBackgroundResource(R.drawable.drawable_verify_background_btn_failed_likely_red_without_radius)
                binding.imgIcon.setImageResource(R.drawable.verifylabs_robot_icon_light_grey_rgb_1__traced_)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setBottomNavVisibility(false)
        (activity as? MainActivity)?.setAppBarVisibility(false)
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
