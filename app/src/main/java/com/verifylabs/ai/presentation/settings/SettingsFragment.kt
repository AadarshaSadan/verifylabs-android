package com.verifylabs.ai.presentation.settings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Resource
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.data.repository.VerificationRepository
import com.verifylabs.ai.databinding.FragmentSettingsBinding
import com.verifylabs.ai.presentation.MainActivity
import com.verifylabs.ai.presentation.auth.login.ApiResponseLogin
import com.verifylabs.ai.presentation.auth.login.LoginViewModel
import com.verifylabs.ai.presentation.purchasecredits.PurchaseCreditsBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject lateinit var preferenceHelper: PreferenceHelper

    @Inject lateinit var verificationRepository: VerificationRepository

    private var _binding: FragmentSettingsBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var loginViewModel: LoginViewModel

    companion object {
        private const val TAG = "SettingsFragment"
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        (activity as? MainActivity)?.setAppBarVisibility(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        setupSeekBars()
        setupUi()
        setupObservers()
        setupClickListeners()
    }

    private fun setupUi() {
        binding.etUsername.setText(preferenceHelper.getUserName() ?: "")
        //        binding.etPassword.setText(preferenceHelper.getPassword() ?: "")
        //        binding.tvApiKey.text = "API KEY:${preferenceHelper.getApiKey()?.take(6) ?:
        // ""}....."

        // disable the purchase button until plans are loaded
        // disable the purchase button until plans are loaded
        // binding.btnPurchaseCredits.isEnabled = false
        // binding.btnPurchaseCredits.alpha = 0.5f

        val storeCredits = preferenceHelper.getCreditRemaining()
        val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(storeCredits)
        binding.tvCreditsRemaining.text = getString(R.string.credits_remaining, formattedCredits)

        // ---------- QUICK RECORD DURATION ----------
        val quickRecordSeconds = preferenceHelper.getQuickRecordDuration().takeIf { it > 0 } ?: 40

        binding.seekBar.max = 60
        binding.seekBar.progress = quickRecordSeconds
        binding.textView3.text = getString(R.string.seconds_format, quickRecordSeconds)

        // ---------- HISTORY RETENTION ----------
        val historyDays = preferenceHelper.getHistoryRetentionDays().takeIf { it > 0 } ?: 90

        binding.seekBar1.max = 90
        binding.seekBar1.progress = historyDays
        binding.textView5.text = getString(R.string.days_format, historyDays)
    }

    // --------------------------------------------------
    // SEEK BAR LOGIC
    // --------------------------------------------------
    private fun setupSeekBars() {

        // QUICK RECORD (10s – 60s)
        binding.seekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                    ) {
                        val safeValue = progress.coerceAtLeast(10)
                        binding.textView3.text = getString(R.string.seconds_format, safeValue)
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        val value = seekBar?.progress?.coerceAtLeast(10) ?: 10
                        preferenceHelper.setQuickRecordDuration(value)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                }
        )

        // HISTORY RETENTION (7d – 90d)
        binding.seekBar1.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                    ) {
                        val safeValue = progress.coerceAtLeast(7)
                        binding.textView5.text = getString(R.string.days_format, safeValue)
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        val value = seekBar?.progress?.coerceAtLeast(7) ?: 7
                        preferenceHelper.setHistoryRetentionDays(value)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                }
        )
    }

    private fun setupClickListeners() {
        binding.llCreditsContainer.setOnClickListener {
            showCreditsLoading(true)
            loginViewModel.checkCredits(
                    preferenceHelper.getUserName() ?: "",
                    preferenceHelper.getApiKey() ?: ""
            )
        }

        binding.cardManualCredentials.setOnClickListener {
            parentFragmentManager
                    .beginTransaction()
                    .setCustomAnimations(
                            R.anim.slide_in_right, // enter
                            R.anim.fade_out, // exit
                            R.anim.fade_in, // pop enter
                            R.anim.slide_out_right // pop exit
                    )
                    .hide(this)
                    .add(R.id.container, FragmentAccount())
                    .addToBackStack("FragmentAccount")
                    .commit()
        }

        //        binding.btnTestSave.setOnClickListener {
        //            val username = binding.etUsername.text.toString().trim()
        //            val password = binding.etPassword.text.toString().trim()
        //            if (username.isNotEmpty() && password.isNotEmpty()) {
        //                loginViewModel.login(username, password)
        //            } else {
        //                Toast.makeText(requireContext(), "Please enter username and password",
        // Toast.LENGTH_SHORT).show()
        //            }
        //        }
        //
        //        binding.llLogout.setOnClickListener {
        //            preferenceHelper.setIsLoggedIn(false)
        //            preferenceHelper.clear()
        //            startActivity(
        //                Intent(requireActivity(), OnboardingActivity::class.java).apply {
        //                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
        // Intent.FLAG_ACTIVITY_CLEAR_TASK
        //                }
        //            )
        //            requireActivity().finish()
        //        }

        binding.btnPurchaseCredits.setOnClickListener {
            val tag = "PurchaseCreditsBottomSheet"
            val existingSheet = parentFragmentManager.findFragmentByTag(tag)
            if (existingSheet == null) {
                val bottomSheet = PurchaseCreditsBottomSheet.newInstance()
                bottomSheet.show(parentFragmentManager, tag)
            } else {
                Log.d(TAG, "PurchaseCreditsBottomSheet is already shown")
            }
        }

        binding.btnAboutus.setOnClickListener {
            val bottomSheet = AboutUsBottomSheet()
            bottomSheet.show(parentFragmentManager, "AboutUsBottomSheet")
        }

        // Purge All History
        binding.llDeleteAccount.setOnClickListener {
            // Fetch size dynamically before showing dialog
            viewLifecycleOwner.lifecycleScope.launch {
                val sizeKb = verificationRepository.getTotalSizeKb()
                showPurgeConfirmationDialog(sizeKb)
            }
        }
    }

    private fun showPurgeConfirmationDialog(sizeKb: Long) {
        val dialogView =
                LayoutInflater.from(requireContext()).inflate(R.layout.dialog_purge_history, null)
        val dialog =
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setView(dialogView)
                        .setCancelable(true)
                        .create()

        dialog.window?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )

        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvMessage)
        val btnCancel = dialogView.findViewById<android.widget.TextView>(R.id.btnCancel)
        val btnPurge = dialogView.findViewById<android.widget.TextView>(R.id.btnPurge)

        tvMessage.text = getString(R.string.purge_confirmation_message, sizeKb)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnPurge.setOnClickListener {
            dialog.dismiss()
            performPurge()
        }

        dialog.show()
    }

    private fun performPurge() {
        viewLifecycleOwner.lifecycleScope.launch {
            val deleted = verificationRepository.purgeAll()
            //            Toast.makeText(requireContext(), getString(R.string.deleted_items_toast,
            // deleted), Toast.LENGTH_SHORT).show()
            updateStorageSize()
        }
    }

    private fun setupObservers() {
        loginViewModel.getLoginResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.SUCCESS -> {
                    resource.data?.let { dataJson ->
                        val response =
                                Gson().fromJson(dataJson.toString(), ApiResponseLogin::class.java)
                        preferenceHelper.setApiKey(response.apiKey)
                        preferenceHelper.setIsLoggedIn(true)
                        val totalCredits = response.credits + response.creditsMonthly
                        preferenceHelper.setCreditReamaining(totalCredits)

                        val formattedCredits =
                                NumberFormat.getNumberInstance(Locale.US).format(totalCredits)
                        binding.tvCreditsRemaining.text =
                                getString(R.string.credits_remaining, formattedCredits)
                    }
                }
                Status.ERROR -> {
                    Toast.makeText(
                                    requireContext(),
                                    resource.message ?: getString(R.string.login_failed),
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                }
                else -> {}
            }
        }

        loginViewModel.getCreditsResponse().observe(viewLifecycleOwner) {
                resource: com.verifylabs.ai.core.util.Resource<com.google.gson.JsonObject> ->
            if (resource.status == Status.SUCCESS) {
                showCreditsLoading(false)
                resource.data?.let { dataJson ->
                    val credits = dataJson.get("credits")?.asInt ?: 0
                    val creditsMonthly = dataJson.get("credits_monthly")?.asInt ?: 0
                    val totalCredits = credits + creditsMonthly
                    preferenceHelper.setCreditReamaining(totalCredits)
                    val formattedCredits =
                            NumberFormat.getNumberInstance(Locale.US).format(totalCredits)
                    binding.tvCreditsRemaining.text =
                            getString(R.string.credits_remaining, formattedCredits)
                    //                    Toast.makeText(requireContext(),
                    // getString(R.string.credits_updated), Toast.LENGTH_SHORT).show()
                }
            } else if (resource.status == Status.ERROR) {
                showCreditsLoading(false)
                //                Toast.makeText(
                //                                requireContext(),
                //                                resource.message ?:
                // getString(R.string.failed_refresh_credits),
                //                                Toast.LENGTH_SHORT
                //                        )
                //                        .show()
            }
        }
    }

    private fun showCreditsLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.llCreditsBalance.visibility = View.GONE
            binding.llCreditsLoading.visibility = View.VISIBLE
        } else {
            binding.llCreditsBalance.visibility = View.VISIBLE
            binding.llCreditsLoading.visibility = View.GONE
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            // Restore Settings colors
            (activity as? MainActivity)?.setAppBarVisibility(true)
            (activity as? MainActivity)?.setBottomNavVisibility(true)

            (activity as? MainActivity)?.updateStatusBarColor(R.color.ios_settings_background)
            (activity as? MainActivity)?.updateBottomNavColor(R.color.ios_settings_background, 0f)
            (activity as? MainActivity)?.updateAppBarColor(R.color.ios_settings_background)
            (activity as? MainActivity)?.updateMainBackgroundColor(R.color.ios_settings_background)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateStorageSize() {
        viewLifecycleOwner.lifecycleScope.launch {
            val sizeKb = verificationRepository.getTotalSizeKb()
            binding.tvStorageSize?.text = getString(R.string.size_kb_format, sizeKb)
            Log.d(TAG, "Total history storage: $sizeKb KB")
        }
    }

    private fun cleanupOldHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val days = preferenceHelper.getHistoryRetentionDays()
            if (days > 0) {
                val deleted = verificationRepository.deleteOlderThan(days)
                if (deleted > 0) {
                    Log.d(TAG, "Cleaned up $deleted old history items (older than $days days)")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Show bottom nav again
        (activity as? MainActivity)?.setAppBarVisibility(true)
        (activity as? MainActivity)?.setBottomNavVisibility(true)

        // Set status bar color for this fragment
        (activity as? MainActivity)?.updateStatusBarColor(R.color.ios_settings_background)
        // Pass 0f elevation to remove the white surface tint in dark mode
        (activity as? MainActivity)?.updateBottomNavColor(R.color.ios_settings_background, 0f)
        (activity as? MainActivity)?.updateAppBarColor(R.color.ios_settings_background)
        (activity as? MainActivity)?.updateMainBackgroundColor(R.color.ios_settings_background)

        // Clean up old history based on retention setting
        cleanupOldHistory()

        // Update storage size display
        updateStorageSize()
    }
}
