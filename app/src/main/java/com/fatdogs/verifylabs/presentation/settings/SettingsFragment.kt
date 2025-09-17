package com.fatdogs.verifylabs.presentation.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]
        setupUi()
        setupObservers()
        setupClickListeners()
    }

    private fun setupUi() {
        binding.etUsername.setText(preferenceHelper.getUserName())
        binding.etPassword.setText(preferenceHelper.getPassword())
        binding.tvApiKey.text = "API KEY: ${preferenceHelper.getApiKey()?.take(6) ?: ""}....."
        binding.tvCreditsRemaining.text = "Credits Remaining: ${preferenceHelper.getCreditRemaining()}"
    }

    private fun setupClickListeners() {
        binding.llCreditsInfo.setOnClickListener {
            loginViewModel.checkCredits(
                preferenceHelper.getUserName().toString(),
                preferenceHelper.getApiKey() ?: ""
            )
        }

        binding.btnTestSave.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                loginViewModel.login(username, password)
            }
        }

        binding.llLogout.setOnClickListener {
            preferenceHelper.setIsLoggedIn(false)
            preferenceHelper.clear()
            startActivity(
                Intent(requireActivity(), AuthBaseActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            requireActivity().finish()
        }
    }

    private fun setupObservers() {
        loginViewModel.getCreditsResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    Toast.makeText(requireContext(), "Checking credits...", Toast.LENGTH_SHORT).show()
                }
                Status.SUCCESS -> {
                    resource.data?.let { dataJson ->
                        try {
                            val response = Gson().fromJson(dataJson.toString(), ApiResponseCredits::class.java)
                            val totalCredits = (response.credits ?: 0) + (response.creditsMonthly ?: 0)
                            binding.tvCreditsRemaining.text = "Credits Remaining: $totalCredits"
                            preferenceHelper.setCreditReamaining(totalCredits)
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Parsing error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Status.ERROR -> {
                    Toast.makeText(requireContext(), resource.message ?: "Failed to get credits", Toast.LENGTH_SHORT).show()
                }
            }
        }

        loginViewModel.getLoginResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    binding.btnTestSave.text = "Saving..."
                    binding.btnTestSave.isEnabled = false
                }
                Status.SUCCESS -> {
                    resource.data?.let {
                        try {
                            val response = Gson().fromJson(resource.data.toString(), apiResponseLogin::class.java)
                            preferenceHelper.setApiKey(response.apiKey)
                            preferenceHelper.setIsLoggedIn(true)
                            preferenceHelper.setCreditReamaining(response.credits)
                            binding.tvApiKey.text = "API KEY: ${preferenceHelper.getApiKey()?.take(6) ?: ""}....."
                            binding.tvCreditsRemaining.text = "Credits Remaining: ${preferenceHelper.getCreditRemaining()}"
                            binding.btnTestSave.text = "Saved"
                            binding.btnTestSave.isEnabled = true
                            Toast.makeText(requireContext(), "${resource.data}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Parsing error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Status.ERROR -> {
                    binding.btnTestSave.text = "Error! Try Again"
                    binding.btnTestSave.isEnabled = true
                    Toast.makeText(requireContext(), resource.message ?: "Login failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}