package com.verifylabs.ai.presentation.purchasecredits

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.verifylabs.ai.R
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.FragmentPurchaseCreditsBottomSheetBinding
import com.verifylabs.ai.presentation.plan.CreditPackage
import com.verifylabs.ai.presentation.plan.CreditPackageAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.purchasePackageWith
import com.revenuecat.purchases.restorePurchasesWith
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PurchaseCreditsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentPurchaseCreditsBottomSheetBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var preferenceHelper: PreferenceHelper
    private var adapter: CreditPackageAdapter? = null
    private val TAG = "PurchaseCreditsBS"

    override fun getTheme() = R.style.FullScreenBottomSheetDialogTheme

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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

//        (dialog as? BottomSheetDialog)?.apply {
//            behavior.state = BottomSheetBehavior.STATE_EXPANDED
//            behavior.skipCollapsed = true
//        }
//
//        override fun onStart() {
//            super.onStart()
//            (dialog as? BottomSheetDialog)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
//                sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
//                sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT) // <-- important
//                BottomSheetBehavior.from(sheet).apply {
//                    state = BottomSheetBehavior.STATE_EXPANDED
//                    isDraggable = true
//                    skipCollapsed = true
//                }
//        }

        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun setupUI() {
        binding.tvCurrentBalance.text = "${preferenceHelper.getCreditRemaining() ?: 0}"
        binding.tvRestorePurchase.setOnClickListener { restorePurchases() }
    }

    private fun setupRecyclerView() {
        adapter = CreditPackageAdapter { selectedPackage ->
            Purchases.sharedInstance.purchasePackageWith(
                requireActivity(),
                selectedPackage.rcPackage,
                onError = { error, userCancelled ->
                    if (!userCancelled) {
                        Toast.makeText(requireContext(), "Purchase failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                },
                onSuccess = { _, customerInfo ->
                    Toast.makeText(requireContext(), "Thank you! ${selectedPackage.buttonText} successful", Toast.LENGTH_SHORT).show()
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

    private fun loadOfferings() {
        Purchases.sharedInstance.getOfferingsWith(
            onError = { error ->
                Log.e(TAG, "Failed to load offerings")
                Toast.makeText(requireContext(), "Network error. Try again.", Toast.LENGTH_SHORT).show()
            },
            onSuccess = { offerings ->
                val current = offerings.current ?: return@getOfferingsWith

                val packages = current.availablePackages.mapNotNull { pkg ->
                    val product = pkg.product
                    val isSub = product.subscriptionOptions != null

                    val credits = if (isSub) 0 else {
                        // Extract number from title or description
                        (product.title + product.description)
                            .let { "\\d+".toRegex().find(it)?.value?.toIntOrNull() ?: 0 }
                    }

                    Log.d(TAG, "product title: ${product.title} ")

//                    val cleanName = product.title
//                        .substringBefore("(").trim()
//                        .let { if (it.contains("Credits")) it.substringBefore("Credits").trim() else it }
//                        .ifBlank { if (isSub) product.title.substringBefore("(").trim() else "Credit Pack" }

                    val cleanName = product.title
                        .replace(Regex("\\(.*\\)"), "")
                        .trim()


                    Log.d(TAG, "cleanName: $cleanName, credits: $credits, isSub: $isSub")

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
                val bestValue = packages.filter { !it.isSubscription && it.credits > 0 }
                    .maxByOrNull { it.credits / it.priceUsd }

                val finalList = packages.map { it.copy(isBestValue = it == bestValue) }

                adapter?.submitList(finalList)
            }
        )
    }

    private fun restorePurchases() {
        Purchases.sharedInstance.restorePurchasesWith { customerInfo ->
            Toast.makeText(requireContext(), "Purchases restored!", Toast.LENGTH_SHORT).show()
            // Optional: refresh balance from backend or customerInfo
        }
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
        (dialog as? BottomSheetDialog)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
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