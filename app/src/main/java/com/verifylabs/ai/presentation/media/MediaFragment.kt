package com.verifylabs.ai.presentation.media

import InternetHelper
import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.FragmentMediaBinding
import com.verifylabs.ai.presentation.viewmodel.MediaViewModel
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

enum class ScanButtonState { VERIFY, SCANNING, DONE, FAILED }

@AndroidEntryPoint
class MediaFragment : Fragment() {

    private val TAG = "MediaFragment"
    private var _binding: FragmentMediaBinding? = null
    private val binding get() = _binding ?: error("Binding is null")

    private lateinit var viewModel: MediaViewModel

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private var buttonState = ScanButtonState.VERIFY
    private var selectedMediaUri: Uri? = null
    private var mediaType = MediaType.IMAGE

    // Internet monitoring
    private lateinit var internetHelper: InternetHelper
    private var isMonitoringActive = false // Track monitoring state

    // Timestamp recording
    private var timestampHandler: Handler? = null
    private var timestampRunnable: Runnable? = null
    private val timestampList = mutableListOf<Long>()
    private val timestampInterval: Long = 1000 // 1 second

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        try {
            if (permissions.all { it.value }) {
                selectMedia()
            } else {
                showPermissionDialog()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error handling permission result: ${e.message}")
        }
    }

    private val selectMediaLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        try {
            uri?.let {
                resetUI()
                selectedMediaUri = it
                mediaType = when (requireContext().contentResolver.getType(it)?.substringBefore("/")) {
                    "video" -> MediaType.VIDEO
                    "audio" -> MediaType.AUDIO
                    else -> MediaType.IMAGE
                }

                binding.iconAction.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_folder_add)
                )

                val file = getFileFromUri(requireContext(), it)
                file?.let { localFile ->
                    preferenceHelper.setSelectedMediaPath(localFile.absolutePath)
                    preferenceHelper.setSelectedMediaType(mediaType.name)
                } ?: run {
                    Log.d(TAG, "Failed to get file from URI")
                    updateStatus("Failed to process media file", true)
                    return@let
                }

                showPreview(it)
                setButtonState(ScanButtonState.VERIFY)
                updateStatus("", false)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error handling media selection: ${e.message}")
            updateStatus("Error selecting media", true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            viewModel = ViewModelProvider(this)[MediaViewModel::class.java]
            internetHelper = InternetHelper(requireContext()) // Initialize here

            // Restore previously selected media
            val savedPath = preferenceHelper.getSelectedMediaPath()
            val savedType = preferenceHelper.getSelectedMediaType()
            if (!savedPath.isNullOrEmpty() && !savedType.isNullOrEmpty()) {
                val file = File(savedPath)
                if (file.exists()) {
                    mediaType = MediaType.valueOf(savedType)
                    selectedMediaUri = Uri.fromFile(file)
                    showPreview(selectedMediaUri!!)
                    setButtonState(ScanButtonState.VERIFY)
                }
            }

            binding.btnSelectMedia.setOnClickListener { checkMediaPermissionsAndSelect() }

            binding.imageViewMedia.setOnClickListener { checkMediaPermissionsAndSelect() }


            binding.btnAction.setOnClickListener {

                viewModel.checkCredits(
                    username = preferenceHelper.getUserName().toString(),
                    apiKey = preferenceHelper.getApiKey().toString()
                )

                val totalCredits = preferenceHelper.getCreditRemaining() ?: 0

                if (totalCredits <= 0) {
                    binding.btnAction.visibility=View.GONE
                    binding.layoutNoCreditStatus.visibility=View.VISIBLE
                } else {
                    binding.layoutNoCreditStatus.visibility=View.GONE
                    try {
                        when (buttonState) {
                            ScanButtonState.VERIFY -> startUpload()
                            ScanButtonState.SCANNING -> {
                                isMonitoringActive = true // Set monitoring active
                                internetHelper.startMonitoring()
                                internetHelper.isConnected.observe(viewLifecycleOwner) { connected ->
                                    if (!connected) {
                                        updateStatus("No internet connection", true)
                                        setButtonState(ScanButtonState.FAILED)
                                    }
                                }
                            }

                            ScanButtonState.DONE -> {
                                resetUI()
                                clearSavedMedia()
                            }

                            ScanButtonState.FAILED -> {}
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Error handling button action: ${e.message}")
                        updateStatus("Error performing action", true)
                    }
                }


            }


        } catch (e: Exception) {
            Log.d(TAG, "Error in onViewCreated: ${e.message}")
        }


        observeViewModel()
        obServereCredits()

    }

    private fun obServereCredits(){
        // Observe credits
        viewModel.getCreditsResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.SUCCESS -> {
                    val creditsJson = resource.data
                    val credits = creditsJson?.get("credits")?.asInt ?: 0
                    val monthlyCredits = creditsJson?.get("creditsMonthly")?.asInt ?: 0
                    val totalCredits = credits + monthlyCredits
                    preferenceHelper.setCreditReamaining(totalCredits)
                  //  binding.tvCredits.text = "Credits Remaining: $totalCredits"
                    binding.btnAction.isEnabled = totalCredits > 0
                    binding.btnAction.alpha = if (totalCredits > 0) 1f else 0.5f
                }
                Status.ERROR -> {
                    binding.btnAction.isEnabled = false
                    binding.btnAction.alpha = 0.5f
                }
                else -> {}
            }
        }


    }

    private fun initChangeBtnColor() {
        try {
            when (buttonState) {
                ScanButtonState.VERIFY -> {
                    binding.btnAction.background =
                        ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_gray_less_radius)
                }
                ScanButtonState.SCANNING -> {
                    binding.btnAction.background =
                        ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_btn_blue)
                    binding.iconAction.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_seach_icon)
                    )
                }
                ScanButtonState.DONE -> {
                    binding.btnAction.background =
                        ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_btn_blue)
                    binding.iconAction.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_tick_icon)
                    )
                }
                ScanButtonState.FAILED -> {
                    binding.iconAction.setImageDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_cloud_cross)
                    )
                    binding.btnAction.background =
                        ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_btn_failed_likely_red)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error changing button color: ${e.message}")
        }
    }

    private fun checkMediaPermissionsAndSelect() {
        try {
            val permissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            val allGranted = permissions.all { perm ->
                ContextCompat.checkSelfPermission(requireContext(), perm) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                selectMedia()
            } else {
                requestPermissionLauncher.launch(permissions.toTypedArray())
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error checking media permissions: ${e.message}")
            updateStatus("Error checking permissions", true)
        }
    }

    private fun selectMedia() {
        try {
            selectMediaLauncher.launch(arrayOf("image/*", "video/*", "audio/*"))
        } catch (e: Exception) {
            Log.d(TAG, "Error launching media selector: ${e.message}")
            updateStatus("Error selecting media", true)
        }
    }

    private fun showPermissionDialog() {
        try {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Permission Required")
                .setMessage("Media access is required to select files. Please grant the permission in app settings.")
                .setCancelable(false)
                .setPositiveButton("Open Settings") { dialog, _ ->
                    dialog.dismiss()
                    openAppSettings()
                }
                .show()
        } catch (e: Exception) {
            Log.d(TAG, "Error showing permission dialog: ${e.message}")
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", requireContext().packageName, null)
            startActivity(intent)
        } catch (e: Exception) {
            Log.d(TAG, "Error opening app settings: ${e.message}")
        }
    }

    private fun showPreview(uri: Uri) {
        try {
            when (mediaType) {
                MediaType.IMAGE -> {
                    binding.imageViewMedia.visibility = View.VISIBLE
                    binding.videoViewMedia.visibility = View.GONE
                    binding.imageViewMedia.setImageURI(uri)
                }
                MediaType.VIDEO -> {
                    binding.imageViewMedia.visibility = View.GONE
                    binding.videoViewMedia.visibility = View.VISIBLE
                    binding.videoViewMedia.setVideoURI(uri)
                    val mediaController = MediaController(requireContext())
                    mediaController.setAnchorView(binding.videoViewMedia)
                    binding.videoViewMedia.setMediaController(mediaController)
                    binding.videoViewMedia.setOnPreparedListener { mp ->
                        mp.isLooping = true
                        binding.videoViewMedia.start()
                    }
                }
                MediaType.AUDIO -> {
                    binding.imageViewMedia.visibility = View.VISIBLE
                    binding.videoViewMedia.visibility = View.GONE
                    binding.imageViewMedia.setImageResource(R.drawable.ic_media)
                }
            }
            binding.imageOverlay.visibility = View.GONE
            binding.btnAction.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.d(TAG, "Error showing media preview: ${e.message}")
            updateStatus("Error displaying media", true)
        }
    }

    private fun startUpload() {
        try {
            selectedMediaUri?.let { uri ->
                val filePath = getFileFromUri(requireContext(), uri)?.absolutePath ?: return
                val file = File(filePath)
                if (file.length() > 100 * 1024 * 1024) {
                    updateStatus("File too large (max 100MB)", true)
                    return
                }

                Log.d(TAG, "Starting upload for file: $filePath, Type: $mediaType")
                setButtonState(ScanButtonState.SCANNING)
                updateStatus("Uploading media...", false)
                viewModel.uploadMedia(filePath, mediaType)
            } ?: run {
                Log.d(TAG, "No media URI selected for upload")
                updateStatus("No media selected", true)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error starting upload: ${e.message}")
            updateStatus("Error uploading media", true)
            setButtonState(ScanButtonState.FAILED)
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        try {
            val fileName = getFileName(context, uri) ?: "temp_file"
            val tempFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return tempFile
        } catch (e: Exception) {
            Log.d(TAG, "Failed to copy file from URI: ${e.message}")
            updateStatus("Failed to process file", true)
            return null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        try {
            var name: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
            return name
        } catch (e: Exception) {
            Log.d(TAG, "Error getting file name: ${e.message}")
            return null
        }
    }

    private fun observeViewModel() {
        try {
            viewModel.getUploadResponse().observe(viewLifecycleOwner) { resource ->
                try {
                    when (resource.status) {
                        Status.LOADING -> {
                            setButtonState(ScanButtonState.SCANNING)
                            updateStatus("Uploading media...", false)
                        }
                        Status.SUCCESS -> {


                            val uploadedUrl = resource.data?.get("uploadedUrl")?.asString ?: "Unknown URL"
                            updateStatus("Verifying media...", false)
                            viewModel.verifyMedia(
                                username = preferenceHelper.getUserName().toString(),
                                apiKey = preferenceHelper.getApiKey().toString(),
                                mediaType = mediaType.value,
                                mediaUrl = uploadedUrl
                            )
                        }
                        Status.ERROR -> {
                            updateStatus("Upload failed: ${resource.message}", true)
                            setButtonState(ScanButtonState.FAILED)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error handling upload response: ${e.message}")
                    updateStatus("Error processing upload response", true)
                }
            }

            viewModel.getVerifyResponse().observe(viewLifecycleOwner) { resource ->
                try {
                    when (resource.status) {
                        Status.LOADING -> {
                            setButtonState(ScanButtonState.SCANNING)
                            updateStatus("Verifying media...", false)
                        }
                        Status.SUCCESS -> {
                            val response = Gson().fromJson(
                                resource.data.toString(),
                                VerificationResponse::class.java
                            )

                            if (response.error != null) {
                                updateStatus("Verification error: ${response.error}", true)
                                binding.textStatusMessage.text = "${response.bandDescription}"
                                binding.txtIdentifixation.text = "${response.error}"
                                setButtonState(ScanButtonState.FAILED)
                                return@observe
                            }

                            Log.d(TAG, "observeViewModel: ${resource.data}")

                            binding.layoutInfoStatus.visibility = View.VISIBLE
                            binding.textStatusMessage.visibility = View.VISIBLE
                            binding.imageOverlay.visibility = View.VISIBLE

                            binding.textStatusMessage.text = "${response.bandDescription}"
                            binding.txtIdentifixation.text = "${getBandResult(response.band)}"

                            when (response.band) {
                                1, 2 -> { // Human-made
                                    binding.imageOverlay.setImageDrawable(
                                        ContextCompat.getDrawable(requireContext(), R.drawable.verifylabs_tick_icon_light_grey_rgb_2__traced___1_)
                                    )
                                    binding.txtIdentifixation.background =
                                        ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_green)
                                    binding.imgIdentification.setImageDrawable(
                                        ContextCompat.getDrawable(requireContext(), R.drawable.verifylabs_smile_icon_light_grey_rgb_1__traced_)
                                    )
                                }
                                3 -> { // Inconclusive
                                    binding.imageOverlay.setImageDrawable(
                                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_gray_area)
                                    )
                                    binding.txtIdentifixation.background =
                                        ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_btn_failed_likely_gray)
                                    binding.imgIdentification.setImageDrawable(
                                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_question_circle)
                                    )
                                }
                                4, 5 -> { // Likely AI / AI
                                    binding.imageOverlay.setImageDrawable(
                                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_red_cross_tranparent)
                                    )
                                    binding.txtIdentifixation.background =
                                        ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_btn_failed_likely_red_without_radius)
                                    binding.imgIdentification.setImageDrawable(
                                        ContextCompat.getDrawable(requireContext(), R.drawable.verifylabs_robot_icon_light_grey_rgb_1__traced_)
                                    )
                                }
                            }

                            setButtonState(ScanButtonState.DONE)
                        }
                        Status.ERROR -> {
                            Toast.makeText(requireContext(), "${resource.message}", Toast.LENGTH_SHORT).show()
                            updateStatus("Verification failed: ${resource.message}", true)
                            setButtonState(ScanButtonState.FAILED)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error handling verification response: ${e.message}")
                    updateStatus("Error processing verification response", true)
                    setButtonState(ScanButtonState.FAILED)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error setting up ViewModel observers: ${e.message}")
        }
    }

    private fun setButtonState(state: ScanButtonState) {
        try {
            buttonState = state
            binding.btnAction.visibility = View.VISIBLE
            binding.textAction.text = when (state) {
                ScanButtonState.VERIFY -> "Verify Media"
                ScanButtonState.SCANNING -> "Scanning..."
                ScanButtonState.DONE -> "Done!"
                ScanButtonState.FAILED -> "FAILED!"
            }
            initChangeBtnColor()
        } catch (e: Exception) {
            Log.d(TAG, "Error setting button state: ${e.message}")
        }
    }

    private fun updateStatus(message: String, isError: Boolean) {
        try {
            _binding?.textStatusMessage?.text = message
            _binding?.textStatusMessage?.setTextColor(
                if (isError) Color.RED else ContextCompat.getColor(requireContext(), R.color.colorBlack)
            )
        } catch (e: Exception) {
            Log.d(TAG, "Error updating status: ${e.message}")
        }
    }

    private fun resetUI() {
        try {
            binding.imageViewMedia.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.drawable_image_chooser)
            )
            binding.imageViewMedia.visibility = View.VISIBLE
            binding.videoViewMedia.visibility = View.GONE
            binding.imageOverlay.visibility = View.GONE
            binding.btnAction.visibility = View.GONE
            binding.layoutInfoStatus.visibility = View.GONE
//            setButtonState(ScanButtonState.VERIFY)
            updateStatus("", false)
            selectedMediaUri = null
        } catch (e: Exception) {
            Log.d(TAG, "Error resetting UI: ${e.message}")
        }
    }

    private fun clearSavedMedia() {
        try {
            preferenceHelper.getSelectedMediaPath()?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete() // Clean up temporary file
                }
            }
            preferenceHelper.setSelectedMediaPath(null)
            preferenceHelper.setSelectedMediaType(null)
        } catch (e: Exception) {
            Log.d(TAG, "Error clearing saved media: ${e.message}")
        }
    }

    private fun getBandResult(band: Int?): String {
        try {
            return when (band) {
                1 -> "Human Made"
                2 -> "Likely Human Made"
                3 -> "Inconclusive"
                4 -> "Likely AI"
                5 -> "AI-generated"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error getting band result: ${e.message}")
            return "Unknown"
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            binding.videoViewMedia.pause()
        } catch (e: Exception) {
            Log.d(TAG, "Error pausing video: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            binding.videoViewMedia.stopPlayback() // Stop video playback
            timestampHandler?.removeCallbacksAndMessages(null) // Clear Handler callbacks
            timestampHandler = null
            timestampRunnable = null
            if(isMonitoringActive) {
                internetHelper.stopMonitoring() // Stop network monitoring
            }
            _binding = null // Clear binding
        } catch (e: Exception) {
            Log.d(TAG, "Error in onDestroyView: ${e.message}")
        }
    }
}