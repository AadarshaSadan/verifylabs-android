package com.verifylabs.ai.presentation.media

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
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
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.MediaQualityAnalyzer
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.data.database.VerificationEntity
import com.verifylabs.ai.data.network.InternetHelper
import com.verifylabs.ai.data.repository.VerificationRepository
import com.verifylabs.ai.databinding.FragmentMediaBinding
import com.verifylabs.ai.presentation.MainActivity
import com.verifylabs.ai.presentation.viewmodel.MediaViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
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
    private var currentQualityScore: com.verifylabs.ai.core.util.QualityScore? = null

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

        viewModel =
                ViewModelProvider(
                                requireActivity().viewModelStore,
                                requireActivity().defaultViewModelProviderFactory,
                                requireActivity().defaultViewModelCreationExtras
                        )
                        .get("MediaScope", MediaViewModel::class.java)
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

        binding.cardQuality.setOnClickListener {
            currentQualityScore?.let { score -> showQualityTipsDialog(score.percentage) }
        }

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

        binding.btnCrop.setOnClickListener {
            selectedMediaUri?.let { uri -> showCropBottomSheet(uri) }
        }

        binding.btnReport.setOnClickListener {
            val reportDialog = ReportResultDialogFragment.newInstance()
            reportDialog.onReportSelected = { reportType ->
                selectedMediaUri?.let { uri ->
                    // Resolve URI to a local file path for reporting
                    val file = getFileFromUri(requireContext(), uri)
                    if (file != null && file.exists()) {
                        viewModel.reportResult(reportType, file.absolutePath)
                    } else {
                        Toast.makeText(requireContext(), "Error accessing media for report", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            reportDialog.show(childFragmentManager, ReportResultDialogFragment.TAG)
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
        binding.llCreditsInfo.root.setOnClickListener {
            val username = preferenceHelper.getUserName()
            val apiKey = preferenceHelper.getApiKey()
            if (!username.isNullOrEmpty() && !apiKey.isNullOrEmpty()) {
                binding.llCreditsInfo.progressCredits.visibility = View.VISIBLE
                binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
                binding.llCreditsInfo.tvCreditsRemaining.text = "Loading..."
                viewModel.checkCredits(username, apiKey)
            }
        }

        observeViewModel()
        observeCredits()
    }

    override fun onResume() {
        super.onResume()
        updateSystemUI()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            updateSystemUI()
        }
    }

    private fun updateSystemUI() {
        (activity as? MainActivity)?.updateStatusBarColor(R.color.app_background)
        // Restore standard elevation
        (activity as? MainActivity)?.updateBottomNavColor(R.color.app_background_3, 8f)
        (activity as? MainActivity)?.updateAppBarColor(R.color.app_background)
        (activity as? MainActivity)?.updateMainBackgroundColor(R.color.app_background)
    }

    private fun loadLocalCredits() {
        val totalCredits = preferenceHelper.getCreditRemaining() ?: 0
        val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(totalCredits)
        binding.llCreditsInfo.tvCreditsRemaining.text =
                getString(R.string.credits_remaining, formattedCredits)
        binding.llCreditsInfo.progressCredits.visibility = View.GONE
        binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE

        binding.btnAction.isEnabled = totalCredits > 0
        binding.btnAction.alpha = if (totalCredits > 0) 1f else 0.5f
    }

    private fun observeCredits() {
        viewModel.getCreditsResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.SUCCESS -> {
                    binding.llCreditsInfo.progressCredits.visibility = View.GONE
                    binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE

                    val creditsJson = resource.data
                    val credits = creditsJson?.get("credits")?.asInt ?: 0
                    val monthlyCredits = creditsJson?.get("credits_monthly")?.asInt ?: 0
                    val totalCredits = credits + monthlyCredits
                    preferenceHelper.setCreditReamaining(totalCredits)

                    val formattedCredits =
                            NumberFormat.getNumberInstance(Locale.US).format(totalCredits)
                    binding.llCreditsInfo.tvCreditsRemaining.text =
                            getString(R.string.credits_remaining, formattedCredits)

                    binding.btnAction.isEnabled = totalCredits > 0
                    binding.btnAction.alpha = if (totalCredits > 0) 1f else 0.5f
                }
                Status.ERROR -> {
                    binding.llCreditsInfo.progressCredits.visibility = View.GONE
                    binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
                    Toast.makeText(context, "Failed to refresh credits", Toast.LENGTH_SHORT).show()

                    binding.btnAction.isEnabled = false
                    binding.btnAction.alpha = 0.5f
                }
                Status.LOADING -> {
                    binding.llCreditsInfo.progressCredits.visibility = View.VISIBLE
                    binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
                    binding.llCreditsInfo.tvCreditsRemaining.text = "Loading..."
                }
                Status.INSUFFICIENT_CREDITS -> {
                    binding.llCreditsInfo.progressCredits.visibility = View.GONE
                    binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
                    // No specific UI for this in credits box, just show text or ignore
                }
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
                    // Silent autoplay for preview
                    mp.setVolume(0f, 0f)
                    binding.videoViewMedia.start()
                    // Force re-layout to respect aspect ratio with constraints
                    binding.videoViewMedia.requestLayout()
                }
            }
            MediaType.AUDIO -> {
                /* TODO if needed */
            }
        }
        binding.resultOverlay.visibility = View.GONE
        // Visibility managed by setButtonState
    }

    private fun startUpload() {
        // Initial UI State for processing
        binding.statsLayout.visibility = View.GONE
        setButtonState(ScanButtonState.SCANNING)

        // Show iOS-style status
        updateStatusView(ScanButtonState.SCANNING)

        viewLifecycleOwner.lifecycleScope.launch {
            // 1. Check Internet (Ping)
            val isConnected = internetHelper.isInternetAvailable()
            if (!isConnected) {
                updateStatusView(ScanButtonState.FAILED)
                setButtonState(ScanButtonState.FAILED) // Shows Retry
                return@launch
            }

            // 2. Local Credit Check (Optional, but keeping flow)
            val totalCredits = preferenceHelper.getCreditRemaining() ?: 0
            // if (totalCredits <= 0) ... handled elsewhere or rely on backend

            // 3. Prepare File
            val uri =
                    selectedMediaUri
                            ?: run {
                                updateStatusView(ScanButtonState.FAILED, "No media selected")
                                setButtonState(ScanButtonState.FAILED)
                                return@launch
                            }

            val file =
                    getFileFromUri(requireContext(), uri)
                            ?: run {
                                updateStatusView(ScanButtonState.FAILED, "Cannot prepare file")
                                setButtonState(ScanButtonState.FAILED)
                                return@launch
                            }

            if (file.length() > 100 * 1024 * 1024) {
                updateStatusView(ScanButtonState.FAILED, "File too large (max 100MB)")
                setButtonState(ScanButtonState.FAILED)
                return@launch
            }

            // 4. Start Upload
            viewModel.uploadMedia(file.absolutePath, mediaType)
        }
    }

    private fun updateStatusView(state: ScanButtonState, errorMessage: String? = null) {
        binding.layoutInfoStatus.visibility = View.VISIBLE
        binding.textStatusMessage.visibility = View.VISIBLE
        binding.txtIdentifixation.visibility = View.GONE
        binding.imgIdentification.visibility = View.GONE
        binding.btnReport.visibility = View.GONE

        // Default Background & Text Color
        binding.textStatusMessage.background = null

        when (state) {
            ScanButtonState.SCANNING -> {
                binding.layoutInfoStatus.background =
                        ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.drawable_verify_background_blue_light
                        )
                binding.textStatusMessage.text = "Please wait while we analyze your media"
                binding.textStatusMessage.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.secondary_text)
                )
            }
            ScanButtonState.FAILED -> {
                binding.layoutInfoStatus.background =
                        ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.drawable_verify_background_red_light
                        )
                // Use custom error if provided, else default to generic
                binding.textStatusMessage.text =
                        errorMessage ?: "An error occurred during verification"
                binding.textStatusMessage.setTextColor(Color.RED)
            }
            // For DONE, we usually have specific band logic, but if a generic "DONE" is called:
            ScanButtonState.DONE -> {
                // logic handled in verify success usually
            }
            ScanButtonState.VERIFY -> {
                binding.layoutInfoStatus.visibility = View.GONE
            }
        }
    }

    // Helper to handle INSUFFICIENT_CREDITS specifically if needed as a separate visual state
    private fun showInsufficientCreditsStatus() {
        binding.layoutInfoStatus.visibility = View.VISIBLE
        binding.statsLayout.visibility = View.GONE

        binding.layoutInfoStatus.background =
                ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.drawable_verify_background_orange // or red light if preferred
                )
        binding.textStatusMessage.text = "Please purchase more credits to continue"
        binding.textStatusMessage.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.vl_red) // or orange
        )

        binding.txtIdentifixation.visibility = View.GONE
        binding.imgIdentification.visibility = View.GONE
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        val extension = if (mediaType == MediaType.VIDEO) "mp4" else "jpg"
        val tempFile =
                File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}.$extension")

        return try {
            // Use ContentResolver to handle all URI schemes (content://, file://)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file from URI: $uri", e)
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
                                        updateStatusView(
                                                ScanButtonState.FAILED,
                                                "Upload succeeded but no URL received"
                                        )
                                        setButtonState(ScanButtonState.FAILED)
                                        return@observe
                                    }
                    updateStatusView(ScanButtonState.SCANNING)
                    // Button stays SCANNING during verification

                    // Check if verification is already in progress or completed to avoid
                    // re-triggering
                    val currentVerifyState = viewModel.verifyResponseFlow.value?.status
                    if (currentVerifyState != Status.LOADING && currentVerifyState != Status.SUCCESS
                    ) {
                        viewModel.verifyMedia(
                                username = preferenceHelper.getUserName().toString(),
                                apiKey = preferenceHelper.getApiKey().toString(),
                                mediaType = mediaType.value,
                                mediaUrl = uploadedUrl
                        )
                    }
                }
                Status.ERROR -> {
                    updateStatusView(ScanButtonState.FAILED, resource.message)
                    setButtonState(ScanButtonState.FAILED)
                }
                Status.INSUFFICIENT_CREDITS -> {
                    showInsufficientCreditsStatus()
                    setButtonState(ScanButtonState.FAILED)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.verifyResponseFlow.collect { resource ->
                resource?.let { res ->
                    when (res.status) {
                        Status.LOADING -> {
                            binding.statsLayout.visibility = View.GONE
                            // Ensure scanning state visual if coming specifically from verify step
                            // updateStatusView(ScanButtonState.SCANNING)
                        }
                        Status.SUCCESS -> {
                            val response =
                                    Gson().fromJson(
                                                    res.data.toString(),
                                                    VerificationResponse::class.java
                                            )

                            binding.layoutInfoStatus.visibility = View.VISIBLE
                            binding.textStatusMessage.visibility = View.VISIBLE
                            binding.resultOverlay.visibility = View.VISIBLE
                            binding.statsLayout.visibility = View.GONE

                            if (response.error != null) {
                                updateStatusView(ScanButtonState.FAILED, response.error)
                                setButtonState(ScanButtonState.FAILED)
                            } else {
                                // Band logic handles the "DONE" state visualization
                                // which effectively shows "Analysis results are displayed above"
                                // by showing the result description instead (as per iOS logic)
                                binding.textStatusMessage.text = getBandDescription(response.band)
                                binding.txtIdentifixation.text = getBandResult(response.band)

                                // Success result logic matching iOS HistoryDetailView color scheme
                                binding.textStatusMessage.background = null
                                binding.txtIdentifixation.visibility = View.VISIBLE
                                binding.imgIdentification.visibility = View.VISIBLE

                                when (response.band) {
                                    1 -> { // Human (VLGreen Card + Capsule)
                                        binding.layoutInfoStatus.background =
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.bg_result_card_human
                                                )

                                        binding.txtIdentifixation.background =
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.bg_result_capsule_green
                                                )
                                        binding.txtIdentifixation.setTextColor(Color.WHITE)
                                        binding.textStatusMessage.setTextColor(
                                                Color.parseColor("#2E7D32")
                                        ) // Dark green

                                        binding.imgIdentification.background = null
                                        binding.imgIdentification.setImageDrawable(
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.verifylabs_smile_icon_green_white
                                                )
                                        )
                                        binding.imgIdentification.imageTintList = null
                                    }
                                    2 -> { // Likely Human (System Green Card + Green Capsule)
                                        binding.layoutInfoStatus.background =
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.bg_result_card_likely_human
                                                )

                                        binding.txtIdentifixation.background =
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.bg_result_capsule_green
                                                )
                                        binding.txtIdentifixation.setTextColor(Color.WHITE)
                                        binding.textStatusMessage.setTextColor(
                                                Color.parseColor("#2E7D32")
                                        ) // Dark green

                                        binding.imgIdentification.background = null
                                        binding.imgIdentification.setImageDrawable(
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.verifylabs_smile_icon_green_white
                                                )
                                        )
                                        binding.imgIdentification.imageTintList = null
                                    }
                                    3 -> { // Unsure (Gray Card + Gray Capsule)
                                        binding.layoutInfoStatus.background =
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.bg_result_card_unsure
                                                )

                                        binding.txtIdentifixation.background =
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.bg_result_capsule_gray
                                                )
                                        binding.txtIdentifixation.setTextColor(Color.WHITE)
                                        binding.textStatusMessage.setTextColor(
                                                Color.parseColor("#616161")
                                        ) // Dark gray

                                        binding.imgIdentification.background = null
                                        binding.imgIdentification.setImageDrawable(
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.ic_question_circle
                                                )
                                        )
                                        binding.imgIdentification.imageTintList =
                                                ColorStateList.valueOf(Color.GRAY)
                                    }
                                    4 -> { // Likely AI (System Red Card + Red Rect)
                                        //
                                        // binding.layoutInfoStatus.background =
                                        //
                                        // ContextCompat.getDrawable(
                                        //
                                        // requireContext(),
                                        //
                                        // R.drawable.bg_result_card_likely_ai
                                        //                                            )

                                        binding.layoutInfoStatus.background =
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.bg_result_card_likely_human
                                                )

                                        binding.txtIdentifixation.background =
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.bg_result_rect_red
                                                )
                                        binding.txtIdentifixation.setTextColor(Color.WHITE)
                                        binding.textStatusMessage.setTextColor(
                                                Color.parseColor("#C62828")
                                        ) // Dark red

                                        binding.imgIdentification.background = null
                                        binding.imgIdentification.setImageDrawable(
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.verifylabs_robot_icon_red_white
                                                )
                                        )
                                        //
                                        // binding.imgIdentification.imageTintList =
                                        //
                                        // ColorStateList.valueOf(
                                        //
                                        // ContextCompat.getColor(
                                        //
                                        // requireContext(),
                                        //
                                        // R.color.vl_red
                                        //                                                    )
                                        //                                            )

                                        binding.imgIdentification.imageTintList = null
                                    }
                                    5 -> { // AI (VLRed Card + Red Rect)
                                        binding.layoutInfoStatus.background =
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.bg_result_card_likely_human
                                                )

                                        binding.txtIdentifixation.background =
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.bg_result_rect_red
                                                )
                                        binding.txtIdentifixation.setTextColor(Color.WHITE)
                                        binding.textStatusMessage.setTextColor(
                                                Color.parseColor("#C62828")
                                        ) // Dark red

                                        binding.imgIdentification.background = null
                                        binding.imgIdentification.setImageDrawable(
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.verifylabs_robot_icon_red_white
                                                )
                                        )
                                        //
                                        // binding.imgIdentification.imageTintList =
                                        //
                                        // ColorStateList.valueOf(
                                        //
                                        // ContextCompat.getColor(
                                        //
                                        // requireContext(),
                                        //
                                        // R.color.vl_red
                                        //                                                    )
                                        //                                            )

                                        binding.imgIdentification.imageTintList = null
                                    }
                                }

                                // Overlay Image Logic (Center of Preview)
                                binding.resultOverlay.visibility = View.VISIBLE
                                binding.resultOverlay.alpha =
                                        0.7f // Ensure transparency matches typical overlay
                                when (response.band) {
                                    1, 2 -> { // Human -> Tick
                                        binding.resultOverlay.setImageDrawable(
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable
                                                                .verifylabs_tick_icon_light_grey_rgb_2__traced___1_
                                                )
                                        )
                                        binding.resultOverlay.imageTintList =
                                                null // Use original colors
                                    }
                                    3 -> { // Unsure -> Warning (Gray)
                                        binding.resultOverlay.setImageDrawable(
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.ic_warning
                                                )
                                        )
                                        binding.resultOverlay.imageTintList =
                                                ColorStateList.valueOf(Color.GRAY)
                                    }
                                    4, 5 -> { // AI -> Cross (Red)
                                        binding.resultOverlay.setImageDrawable(
                                                ContextCompat.getDrawable(
                                                        requireContext(),
                                                        R.drawable.ic_red_cross_tranparent
                                                )
                                        )
                                        binding.resultOverlay.imageTintList =
                                                null // Use original colors
                                    }
                                }

                                binding.btnReport.visibility = View.VISIBLE

                                setButtonState(ScanButtonState.DONE)

                                // Save verification result to database
                                viewLifecycleOwner.lifecycleScope.launch {
                                    if (viewModel.isResultHandled) return@launch
                                    viewModel.isResultHandled = true
                                    try {
                                        val entity =
                                                VerificationEntity(
                                                        mediaType =
                                                                if (mediaType == MediaType.IMAGE)
                                                                        "Image"
                                                                else "Video",
                                                        mediaUri =
                                                                selectedMediaUri?.let { uri ->
                                                                    // Save to internal storage for
                                                                    // persistence
                                                                    val savedPath =
                                                                            com.verifylabs.ai.core
                                                                                    .util
                                                                                    .HistoryFileManager
                                                                                    .saveMedia(
                                                                                            requireContext(),
                                                                                            uri,
                                                                                            if (mediaType ==
                                                                                                            MediaType
                                                                                                                    .IMAGE
                                                                                            )
                                                                                                    "image"
                                                                                            else
                                                                                                    "video"
                                                                                    )
                                                                    // Return file URI string if
                                                                    // saved,
                                                                    // else original URI
                                                                    if (savedPath != null)
                                                                            Uri.fromFile(
                                                                                            File(
                                                                                                    savedPath
                                                                                            )
                                                                                    )
                                                                                    .toString()
                                                                    else uri.toString()
                                                                },
                                                        mediaThumbnail =
                                                                if (mediaType == MediaType.IMAGE)
                                                                        selectedMediaUri?.toString()
                                                                else null,
                                                        band = response.band,
                                                        bandName = response.bandName,
                                                        bandDescription = response.bandDescription,
                                                        aiScore = response.score,
                                                        fileSizeKb =
                                                                selectedMediaUri?.let {
                                                                    getFileSizeKb(it)
                                                                },
                                                        resolution =
                                                                when (mediaType) {
                                                                    MediaType.IMAGE ->
                                                                            selectedMediaUri?.let {
                                                                                getImageResolution(
                                                                                        it
                                                                                )
                                                                            }
                                                                    MediaType.VIDEO ->
                                                                            selectedMediaUri?.let {
                                                                                getVideoResolution(
                                                                                        it
                                                                                )
                                                                            }
                                                                    else -> null
                                                                },
                                                        // Save Percentage
                                                        quality = currentQualityScore?.percentage,
                                                        timestamp = System.currentTimeMillis(),
                                                        username = preferenceHelper.getUserName()
                                                                        ?: ""
                                                )
                                        verificationRepository.saveVerification(entity)
                                        Log.d(
                                                TAG,
                                                "Verification saved to database with ID: ${entity.id}"
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to save verification to database", e)
                                    }
                                }

                                // Auto-refresh credits
                                viewModel.checkCredits(
                                        preferenceHelper.getUserName() ?: "",
                                        preferenceHelper.getApiKey() ?: ""
                                )
                            }
                        }
                        Status.ERROR -> {
                            Toast.makeText(
                                            requireContext(),
                                            resource.message ?: "Verification failed",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                            updateStatusView(
                                    ScanButtonState.FAILED,
                                    "Verification failed: ${resource.message}"
                            )
                            setButtonState(ScanButtonState.FAILED)
                        }
                        Status.INSUFFICIENT_CREDITS -> {
                            binding.layoutInfoStatus.visibility = View.VISIBLE
                            binding.textStatusMessage.visibility = View.VISIBLE
                            binding.resultOverlay.visibility = View.VISIBLE
                            binding.statsLayout.visibility = View.GONE

                            // Orange configuration matching iOS
                            binding.layoutInfoStatus.background =
                                    ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable.bg_result_card_orange
                                    )

                            // Fallback if drawable missing (though we just created it):
                            if (ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable.bg_result_card_orange
                                    ) == null
                            ) {
                                binding.layoutInfoStatus.setBackgroundColor(
                                        Color.parseColor("#FFF3E0")
                                )
                            }

                            binding.textStatusMessage.text = "Insufficient Credits"
                            binding.textStatusMessage.setTextColor(
                                    Color.parseColor("#FF9800")
                            ) // Orange

                            binding.txtIdentifixation.visibility = View.VISIBLE
                            binding.txtIdentifixation.text =
                                    getString(R.string.please_purchase_more_credits)
                            binding.txtIdentifixation.setTextColor(Color.GRAY)
                            binding.txtIdentifixation.background = null // Remove capsule background

                            binding.imgIdentification.visibility = View.VISIBLE
                            binding.imgIdentification.setImageDrawable(
                                    ContextCompat.getDrawable(
                                            requireContext(),
                                            R.drawable.ic_credit_card_error
                                    )
                            )
                            binding.imgIdentification.background = null // No square bg
                            binding.imgIdentification.imageTintList =
                                    ColorStateList.valueOf(
                                            Color.parseColor("#FF9800")
                                    ) // Orange Tint

                            binding.btnReport.visibility = View.GONE

                            setButtonState(
                                    ScanButtonState.FAILED
                            ) // To show Retry/Action button? iOS shows "Buy Credits"?
                            // User request: "exact same function design". iOS likely blocks or
                            // offers
                            // buy.
                            // StartButtonState.FAILED shows "Retry" which calls verify again.
                            // Maybe we need a "BUY" button state? For now, FAILED allows retrying.
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.creditConsumedFlow.collect {
                val currentCredits = preferenceHelper.getCreditRemaining()
                val newCredits = (currentCredits - 1).coerceAtLeast(0)
                preferenceHelper.setCreditReamaining(newCredits)
                binding.llCreditsInfo.tvCreditsRemaining.text =
                        getString(R.string.credits_remaining_format, newCredits)
                Log.d(TAG, "Credit consumed. New balance: $newCredits")
            }
        }
    }

    private fun setButtonState(state: ScanButtonState) {
        buttonState = state

        // Button Logic:
        // VERIFY: Visible
        // SCANNING: Hidden (User requirement)
        // DONE: Hidden (User requirement implied, or shows "Test Another" on top button) -> Wait,
        // user says "Select Media button text changes to Test Another". Verify button usually
        // hidden on Done?
        // FAILED: Visible (Shows "Retry")

        // Crop Button Visibility: Only show when image is selected (VERIFY state)
        binding.btnCrop.visibility =
                if (state == ScanButtonState.VERIFY && mediaType == MediaType.IMAGE) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

        when (state) {
            ScanButtonState.VERIFY -> {
                binding.btnAction.visibility = View.VISIBLE
                binding.btnAction.text = getString(R.string.verify_media_button)
            }
            ScanButtonState.SCANNING -> {
                binding.btnAction.visibility = View.GONE
            }
            ScanButtonState.DONE -> {
                binding.btnAction.visibility = View.GONE
                setSelectMediaButtonText(true)
            }
            ScanButtonState.FAILED -> {
                binding.btnAction.visibility = View.VISIBLE
                binding.btnAction.text = getString(R.string.retry_button)
            }
        }
        initChangeBtnColor()
    }

    private fun resetUI() {
        binding.imageViewMedia2.visibility = View.VISIBLE
        binding.imageViewMedia.visibility = View.GONE
        binding.videoViewMedia.visibility = View.GONE
        binding.resultOverlay.visibility = View.GONE
        binding.layoutInfoStatus.visibility = View.GONE
        binding.statsLayout.visibility = View.GONE
        binding.btnCrop.visibility = View.GONE
        binding.btnReport.visibility = View.GONE

        selectedMediaUri = null

        setSelectMediaButtonText(false)

        // Reset button to initial state
        setButtonState(ScanButtonState.VERIFY)
        binding.btnAction.visibility = View.GONE // Initially hidden until media selected
        binding.btnCrop.visibility = View.GONE // Ensure crop is also hidden
    }

    private fun setSelectMediaButtonText(isTestAnother: Boolean) {
        val tv = binding.btnSelectMedia.getChildAt(1) as? android.widget.TextView
        tv?.text =
                getString(if (isTestAnother) R.string.test_another else R.string.select_media_file)

        if (isTestAnother) {
            // Apply blue background from drawables
            binding.btnSelectMedia.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bg_ios_blue_button)
        } else {
            // Revert to original green if resetting
            binding.btnSelectMedia.background =
                    ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.drawable_verify_background_green
                    )
        }
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
        bottomSheet.onImageResult = { resultUri, _ ->
            selectedMediaUri = resultUri

            // Centralized UI update and preview logic
            showPreview(resultUri)
            updateMediaStatsForCurrentMedia()
            setButtonState(ScanButtonState.VERIFY)

            // Reset result-specific views
            binding.layoutInfoStatus.visibility = View.GONE
            binding.btnReport.visibility = View.GONE
        }
        bottomSheet.show(parentFragmentManager, "CropBottomSheet")
    }

    // ────────────────────────────────────────────────
    //              STATS UPDATE LOGIC (unchanged)
    // ────────────────────────────────────────────────

    // ────────────────────────────────────────────────
    //              STATS UPDATE LOGIC
    // ────────────────────────────────────────────────

    private fun updateMediaStatsForCurrentMedia(
            forcedScore: com.verifylabs.ai.core.util.QualityScore? = null
    ) {
        val uri = selectedMediaUri ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val score =
                    forcedScore
                            ?: if (mediaType == MediaType.IMAGE) {
                                MediaQualityAnalyzer.analyzeImage(requireContext(), uri)
                            } else {
                                MediaQualityAnalyzer.analyzeVideo(requireContext(), uri)
                            }

            if (forcedScore == null) currentQualityScore = score

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
                    mediaTypeStr =
                            getString(
                                    if (mediaType == MediaType.VIDEO) R.string.media_type_video
                                    else R.string.media_type_image
                            ),
                    qualityScore = score
            )
        }
    }

    private fun updateMediaStats(
            fileSizeKb: Long,
            resolution: String,
            mediaTypeStr: String,
            qualityScore: com.verifylabs.ai.core.util.QualityScore
    ) {
        binding.apply {
            if (fileSizeKb > 1024) {
                tvSizeValue.text = String.format("%.1f", fileSizeKb / 1024.0)
                tvSizeUnit.text = getString(R.string.unit_mb)
            } else {
                tvSizeValue.text = fileSizeKb.toString()
                tvSizeUnit.text = getString(R.string.unit_kb)
            }
            tvResolutionValue.text = resolution

            tvTypeValue?.text = mediaTypeStr

            val percent = qualityScore.percentage
            qualityProgressBar.progress = percent
            tvQualityValue.text = percent.toString()

            val tintColor = getQualityColor(percent)
            qualityProgressBar.progressTintList =
                    android.content.res.ColorStateList.valueOf(tintColor)
            tvQualityValue.setTextColor(tintColor)

            // Quality Advice shown in Dialog on click now
            // tvQualityAdvice.text =
            // com.verifylabs.ai.core.util.MediaQualityAnalyzer.getQualityImprovementAdvice(percent)
            tvQualityAdvice.visibility = View.GONE

            if (buttonState == ScanButtonState.VERIFY) {
                statsLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun getQualityColor(score: Int): Int {
        val value = score.coerceIn(0, 100).toDouble()

        return when {
            value < 50 -> {
                // Red to Orange (0-50%)
                val ratio = value / 50.0
                android.graphics.Color.rgb(255, (255 * (ratio * 0.5)).toInt(), 0)
            }
            value < 70 -> {
                // Orange to Yellow (50-70%)
                val ratio = (value - 50) / 20.0
                android.graphics.Color.rgb(255, (255 * (0.5 + (ratio * 0.5))).toInt(), 0)
            }
            value < 85 -> {
                // Yellow to Yellow-Green (70-85%)
                val ratio = (value - 70) / 15.0
                android.graphics.Color.rgb((255 * (1.0 - (ratio * 0.5))).toInt(), 255, 0)
            }
            else -> {
                // Yellow-Green to Green (85-100%)
                val ratio = (value - 85) / 15.0
                android.graphics.Color.rgb((255 * (0.5 - (ratio * 0.5))).toInt(), 255, 0)
            }
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
        return getString(
                when (band) {
                    1 -> R.string.band_human_made
                    2 -> R.string.band_likely_human_made
                    3 -> R.string.band_inconclusive
                    4 -> R.string.band_likely_machine_made
                    5 -> R.string.band_machine_made
                    else -> R.string.band_unknown
                }
        )
    }

    private fun getBandDescription(band: Int): String {
        val resId =
                when (band) {
                    1 -> R.string.desc_human_made
                    2 -> R.string.desc_likely_human_made
                    3 -> R.string.desc_inconclusive
                    4 -> R.string.desc_likely_machine_made
                    5 -> R.string.desc_machine_made
                    else -> 0
                }
        return if (resId != 0) getString(resId) else ""
    }

    private fun showQualityTipsDialog(score: Int) {
        val advice = MediaQualityAnalyzer.getQualityImprovementAdvice(score)

        val dialogView =
                LayoutInflater.from(requireContext()).inflate(R.layout.dialog_guidelines, null)
        val dialogBinding = com.verifylabs.ai.databinding.DialogGuidelinesBinding.bind(dialogView)

        // Reuse guidelines layout structure but change content for Tips
        dialogBinding.tvTitle.text = getString(R.string.media_quality_tips_title)
        dialogBinding.tvTitle.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.colorBlack)
        ) // Title Black
        dialogBinding.tvDescription.text = advice

        // Hide Guidelines specific sections
        dialogBinding.tvImagesLabel.visibility = View.GONE
        dialogBinding.tvImagesText.visibility = View.GONE
        dialogBinding.tvVideosLabel.visibility = View.GONE
        dialogBinding.tvVideosText.visibility = View.GONE
        dialogBinding.tvAudioLabel.visibility = View.GONE
        dialogBinding.tvAudioText.visibility = View.GONE

        val dialog =
                androidx.appcompat.app.AlertDialog.Builder(
                                requireContext(),
                                R.style.CustomAlertDialog
                        )
                        .setView(dialogView)
                        .create()

        // Ensure transparent background for custom card shape with margins (Match ReportResult
        // style)
        val back = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        val margin = (24 * resources.displayMetrics.density).toInt()
        val inset = android.graphics.drawable.InsetDrawable(back, margin)
        dialog.window?.setBackgroundDrawable(inset)
        dialog.window?.requestFeature(android.view.Window.FEATURE_NO_TITLE)

        dialogBinding.btnGotIt.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
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
