package com.fatdogs.verifylabs.presentation.auth.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.fatdogs.verifylabs.R
import com.fatdogs.verifylabs.core.util.Resource
import com.fatdogs.verifylabs.core.util.Status
import com.fatdogs.verifylabs.data.base.PreferenceHelper
import com.fatdogs.verifylabs.databinding.FragmentLoginBinding
import com.fatdogs.verifylabs.presentation.MainActivity
import com.fatdogs.verifylabs.presentation.auth.signup.SignUpActivity
import com.fatdogs.verifylabs.presentation.onboarding.OnboardingActivity
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.log

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

                // Update button background based on both fields
                binding.btnGetStarted.setBackgroundResource(
                    if (username.isNotEmpty() && password.isNotEmpty())
                        R.drawable.drawable_verify_background_green_radius_more
                    else
                        R.drawable.drawable_verify_background_btn_failed_likely_gray
                )
            }
        }

        // Attach the same TextWatcher to both fields
        binding.etUsername.addTextChangedListener(textWatcher)
        binding.etPassword.addTextChangedListener(textWatcher)
    }

    private fun validateInputs(username: String, password: String): Boolean {
        if (username.isEmpty()) {
            binding.etUsername.error = "Please enter username"
            return false
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "Please enter password"
            return false
        }
        return true
    }

    private fun setupObservers() {
        loginViewModel.getLoginResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    Toast.makeText(requireContext(), "Logging in...", Toast.LENGTH_SHORT).show()

                    binding.tvSignIn.text= getString(R.string.signing_in)
                }
                Status.SUCCESS -> {
                    resource.data?.let {
                        try {
                            val response = Gson().fromJson(it.toString(), ApiResponseLogin::class.java)
                            preferenceHelper.setApiKey(response.apiKey)
                            preferenceHelper.setIsLoggedIn(true)
                            preferenceHelper.setCreditReamaining(response.credits + response.creditsMonthly)
                            Toast.makeText(requireContext(), "Login Successful", Toast.LENGTH_SHORT).show()
                            navigateToMainActivity()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Parsing error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Status.ERROR -> {
                    binding.tvSignIn.text= getString(R.string.sign_in_login)
                    Toast.makeText(requireContext(), "Login Failed: ${resource.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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