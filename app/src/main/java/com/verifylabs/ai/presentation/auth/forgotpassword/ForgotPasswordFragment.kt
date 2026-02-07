package com.verifylabs.ai.presentation.auth.forgotpassword

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.DialogUtils
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.databinding.FragmentForgotPasswordBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding
        get() = _binding!!
    private lateinit var viewModel: ForgotPasswordViewModel

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ForgotPasswordViewModel::class.java]

        setupUI()
        setupTextWatcher()
        setupObservers()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.tvLogin.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.btnGetNewPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (isValidEmail(email)) {
                viewModel.requestPasswordReset(email)
            } else {
                showError("Please enter a valid username or email.")
            }
        }
    }

    private fun setupTextWatcher() {
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
                    ) {
                        binding.tvError.visibility = View.GONE
                    }

                    override fun afterTextChanged(s: Editable?) {
                        val email = s.toString().trim()

                        // Update input field background
                        binding.etEmail.setBackgroundResource(
                                if (email.isNotEmpty()) R.drawable.bg_text_input_green
                                else R.drawable.bg_text_input
                        )

                        // Update button state
                        if (email.isNotEmpty()) {
                            binding.btnGetNewPassword.setBackgroundResource(
                                    R.drawable.bg_ios_green_button
                            )
                            binding.btnGetNewPassword.alpha = 1.0f
                            binding.btnGetNewPassword.isEnabled = true
                        } else {
                            binding.btnGetNewPassword.setBackgroundResource(
                                    R.drawable.bg_ios_gray_button
                            )
                            binding.btnGetNewPassword.alpha = 0.6f
                            binding.btnGetNewPassword.isEnabled = false
                        }
                    }
                }
        binding.etEmail.addTextChangedListener(textWatcher)

        // Initial state
        binding.btnGetNewPassword.setBackgroundResource(R.drawable.bg_ios_gray_button)
        binding.btnGetNewPassword.alpha = 0.6f
        binding.btnGetNewPassword.isEnabled = false
    }

    private fun isValidEmail(target: CharSequence): Boolean {
        return target.isNotEmpty()
    }

    private fun setupObservers() {
        viewModel.resetPasswordResponse.observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvBtnText.text = "Sending..."
                    binding.btnGetNewPassword.isEnabled = false
                    binding.etEmail.isEnabled = false
                    binding.btnGetNewPassword.alpha = 0.8f
                }
                Status.SUCCESS -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvBtnText.text = "Get New Password"
                    binding.btnGetNewPassword.isEnabled = true
                    binding.etEmail.isEnabled = true
                    binding.btnGetNewPassword.alpha = 1.0f

                    DialogUtils.showIosErrorDialog(
                            requireContext(),
                            "Success",
                            "If an account exists for this email/username, you will receive a password reset link shortly."
                    ) {
                        try {
                            parentFragmentManager.popBackStack()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                Status.ERROR -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvBtnText.text = "Get New Password"
                    binding.btnGetNewPassword.isEnabled = true
                    binding.etEmail.isEnabled = true
                    binding.btnGetNewPassword.alpha = 1.0f

                    showError(resource.message ?: "An error occurred.")
                }
                else -> {}
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        // Keep the input green if it has text, or red if error?
        // LoginFragment doesn't turn red on error? Just shows dialog.
        // But here we have an inline error message.
        // Let's keep the error styling simple, maybe just the text message is enough.
        // Or if we want to turn it red, we'd need another drawable or state.
        // For now, let's stick to the text message as defined in refined plan.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
