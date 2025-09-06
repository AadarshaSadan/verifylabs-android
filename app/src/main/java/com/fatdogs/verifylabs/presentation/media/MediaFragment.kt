package com.fatdogs.verifylabs.presentation.media

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fatdogs.verifylabs.R
import com.fatdogs.verifylabs.databinding.FragmentMediaBinding
import com.fatdogs.verifylabs.core.util.Resource
import com.fatdogs.verifylabs.core.util.Status
import com.fatdogs.verifylabs.presentation.viewmodel.MediaViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

enum class ScanButtonState { VERIFY, SCANNING, DONE }
enum class Robot { HUMAN, ROBOT, UNKNOWN }

@AndroidEntryPoint
class MediaFragment : Fragment() {

    private val TAG = "MediaFragment"
    private var _binding: FragmentMediaBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MediaViewModel

    private var buttonState = ScanButtonState.VERIFY
    private var currentRobot = Robot.UNKNOWN
    private var selectedMediaUri: Uri? = null
    private var mediaType = "image" // or "video"

    private val selectMediaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedMediaUri = it
            binding.imageViewMedia.setImageURI(it)
            binding.imageViewMedia.visibility = View.VISIBLE
            binding.btnAction.visibility = View.VISIBLE
            setButtonState(ScanButtonState.VERIFY)
            updateStatus("", false)
            hideOverlay()
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

        // Initialize ViewModel

        viewModel = MediaViewModel::class.java.let {
            androidx.lifecycle.ViewModelProvider(this)[it]
        }

        binding.btnSelectMedia.setOnClickListener {
            selectMediaLauncher.launch("image/*") // or "image/*,video/*"
        }

        binding.btnAction.setOnClickListener {
            when (buttonState) {
                ScanButtonState.VERIFY -> startVerification()
                ScanButtonState.SCANNING -> {} // optional
                ScanButtonState.DONE -> requireActivity().finish()
            }
        }

        observeViewModel()
    }

    private fun startVerification() {
        selectedMediaUri?.let { uri ->
            val filePath = File(uri.path ?: "").absolutePath // convert Uri to File path
            setButtonState(ScanButtonState.SCANNING)
            updateStatus("Scanning media...", false)

            viewModel.uploadMedia(filePath, mediaType)
        }
    }


    private fun observeViewModel() {
        viewModel.uploadResponse.observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    setButtonState(ScanButtonState.SCANNING)
                    updateStatus("Uploading media...", false)
                }
                Status.SUCCESS -> {
                    // Example: parse response to determine robot type

                    Log.d(TAG, "observeViewModel: ${resource.data.toString()}")
                    val robotType = resource.data?.get("robotType")?.asString ?: "unknown"
                    currentRobot = when (robotType.lowercase()) {
                        "human" -> Robot.HUMAN
                        "robot" -> Robot.ROBOT
                        else -> Robot.UNKNOWN
                    }
                    showOverlay(currentRobot)
                    updateStatus("Media verified successfully", false)
                    setButtonState(ScanButtonState.DONE)
                }
                Status.ERROR -> {
                    currentRobot = Robot.UNKNOWN
                    showOverlay(currentRobot)
                    updateStatus(resource.message ?: "Upload failed", true)
                    setButtonState(ScanButtonState.VERIFY)
                }
            }
        }
    }

    private fun setButtonState(state: ScanButtonState) {
        buttonState = state
        when (state) {
            ScanButtonState.VERIFY -> {
                binding.btnAction.visibility = View.VISIBLE
                binding.textAction.text = "Verify Media"
                binding.iconAction.setImageResource(R.drawable.ic_2db)
            }
            ScanButtonState.SCANNING -> {
                binding.btnAction.visibility = View.VISIBLE
                binding.textAction.text = "Scanning..."
                binding.iconAction.setImageResource(R.drawable.ic_2db)
            }
            ScanButtonState.DONE -> {
                binding.btnAction.visibility = View.VISIBLE
                binding.textAction.text = "Done!"
                binding.iconAction.setImageResource(R.drawable.ic_2db)
            }
        }
    }

    private fun showOverlay(robot: Robot) {
        val overlayRes = when (robot) {
            Robot.HUMAN -> R.drawable.ic_2db
            Robot.ROBOT -> R.drawable.ic_2db
            Robot.UNKNOWN -> R.drawable.ic_2db
        }
        binding.imageOverlay.setImageResource(overlayRes)
        binding.imageOverlay.visibility = View.VISIBLE
    }

    private fun hideOverlay() {
        binding.imageOverlay.visibility = View.GONE
    }

    private fun updateStatus(message: String, isError: Boolean) {
        binding.textStatusMessage.text = message
        binding.textStatusMessage.setTextColor(
            if (isError) Color.RED else ContextCompat.getColor(requireContext(), R.color.txtGreen)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
