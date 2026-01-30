package com.verifylabs.ai.presentation.auth.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.FragmentLoginBinding
import com.verifylabs.ai.presentation.MainActivity
import com.verifylabs.ai.presentation.auth.signup.SignUpActivity
import com.verifylabs.ai.presentation.onboarding.OnboardingActivity
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private val TAG = "LoginFragment"

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var loginViewModel: LoginViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)


        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        setupClickListeners()
        setupTextWatchers()
        setupObservers()
        observeKeyboard()
    }


    private fun observeKeyboard() {
        val rootView = binding.root
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - r.bottom

            val isKeyboardOpen = keypadHeight > screenHeight * 0.15 // 15% threshold


            // Call the activity method to hide/show bottom layout
            (activity as? OnboardingActivity)?.setBottomLayoutVisibility(!isKeyboardOpen)
        }
    }



    private fun setupClickListeners() {
        binding.btnGetStarted.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (!validateInputs(username, password)) {
                return@setOnClickListener
            }

            preferenceHelper.setUserName(username)
            preferenceHelper.setPassword(password)
            loginViewModel.login(username, password)
        }

        binding.btnCreateAccount.setOnClickListener {
            val intent = Intent(requireContext(), SignUpActivity::class.java)
            startActivity(intent)
            requireActivity().overridePendingTransition(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
        }
    }

    private fun setupTextWatchers() {
        // Common TextWatcher for both fields to update button background
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Update input field backgrounds
                val username = binding.etUsername.text.toString().trim()
                val password = binding.etPassword.text.toString().trim()

                binding.etUsername.setBackgroundResource(
                    if (username.isEmpty()) R.drawable.bg_text_input
                    else R.drawable.bg_text_input_green
                )

                binding.etPassword.setBackgroundResource(
                    if (password.isEmpty()) R.drawable.bg_text_input
                    else R.drawable.bg_text_input_green
                )

                // Update button state (iOS Style)
                val canProceed = username.isNotEmpty() && password.isNotEmpty()
                binding.btnGetStarted.isEnabled = canProceed
                // iOS: Gray opacity 0.6 when disabled, Green opacity 1.0 when enabled
                // We use different drawables to match colors exactly
                if (canProceed) {
                    binding.btnGetStarted.setBackgroundResource(R.drawable.bg_ios_green_button)
                    binding.btnGetStarted.alpha = 1.0f
                    binding.btnGetStarted.elevation = 8f // Add shadow
                } else {
                    binding.btnGetStarted.setBackgroundResource(R.drawable.bg_ios_gray_button)
                    binding.btnGetStarted.alpha = 0.6f
                    binding.btnGetStarted.elevation = 0f // No shadow
                }
            }
        }

        // Attach the same TextWatcher to both fields
        binding.etUsername.addTextChangedListener(textWatcher)
        binding.etPassword.addTextChangedListener(textWatcher)
        
        // Initial state check
        binding.btnGetStarted.isEnabled = false
        binding.btnGetStarted.setBackgroundResource(R.drawable.bg_ios_gray_button)
        binding.btnGetStarted.alpha = 0.6f
        binding.btnGetStarted.elevation = 0f
    }

    private fun validateInputs(username: String, password: String): Boolean {
        if (username.isEmpty() || password.isEmpty()) {
            showErrorDialog("Error", "Please check your username and password.")
            return false
        }
        return true
    }

    private fun setupObservers() {
        loginViewModel.getLoginResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    // Show Spinner
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvSignIn.text = "SIGNING IN..." 
                    binding.btnGetStarted.isEnabled = false
                    binding.btnGetStarted.alpha = 0.8f // Slight dim during loading
                }
                Status.SUCCESS -> {
                    // Hide Spinner
                    binding.progressBar.visibility = View.GONE
                    binding.tvSignIn.text = "SIGN IN"
                    
                    resource.data?.let {
                        try {
                            val response = Gson().fromJson(it.toString(), ApiResponseLogin::class.java)
                            Log.d(TAG, "setupObservers: Login Response: $response")
                            
                            // Note: ViewModel already handles authoritative credit sync & RevenueCat login
                            // We just need to save the basic session info and navigate
                            
                            preferenceHelper.setApiKey(response.apiKey)
                            preferenceHelper.setIsLoggedIn(true)
                            // Credits are already set in ViewModel before this observer flows
                            
                            navigateToMainActivity()
                        } catch (e: Exception) {
                            // On error, revert button state
                             binding.btnGetStarted.isEnabled = true
                             binding.btnGetStarted.alpha = 1.0f
                        }
                    }
                }
                Status.ERROR -> {
                    // Hide Spinner and Reset
                    binding.progressBar.visibility = View.GONE
                    binding.tvSignIn.text = "SIGN IN" // Reset text
                    binding.btnGetStarted.isEnabled = true
                    binding.btnGetStarted.alpha = 1.0f
                    
                    showErrorDialog("Error", resource.message ?: "Invalid credentials. Please check your username and password.")
                }
                Status.INSUFFICIENT_CREDITS -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvSignIn.text = "SIGN IN"
                    binding.btnGetStarted.isEnabled = true
                    binding.btnGetStarted.alpha = 1.0f
                    
                    showErrorDialog("Error", "Insufficient credits.")
                }
            }
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        com.verifylabs.ai.core.util.DialogUtils.showIosErrorDialog(requireContext(), title, message)
    }

    private fun navigateToMainActivity() {
        val intent = Intent(requireActivity(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}