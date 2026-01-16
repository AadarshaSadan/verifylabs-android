package com.verifylabs.ai.presentation.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Constants
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.FragmentSettingsBinding
import com.verifylabs.ai.presentation.auth.login.ApiResponseLogin
import com.verifylabs.ai.presentation.auth.login.LoginViewModel
import com.verifylabs.ai.presentation.onboarding.OnboardingActivity
import com.verifylabs.ai.presentation.plan.PlanResponse
import com.verifylabs.ai.presentation.purchasecredits.PurchaseCreditsBottomSheet
import com.google.gson.Gson
import com.verifylabs.ai.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var planViewModel: PlanViewModel
    private var planList: List<PlanResponse> = emptyList()

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
        planViewModel = ViewModelProvider(this)[PlanViewModel::class.java]


        setupSeekBars()
        setupUi()
        setupObservers()
        setupClickListeners()
        fetchPlans()
    }

    private fun setupUi() {
        binding.etUsername.setText(preferenceHelper.getUserName() ?: "")
//        binding.etPassword.setText(preferenceHelper.getPassword() ?: "")
//        binding.tvApiKey.text = "API KEY:${preferenceHelper.getApiKey()?.take(6) ?: ""}....."

        // disable the purchase button until plans are loaded
        binding.btnPurchaseCredits.isEnabled = false
        binding.btnPurchaseCredits.alpha = 0.5f

        val storeCredits = preferenceHelper.getCreditRemaining()
        val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(storeCredits)
        binding.tvCreditsRemaining.text = getString(R.string.credits_remaining, formattedCredits)
        binding.tvCreditsRemaining.visibility = View.VISIBLE


        // ---------- QUICK RECORD DURATION ----------
        val quickRecordSeconds =
            preferenceHelper.getQuickRecordDuration().takeIf { it > 0 } ?: 40

        binding.seekBar.max = 60
        binding.seekBar.progress = quickRecordSeconds
        binding.textView3.text = "${quickRecordSeconds}s"

        // ---------- HISTORY RETENTION ----------
        val historyDays =
            preferenceHelper.getHistoryRetentionDays().takeIf { it > 0 } ?: 90

        binding.seekBar1.max = 90
        binding.seekBar1.progress = historyDays
        binding.textView5.text = "${historyDays}d"
    }


    // --------------------------------------------------
    // SEEK BAR LOGIC
    // --------------------------------------------------
    private fun setupSeekBars() {

        // QUICK RECORD (10s – 60s)
        binding.seekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                val safeValue = progress.coerceAtLeast(10)
                binding.textView3.text = "${safeValue}s"
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = seekBar?.progress?.coerceAtLeast(10) ?: 10
                preferenceHelper.setQuickRecordDuration(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        })

        // HISTORY RETENTION (7d – 90d)
        binding.seekBar1.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                val safeValue = progress.coerceAtLeast(7)
                binding.textView5.text = "${safeValue}d"
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = seekBar?.progress?.coerceAtLeast(7) ?: 7
                preferenceHelper.setHistoryRetentionDays(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        })
    }


    private fun setupClickListeners() {
        binding.llCreditsInfo.setOnClickListener {
            loginViewModel.checkCredits(
                preferenceHelper.getUserName() ?: "",
                preferenceHelper.getApiKey() ?: ""
            )
        }


        binding.cardManualCredentials.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right,  // enter
                    R.anim.fade_out,        // exit
                    R.anim.fade_in,         // pop enter
                    R.anim.slide_out_right  // pop exit
                )
                .replace(R.id.container, FragmentAccount())
                .addToBackStack("FragmentAccount")
                .commit()
        }



//        binding.btnTestSave.setOnClickListener {
//            val username = binding.etUsername.text.toString().trim()
//            val password = binding.etPassword.text.toString().trim()
//            if (username.isNotEmpty() && password.isNotEmpty()) {
//                loginViewModel.login(username, password)
//            } else {
//                Toast.makeText(requireContext(), "Please enter username and password", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        binding.llLogout.setOnClickListener {
//            preferenceHelper.setIsLoggedIn(false)
//            preferenceHelper.clear()
//            startActivity(
//                Intent(requireActivity(), OnboardingActivity::class.java).apply {
//                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
    }

    private fun setupObservers() {
        loginViewModel.getLoginResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
//                    binding.tvTestSave.text = "Saving..."
//                    binding.btnTestSave.isEnabled = false
                }
                Status.SUCCESS -> {
                    resource.data?.let { dataJson ->
                        val response = Gson().fromJson(dataJson.toString(), ApiResponseLogin::class.java)
                        preferenceHelper.setApiKey(response.apiKey)
                        preferenceHelper.setIsLoggedIn(true)
                        preferenceHelper.setCreditReamaining(response.credits)
//                        binding.tvApiKey.text = "API KEY: ${response.apiKey.take(6)}....."
//                        binding.tvTestSave.text = "Saved"
//                        binding.btnTestSave.isEnabled = true
                        val storeCredits = preferenceHelper.getCreditRemaining()
                        val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(storeCredits)
                        binding.tvCreditsRemaining.text = getString(R.string.credits_remaining, formattedCredits)
                    }
                }
                Status.ERROR -> {
//                    binding.tvTestSave.text = "Error! Try Again"
//                    binding.btnTestSave.isEnabled = true
                    Toast.makeText(requireContext(), resource.message ?: "Login failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchPlans() {
        planViewModel.getPlans(Constants.SECRET_KEY)
        planViewModel.plansObserver.observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    Log.d(TAG, "Fetching plans...")
                }
                Status.SUCCESS -> {
                    resource.data?.let {
                        planList = it
                        Log.d(TAG, "Plans fetched: $planList")
                        // enable purchase button
                        binding.btnPurchaseCredits.isEnabled = true
                        binding.btnPurchaseCredits.alpha = 1f
                    }
                }
                Status.ERROR -> {
                    Log.e(TAG, "Error fetching plans: ${resource.message}")
                    Toast.makeText(requireContext(), "Failed to load plans", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    override fun onResume() {
        super.onResume()
        // Show bottom nav again
        (activity as? MainActivity)?.setAppBarVisibility(true)
        (activity as? MainActivity)?.setBottomNavVisibility(true)
    }
}
