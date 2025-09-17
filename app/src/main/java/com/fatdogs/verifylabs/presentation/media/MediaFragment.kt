package com.fatdogs.verifylabs.presentation.media

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.fatdogs.verifylabs.R
import com.fatdogs.verifylabs.core.util.Resource
import com.fatdogs.verifylabs.core.util.Status
import com.fatdogs.verifylabs.data.base.PreferenceHelper
import com.fatdogs.verifylabs.databinding.FragmentMediaBinding
import com.fatdogs.verifylabs.presentation.auth.login.apiResponseLogin
import com.fatdogs.verifylabs.presentation.viewmodel.MediaViewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject

enum class ScanButtonState {VERIFY, SCANNING, DONE }

@AndroidEntryPoint
class MediaFragment : Fragment() {

    private val TAG = "MediaFragment"
    private var _binding: FragmentMediaBinding? = null


    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private val binding get() = _binding!!

    private lateinit var viewModel: MediaViewModel
    private var buttonState = ScanButtonState.VERIFY
    private var selectedMediaUri: Uri? = null
    private var mediaType = MediaType.IMAGE

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) selectMedia() else showPermissionDialog()
    }

    private val selectMediaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedMediaUri = it
            binding.imageViewMedia.setImageURI(it)
            binding.imageViewMedia.elevation = 3f
            binding.imageViewMedia.background = null
            binding.imageOverlay.visibility=View.GONE
            binding.imageViewMedia.visibility = View.VISIBLE
            binding.btnAction.visibility = View.VISIBLE
            setButtonState(ScanButtonState.VERIFY)
            updateStatus("", false)

            // Determine media type based on MIME type
            mediaType = when (requireContext().contentResolver.getType(it)?.substringBefore("/")) {
                "video" -> MediaType.VIDEO
                "audio" -> MediaType.AUDIO
                else -> MediaType.IMAGE
            }
            Log.d(TAG, "Selected media URI: $it, Type: $mediaType")
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

        viewModel = ViewModelProvider(this)[MediaViewModel::class.java]

        binding.btnSelectMedia.setOnClickListener { checkMediaPermissionsAndSelect() }
        binding.btnAction.setOnClickListener {
            when (buttonState) {
                ScanButtonState.VERIFY -> startUpload()
                ScanButtonState.SCANNING -> {}
                ScanButtonState.DONE ->{ resetUI() }
            }
        }

        observeViewModel()



    }


    private fun  resetUI(){
        binding.imageViewMedia.setImageDrawable(ContextCompat.getDrawable(requireContext(),R.drawable.drawable_image_chooser))
        binding.imageViewMedia.elevation = 0f
        binding.imageViewMedia.background = ContextCompat.getDrawable(requireContext(),R.drawable.drawable_image_chooser)
        binding.imageOverlay.visibility=View.GONE
        binding.imageViewMedia.visibility = View.VISIBLE
        binding.btnAction.visibility = View.GONE
        setButtonState(ScanButtonState.VERIFY)
        updateStatus("", false)
        selectedMediaUri=null
    }


    private fun checkMediaPermissionsAndSelect() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all { perm ->
            ContextCompat.checkSelfPermission(requireContext(), perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) selectMedia()
        else requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun selectMedia() {
        selectMediaLauncher.launch("image/*,video/*,audio/*")
    }

    private fun showPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Permission Required")
            .setMessage("You need to grant media access to select files.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun startUpload() {
        selectedMediaUri?.let { uri ->
            val filePath = getFileFromUri(requireContext(), uri)?.absolutePath ?: return
            val file = File(filePath)
            if (file.length() > 100 * 1024 * 1024) { // 100 MB limit
                updateStatus("File too large (max 100MB)", true)
                return
            }

            Log.d(TAG, "Starting upload for file: $filePath, Type: $mediaType")
            setButtonState(ScanButtonState.SCANNING)
            updateStatus("Uploading media...", false)
            viewModel.uploadMedia(filePath, mediaType)
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        val fileName = getFileName(context, uri) ?: "temp_file"
        val tempFile = File(context.cacheDir, fileName)
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file from URI", e)
            updateStatus("Failed to process file", true)
            return null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        return name
    }

    private fun observeViewModel() {
        viewModel.getUploadResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    setButtonState(ScanButtonState.SCANNING)
                    updateStatus("Uploading media...", false)
                }
            Status.SUCCESS -> {
                Log.d(TAG, "observeViewModelUrl: ${resource.data}")
                   val uploadedUrl = resource.data?.get("uploadedUrl")?.asString ?: "Unknown URL"


//
//                Log.d(TAG, "observeViewModel: Uploaded URL: $uploadedUrl")
                    updateStatus("Verifying media...", false)
                    viewModel.verifyMedia(
                        username = preferenceHelper.getUserName().toString(), // Replace with actual username
                        apiKey = preferenceHelper.getApiKey().toString(),   // Replace with actual API key
                        mediaType = mediaType.value,
                        mediaUrl = uploadedUrl
                    )
                }
             Status.ERROR -> {
                 Log.d(TAG, "observeViewModel: ${resource.message}")
                    updateStatus("Upload failed: ${resource.message}", true)
                    setButtonState(ScanButtonState.VERIFY)
                }
            }
        }

        viewModel.getLoading().observe(viewLifecycleOwner) { isLoading ->
            binding.btnAction.isEnabled = !isLoading
        }

        viewModel.getErrorMessage().observe(viewLifecycleOwner) { error ->
            updateStatus(error, true)
        }


        viewModel.getVerifyResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    setButtonState(ScanButtonState.SCANNING)
                    updateStatus("Verifying media...", false)
                }
                Status.SUCCESS -> {
                    Log.d(TAG, "Verification response: ${resource.data}")
                    try {
                        val response = Gson().fromJson(
                            resource.data.toString(),
                            VerificationResponse::class.java
                        )
                        updateStatus("Verification result: ${response.bandName}\n${response.bandDescription}", false)
//                         when(response.band){
//                             1,2 -> binding.textStatusMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.txtGreen))
//                             3,4 -> binding.textStatusMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))
//                             5 -> binding.textStatusMessage.setTextColor(Color.RED)
//                             else -> binding.textStatusMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_200))
//                         }

                        binding.textStatusMessage.visibility= View.VISIBLE
                        binding.imageOverlay.visibility=View.VISIBLE
//                        binding.imageOverlay.setImageURI(verifylabs_tick_icon_light_grey_rgb_2__traced___1_)

                        binding.imageOverlay.setImageDrawable(ContextCompat.getDrawable(requireContext(),R.drawable.verifylabs_tick_icon_light_grey_rgb_2__traced___1_))

                        when(response.band){
                            1,2 -> binding.textStatusMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.txtGreen))
//                            3,4 -> binding.textStatusMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.yellow))
                            5 -> binding.textStatusMessage.setTextColor(Color.RED)
                            else -> binding.textStatusMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_200))
                        }

                        setButtonState(ScanButtonState.DONE)
                    }catch (e:Exception){
                        Log.d(TAG, "onViewCreated: ${e.message}")
                    }


                }
                Status.ERROR -> {
                    Log.d(TAG, "Verification error: ${resource.message}")
                    updateStatus("Verification failed: ${resource.message}", true)
//                    setButtonState(ScanButtonState.UPLOAD)
                }
            }
        }
    }




    private fun setButtonState(state: ScanButtonState) {
        buttonState = state
        when (state) {
            ScanButtonState.VERIFY -> {
                binding.btnAction.visibility = View.VISIBLE
                binding.textAction.text = "Upload Media"
               // binding.iconAction.setImageResource(R.drawable.ic_upload)
            }
            ScanButtonState.SCANNING -> {
                binding.btnAction.visibility = View.VISIBLE
                binding.textAction.text = "Scanning..."
              //  binding.iconAction.setImageResource(R.drawable.ic_loading)
            }
            ScanButtonState.DONE -> {
                binding.btnAction.visibility = View.VISIBLE
                binding.textAction.text = "Done!"
               // binding.iconAction.setImageResource(R.drawable.ic_done)
            }
        }
    }

    private fun updateStatus(message: String, isError: Boolean) {
        binding.textStatusMessage.text = message
        binding.textStatusMessage.setTextColor(
            if (isError) Color.RED else ContextCompat.getColor(requireContext(), R.color.txtGreen)
        )
    }


    private fun getBandResult(band: Int?): String {
        return when (band) {
            1 -> "Man-made ✅"
            2 -> "Likely Man-made ✅"
            3 -> "Inconclusive ⚠️"
            4 -> "Likely AI ⚠️"
            5 -> "AI-generated ❌"
            else -> "Unknown ❓"
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}