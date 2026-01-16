package com.verifylabs.ai.presentation.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Constants
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.FragmentAccountBinding
import com.verifylabs.ai.presentation.MainActivity
import com.verifylabs.ai.presentation.onboarding.OnboardingActivity
import com.verifylabs.ai.presentation.settings.viewmodel.ViewModelgetAccountInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FragmentAccount : Fragment() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private lateinit var viewModel: ViewModelgetAccountInfo

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val TAG = "FragmentAccount"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        initViewModel()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        setupClickListeners()
        observeAccountInfo()

        // Fetch account info
        val secretKey = Constants.SECRET_KEY
        val username = preferenceHelper.getUserName() ?: ""
        if (secretKey.isNotEmpty() && username.isNotEmpty()) {
            viewModel.getAccountInfo(secretKey, username)
        }
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[ViewModelgetAccountInfo::class.java]
    }

    private fun observeAccountInfo() {
        viewModel.accountInfoObserver.observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.SUCCESS -> {
                    resource.data?.let { json ->
                        binding.tvUsernameValue.text = preferenceHelper.getUserName() ?: ""
                        binding.etFullNameValue.setText(json.get("name")?.asString ?: "")
                        binding.etEmailValue.setText(json.get("email")?.asString ?: "")
                    }
                }
                Status.ERROR -> {
                    Toast.makeText(
                        requireContext(),
                        resource.message ?: "Error fetching account info",
                        Toast.LENGTH_LONG
                    ).show()
                }
                Status.LOADING -> {
                    // Optional: show ProgressBar if you add one in XML
                }
            }
        }

        viewModel.getErrorMessage.observe(viewLifecycleOwner) { msg ->
            if (msg.isNotEmpty()) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }

        viewModel.getLoading.observe(viewLifecycleOwner) { isLoading ->
            // Optional: binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun setupUi() {
        // Hide bottom nav if MainActivity
        (activity as? MainActivity)?.setBottomNavVisibility(false)
        (activity as? MainActivity)?.setAppBarVisibility(false)

        // Pre-fill profile info from preferences
        binding.tvUsernameValue.text = preferenceHelper.getUserName() ?: ""
      //  binding.etFullNameValue.setText(preferenceHelper.getFullName() ?: "")
    //    binding.etEmailValue.setText(preferenceHelper.getEmail() ?: "")
    }

    private fun setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener { requireActivity().onBackPressed() }

        // Logout
        binding.btnLogout.setOnClickListener {
            preferenceHelper.setIsLoggedIn(false)
            preferenceHelper.clear()
            startActivity(
                Intent(requireActivity(), OnboardingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            requireActivity().finish()
        }

        // Delete account
        binding.btnDeleteAccount.setOnClickListener {
            Toast.makeText(requireContext(), "Delete account clicked", Toast.LENGTH_SHORT).show()
        }

        // Change password
        binding.btnChangePassword.setOnClickListener {
            Toast.makeText(requireContext(), "Change password clicked", Toast.LENGTH_SHORT).show()

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
