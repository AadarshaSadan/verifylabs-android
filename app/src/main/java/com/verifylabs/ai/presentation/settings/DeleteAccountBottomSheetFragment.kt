package com.verifylabs.ai.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.verifylabs.ai.R
import com.verifylabs.ai.databinding.FragmentDeleteAccountBottomSheetBinding

class DeleteAccountBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentDeleteAccountBottomSheetBinding? = null
    private val binding
        get() = _binding!!

    // Callback to parent
    var onDeleteConfirmed: (() -> Unit)? = null

    override fun getTheme(): Int = R.style.FullScreenBottomSheetDialogTheme

    override fun onStart() {
        super.onStart()
        setupFullScreenBottomSheet()
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeleteAccountBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnKeepAccount.setOnClickListener { dismiss() }

        binding.btnDelete.setOnClickListener {
            dismiss()
            onDeleteConfirmed?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DeleteAccountBottomSheetFragment"
        fun newInstance() = DeleteAccountBottomSheetFragment()
    }

    private fun setupFullScreenBottomSheet() {
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.findViewById<View>(
                        com.google.android.material.R.id.design_bottom_sheet
                )
                ?.let { sheet ->
                    sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet).apply {
                        state =
                                com.google.android.material.bottomsheet.BottomSheetBehavior
                                        .STATE_EXPANDED
                        isDraggable = true
                        skipCollapsed = true
                    }
                }
    }
}
