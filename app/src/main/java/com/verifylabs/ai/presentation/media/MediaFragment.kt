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
import com.google.gson.Gson
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.data.repository.VerificationRepository
import com.verifylabs.ai.databinding.FragmentMediaBinding
import com.verifylabs.ai.presentation.viewmodel.MediaViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.launch

enum class ScanButtonState {
    VERIFY,
    SCANNING,
    DONE,
    FAILED
}

@AndroidEntryPoint
class MediaFragment : Fragment() {

    private val TAG = "MediaFragment"
    private var _binding: FragmentMediaBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var viewModel: MediaViewModel

    @Inject lateinit var preferenceHelper: PreferenceHelper
    @Inject lateinit var verificationRepository: VerificationRepository

    private var buttonState = ScanButtonState.VERIFY
    private var selectedMediaUri: Uri? = null
    private var mediaType = MediaType.IMAGE
    private var currentQualityPercent: Int? = null

    private lateinit var internetHelper: InternetHelper
    private var isMonitoringActive = false
    private var timestampHandler: Handler? = null
    private var timestampRunnable: Runnable? = null
    private val timestampList = mutableListOf<Long>()
    private val timestampInterval: Long = 1000

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
                if (permissions.all { it.value }) {
                    selectMedia()
                } else {
                    showPermissionDialog()
                }
            }

    private val selectMediaLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                uri?.let {
                    resetUI()
                    val type =
                            requireContext().contentResolver.getType(it)?.substringBefore("/")
                                    ?: "image"
                    mediaType =
                            when (type) {
                                "video" -> MediaType.VIDEO
                                else -> MediaType.IMAGE
                            }

                    if (mediaType == MediaType.IMAGE) {
                        showCropBottomSheet(it)
                    } else {
                        selectedMediaUri = it
                        showPreview(it)
                        updateMediaStatsForCurrentMedia()
                        setButtonState(ScanButtonState.VERIFY)
                    }
                }
            }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[MediaViewModel::class.java]
        internetHelper = InternetHelper(requireContext())

        val savedPath = preferenceHelper.getSelectedMediaPath()
        val savedType = preferenceHelper.getSelectedMediaType()

        if (!savedPath.isNullOrEmpty() && !savedType.isNullOrEmpty()) {
            val file = File(savedPath)
            if (file.exists()) {
                mediaType = MediaType.valueOf(savedType)
                selectedMediaUri = Uri.fromFile(file)
                showPreview(selectedMediaUri!!)
                updateMediaStatsForCurrentMedia()
                setButtonState(ScanButtonState.VERIFY)
            }
        } else {
            resetUI()
        }

        binding.btnSelectMedia.setOnClickListener { checkMediaPermissionsAndSelect() }

        binding.imageViewMedia.setOnClickListener { checkMediaPermissionsAndSelect() }

        binding.btnAction.setOnClickListener {
            val totalCredits = preferenceHelper.getCreditRemaining() ?: 0
            if (totalCredits <= 0) {
                binding.layoutNoCreditStatus.visibility = View.VISIBLE
                return@setOnClickListener
            }

            binding.layoutNoCreditStatus.visibility = View.GONE

            when (buttonState) {
                ScanButtonState.VERIFY -> startUpload()
                ScanButtonState.DONE, ScanButtonState.FAILED -> {
                    resetUI() // Reset for new verification
                }
                ScanButtonState.SCANNING -> {
                    // Optional: Toast.makeText(requireContext(), "Processing, please wait...",
                    // Toast.LENGTH_SHORT).show()
                }
            }
        }

        observeViewModel()
        observeCredits()
    }

    private fun observeCredits() {
        viewModel.getCreditsResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.SUCCESS -> {
                    val creditsJson = resource.data
                    val credits = creditsJson?.get("credits")?.asInt ?: 0
                    val monthlyCredits = creditsJson?.get("creditsMonthly")?.asInt ?: 0
                    val totalCredits = credits + monthlyCredits
                    preferenceHelper.setCreditReamaining(totalCredits)
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
        when (buttonState) {
            ScanButtonState.VERIFY -> {
                //                binding.btnAction.background = ContextCompat.getDrawable(
                //                    requireContext(),
                //                    R.drawable.drawable_verify_background_gray_less_radius
                //                )
                // Optional: set default icon if you have one
                // binding.iconAction.setImageDrawable(...)
            }
            ScanButtonState.SCANNING -> {
                //                binding.btnAction.background = ContextCompat.getDrawable(
                //                    requireContext(),
                //                    R.drawable.drawable_verify_background_btn_blue
                //                )
                // binding.iconAction.setImageDrawable(...)  // search/spinner icon
            }
            ScanButtonState.DONE -> {
                //                binding.btnAction.background = ContextCompat.getDrawable(
                //                    requireContext(),
                //                    R.drawable.drawable_verify_background_btn_blue
                //                )
                // binding.iconAction.setImageDrawable(...)  // check icon
            }
            ScanButtonState.FAILED -> {
                //                binding.btnAction.background = ContextCompat.getDrawable(
                //                    requireContext(),
                //                    R.drawable.drawable_verify_background_btn_failed_likely_red
                //                )
                // binding.iconAction.setImageDrawable(...)  // cross icon
            }
        }
    }

    private fun checkMediaPermissionsAndSelect() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted =
                permissions.all {
                    ContextCompat.checkSelfPermission(requireContext(), it) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                }

        if (allGranted) {
            selectMedia()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun selectMedia() {
        selectMediaLauncher.launch(arrayOf("image/*", "video/*"))
    }

    private fun showPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Permission Required")
                .setMessage(
                        "Media access is required to select files. Please grant the permission in app settings."
                )
                .setCancelable(false)
                .setPositiveButton("Open Settings") { dialog, _ ->
                    dialog.dismiss()
                    openAppSettings()
                }
                .show()
    }

    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", requireContext().packageName, null)
        startActivity(intent)
    }

    private fun showPreview(uri: Uri) {
        binding.imageViewMedia2.visibility = View.GONE

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
                /* TODO if needed */
            }
        }
        binding.imageOverlay.visibility = View.GONE
        binding.btnAction.visibility = View.VISIBLE
    }

    private fun startUpload() {
        val uri =
                selectedMediaUri
                        ?: run {
                            updateStatus("No media selected", true)
                            setButtonState(ScanButtonState.FAILED)
                            return
                        }

        val file =
                getFileFromUri(requireContext(), uri)
                        ?: run {
                            updateStatus("Cannot prepare file", true)
                            setButtonState(ScanButtonState.FAILED)
                            return
                        }

        if (file.length() > 100 * 1024 * 1024) {
            updateStatus("File too large (max 100MB)", true)
            setButtonState(ScanButtonState.FAILED)
            return
        }

        setButtonState(ScanButtonState.SCANNING)
        updateStatus("Uploading media...", false)
        viewModel.uploadMedia(file.absolutePath, mediaType)
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        val fileName =
                getFileName(context, uri)
                        ?: "temp_media.${if (mediaType == MediaType.VIDEO) "mp4" else "jpg"}"
        val tempFile = File(context.cacheDir, fileName)

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file from URI", e)
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        return null
    }

    private fun observeViewModel() {
        viewModel.getUploadResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    // Already in SCANNING from startUpload()
                }
                Status.SUCCESS -> {
                    val uploadedUrl =
                            resource.data?.get("uploadedUrl")?.asString
                                    ?: run {
                                        updateStatus("Upload succeeded but no URL received", true)
                                        setButtonState(ScanButtonState.FAILED)
                                        return@observe
                                    }
                    updateStatus("Verifying media...", false)
                    // Button stays SCANNING during verification
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
        }

        viewModel.getVerifyResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    // Keep SCANNING
                }
                Status.SUCCESS -> {
                    val response =
                            Gson().fromJson(
                                            resource.data.toString(),
                                            VerificationResponse::class.java
                                    )

                    binding.layoutInfoStatus.visibility = View.VISIBLE
                    binding.textStatusMessage.visibility = View.VISIBLE
                    binding.imageOverlay.visibility = View.VISIBLE

                    if (response.error != null) {
                        updateStatus("Verification error: ${response.error}", true)
                        binding.textStatusMessage.text = response.bandDescription ?: ""
                        binding.txtIdentifixation.text = response.error
                        setButtonState(ScanButtonState.FAILED)
                        return@observe
                    }

                    binding.textStatusMessage.text = response.bandDescription ?: ""
                    binding.txtIdentifixation.text = getBandResult(response.band)

                    when (response.band) {
                        1, 2 -> {
                            binding.imageOverlay.setImageDrawable(
                                    ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable
                                                    .verifylabs_tick_icon_light_grey_rgb_2__traced___1_
                                    )
                            )
                            binding.txtIdentifixation.background =
                                    ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable.drawable_verify_background_green
                                    )
                            binding.imgIdentification.setImageDrawable(
                                    ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable
                                                    .verifylabs_smile_icon_light_grey_rgb_1__traced_
                                    )
                            )
                        }
                        3 -> {
                            binding.imageOverlay.setImageDrawable(
                                    ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable.ic_gray_area
                                    )
                            )
                            binding.txtIdentifixation.background =
                                    ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable
                                                    .drawable_verify_background_btn_failed_likely_gray
                                    )
                            binding.imgIdentification.setImageDrawable(
                                    ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable.ic_question_circle
                                    )
                            )
                        }
                        4, 5 -> {
                            binding.imageOverlay.setImageDrawable(
                                    ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable.ic_red_cross_tranparent
                                    )
                            )
                            binding.txtIdentifixation.background =
                                    ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable
                                                    .drawable_verify_background_btn_failed_likely_red_without_radius
                                    )
                            binding.imgIdentification.setImageDrawable(
                                    ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable
                                                    .verifylabs_robot_icon_light_grey_rgb_1__traced_
                                    )
                            )
                        }
                    }

                    setButtonState(ScanButtonState.DONE)

                    // Save verification result to database
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val entity =
                                    VerificationEntity(
                                            mediaType =
                                                    if (mediaType == MediaType.IMAGE) "Image"
                                                    else "Video",
                                            mediaUri = selectedMediaUri?.toString(),
                                            mediaThumbnail =
                                                    if (mediaType == MediaType.IMAGE)
                                                            selectedMediaUri?.toString()
                                                    else null,
                                            band = response.band,
                                            bandName = response.bandName,
                                            bandDescription = response.bandDescription,
                                            aiScore = response.score,
                                            fileSizeKb =
                                                    selectedMediaUri?.let { getFileSizeKb(it) },
                                            resolution =
                                                    when (mediaType) {
                                                        MediaType.IMAGE ->
                                                                selectedMediaUri?.let {
                                                                    getImageResolution(it)
                                                                }
                                                        MediaType.VIDEO ->
                                                                selectedMediaUri?.let {
                                                                    getVideoResolution(it)
                                                                }
                                                        else -> null
                                                    },
                                            quality = currentQualityPercent,
                                            timestamp = System.currentTimeMillis(),
                                            username = preferenceHelper.getUserName() ?: ""
                                    )
                            verificationRepository.saveVerification(entity)
                            Log.d(TAG, "Verification saved to database with ID: ${entity.id}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save verification to database", e)
                        }
                    }
                }
                Status.ERROR -> {
                    Toast.makeText(
                                    requireContext(),
                                    resource.message ?: "Verification failed",
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                    updateStatus("Verification failed: ${resource.message}", true)
                    setButtonState(ScanButtonState.FAILED)
                }
            }
        }
    }

    private fun setButtonState(state: ScanButtonState) {
        buttonState = state
        binding.btnAction.visibility = View.VISIBLE
        binding.btnAction.text =
                when (state) {
                    ScanButtonState.VERIFY -> "Verify Media"
                    ScanButtonState.SCANNING -> "Scanning..."
                    ScanButtonState.DONE -> "Done!"
                    ScanButtonState.FAILED -> "FAILED!"
                }
        initChangeBtnColor()
    }

    private fun updateStatus(message: String, isError: Boolean) {
        binding.textStatusMessage.text = message
        binding.textStatusMessage.setTextColor(
                if (isError) Color.RED
                else ContextCompat.getColor(requireContext(), R.color.colorBlack)
        )
    }

    private fun resetUI() {
        binding.imageViewMedia2.visibility = View.VISIBLE
        binding.imageViewMedia.visibility = View.GONE
        binding.videoViewMedia.visibility = View.GONE
        binding.imageOverlay.visibility = View.GONE
        binding.layoutInfoStatus.visibility = View.GONE
        binding.statsLayout.visibility = View.GONE
        updateStatus("", false)
        selectedMediaUri = null

        // Reset button to initial state
        setButtonState(ScanButtonState.VERIFY)
    }

    private fun clearSavedMedia() {
        preferenceHelper.getSelectedMediaPath()?.let { path ->
            File(path).takeIf { it.exists() }?.delete()
        }
        preferenceHelper.setSelectedMediaPath(null)
        preferenceHelper.setSelectedMediaType(null)
    }

    private fun showCropBottomSheet(uri: Uri) {
        val bottomSheet = CropBottomSheetFragment.newInstance(uri)
        bottomSheet.onImageResult = { resultUri, quality ->
            selectedMediaUri = resultUri
            binding.btnAction.visibility = View.VISIBLE
            binding.imageViewMedia2.visibility = View.GONE
            binding.imageViewMedia.visibility = View.VISIBLE
            binding.videoViewMedia.visibility = View.GONE
            binding.imageOverlay.visibility = View.GONE

            try {
                val bitmap =
                        android.provider.MediaStore.Images.Media.getBitmap(
                                requireContext().contentResolver,
                                resultUri
                        )
                binding.imageViewMedia.setImageBitmap(bitmap)
            } catch (e: Exception) {
                binding.imageViewMedia.setImageURI(resultUri)
            }

            updateMediaStatsForCurrentMedia(quality)
        }
        bottomSheet.show(parentFragmentManager, "CropBottomSheet")
    }

    // ────────────────────────────────────────────────
    //              STATS UPDATE LOGIC (unchanged)
    // ────────────────────────────────────────────────

    private fun updateMediaStatsForCurrentMedia(forcedQuality: Int? = null) {
        val uri = selectedMediaUri ?: return

        val sizeKb = getFileSizeKb(uri)
        val resolution =
                when (mediaType) {
                    MediaType.IMAGE -> getImageResolution(uri)
                    MediaType.VIDEO -> getVideoResolution(uri) ?: "?"
                    else -> "?"
                }

        updateMediaStats(
                fileSizeKb = sizeKb,
                resolution = resolution,
                mediaTypeStr = if (mediaType == MediaType.VIDEO) "VIDEO" else "IMAGE",
                qualityPercent = forcedQuality
        )
    }

    private fun updateMediaStats(
            fileSizeKb: Long,
            resolution: String,
            mediaTypeStr: String,
            qualityPercent: Int? = null
    ) {
        binding.apply {
            tvSizeValue.text = fileSizeKb.toString()
            tvResolutionValue.text = resolution

            tvTypeValue?.text = mediaTypeStr

            qualityPercent?.let { percent ->
                qualityProgressBar.progress = percent
                tvQualityValue.text = percent.toString()

                val tintColor =
                        when {
                            percent >= 85 -> Color.parseColor("#4CAF50")
                            percent >= 65 -> Color.parseColor("#FF9800")
                            else -> Color.RED
                        }
                qualityProgressBar.progressTintList =
                        android.content.res.ColorStateList.valueOf(tintColor)
            }

            statsLayout.visibility = View.VISIBLE
        }
    }

    private fun getImageResolution(uri: Uri): String {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input, null, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read image dimensions", e)
            return "?"
        }
        return if (options.outWidth > 0 && options.outHeight > 0) {
            "${options.outWidth}×${options.outHeight}"
        } else "?"
    }

    private fun getVideoResolution(uri: Uri): String? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(requireContext(), uri)
            val width =
                    retriever
                            .extractMetadata(
                                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                            )
                            ?.toIntOrNull()
                            ?: 0
            val height =
                    retriever
                            .extractMetadata(
                                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                            )
                            ?.toIntOrNull()
                            ?: 0
            if (width > 0 && height > 0) "${width}×${height}" else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read video metadata", e)
            null
        } finally {
            retriever.release()
        }
    }

    private fun getFileSizeKb(uri: Uri): Long {
        var sizeKb = 0L
        val scheme = uri.scheme

        if (scheme == android.content.ContentResolver.SCHEME_FILE) {
            val file = File(uri.path ?: return 0L)
            if (file.exists()) {
                sizeKb = file.length() / 1024
            }
        } else {
            try {
                requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst() && sizeIndex != -1) {
                        sizeKb = cursor.getLong(sizeIndex) / 1024
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get file size", e)
            }
        }
        return sizeKb
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

    override fun onDestroyView() {
        super.onDestroyView()
        binding.videoViewMedia.stopPlayback()
        timestampHandler?.removeCallbacksAndMessages(null)
        timestampHandler = null
        timestampRunnable = null
        if (isMonitoringActive) internetHelper.stopMonitoring()
        _binding = null
    }
}
