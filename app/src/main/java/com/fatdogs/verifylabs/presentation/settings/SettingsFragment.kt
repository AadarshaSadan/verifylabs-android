package com.fatdogs.verifylabs.presentation.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.fatdogs.verifylabs.R
import com.fatdogs.verifylabs.core.util.Resource
import com.fatdogs.verifylabs.core.util.Status
import com.fatdogs.verifylabs.data.base.PreferenceHelper
import com.fatdogs.verifylabs.databinding.FragmentSettingsBinding
import com.fatdogs.verifylabs.presentation.auth.AuthBaseActivity
import com.fatdogs.verifylabs.presentation.auth.login.LoginViewModel
import com.fatdogs.verifylabs.presentation.auth.login.apiResponseLogin
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var loginViewModel: LoginViewModel

    companion object {
        private const val TAG = "SettingsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            _binding = FragmentSettingsBinding.inflate(inflater, container, false)
            return binding.root
        } catch (e: Exception) {
            Log.e(TAG, "Error inflating view: ${e.message}", e)
            return null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]
            setupUi()
            setupObservers()
            setupClickListeners()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}", e)
            if (isAdded) {
                Toast.makeText(requireContext(), "Error initializing settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupUi() {
        try {
            binding.etUsername.setText(preferenceHelper.getUserName() ?: "")
            binding.etPassword.setText(preferenceHelper.getPassword() ?: "")
            binding.tvApiKey.text = "API KEY: ${preferenceHelper.getApiKey()?.take(6) ?: ""}....."
            binding.tvCreditsRemaining.text = "Credits Remaining: ${preferenceHelper.getCreditRemaining() ?: 0}"
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up UI: ${e.message}", e)
            if (isAdded) {
                Toast.makeText(requireContext(), "Error loading settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        try {
            binding.llCreditsInfo.setOnClickListener {
                try {
                    loginViewModel.checkCredits(
                        preferenceHelper.getUserName() ?: "",
                        preferenceHelper.getApiKey() ?: ""
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking credits: ${e.message}", e)
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Error checking credits", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            binding.btnTestSave.setOnClickListener {
                try {
                    val username = binding.etUsername.text.toString().trim()
                    val password = binding.etPassword.text.toString().trim()

                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        loginViewModel.login(username, password)
                    } else {
                        Log.w(TAG, "Username or password is empty")
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Please enter username and password", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling save button click: ${e.message}", e)
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Error saving settings", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            binding.llLogout.setOnClickListener {
                try {
                    preferenceHelper.setIsLoggedIn(false)
                    preferenceHelper.clear()
                    if (isAdded) {
                        startActivity(
                            Intent(requireActivity(), AuthBaseActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                        requireActivity().finish()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling logout: ${e.message}", e)
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Error logging out", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up click listeners: ${e.message}", e)
            if (isAdded) {
                Toast.makeText(requireContext(), "Error initializing click listeners", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        try {
            loginViewModel.getCreditsResponse().observe(viewLifecycleOwner) { resource ->
                when (resource.status) {
                    Status.LOADING -> {
                        Log.d(TAG, "Checking credits...")
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Checking credits...", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Status.SUCCESS -> {
                        resource.data?.let { dataJson ->
                            try {
                                val response = Gson().fromJson(dataJson.toString(), ApiResponseCredits::class.java)
                                val totalCredits = (response.credits ?: 0) + (response.creditsMonthly ?: 0)
                                binding.tvCreditsRemaining.text = "Credits Remaining: $totalCredits"
                                preferenceHelper.setCreditReamaining(totalCredits) // Fixed typo
                                Log.d(TAG, "Credits updated: $totalCredits")
                            } catch (e: Exception) {
                                Log.e(TAG, "Credits parsing error: ${e.message}", e)
                                if (isAdded) {
                                    Toast.makeText(requireContext(), "Error parsing credits data", Toast.LENGTH_SHORT).show()
                                } else {

                                }
                            }
                        } ?: run {
                            Log.w(TAG, "Credits response success but data is null")
                            if (isAdded) {
                                Toast.makeText(requireContext(), "No credits data available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Status.ERROR -> {
                        Log.e(TAG, "Credits error: ${resource.message}")
                        if (isAdded) {
                            Toast.makeText(requireContext(), resource.message ?: "Failed to get credits", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            loginViewModel.getLoginResponse().observe(viewLifecycleOwner) { resource ->
                when (resource.status) {
                    Status.LOADING -> {
                        Log.d(TAG, "Logging in...")
                        binding.tvTestSave.text = "Saving..."
                        binding.btnTestSave.isEnabled = false
                    }
                    Status.SUCCESS -> {
                        resource.data?.let { dataJson ->
                            try {
                                val response = Gson().fromJson(dataJson.toString(), apiResponseLogin::class.java)
                                preferenceHelper.setApiKey(response.apiKey)
                                preferenceHelper.setIsLoggedIn(true)
                                preferenceHelper.setCreditReamaining(response.credits) // Fixed typo
                                binding.tvApiKey.text = "API KEY: ${preferenceHelper.getApiKey()?.take(6) ?: ""}....."
                                binding.tvCreditsRemaining.text = "Credits Remaining: ${preferenceHelper.getCreditRemaining() ?: 0}"
                                binding.tvTestSave.text = "Saved"
                                binding.btnTestSave.isEnabled = true
                                Log.d(TAG, "Login successful, API key: ${response.apiKey}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Login parsing error: ${e.message}", e)
                            }
                        } ?: run {
                            Log.w(TAG, "Login response success but data is null")
                            binding.tvTestSave.text = "Error! Try Again"
                            binding.btnTestSave.background = ContextCompat.getDrawable(requireContext(), R.drawable.drawable_verify_background_btn_failed_likely_red)
                            binding.btnTestSave.isEnabled = true
                            if (isAdded) {
                                Toast.makeText(requireContext(), "No login data available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Status.ERROR -> {
                        Log.e(TAG, "Login error: ${resource.message}")
                        binding.tvTestSave.text = "Error! Try Again"
                        binding.btnTestSave.isEnabled = true
                        if (isAdded) {
                            Toast.makeText(requireContext(), resource.message ?: "Login failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up observers: ${e.message}", e)
            if (isAdded) {
                Toast.makeText(requireContext(), "Error initializing observers", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}