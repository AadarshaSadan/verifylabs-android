package com.verifylabs.ai.presentation.media

import com.verifylabs.ai.core.util.MediaQualityAnalyzer
import InternetHelper
import android.Manifest
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
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
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.data.database.VerificationEntity
import com.verifylabs.ai.data.repository.VerificationRepository
import com.verifylabs.ai.databinding.FragmentMediaBinding
import com.verifylabs.ai.presentation.viewmodel.MediaViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import java.text.NumberFormat
import java.util.Locale
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

        binding.cardQuality.setOnClickListener {
             currentQualityScore?.let { score ->
                showQualityTipsDialog(score.percentage)
            }
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
             selectedMediaUri?.let { uri ->
                 showCropBottomSheet(uri)
             }
        }

        binding.btnReport.setOnClickListener {
            val reportDialog = ReportResultDialogFragment.newInstance()
            reportDialog.onReportSelected = { reportType ->
                 selectedMediaUri?.let { uri ->
                    val path = if(uri.scheme == "file") uri.path else uri.toString()
                    viewModel.reportResult(reportType, path ?: "")
                 }
            }
            reportDialog.show(childFragmentManager, ReportResultDialogFragment.TAG)
        }

        binding.btnGuidelines.setOnClickListener {
            GuidelinesDialogFragment.newInstance().show(parentFragmentManager, GuidelinesDialogFragment.TAG)
        }
        // Underline the guidelines text
        val guidelinesTv = binding.btnGuidelines.getChildAt(1) as? android.widget.TextView
        guidelinesTv?.paintFlags = guidelinesTv?.paintFlags?.or(android.graphics.Paint.UNDERLINE_TEXT_FLAG) ?: 0
        
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
    
    private fun loadLocalCredits() {
        val totalCredits = preferenceHelper.getCreditRemaining() ?: 0
        val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(totalCredits)
        binding.llCreditsInfo.tvCreditsRemaining.text = getString(R.string.credits_remaining, formattedCredits)
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
                    
                    val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(totalCredits)
                    binding.llCreditsInfo.tvCreditsRemaining.text = getString(R.string.credits_remaining, formattedCredits)
                    
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
                }
            }
            MediaType.AUDIO -> {
                /* TODO if needed */
            }
        }
        binding.imageOverlay.visibility = View.GONE
        binding.btnAction.visibility = View.VISIBLE
        binding.btnCrop.visibility = if (mediaType == MediaType.IMAGE) View.VISIBLE else View.GONE
    }

    private fun startUpload() {
        // Initial UI State for processing
        binding.statsLayout.visibility = View.GONE
        setButtonState(ScanButtonState.SCANNING)
        
        // Show \"Please wait\" message with Blue background (iOS style)
        binding.layoutInfoStatus.visibility = View.VISIBLE
        binding.layoutInfoStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_blue_light)
        
        binding.txtIdentifixation.visibility = View.GONE // Hide specific result views
        binding.imgIdentification.visibility = View.GONE
        binding.btnReport.visibility = View.GONE
        
        binding.textStatusMessage.visibility = View.VISIBLE
        binding.textStatusMessage.text = "Please wait while we analyze your media"
        binding.textStatusMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorBlack))
        // Background is now on layoutInfoStatus (the card)
        binding.textStatusMessage.background = null 

        viewLifecycleOwner.lifecycleScope.launch {
            // 1. Check Internet (Ping)
            val isConnected = internetHelper.isInternetAvailable()
            if (!isConnected) {
                // Manually set background for error on the card
                binding.layoutInfoStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_red_light)
                binding.textStatusMessage.text = "An error occurred during verification"
                binding.textStatusMessage.setTextColor(Color.RED)
                
                binding.statsLayout.visibility = View.GONE
                setButtonState(ScanButtonState.FAILED) // Shows Retry
                return@launch
            }

            // 2. Check Credits (Local + potentially refreshing via VM, but for now blocking on local info or proceeding to upload which handles backend check)
             // User says: "proceeds to check the user’s available credits".
             // We can do a quick check here if needed, or rely on the fact that upload/verify will check backend.
             // Given the UI requirement "While the credit check... is in progress", we flow naturally.
            val totalCredits = preferenceHelper.getCreditRemaining() ?: 0
             /* If we wanted to enforce backend check first:
            if (totalCredits <= 0) {
                 binding.layoutNoCreditStatus.visibility = View.VISIBLE
                 updateStatus("", false)
                 setButtonState(ScanButtonState.VERIFY)
                 return@launch
            }
            */

             // 3. Prepare File
             val uri = selectedMediaUri ?: run {
                updateStatus("No media selected", true)
                setButtonState(ScanButtonState.FAILED)
                return@launch
            }

            val file = getFileFromUri(requireContext(), uri) ?: run {
                 updateStatus("Cannot prepare file", true)
                 setButtonState(ScanButtonState.FAILED)
                 return@launch
            }

            if (file.length() > 100 * 1024 * 1024) {
                 updateStatus("File too large (max 100MB)", true)
                 setButtonState(ScanButtonState.FAILED)
                 return@launch
            }

            // 4. Start Upload
            viewModel.uploadMedia(file.absolutePath, mediaType)
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        val extension = if (mediaType == MediaType.VIDEO) "mp4" else "jpg"
        val tempFile = File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}.$extension")

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
                Status.INSUFFICIENT_CREDITS -> {
                    updateStatus("Insufficient credits for upload", true)
                    setButtonState(ScanButtonState.FAILED)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.verifyResponseFlow.collect { resource ->
                when (resource.status) {
                    Status.LOADING -> {
                    // StartUpload handles SCANNING state, but we ensure stats are hidden
                     binding.statsLayout.visibility = View.GONE
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
                        binding.statsLayout.visibility = View.GONE

                        if (response.error != null) {
                            binding.layoutInfoStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_red_light)
                            binding.textStatusMessage.text = response.error
                            binding.textStatusMessage.setTextColor(Color.RED)
                            binding.txtIdentifixation.visibility = View.GONE
                            binding.imgIdentification.visibility = View.GONE
                            setButtonState(ScanButtonState.FAILED)
                        } else {
                            binding.textStatusMessage.text = getBandDescription(response.band)
                            binding.txtIdentifixation.text = getBandResult(response.band)

                            // Success result logic matching iOS Parity
                            // ALL successful bands (1-5) use the light green card background per iOS screenshot
                            binding.layoutInfoStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_result_card_green)
                            binding.textStatusMessage.background = null
                            binding.txtIdentifixation.visibility = View.VISIBLE
                            binding.imgIdentification.visibility = View.VISIBLE
                            
                            when (response.band) {
                                1, 2 -> { // Human (Capsule, Green Text, Traced Green Smile)
                                    binding.txtIdentifixation.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_result_capsule_green)
                                    binding.textStatusMessage.setTextColor(Color.parseColor("#2E7D32")) // Dark green
                                    binding.imgIdentification.background = null
                                    binding.imgIdentification.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.verifylabs_smile_icon_light_grey_rgb_1__traced_))
                                    binding.imgIdentification.imageTintList = null // Use original green from drawable
                                }
                                3 -> { // Unsure (Capsule, Gray Text)
                                    binding.txtIdentifixation.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_result_capsule_gray)
                                    binding.textStatusMessage.setTextColor(Color.parseColor("#616161")) // Dark gray
                                    binding.imgIdentification.background = null
                                    binding.imgIdentification.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_question_circle))
                                    binding.imgIdentification.imageTintList = ColorStateList.valueOf(Color.GRAY)
                                }
                                4, 5 -> { // AI (Rectangle, Red Text, Red Square Icon with White Robot)
                                    binding.txtIdentifixation.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_result_rect_red)
                                    binding.textStatusMessage.setTextColor(Color.parseColor("#C62828")) // Dark red
                                    binding.imgIdentification.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_result_square_red)
                                    binding.imgIdentification.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.verifylabs_robot_icon_light_grey_rgb_1__traced_))
                                    binding.imgIdentification.imageTintList = ColorStateList.valueOf(Color.WHITE) // Tint white on red background
                                }
                            }
                            binding.btnReport.visibility = View.VISIBLE

                            setButtonState(ScanButtonState.DONE)

                        // Save verification result to database
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    val entity = VerificationEntity(
                                                    mediaType =
                                                            if (mediaType == MediaType.IMAGE) "Image"
                                                            else "Video",
                                                    mediaUri = selectedMediaUri?.let { uri ->
                                                        // Save to internal storage for persistence
                                                        val savedPath = com.verifylabs.ai.core.util.HistoryFileManager.saveMedia(
                                                            requireContext(), 
                                                            uri, 
                                                            if (mediaType == MediaType.IMAGE) "image" else "video"
                                                        )
                                                        // Return file URI string if saved, else original URI
                                                        if (savedPath != null) Uri.fromFile(File(savedPath)).toString()
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
                                                    // Save Percentage
                                                    quality = currentQualityScore?.percentage,
                                                    timestamp = System.currentTimeMillis(),
                                                    username = preferenceHelper.getUserName() ?: ""
                                            )
                                    verificationRepository.saveVerification(entity)
                                    Log.d(TAG, "Verification saved to database with ID: ${entity.id}")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to save verification to database", e)
                                }
                            }

                            // Auto-refresh credits
                            viewModel.checkCredits(preferenceHelper.getUserName() ?: "", preferenceHelper.getApiKey() ?: "")
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
                    Status.INSUFFICIENT_CREDITS -> {
                         binding.layoutInfoStatus.visibility = View.VISIBLE
                         binding.textStatusMessage.visibility = View.VISIBLE
                         binding.imageOverlay.visibility = View.VISIBLE
                         binding.statsLayout.visibility = View.GONE
                         
                         // Orange configuration matching iOS
                         binding.layoutInfoStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_result_card_orange) 
                         
                         // Fallback if drawable missing (though we just created it):
                         if (ContextCompat.getDrawable(requireContext(), R.drawable.bg_result_card_orange) == null) {
                              binding.layoutInfoStatus.setBackgroundColor(Color.parseColor("#FFF3E0"))
                         }
                         
                         binding.textStatusMessage.text = "Insufficient Credits"
                         binding.textStatusMessage.setTextColor(Color.parseColor("#FF9800")) // Orange
                         
                         binding.txtIdentifixation.visibility = View.VISIBLE
                         binding.txtIdentifixation.text = "Please purchase more credits to continue"
                         binding.txtIdentifixation.setTextColor(Color.GRAY)
                         binding.txtIdentifixation.background = null // Remove capsule background
                         
                         binding.imgIdentification.visibility = View.VISIBLE
                         binding.imgIdentification.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_credit_card_error))
                         binding.imgIdentification.background = null // No square bg
                         binding.imgIdentification.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF9800")) // Orange Tint
                         

                         binding.btnReport.visibility = View.GONE
                         
                         setButtonState(ScanButtonState.FAILED) // To show Retry/Action button? iOS shows "Buy Credits"?
                         // User request: "exact same function design". iOS likely blocks or offers buy.
                         // StartButtonState.FAILED shows "Retry" which calls verify again.
                         // Maybe we need a "BUY" button state? For now, FAILED allows retrying.
                    }
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.creditConsumedFlow.collect {
                val currentCredits = preferenceHelper.getCreditRemaining()
                val newCredits = (currentCredits - 1).coerceAtLeast(0)
                preferenceHelper.setCreditReamaining(newCredits)
                binding.llCreditsInfo.tvCreditsRemaining.text = "Credits remaining: $newCredits"
                Log.d(TAG, "Credit consumed. New balance: $newCredits")
            }
        }
        

    }

    private fun setButtonState(state: ScanButtonState) {
        buttonState = state
        
        // Button Logic:
        // VERIFY: Visible
        // SCANNING: Hidden (User requirement)
        // DONE: Hidden (User requirement implied, or shows "Test Another" on top button) -> Wait, user says "Select Media button text changes to Test Another". Verify button usually hidden on Done?
        // FAILED: Visible (Shows "Retry")
        
        when (state) {
            ScanButtonState.VERIFY -> {
                binding.btnAction.visibility = View.VISIBLE
                binding.btnAction.text = "✔ Verify Media"
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
                binding.btnAction.text = "Retry"
            }
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
        binding.btnCrop.visibility = View.GONE
        binding.btnReport.visibility = View.GONE
        updateStatus("", false)
        selectedMediaUri = null
        
        setSelectMediaButtonText(false)

        // Reset button to initial state
        setButtonState(ScanButtonState.VERIFY)
        binding.btnAction.visibility = View.GONE // Initially hidden until media selected
    }
    
    private fun setSelectMediaButtonText(isTestAnother: Boolean) {
        val tv = binding.btnSelectMedia.getChildAt(1) as? android.widget.TextView
        tv?.text = if (isTestAnother) "Test another?" else "Select Media File"
        
        if (isTestAnother) {
            // Apply blue background from drawables
            binding.btnSelectMedia.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_ios_blue_button)
        } else {
            // Revert to original green if resetting
            binding.btnSelectMedia.background = ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_green)
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

    private fun updateMediaStatsForCurrentMedia(forcedScore: com.verifylabs.ai.core.util.QualityScore? = null) {
        val uri = selectedMediaUri ?: return

        viewLifecycleOwner.lifecycleScope.launch {
             val score = forcedScore ?: if (mediaType == MediaType.IMAGE) {
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
                    mediaTypeStr = if (mediaType == MediaType.VIDEO) "VIDEO" else "IMAGE",
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
            tvSizeValue.text = if (fileSizeKb > 1024) "${String.format("%.1f", fileSizeKb / 1024.0)} MB" else "$fileSizeKb KB"
            tvResolutionValue.text = resolution

            tvTypeValue?.text = mediaTypeStr

            val percent = qualityScore.percentage
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

            // Quality Advice shown in Dialog on click now
            // tvQualityAdvice.text = com.verifylabs.ai.core.util.MediaQualityAnalyzer.getQualityImprovementAdvice(percent)
            tvQualityAdvice.visibility = View.GONE

            if (buttonState == ScanButtonState.VERIFY) {
                statsLayout.visibility = View.VISIBLE
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
        return when (band) {
            1 -> "Human made"
            2 -> "Likely human made"
            3 -> "Result inconclusive"
            4 -> "Likely machine made"
            5 -> "Machine made"
            else -> "Unknown"
        }
    }

    private fun getBandDescription(band: Int): String {
        return when (band) {
            1 -> "There’s a high probability that this was created by a human and has not been altered by AI."
            2 -> "This was likely created by a human, but may have been improved by photo apps or a phone's automated software."
            3 -> "This result can't be determined due to quality or testing suitability. This could be because it is partly machine-made, low resolution or too dark. Check FAQs on VerifyLabs.AI for more information."
            4 -> "This was likely created by a machine. Partly AI-generated content or deepfakes can often give these results."
            5 -> "There’s a high probability that this is deepfake or AI-generated."
            else -> ""
        }
    }

    private fun showQualityTipsDialog(score: Int) {
        val advice = MediaQualityAnalyzer.getQualityImprovementAdvice(score)
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_guidelines, null)
        val dialogBinding = com.verifylabs.ai.databinding.DialogGuidelinesBinding.bind(dialogView)

        // Reuse guidelines layout structure but change content for Tips
        dialogBinding.tvTitle.text = "Media Quality Tips"
        dialogBinding.tvTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorBlack)) // Title Black
        dialogBinding.tvDescription.text = advice
        
        // Hide Guidelines specific sections
        dialogBinding.tvImagesLabel.visibility = View.GONE
        dialogBinding.tvImagesText.visibility = View.GONE
        dialogBinding.tvVideosLabel.visibility = View.GONE
        dialogBinding.tvVideosText.visibility = View.GONE
        dialogBinding.tvAudioLabel.visibility = View.GONE
        dialogBinding.tvAudioText.visibility = View.GONE
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        // Ensure transparent background for custom card shape with margins (Match ReportResult style)
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
