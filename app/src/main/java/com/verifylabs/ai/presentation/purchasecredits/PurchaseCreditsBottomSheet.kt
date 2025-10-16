package com.verifylabs.ai.presentation.purchasecredits

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.verifylabs.ai.R
import com.verifylabs.ai.core.util.Constants
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.FragmentPurchaseCreditsBottomSheetBinding
import com.verifylabs.ai.presentation.plan.CreditPackage
import com.verifylabs.ai.presentation.plan.CreditPackageAdapter
import com.verifylabs.ai.presentation.plan.PlanResponse
import com.verifylabs.ai.presentation.settings.PlanViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PurchaseCreditsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentPurchaseCreditsBottomSheetBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    private lateinit var planViewModel: PlanViewModel
    private var adapter: CreditPackageAdapter? = null
    private val planList: MutableList<PlanResponse> = mutableListOf()

    override fun getTheme(): Int = R.style.FullScreenBottomSheetDialogTheme
    private val TAG = "PurchaseCreditsBottomSheet"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPurchaseCreditsBottomSheetBinding.inflate(inflater, container, false)

        planViewModel = ViewModelProvider(this)[PlanViewModel::class.java]
        setupUI()
        setupAdapter()
        setupStickyHeader()

        val receivedPlans = arguments?.getSerializable(ARG_PLANS) as? ArrayList<PlanResponse>
        if (!receivedPlans.isNullOrEmpty()) {
            Log.d(TAG, "Received ${receivedPlans} plans from SettingsFragment")
            updateAdapter(receivedPlans)
        } else {
            Log.d(TAG, "No plans passed, fetching from API")
            fetchPlans()
        }

        return binding.root
    }

    private fun setupUI() {
        binding.tvCurrentBalance.text = "${preferenceHelper.getCreditRemaining() ?: 0}"
        binding.tvHeaderTitle.alpha = 0f // initially hidden
    }

    private fun setupStickyHeader() {
        // Listen to scroll changes
        binding.nestedScrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->

            // Threshold: when main title scrolls past 150px (adjust as needed)
            val threshold = binding.tvTitle.top

            if (scrollY > threshold) {
                // Fade in header
                if (binding.tvHeaderTitle.alpha == 0f) {
                    binding.tvHeaderTitle.animate().alpha(1f).setDuration(500).start()
                }
            } else {
                // Fade out header
                if (binding.tvHeaderTitle.alpha == 1f) {
                    binding.tvHeaderTitle.animate().alpha(0f).setDuration(500).start()
                }
            }
        }
    }

//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        (dialog as? BottomSheetDialog)?.behavior?.apply {
//            state = BottomSheetBehavior.STATE_EXPANDED
//            skipCollapsed = true
//
//        }
//
//        // Make background transparent so rounded corners show
//        (dialog as? BottomSheetDialog)?.window?.setBackgroundDrawableResource(android.R.color.transparent)
//
//        binding.btnClose.setOnClickListener { dismiss() }
//    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bottomSheetDialog = dialog as? BottomSheetDialog
        bottomSheetDialog?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        // Make the dialog window transparent
        bottomSheetDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Fix extra white background by making the container transparent and clipping corners
        val bottomSheet = bottomSheetDialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.let {
            it.setBackgroundResource(android.R.color.transparent) // remove default white
            it.clipToOutline = true // ensure rounded corners clip content
        }

        binding.btnClose.setOnClickListener { dismiss() }
    }


    private fun setupAdapter() {
        adapter = CreditPackageAdapter(emptyList()) { selected ->
            Toast.makeText(requireContext(), "Selected ${selected.credits} credits", Toast.LENGTH_SHORT).show()
        }
        binding.rvCreditPackages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCreditPackages.adapter = adapter
    }

    private fun updateAdapter(plans: List<PlanResponse>) {
        val creditPackages = plans.map { plan ->
            CreditPackage(
                R.drawable.verifylabs_circle_square_icon,
                plan.credits,
                plan.price.toDoubleOrNull() ?: 0.0,
                plan.name
            )
        }
        adapter?.updateList(creditPackages)
    }

    private fun fetchPlans() {
        planViewModel.getPlans(Constants.SECRET_KEY)
        planViewModel.plansObserver.observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.SUCCESS -> {
                    resource.data?.let { plans ->
                        planList.clear()
                        planList.addAll(plans)
                        updateAdapter(plans)
                    }
                }
                Status.ERROR -> {
                    Toast.makeText(requireContext(), "Failed to fetch plans", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PLANS = "plans"

        fun newInstance(plans: ArrayList<PlanResponse>): PurchaseCreditsBottomSheet {
            val fragment = PurchaseCreditsBottomSheet()
            val bundle = Bundle().apply {
                putSerializable(ARG_PLANS, plans)
            }
            fragment.arguments = bundle
            return fragment
        }
    }


}

