package com.fatdogs.verifylabs.presentation.auth.login

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.fatdogs.verifylabs.R
import com.fatdogs.verifylabs.core.util.Resource
import com.fatdogs.verifylabs.core.util.Status
import com.fatdogs.verifylabs.data.base.PreferenceHelper
import com.fatdogs.verifylabs.databinding.FragmentLoginBinding
import com.fatdogs.verifylabs.presentation.MainActivity
import com.fatdogs.verifylabs.presentation.auth.signup.FragmentSignUp
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var loginViewModel: LoginViewModel
    private val tag = "LoginFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        setupClickListeners()
        setupObservers()
    }

    // Set up click listeners for UI elements
    private fun setupClickListeners() {
        // Handle login button click
        binding.btnGetStarted.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            // Validate input fields
            if (!validateInputs(username, password)) {
                return@setOnClickListener
            }

            // Save credentials to preferences
            preferenceHelper.setUserName(username)
            preferenceHelper.setPassword(password)

            // Call login API
            loginViewModel.login(username, password)
        }

        // Navigate to SignUp fragment
        binding.btnCreateAccount.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.auth_fragment_container, FragmentSignUp())
                .addToBackStack(null)
                .commit()
        }
    }

    // Validate user inputs and set error messages if invalid
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

    // Set up observers for ViewModel responses
    private fun setupObservers() {
        loginViewModel.getLoginResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    Toast.makeText(requireContext(), "Logging in...", Toast.LENGTH_SHORT).show()
                }
                Status.SUCCESS -> {
                    resource.data?.let {
                        try {
                            val response = Gson().fromJson(it.toString(), apiResponseLogin::class.java)
                            preferenceHelper.setApiKey(response.apiKey)
                            preferenceHelper.setIsLoggedIn(true)
                            preferenceHelper.setCreditReamaining(response.credits+response.creditsMonthly)
                            Toast.makeText(requireContext(), "Login Successful", Toast.LENGTH_SHORT).show()
                            navigateToMainActivity()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Parsing error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Status.ERROR -> {
                    Toast.makeText(requireContext(), "Login Failed: ${resource.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Navigate to MainActivity and clear back stack
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