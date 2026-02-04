package com.verifylabs.ai.presentation.media

import com.verifylabs.ai.data.network.InternetHelper
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.verifylabs.ai.core.util.Status
import com.verifylabs.ai.databinding.DialogReportResultBinding
import com.verifylabs.ai.presentation.viewmodel.MediaViewModel
import kotlinx.coroutines.launch

class ReportResultDialogFragment : DialogFragment() {

    private var _binding: DialogReportResultBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MediaViewModel
    private lateinit var internetHelper: InternetHelper
    
    // Callback to trigger the actual API call in parent fragment
    var onReportSelected: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogReportResultBinding.inflate(inflater, container, false)
        
        // Ensure transparent background for custom card shape with margins
        val back = ColorDrawable(Color.TRANSPARENT)
        val margin = (24 * resources.displayMetrics.density).toInt()
        val inset = android.graphics.drawable.InsetDrawable(back, margin)
        dialog?.window?.setBackgroundDrawable(inset)
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Connect to shared ViewModel (scoped to Activity with specific key for persistence)
        viewModel = ViewModelProvider(
            requireActivity().viewModelStore,
            requireActivity().defaultViewModelProviderFactory,
            requireActivity().defaultViewModelCreationExtras
        ).get("MediaScope", MediaViewModel::class.java)
        internetHelper = InternetHelper(requireContext())

        setupClickListeners()
        observeViewModel()
        
        // Initial State
        showSelectionState()
    }
    
    override fun onStart() {
        super.onStart()
        // Optional: specific width/height if needed, but match_parent/wrap_content in constraint layout usually works if dialog window is set
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupClickListeners() {
        binding.btnReportHuman.setOnClickListener {
            checkInternetAndReport("human")
        }

        binding.btnReportAI.setOnClickListener {
            checkInternetAndReport("ai")
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
        
        binding.btnSuccessOK.setOnClickListener {
            dismiss()
        }

        binding.btnErrorOK.setOnClickListener {
            dismiss()
        }
    }

    private fun checkInternetAndReport(type: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (internetHelper.isInternetAvailable()) {
                onReportSelected?.invoke(type)
            } else {
                showErrorState()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.reportResponse.observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    showLoadingState()
                }
                Status.SUCCESS -> {
                    showSuccessState()
                }
                Status.ERROR -> {
                    showErrorState()
                }
                Status.INSUFFICIENT_CREDITS -> {
                    showErrorState()
                }
            }
        }
    }

    private fun showSelectionState() {
        binding.groupSelection.visibility = View.VISIBLE
        binding.groupLoading.visibility = View.GONE
        binding.groupSuccess.visibility = View.GONE
        binding.groupError.visibility = View.GONE
        
        // Ensure close button is visible (it's not in a group but we want to be sure)
        binding.btnClose.visibility = View.VISIBLE
    }

    private fun showLoadingState() {
        binding.groupSelection.visibility = View.GONE
        binding.groupLoading.visibility = View.VISIBLE
        binding.groupSuccess.visibility = View.GONE
        binding.groupError.visibility = View.GONE
        
        binding.btnClose.visibility = View.GONE
        isCancelable = false // Prevent dismissal while loading
    }

    private fun showSuccessState() {
        binding.groupSelection.visibility = View.GONE
        binding.groupLoading.visibility = View.GONE
        binding.groupSuccess.visibility = View.VISIBLE
        binding.groupError.visibility = View.GONE
        
        binding.btnClose.visibility = View.GONE
        isCancelable = true
    }

    private fun showErrorState() {
        binding.groupSelection.visibility = View.GONE
        binding.groupLoading.visibility = View.GONE
        binding.groupSuccess.visibility = View.GONE
        binding.groupError.visibility = View.VISIBLE
        
        binding.btnClose.visibility = View.GONE
        isCancelable = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ReportResultDialog"
        fun newInstance() = ReportResultDialogFragment()
    }
}
