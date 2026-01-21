package com.verifylabs.ai.presentation.media

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.verifylabs.ai.databinding.FragmentCropBottomSheetBinding
import java.io.File
import java.util.UUID

class CropBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCropBottomSheetBinding? = null
    private val binding get() = _binding!!
    private var inputUri: Uri? = null
    var onImageResult: ((Uri, Int) -> Unit)? = null

    companion object {
        private const val ARG_URI = "ARG_URI"

        fun newInstance(uri: Uri): CropBottomSheetFragment {
            val fragment = CropBottomSheetFragment()
            val args = Bundle()
            args.putParcelable(ARG_URI, uri)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set style to transparency so our custom background with corners is visible
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Transparent window background for the dialog itself
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        _binding = FragmentCropBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inputUri = arguments?.getParcelable(ARG_URI)

        // Configure CropImageView
        binding.cropImageView.setImageUriAsync(inputUri)
        binding.cropImageView.setFixedAspectRatio(false) // Free-form

        binding.btnUseFullImage.setOnClickListener {
            inputUri?.let { uri -> onImageResult?.invoke(uri, 100) }
            dismiss()
        }

        binding.btnContinue.setOnClickListener {
            // Crop the image
            var cropped = binding.cropImageView.getCroppedImage()
            if (cropped != null) {

                // Smart Quality and Resizing Logic
                // 1. Resize if needed
                val maxDimension = 1280
                if (cropped.width > maxDimension || cropped.height > maxDimension) {
                    cropped = resizeBitmap(cropped, maxDimension)
                }

                // 2. Calculate Quality
                val quality = calculateSmartQuality(cropped)

                // Save to cache
                val fileName = "cropped_${UUID.randomUUID()}.jpg"
                val file = File(requireContext().cacheDir, fileName)
                try {
                    java.io.FileOutputStream(file).use { out ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    }
                    onImageResult?.invoke(Uri.fromFile(file), quality)
                    dismiss()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error saving crop", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = width.toFloat() / height.toFloat()

        var newWidth = width
        var newHeight = height

        if (width > height) {
            if (width > maxDimension) {
                newWidth = maxDimension
                newHeight = (newWidth / ratio).toInt()
            }
        } else {
            if (height > maxDimension) {
                newHeight = maxDimension
                newWidth = (newHeight * ratio).toInt()
            }
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun calculateSmartQuality(bitmap: Bitmap): Int {
        val sizeInMB = bitmap.byteCount / (1024 * 1024f)
        return if (sizeInMB > 5) {
            60
        } else if (sizeInMB > 2) {
            70
        } else {
            90
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            val layoutParams = it.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            it.layoutParams = layoutParams
        }
    }
}
