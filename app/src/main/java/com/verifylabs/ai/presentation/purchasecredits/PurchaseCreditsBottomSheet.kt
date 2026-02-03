package com.verifylabs.ai.presentation.purchasecredits

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.purchasePackageWith
import com.revenuecat.purchases.restorePurchasesWith
import com.verifylabs.ai.R
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.data.network.ApiRepository
import com.verifylabs.ai.databinding.FragmentPurchaseCreditsBottomSheetBinding
import com.verifylabs.ai.presentation.plan.CreditPackage
import com.verifylabs.ai.presentation.plan.CreditPackageAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PurchaseCreditsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentPurchaseCreditsBottomSheetBinding? = null
    private val binding
        get() = _binding!!

    @Inject lateinit var preferenceHelper: PreferenceHelper
    @Inject lateinit var repository: ApiRepository
    private var adapter: CreditPackageAdapter? = null
    private val TAG = "PurchaseCreditsBS"

    override fun getTheme() = R.style.FullScreenBottomSheetDialogTheme

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPurchaseCreditsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()

        setupFullScreenBottomSheet()
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupRecyclerView()
        loadOfferings()
        refreshBalance()

        //        (dialog as? BottomSheetDialog)?.apply {
        //            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        //            behavior.skipCollapsed = true
        //        }
        //
        //        override fun onStart() {
        //            super.onStart()
        //            (dialog as?
        // BottomSheetDialog)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
        //                sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        //                sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT) // <--
        // important
        //                BottomSheetBehavior.from(sheet).apply {
        //                    state = BottomSheetBehavior.STATE_EXPANDED
        //                    isDraggable = true
        //                    skipCollapsed = true
        //                }
        //        }

        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun setupUI() {
        val initialBalance = preferenceHelper.getCreditRemaining()
        binding.tvCurrentBalance.text = if (initialBalance == 0) "--" else "$initialBalance"
        binding.restoreContainer.setOnClickListener { restorePurchases() }

        binding.restoreContainer.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> v.alpha = 0.5f
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                        v.animate().alpha(1f).setDuration(150).start()
            }
            false
        }

        binding.btnRetry.setOnClickListener { loadOfferings() }
    }

    private fun refreshBalance() {
        val username = preferenceHelper.getUserName() ?: return
        val apiKey = preferenceHelper.getApiKey() ?: return

        binding.balanceProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = repository.checkCredits(username, apiKey)
                if (response.isSuccessful && response.body() != null) {
                    val credits = response.body()!!.get("credits")?.asInt ?: 0
                    val creditsMonthly = response.body()!!.get("credits_monthly")?.asInt ?: 0
                    val totalCredits = credits + creditsMonthly

                    preferenceHelper.setCreditReamaining(totalCredits)
                    binding.tvCurrentBalance.text = "$totalCredits"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh balance: ${e.message}")
            } finally {
                binding.balanceProgressBar.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = CreditPackageAdapter { selectedPackage ->
            Purchases.sharedInstance.purchasePackageWith(
                    requireActivity(),
                    selectedPackage.rcPackage,
                    onError = { error, userCancelled ->
                        if (!userCancelled) {
                            Toast.makeText(
                                            requireContext(),
                                            "Purchase failed: ${error.message}",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                    },
                    onSuccess = { _, customerInfo ->
                        Toast.makeText(
                                        requireContext(),
                                        "Thank you! ${selectedPackage.buttonText} successful",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                        refreshBalance() // Refresh balance after purchase
                        dismiss()
                    }
            )
        }
        binding.rvCreditPackages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PurchaseCreditsBottomSheet.adapter
            isNestedScrollingEnabled = false
        }
    }

    private fun loadOfferings(completion: (() -> Unit)? = null) {
        binding.layoutLoadingState.visibility = View.VISIBLE
        binding.layoutErrorState.visibility = View.GONE
        // Keep RecyclerView visible if it has content? Or hide it to show loading?
        // Better to show loading spinner over it or hide it. Let's hide it for specific loading
        // state.
        binding.rvCreditPackages.visibility = View.GONE

        Purchases.sharedInstance.getOfferingsWith(
                onError = { error ->
                    Log.e(TAG, "Failed to load offerings: ${error.message}")
                    binding.layoutLoadingState.visibility = View.GONE

                    // Show Error State
                    binding.layoutErrorState.visibility = View.VISIBLE
                    binding.tvErrorMessage.text = "Failed to load products: ${error.message}"

                    completion?.invoke()
                },
                onSuccess = { offerings ->
                    binding.layoutLoadingState.visibility = View.GONE
                    val current =
                            offerings.current
                                    ?: run {
                                        // Empty offerings might also be an error or just empty
                                        binding.layoutErrorState.visibility = View.VISIBLE
                                        binding.tvErrorMessage.text =
                                                "No products available at the moment."
                                        completion?.invoke()
                                        return@getOfferingsWith
                                    }

                    val packages =
                            current.availablePackages.mapNotNull { pkg ->
                                val product = pkg.product
                                val isSub = product.subscriptionOptions != null

                                val credits =
                                        if (isSub) 0
                                        else {
                                            // Extract number from title or description
                                            (product.title + product.description).let {
                                                "\\d+".toRegex().find(it)?.value?.toIntOrNull() ?: 0
                                            }
                                        }

                                Log.d(TAG, "product title: ${product.title} ")

                                val cleanName = product.title.replace(Regex("\\(.*\\)"), "").trim()

                                Log.d(
                                        TAG,
                                        "cleanName: $cleanName, credits: $credits, isSub: $isSub"
                                )

                                CreditPackage(
                                        imageRes = R.drawable.verifylabs_circle_square_icon,
                                        credits = credits,
                                        priceUsd = product.price.amountMicros / 1_000_000.0,
                                        name = cleanName,
                                        description = product.description,
                                        rcPackage = pkg,
                                        isSubscription = isSub
                                )
                            }

                    // Mark best value (highest credits per dollar)
                    val bestValue =
                            packages.filter { !it.isSubscription && it.credits > 0 }.maxByOrNull {
                                it.credits / it.priceUsd
                            }

                    val finalList = packages.map { it.copy(isBestValue = it == bestValue) }

                    if (finalList.isEmpty()) {
                        binding.layoutErrorState.visibility = View.VISIBLE
                        binding.tvErrorMessage.text = "No packages found."
                    } else {
                        binding.rvCreditPackages.visibility = View.VISIBLE
                        adapter?.submitList(finalList)
                    }

                    completion?.invoke()
                }
        )
    }

    private fun restorePurchases() {
        binding.restoreProgressBar.visibility = View.VISIBLE
        binding.imgRestore.visibility = View.GONE

        // Disable button to prevent double clicks
        binding.restoreContainer.isEnabled = false

        Purchases.sharedInstance.restorePurchasesWith(
                onError = { error ->
                    // Even if restore fails, we try to reload offerings?
                    // Or just stop here. RevenueCat says restore error usually means network.
                    //    Toast.makeText(requireContext(), "Restore failed: ${error.message}",
                    // Toast.LENGTH_SHORT).show()

                    // Still try to refresh plans as user requested "fetch same api"
                    loadOfferings {
                        binding.restoreProgressBar.visibility = View.GONE
                        binding.imgRestore.visibility = View.VISIBLE
                        binding.restoreContainer.isEnabled = true
                    }
                },
                onSuccess = { customerInfo ->
                    //    Toast.makeText(requireContext(), "Purchases restored!",
                    // Toast.LENGTH_SHORT).show()
                    // Now fetch plans as requested
                    loadOfferings {
                        binding.restoreProgressBar.visibility = View.GONE
                        binding.imgRestore.visibility = View.VISIBLE
                        binding.restoreContainer.isEnabled = true
                    }
                }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = PurchaseCreditsBottomSheet()
    }

    // --------------------------
    // Fullscreen Bottom Sheet
    // --------------------------
    private fun setupFullScreenBottomSheet() {
        (dialog as? BottomSheetDialog)?.findViewById<View>(
                        com.google.android.material.R.id.design_bottom_sheet
                )
                ?.let { sheet ->
                    sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    BottomSheetBehavior.from(sheet).apply {
                        state = BottomSheetBehavior.STATE_EXPANDED
                        isDraggable = true
                        skipCollapsed = true
                    }
                }
    }
}
