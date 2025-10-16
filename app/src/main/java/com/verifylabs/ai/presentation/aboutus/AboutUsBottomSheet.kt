package com.verifylabs.ai.presentation.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.verifylabs.ai.R
import com.verifylabs.ai.databinding.FragmentAboutUsBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AboutUsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentAboutUsBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.FullScreenBottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): BottomSheetDialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { dialog ->
            val behavior = BottomSheetBehavior.from(
                (dialog as BottomSheetDialog).findViewById(
                    com.google.android.material.R.id.design_bottom_sheet
                )!!
            )
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
        return bottomSheetDialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutUsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bottomSheetDialog = dialog as? BottomSheetDialog
        bottomSheetDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        bottomSheetDialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.apply {
            setBackgroundResource(android.R.color.transparent)
            clipToOutline = true
        }

        binding.btnClose.setOnClickListener { dismiss() }

        setupContent()
    }

    private fun setupContent() {
        // Dynamically set app version
        binding.tvVersion.text = "Version ${getAppVersionName()} (API ${getAndroidVersionCode()})"

        // --- Legal Section ---
        // Set click on the whole card for better UX (add android:id="@+id/rlPrivacyPolicy" to the RelativeLayout in XML)
        // For now, using the LinearLayout inside
        binding.llPrivacyPolicyTexts.setOnClickListener { openUrl("https://verifylabs.ai/privacy") }
        binding.tvPrivacyPolicy.setOnClickListener { openUrl("https://verifylabs.ai/privacy") }

        binding.llTermsTexts.setOnClickListener { openUrl("https://verifylabs.ai/terms") }
        binding.tvTerms.setOnClickListener { openUrl("https://verifylabs.ai/terms") }

        // --- Support Section ---
        binding.llVisitWebsiteTexts.setOnClickListener { openUrl("https://verifylabs.ai") }
        binding.tvVisitWebsite.setOnClickListener { openUrl("https://verifylabs.ai") }

        binding.llContactSupportTexts.setOnClickListener { openEmail("support@verifylabs.ai") }
        binding.tvContactSupport.setOnClickListener { openEmail("support@verifylabs.ai") }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to open link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openEmail(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, "Support Request")
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to open email: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAppVersionName(): String {
        return try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun getAndroidVersionCode(): Int {
        return android.os.Build.VERSION.SDK_INT
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}