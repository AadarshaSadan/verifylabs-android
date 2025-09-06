package com.fatdogs.verifylabs.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fatdogs.verifylabs.data.base.PreferenceHelper
import com.fatdogs.verifylabs.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!   // Only valid between onCreateView and onDestroyView

    @Inject
    lateinit var preferenceHelper: PreferenceHelper // Example of injected dependency

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

       //  binding.tvWelcome.text = "Welcome, ${preferenceHelper.getUserName() ?: "Guest"}"

        // Example usage of binding
            //   binding.textViewHome.text = "Welcome to Home Fragment"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks
    }
}
