package com.verifylabs.ai.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.FragmentHomeBinding
import com.verifylabs.ai.presentation.auth.login.LoginViewModel
import com.google.gson.JsonElement
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.text.NumberFormat
import java.util.Locale


@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var loginViewModel: LoginViewModel

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set welcome message
//        binding.tvWelcome.text = "Welcome, ${preferenceHelper.getUserName() ?: "Guest"}"

        initViewModels()
        initUi()
        observeViewModelData()
        apiCheckCredits()
    }

    private fun initUi() {
        val storeCredits = preferenceHelper.getCreditRemaining()
        val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(storeCredits)
        binding.llCreditsInfo.tvCreditsRemaining.text = getString(R.string.credits_remaining, formattedCredits)
        binding.llCreditsInfo.progressCredits.visibility = View.GONE
        binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE

        // Add Click Listener to Refresh
        binding.llCreditsInfo.root.setOnClickListener {
            apiCheckCredits()
        }
    }

    private fun initViewModels() {
        loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]
    }

    private fun apiCheckCredits() {
        val username = preferenceHelper.getUserName()
        val apiKey = preferenceHelper.getApiKey()
        if (username.isNullOrEmpty() || apiKey.isNullOrEmpty()) {
            binding.llCreditsInfo.progressCredits.visibility = View.GONE
            binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
            binding.llCreditsInfo.tvCreditsRemaining.text = "Credits: Invalid credentials"
            Toast.makeText(requireContext(), "Invalid credentials. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }
        
        // Show loading state
        binding.llCreditsInfo.progressCredits.visibility = View.VISIBLE
        binding.llCreditsInfo.tvCreditsRemaining.visibility = View.GONE
        
        loginViewModel.checkCredits(username, apiKey)
    }

    private fun observeViewModelData() {
        // Observe credits response
        loginViewModel.getCreditsResponse().observe(viewLifecycleOwner) { response ->
            when (response.status) {
                 Status.SUCCESS -> {
                    val credits = response.data?.get("credits")?.asIntSafe() ?: 0
                    val creditsMonthly = response.data?.get("credits_monthly")?.asIntSafe() ?: 0
                    binding.llCreditsInfo.progressCredits.visibility = View.GONE
                    binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
                    val totalCredits = creditsMonthly + credits
                    preferenceHelper.setCreditReamaining(totalCredits)
                    
                    val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(totalCredits)
                    binding.llCreditsInfo.tvCreditsRemaining.text = getString(R.string.credits_remaining, formattedCredits)
                }
                Status.ERROR -> {
                    binding.llCreditsInfo.progressCredits.visibility = View.GONE
                    binding.llCreditsInfo.tvCreditsRemaining.visibility = View.VISIBLE
                    // Fallback to stored credits on error
                    val storeCredits = preferenceHelper.getCreditRemaining()
                    val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(storeCredits)
                    binding.llCreditsInfo.tvCreditsRemaining.text = getString(R.string.credits_remaining, formattedCredits)
                    Toast.makeText(requireContext(), "Failed to refresh credits", Toast.LENGTH_SHORT).show()
                }
                 Status.LOADING -> {
                    // Handled in apiCheckCredits, but good to keep sync
                    binding.llCreditsInfo.progressCredits.visibility = View.VISIBLE
                    binding.llCreditsInfo.tvCreditsRemaining.visibility = View.GONE
                }
            }
        }

        // Observe error messages
        loginViewModel.getErrorMessage().observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                // Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }

        // Observe loading state
        loginViewModel.getLoading().observe(viewLifecycleOwner) { isLoading ->
             // handled by status
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Extension function to safely parse JsonElement to Int
private fun JsonElement.asIntSafe(): Int {
    return try {
        if (this.isJsonPrimitive && this.asJsonPrimitive.isNumber) {
            this.asInt
        } else {
            0
        }
    } catch (e: Exception) {
        0
    }
}