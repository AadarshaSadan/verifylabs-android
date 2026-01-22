package com.verifylabs.ai.presentation.auth.signup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.setPadding
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.ActivitySignUpBinding
import com.verifylabs.ai.presentation.auth.login.ApiResponseLogin
import com.verifylabs.ai.presentation.auth.verifybottomsheet.VerifyEmailBottomSheet
import com.verifylabs.ai.presentation.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SignUpActivity : AppCompatActivity() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var signUpViewModel: SignUpViewModel

    private val secretKey = "dZnxiwh!o*%cf!dNk3kP8R&P"

    private val isVerified=0;

    private val TAG = "SignUpActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.scrollView.setPadding(
                0, 0, 0,
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            )
            insets
        }

        // Ensure status bar icons are correct based on theme (backward compatible)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        windowInsetsController.isAppearanceLightStatusBars = !isDarkMode

        signUpViewModel = ViewModelProvider(this)[SignUpViewModel::class.java]

        setupTextWatchers()
        setupClickListeners()
        setupObservers()



       updateSignUpButtonState()
    }

    // -------------------- CLICK LISTENERS --------------------

    private fun setupClickListeners() {

        binding.btnCreateAccount.setOnClickListener {

            val fullName = binding.etFullUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (!validateInputs(fullName, email, username, password, confirmPassword)) return@setOnClickListener

            signUpViewModel.signUp(
                fullName,
                email,
                username,
                password,
                secretKey,
                isVerified
            )
        }

        binding.cbAgreeTerms.setOnCheckedChangeListener { _, _ ->
            updateSignUpButtonState()
        }

        binding.tvTerms.setOnClickListener {
                openWebUrl("https://verifylabs.ai/terms/")
        }

        binding.tvPrivacy.setOnClickListener {

            openWebUrl("https://verifylabs.ai/privacy/")
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSignInPrompt.setOnClickListener { finish() }
    }

    // -------------------- TEXT WATCHERS (LIKE LOGIN) --------------------

    private fun setupTextWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSignUpButtonState()
            }
        }

        binding.etFullUsername.addTextChangedListener(watcher)
        binding.etEmail.addTextChangedListener(watcher)
        binding.etUsername.addTextChangedListener(watcher)
        binding.etPassword.addTextChangedListener(watcher)
        binding.etConfirmPassword.addTextChangedListener(watcher)
    }

    // -------------------- UI STATE UPDATE (EXACT LIKE LOGIN) --------------------

    private fun updateSignUpButtonState() {

        val fullName = binding.etFullUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        // Input background change
        binding.etFullUsername.setBackgroundResource(
            if (fullName.isEmpty()) R.drawable.bg_text_input else R.drawable.bg_text_input_green
        )
        binding.etEmail.setBackgroundResource(
            if (email.isEmpty()) R.drawable.bg_text_input else R.drawable.bg_text_input_green
        )
        binding.etUsername.setBackgroundResource(
            if (username.isEmpty()) R.drawable.bg_text_input else R.drawable.bg_text_input_green
        )
        binding.etPassword.setBackgroundResource(
            if (password.isEmpty()) R.drawable.bg_text_input else R.drawable.bg_text_input_green
        )
        binding.etConfirmPassword.setBackgroundResource(
            if (confirmPassword.isEmpty()) R.drawable.bg_text_input else R.drawable.bg_text_input_green
        )

        val enableButton =
            fullName.isNotEmpty() &&
                    email.isNotEmpty() &&
                    android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                    username.isNotEmpty() &&
                    password.isNotEmpty() &&
                    confirmPassword.isNotEmpty() &&
                    password == confirmPassword &&
                    binding.cbAgreeTerms.isChecked

        binding.btnCreateAccount.isEnabled = enableButton
        binding.btnCreateAccount.setBackgroundResource(
            if (enableButton)
                R.drawable.drawable_verify_background_green_radius_more
            else
                R.drawable.drawable_verify_background_btn_failed_likely_gray
        )
    }

    // -------------------- VALIDATION --------------------

    private fun validateInputs(
        fullName: String,
        email: String,
        username: String,
        password: String,
        confirmPassword: String
    ): Boolean {

        if (fullName.isEmpty()) {
            binding.etFullUsername.error = "Enter full name"
            return false
        }
        if (email.isEmpty()) {
            binding.etEmail.error = "Enter email"
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Invalid email"
            return false
        }
        if (username.isEmpty()) {
            binding.etUsername.error = "Enter username"
            return false
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Enter password"
            return false
        }
        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Confirm password"
            return false
        }
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return false
        }
        if (!binding.cbAgreeTerms.isChecked) {
            showToast("Please accept Terms & Privacy Policy")
            return false
        }
        return true
    }

    // -------------------- OBSERVERS --------------------

    private fun setupObservers() {
        signUpViewModel.getSignUpResponse().observe(this) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    binding.tvCreateAccount.text = getString(R.string.creating_account_now)
                }


                Status.SUCCESS -> {
                    resource.data?.let {
                        try {
                            val response = Gson().fromJson(it.toString(), SignUpVerificationResponse::class.java)
                            Log.d(TAG, "setupObservers: Sign Up Response: ${response.toString()}")

                            // Show success toast
                            showToast("Sign up successful. Please verify your email before logging in.")

                            // Get the email from the input field (or response if available)
                            val email = binding.etEmail.text.toString().trim()

                            // Open the bottom sheet with the email
                            val bottomSheet = VerifyEmailBottomSheet.newInstance(email)
                            bottomSheet.isCancelable = false
                            bottomSheet.show(supportFragmentManager, "VerifyEmailBottomSheet")

                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }


                Status.ERROR -> {
                    binding.tvCreateAccount.text = getString(R.string.create)
                    showToast(resource.message ?: "Sign up failed")
                }
            }
        }
    }

    // -------------------- NAVIGATION --------------------

//    private fun navigateToMainActivity() {
//        startActivity(
//            Intent(this, OnboardingActivity::class.java).apply {
//                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//                putExtra("START_PAGE", 2)
//            }
//        )
//        finish()
//    }

    // -------------------- HELPERS --------------------

    private fun openWebUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::binding.isInitialized) {
            binding.btnCreateAccount.setOnClickListener(null)
            binding.btnBack.setOnClickListener(null)
            binding.btnSignInPrompt.setOnClickListener(null)
        }
    }



}
