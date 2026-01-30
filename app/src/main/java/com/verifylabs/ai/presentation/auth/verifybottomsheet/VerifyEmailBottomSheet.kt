package com.verifylabs.ai.presentation.auth.verifybottomsheet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.verifylabs.ai.R
import com.verifylabs.ai.databinding.BottomSheetVerifyEmailBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VerifyEmailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetVerifyEmailBinding? = null
    private val binding get() = _binding!!

    override fun getTheme() = R.style.FullScreenBottomSheetDialogTheme


    private var email: String? = null  // <-- Add this


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        email = arguments?.getString(ARG_EMAIL)  // <-- Retrieve email from arguments

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetVerifyEmailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // Set email dynamically
        binding.tvEmail.text = email ?: ""
        // Open Email App
        binding.btnOpenEmail.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show()
            }
        }


        // Verify Later
        binding.btnVerifyLaterContainer.setOnClickListener {
            // Notify parent to finish (iOS parity)
            parentFragmentManager.setFragmentResult("VERIFY_EMAIL_DISMISSED", androidx.core.os.bundleOf())
            dismiss()
        }

        // Disable swipe down / drag to dismiss
        (dialog as? BottomSheetDialog)?.let { bottomSheetDialog ->
            bottomSheetDialog.behavior.apply {
                isDraggable = false
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
            bottomSheetDialog.setCancelable(false)
            bottomSheetDialog.setCanceledOnTouchOutside(false)
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        // Ensure result is sent whenever dismissed
        parentFragmentManager.setFragmentResult("VERIFY_EMAIL_DISMISSED", androidx.core.os.bundleOf())
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT) // <-- important
            BottomSheetBehavior.from(sheet).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                isDraggable = false
                skipCollapsed = true
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    companion object {
        private const val ARG_EMAIL = "arg_email"

        fun newInstance(email: String): VerifyEmailBottomSheet {
            val fragment = VerifyEmailBottomSheet()
            val bundle = Bundle()
            bundle.putString(ARG_EMAIL, email)
            fragment.arguments = bundle
            return fragment
        }
    }
}
