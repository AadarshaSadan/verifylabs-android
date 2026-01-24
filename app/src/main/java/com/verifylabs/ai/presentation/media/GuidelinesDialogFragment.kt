package com.verifylabs.ai.presentation.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.verifylabs.ai.databinding.DialogGuidelinesBinding

class GuidelinesDialogFragment : DialogFragment() {

    private var _binding: DialogGuidelinesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogGuidelinesBinding.inflate(inflater, container, false)
        // Make background transparent for rounded corners
        // Make background transparent for rounded corners with margins
        val back = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        val margin = (24 * resources.displayMetrics.density).toInt()
        val inset = android.graphics.drawable.InsetDrawable(back, margin)
        dialog?.window?.setBackgroundDrawable(inset)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGotIt.setOnClickListener {
            dismiss()
        }
        
        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "GuidelinesDialogFragment"
        fun newInstance() = GuidelinesDialogFragment()
    }
}
