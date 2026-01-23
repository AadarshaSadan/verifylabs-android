package com.verifylabs.ai.presentation.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.verifylabs.ai.R
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.BottomsheetChangePasswordBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ChangePasswordBottomSheet : BottomSheetDialogFragment() {

    @Inject lateinit var preferenceHelper: PreferenceHelper

    private var _binding: BottomsheetChangePasswordBinding? = null
    private val binding
        get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetChangePasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.let {
            val bottomSheet =
                    it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                behavior.state =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
                
                // Set full height
                val layoutParams = sheet.layoutParams
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                sheet.layoutParams = layoutParams
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupValidation()
        setupClickListeners()
    }

    private fun setupValidation() {
        val textWatcher =
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}
                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {}
                    override fun afterTextChanged(s: Editable?) {
                        updateSubmitButton()
                    }
                }

        binding.etCurrentPassword.addTextChangedListener(textWatcher)
        binding.etNewPassword.addTextChangedListener(textWatcher)
        binding.etConfirmPassword.addTextChangedListener(textWatcher)
    }

    private fun updateSubmitButton() {
        val currentPassword = binding.etCurrentPassword.text.toString()
        val newPassword = binding.etNewPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        val isValid =
                currentPassword.isNotEmpty() &&
                        newPassword.isNotEmpty() &&
                        confirmPassword.isNotEmpty() &&
                        newPassword.length >= 8 &&
                        newPassword.any { it.isUpperCase() } &&
                        newPassword.any { it.isDigit() } &&
                        newPassword == confirmPassword &&
                        newPassword != currentPassword

        binding.btnChangePassword.isEnabled = isValid

        // Update button appearance
        val context = requireContext()
        if (isValid) {
            binding.btnChangePassword.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(context, R.color.vl_green)
                    )
            binding.btnChangePassword.setTextColor(
                    androidx.core.content.ContextCompat.getColor(context, android.R.color.white)
            )
        } else {
            binding.btnChangePassword.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(
                                    context,
                                    R.color.colorWhiteGray
                            )
                    )
            binding.btnChangePassword.setTextColor(
                    androidx.core.content.ContextCompat.getColor(context, R.color.secondary_text)
            )
        }

        // Update requirement indicators
        updateRequirement(binding.tvReq8Chars, newPassword.length >= 8)
        updateRequirement(binding.tvReqUppercase, newPassword.any { it.isUpperCase() })
        updateRequirement(binding.tvReqNumber, newPassword.any { it.isDigit() })
        updateRequirement(
                binding.tvReqMatch,
                newPassword.isNotEmpty() && newPassword == confirmPassword
        )
    }

    private fun updateRequirement(textView: android.widget.TextView, isMet: Boolean) {
        if (isMet) {
            textView.setTextColor(resources.getColor(R.color.vl_green, null))
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_check_circle,
                    0,
                    0,
                    0
            )
        } else {
            textView.setTextColor(resources.getColor(R.color.secondary_text, null))
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_circle, 0, 0, 0)
        }
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnChangePassword.setOnClickListener { changePassword() }
    }

    private fun changePassword() {
        val currentPassword = binding.etCurrentPassword.text.toString()
        val newPassword = binding.etNewPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        // Validate current password
        val storedPassword = preferenceHelper.getPassword() ?: ""
        when {
            currentPassword != storedPassword -> {
                binding.tilCurrentPassword.error = "Current password is incorrect"
            }
            newPassword.length < 8 -> {
                binding.tilNewPassword.error = "Password must be at least 8 characters"
            }
            !newPassword.any { it.isUpperCase() } -> {
                binding.tilNewPassword.error = "Password must contain an uppercase letter"
            }
            !newPassword.any { it.isDigit() } -> {
                binding.tilNewPassword.error = "Password must contain a number"
            }
            newPassword != confirmPassword -> {
                binding.tilConfirmPassword.error = "Passwords do not match"
            }
            newPassword == currentPassword -> {
                binding.tilNewPassword.error = "New password must be different"
            }
            else -> {
                // Success - call callback
                (parentFragment as? ChangePasswordCallback)?.onPasswordChanged(newPassword)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    interface ChangePasswordCallback {
        fun onPasswordChanged(newPassword: String)
    }
}
