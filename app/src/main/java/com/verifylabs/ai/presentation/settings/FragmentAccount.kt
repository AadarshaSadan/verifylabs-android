package com.verifylabs.ai.presentation.settings

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Constants
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.data.repository.VerificationRepository
import com.verifylabs.ai.databinding.FragmentAccountBinding
import com.verifylabs.ai.presentation.MainActivity
import com.verifylabs.ai.presentation.onboarding.OnboardingActivity
import com.verifylabs.ai.presentation.settings.viewmodel.ViewModelgetAccountInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FragmentAccount : Fragment(), ChangePasswordBottomSheet.ChangePasswordCallback {

    @Inject lateinit var preferenceHelper: PreferenceHelper

    @Inject lateinit var verificationRepository: VerificationRepository

    private lateinit var viewModel: ViewModelgetAccountInfo

    private var _binding: FragmentAccountBinding? = null
    private val binding
        get() = _binding!!

    private var originalFullName: String = ""
    private var originalEmail: String = ""

    companion object {
        private const val TAG = "FragmentAccount"
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        initViewModel()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        setupTextWatchers()
        setupClickListeners()
        observeViewModel()

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

    private fun observeViewModel() {
        // Account Info Observer
        viewModel.accountInfoObserver.observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.SUCCESS -> {
                    resource.data?.let { json ->
                        val username = preferenceHelper.getUserName() ?: ""
                        binding.tvUsernameValue.text = username

                        val fullName = json.get("name")?.asString ?: ""
                        val email = json.get("email")?.asString ?: ""

                        originalFullName = fullName
                        originalEmail = email

                        binding.etFullNameValue.setText(fullName)
                        binding.etEmailValue.setText(email)
                    }
                }
                Status.ERROR -> {
                    // Silent error for background fetch
                }
                Status.LOADING -> {
                    // Loading state
                }
                Status.INSUFFICIENT_CREDITS -> {
                    // No-op
                }
            }
        }

        // Update Profile Observer
        viewModel.updateProfileObserver.observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.SUCCESS -> {
                    binding.pbFullName.visibility = View.GONE
                    binding.pbEmail.visibility = View.GONE
                    binding.tvFullNameNote.visibility = View.GONE
                    binding.tvEmailNote.visibility = View.GONE

                    Toast.makeText(
                                    requireContext(),
                                    "Profile updated successfully",
                                    Toast.LENGTH_SHORT
                            )
                            .show()

                    // Update original values
                    originalFullName = binding.etFullNameValue.text.toString().trim()
                    originalEmail = binding.etEmailValue.text.toString().trim()
                }
                Status.ERROR -> {
                    binding.pbFullName.visibility = View.GONE
                    binding.pbEmail.visibility = View.GONE
                    Toast.makeText(
                                    requireContext(),
                                    resource.message ?: "Update failed",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
                Status.LOADING -> {
                    // Loading handled by individual progress bars
                }
                Status.INSUFFICIENT_CREDITS -> {
                    // No-op
                }
            }
        }

        // Delete Account Observer
        viewModel.deleteAccountObserver.observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.SUCCESS -> {
                    Toast.makeText(
                                    requireContext(),
                                    "Account deleted successfully",
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                    logout()
                }
                Status.ERROR -> {
                    Toast.makeText(
                                    requireContext(),
                                    resource.message ?: "Delete failed",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
                Status.LOADING -> {
                    // Show loading
                }
                Status.INSUFFICIENT_CREDITS -> {
                    // No-op
                }
            }
        }

        viewModel.getErrorMessage.observe(viewLifecycleOwner) { msg ->
            if (msg.isNotEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupUi() {
        // Hide bottom nav if MainActivity
        (activity as? MainActivity)?.setBottomNavVisibility(false)
        (activity as? MainActivity)?.setAppBarVisibility(false)

        // Pre-fill username
        binding.tvUsernameValue.text = preferenceHelper.getUserName() ?: ""
    }

    private fun setupTextWatchers() {
        // Full Name TextWatcher
        binding.etFullNameValue.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}
                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {}
                    override fun afterTextChanged(s: Editable?) {
                        val currentName = s.toString().trim()
                        if (currentName != originalFullName && currentName.isNotEmpty()) {
                            binding.tvFullNameNote.visibility = View.VISIBLE
                        } else {
                            binding.tvFullNameNote.visibility = View.GONE
                        }
                    }
                }
        )

        // Email TextWatcher
        binding.etEmailValue.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}
                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {}
                    override fun afterTextChanged(s: Editable?) {
                        val currentEmail = s.toString().trim()
                        if (currentEmail != originalEmail &&
                                        currentEmail.isNotEmpty() &&
                                        isValidEmail(currentEmail)
                        ) {
                            binding.tvEmailNote.visibility = View.VISIBLE
                        } else {
                            binding.tvEmailNote.visibility = View.GONE
                        }
                    }
                }
        )
    }

    private fun setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener { requireActivity().onBackPressed() }

        // Save Full Name
        binding.tvFullNameNote.setOnClickListener {
            val newName = binding.etFullNameValue.text.toString().trim()
            if (newName.isNotEmpty() && newName != originalFullName) {
                binding.tvFullNameNote.visibility = View.GONE
                binding.pbFullName.visibility = View.VISIBLE

                val secretKey = Constants.SECRET_KEY
                val apiKey = preferenceHelper.getApiKey() ?: ""
                viewModel.updateProfile(secretKey, apiKey, name = newName, email = null)
            }
        }

        // Save Email
        binding.tvEmailNote.setOnClickListener {
            val newEmail = binding.etEmailValue.text.toString().trim()
            if (newEmail.isNotEmpty() && newEmail != originalEmail && isValidEmail(newEmail)) {
                binding.tvEmailNote.visibility = View.GONE
                binding.pbEmail.visibility = View.VISIBLE

                val secretKey = Constants.SECRET_KEY
                val apiKey = preferenceHelper.getApiKey() ?: ""
                viewModel.updateProfile(secretKey, apiKey, name = null, email = newEmail)
            }
        }

        // Change Password
        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }

        // Logout
        binding.btnLogout.setOnClickListener { showLogoutConfirmation() }

        // Delete Account
        binding.btnDeleteAccount.setOnClickListener { showDeleteAccountConfirmation() }
    }

    private fun showChangePasswordDialog() {
        val bottomSheet = ChangePasswordBottomSheet()
        bottomSheet.show(childFragmentManager, "ChangePasswordBottomSheet")
    }

    override fun onPasswordChanged(newPassword: String) {
        // Call API to update password
        val secretKey = Constants.SECRET_KEY
        val apiKey = preferenceHelper.getApiKey() ?: ""
        viewModel.changePassword(secretKey, apiKey, newPassword)

        // Update locally
        preferenceHelper.setPassword(newPassword)
        Toast.makeText(requireContext(), "Password changed successfully", Toast.LENGTH_SHORT).show()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { dialog, _ ->
                    logout()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
                .show()
    }

    private fun showDeleteAccountConfirmation() {
        val dialogView =
                LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_account, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPasswordConfirm)

        AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage(
                        "This action cannot be undone. All your data will be permanently deleted."
                )
                .setView(dialogView)
                .setPositiveButton("Delete") { dialog, _ ->
                    val password = etPassword.text.toString()
                    val storedPassword = preferenceHelper.getPassword() ?: ""

                    if (password != storedPassword) {
                        Toast.makeText(requireContext(), "Incorrect password", Toast.LENGTH_SHORT)
                                .show()
                    } else {
                        val secretKey = Constants.SECRET_KEY
                        val username = preferenceHelper.getUserName() ?: ""
                        viewModel.deleteAccount(secretKey, username, password)
                        dialog.dismiss()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
                .show()
    }

    private fun logout() {
        // Purge history on logout (iOS parity)
        viewLifecycleOwner.lifecycleScope.launch {
            verificationRepository.purgeAll()

            preferenceHelper.setIsLoggedIn(false)
            preferenceHelper.clear()

            startActivity(
                    Intent(requireActivity(), OnboardingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
            )
            requireActivity().finish()
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
