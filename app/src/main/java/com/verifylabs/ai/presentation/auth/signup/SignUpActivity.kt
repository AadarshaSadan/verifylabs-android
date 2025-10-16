package com.verifylabs.ai.presentation.auth.signup

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.ActivitySignUpBinding
import com.verifylabs.ai.presentation.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SignUpActivity : AppCompatActivity() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private val TAG = "SignUpActivity"
    private val secretKey = "dZnxiwh!o*%cf!dNk3kP8R&P"

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var signUpViewModel: SignUpViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge layout
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set status bar appearance to match theme
        val isLightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_NO
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLightMode
        window.statusBarColor = getColor(R.color.app_background_before_login)

        // Initialize binding and set content view
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Simplified window insets handling
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.scrollView.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            insets
        }

        // Initialize ViewModel and clear state
        signUpViewModel = ViewModelProvider(this)[SignUpViewModel::class.java]
    //    signUpViewModel.clearSignUpResponse() // Reset ViewModel state

        setupClickListeners()
        setupTextWatchers()
        setupObservers()
    }

    private fun setupClickListeners() {
        binding.btnCreateAccount.setOnClickListener {
            Log.d(TAG, "Create Account button clicked")
            val fullName = binding.etFullUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (!validateInputs(fullName, email, username, password, confirmPassword)) return@setOnClickListener

            Log.d(TAG, "Signing up with - FullName: $fullName, Email: $email, Username: $username")
            signUpViewModel.signUp(fullName, email, username, password, secretKey)
        }

        // Navigate back to Login screen
        binding.btnSignInPrompt.setOnClickListener {
            Log.d(TAG, "Sign In button clicked")
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }

        // Back button navigation
        binding.btnBack.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            finish()
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
    }

    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val fullName = binding.etFullUsername.text.toString().trim()
                val email = binding.etEmail.text.toString().trim()
                val username = binding.etUsername.text.toString().trim()
                val password = binding.etPassword.text.toString().trim()
                val confirmPassword = binding.etConfirmPassword.text.toString().trim()

                // Update input field backgrounds
                binding.etFullUsername.setBackgroundResource(
                    if (fullName.isEmpty()) R.drawable.bg_text_input
                    else R.drawable.bg_text_input_green
                )
                binding.etEmail.setBackgroundResource(
                    if (email.isEmpty()) R.drawable.bg_text_input
                    else R.drawable.bg_text_input_green
                )
                binding.etUsername.setBackgroundResource(
                    if (username.isEmpty()) R.drawable.bg_text_input
                    else R.drawable.bg_text_input_green
                )
                binding.etPassword.setBackgroundResource(
                    if (password.isEmpty()) R.drawable.bg_text_input
                    else R.drawable.bg_text_input_green
                )
                binding.etConfirmPassword.setBackgroundResource(
                    if (confirmPassword.isEmpty()) R.drawable.bg_text_input
                    else R.drawable.bg_text_input_green
                )

                // Update Create Account button state
                binding.btnCreateAccount.isEnabled = username.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty()
                binding.btnCreateAccount.setBackgroundResource(
                    if (binding.btnCreateAccount.isEnabled)
                        R.drawable.drawable_verify_background_green_radius_more
                    else
                        R.drawable.drawable_verify_background_btn_failed_likely_gray
                )
            }
        }

        // Attach TextWatcher to all input fields
        binding.etFullUsername.addTextChangedListener(textWatcher)
        binding.etEmail.addTextChangedListener(textWatcher)
        binding.etUsername.addTextChangedListener(textWatcher)
        binding.etPassword.addTextChangedListener(textWatcher)
        binding.etConfirmPassword.addTextChangedListener(textWatcher)
    }

    private fun validateInputs(
        fullName: String,
        email: String,
        username: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        if (username.isEmpty()) {
            binding.etUsername.error = "Enter username"
            return false
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Enter password"
            return false
        }
        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Enter confirm password"
            return false
        }
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return false
        }
        if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Enter a valid email"
            return false
        }
        return true
    }

    private fun setupObservers() {
        signUpViewModel.getSignUpResponse().observe(this) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    binding.tvCreateAccount.text = getString(R.string.creating_account_now)
                }
                Status.SUCCESS -> {
                    Toast.makeText(this, "Sign up successful!", Toast.LENGTH_SHORT).show()
                    resource.data?.let {
                        Log.d(TAG, "SignUp Response: $it")
                        navigateToMainActivity()
                    } ?: run {
                        Toast.makeText(this, "No data received", Toast.LENGTH_SHORT).show()
                    }
                }
                Status.ERROR -> {
                    binding.tvCreateAccount.text = getString(R.string.create_account)
                    Toast.makeText(this, "Sign up failed: ${resource.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToMainActivity() {
        Log.d(TAG, "Navigating to OnboardingActivity")
        val intent = Intent(this, OnboardingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("START_PAGE", 2)
        }
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear click listeners to prevent memory leaks
        if (::binding.isInitialized) {
            binding.btnCreateAccount.setOnClickListener(null)
            binding.tvSignIn.setOnClickListener(null)
            binding.btnBack.setOnClickListener(null)
        }
    }
}