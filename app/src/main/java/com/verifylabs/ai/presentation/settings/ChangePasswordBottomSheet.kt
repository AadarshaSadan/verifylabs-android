package com.verifylabs.ai.presentation.settings

import android.graphics.Color
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
import com.verifylabs.ai.presentation.settings.viewmodel.ViewModelgetAccountInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ChangePasswordBottomSheet : BottomSheetDialogFragment() {

    @Inject lateinit var preferenceHelper: PreferenceHelper

    private lateinit var viewModel: ViewModelgetAccountInfo

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

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        return dialog
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
        initViewModel()
        setupValidation()
        setupClickListeners()
        observeViewModel()
    }

    private fun initViewModel() {
        viewModel = androidx.lifecycle.ViewModelProvider(this)[ViewModelgetAccountInfo::class.java]
    }

    private fun observeViewModel() {
        viewModel.updateProfileObserver.observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                com.verifylabs.ai.core.util.Status.SUCCESS -> {
                    setLoading(false)
                    android.widget.Toast.makeText(
                                    requireContext(),
                                    "Password changed successfully",
                                    android.widget.Toast.LENGTH_SHORT
                            )
                            .show()

                    // Update locally
                    val newPassword = binding.etNewPassword.text.toString()
                    preferenceHelper.setPassword(newPassword)

                    dismiss()
                }
                com.verifylabs.ai.core.util.Status.ERROR -> {
                    setLoading(false)
                    android.widget.Toast.makeText(
                                    requireContext(),
                                    resource.message ?: "Failed to change password",
                                    android.widget.Toast.LENGTH_LONG
                            )
                            .show()
                }
                com.verifylabs.ai.core.util.Status.LOADING -> {
                    setLoading(true)
                }
                else -> {}
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnChangePassword.text =
                if (isLoading) "" else getString(R.string.change_password_button)
        binding.btnChangePassword.isEnabled = !isLoading

        // Also disable inputs
        binding.etCurrentPassword.isEnabled = !isLoading
        binding.etNewPassword.isEnabled = !isLoading
        binding.etConfirmPassword.isEnabled = !isLoading
        binding.btnCancel.isEnabled = !isLoading
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
                        updatePlaceholders()
                    }
                }

        binding.etCurrentPassword.addTextChangedListener(textWatcher)
        binding.etNewPassword.addTextChangedListener(textWatcher)
        binding.etConfirmPassword.addTextChangedListener(textWatcher)

        val focusListener = View.OnFocusChangeListener { _, _ -> updatePlaceholders() }
        binding.etCurrentPassword.onFocusChangeListener = focusListener
        binding.etNewPassword.onFocusChangeListener = focusListener
        binding.etConfirmPassword.onFocusChangeListener = focusListener
    }

    private fun updatePlaceholders() {
        val currentPassword = binding.etCurrentPassword.text.toString()
        val newPassword = binding.etNewPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        if (currentPassword.isNotEmpty() || binding.etCurrentPassword.hasFocus()) {
            binding.tilCurrentPassword.hint = null
        } else {
            binding.tilCurrentPassword.hint = getString(R.string.current_password_hint)
        }

        if (newPassword.isNotEmpty() || binding.etNewPassword.hasFocus()) {
            binding.tilNewPassword.hint = null
        } else {
            binding.tilNewPassword.hint = getString(R.string.new_password_hint)
        }

        if (confirmPassword.isNotEmpty() || binding.etConfirmPassword.hasFocus()) {
            binding.tilConfirmPassword.hint = null
        } else {
            binding.tilConfirmPassword.hint = getString(R.string.confirm_password_hint)
        }
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
                    androidx.core.content.ContextCompat.getColor(context, R.color.white)
            )
        } else {
            binding.btnChangePassword.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(
                                    context,
                                    R.color.system_gray
                            )
                    )
            binding.btnChangePassword.setTextColor(
                    androidx.core.content.ContextCompat.getColor(context, R.color.white)
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
        val context = requireContext()
        if (isMet) {
            val headerColor =
                    androidx.core.content.ContextCompat.getColor(context, R.color.header_text)
            textView.setTextColor(headerColor)
            val drawable =
                    androidx.core.content.ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_circle_fill_change_password
                    )
            drawable?.setTint(
                    androidx.core.content.ContextCompat.getColor(context, R.color.txtGreen)
            )
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    drawable,
                    null,
                    null,
                    null
            )
        } else {
            textView.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                            context,
                            R.color.secondary_text
                    )
            )
            val drawable =
                    androidx.core.content.ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_circle
                    )
            drawable?.setTint(
                    androidx.core.content.ContextCompat.getColor(
                            context,
                            R.color.secondary_text
                    )
            )
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
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
                // Success - Call API via ViewModel
                val secretKey = com.verifylabs.ai.core.util.Constants.SECRET_KEY
                val apiKey = preferenceHelper.getApiKey() ?: ""
                viewModel.changePassword(secretKey, apiKey, newPassword)
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
