package com.fatdogs.verifylabs.presentation.auth.signup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.fatdogs.verifylabs.core.util.Resource
import com.fatdogs.verifylabs.core.util.Status
import com.fatdogs.verifylabs.data.base.PreferenceHelper
import com.fatdogs.verifylabs.databinding.FragmentSignUpBinding
import com.fatdogs.verifylabs.presentation.auth.AuthBaseActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FragmentSignUp : Fragment() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private val tag = "SignUpFragment"
    private val secretKey = "dZnxiwh!o*%cf!dNk3kP8R&P"
    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!

    private lateinit var signUpViewModel: SignUpViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        signUpViewModel = ViewModelProvider(this)[SignUpViewModel::class.java]

        setupClickListeners()
        setupObservers()
    }

    // Set up click listeners for UI elements
    private fun setupClickListeners() {
        binding.btnCreateAccount.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            // Validate input fields
            if (!validateInputs(fullName, email, username, password, confirmPassword)) {
                return@setOnClickListener
            }

            // Call signup API
            signUpViewModel.signUp(fullName, email, username, password, secretKey)
        }
    }

    // Validate user inputs and set error messages if invalid
    private fun validateInputs(
        fullName: String,
        email: String,
        username: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        if (fullName.isEmpty()) {
            binding.etFullName.error = "Enter full name"
            return false
        }
        if (email.isEmpty()) {
            binding.etEmail.error = "Enter email"
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Enter a valid email"
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
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            return false
        }
        return true
    }

    // Set up observers for ViewModel responses
    private fun setupObservers() {
        signUpViewModel.getSignUpResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    Toast.makeText(requireContext(), "Signing up...", Toast.LENGTH_SHORT).show()
                }
                Status.SUCCESS -> {
                    Toast.makeText(requireContext(), "Sign up successful!", Toast.LENGTH_SHORT).show()
                    resource.data?.let {
                        try {
                            Log.d(tag, "SignUp Response: ${resource.data}")
                            navigateToMainActivity()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Parsing error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Status.ERROR -> {
                    Toast.makeText(requireContext(), "Sign up failed: ${resource.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Navigate to AuthBaseActivity and clear back stack
    private fun navigateToMainActivity() {
        val intent = Intent(requireActivity(), AuthBaseActivity::class.java).apply {
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