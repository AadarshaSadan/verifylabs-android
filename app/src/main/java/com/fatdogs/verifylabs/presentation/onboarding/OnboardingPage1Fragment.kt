package com.fatdogs.verifylabs.presentation.onboarding

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fatdogs.verifylabs.databinding.FragmentOnboardingPage1Binding
import dagger.hilt.android.AndroidEntryPoint

/**
 * A simple [Fragment] subclass for the first onboarding page.
 */

@AndroidEntryPoint
class OnboardingPage1Fragment : Fragment() {

    // ViewBinding reference
    private var _binding: FragmentOnboardingPage1Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingPage1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
