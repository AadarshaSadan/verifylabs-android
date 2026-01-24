package com.verifylabs.ai.presentation.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.verifylabs.ai.databinding.FragmentReportBottomSheetBinding

class ReportBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentReportBottomSheetBinding? = null
    private val binding get() = _binding!!

    var onReportSelected: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnReportHuman.setOnClickListener {
            onReportSelected?.invoke("human")
            dismiss()
        }

        binding.btnReportAI.setOnClickListener {
            onReportSelected?.invoke("ai")
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ReportBottomSheetFragment"
        fun newInstance() = ReportBottomSheetFragment()
    }
}
