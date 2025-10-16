package com.verifylabs.ai.presentation.onboarding

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.verifylabs.ai.R
import com.verifylabs.ai.data.base.PreferencesHelperImpl
import com.verifylabs.ai.databinding.ActivityOnboardingBinding
import com.verifylabs.ai.presentation.auth.login.LoginFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: PreferencesHelperImpl
    private val TAG = "OnboardingActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge layout
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isLightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_NO
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLightMode
        window.statusBarColor = getColor(R.color.app_background_before_login)

        prefs = PreferencesHelperImpl(this)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)



        // Setup ViewPager2 adapter
        val totalPages = 3
        binding.onboardingViewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = totalPages
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> OnboardingPage1Fragment()
                1 -> OnboardingPage2Fragment()
                2 -> LoginFragment()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }

        // Connect dots indicator
        binding.springDotsIndicator.setViewPager2(binding.onboardingViewPager)

        // Hide/show Next button based on current page
        binding.onboardingViewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Bottom layout visible for pages 0 and 1

                binding.btnNext.visibility = if (position == totalPages - 1) View.GONE else View.VISIBLE
            }
        })

        // Check for start page from intent
        val startPage = intent.getIntExtra("START_PAGE", 0)
        binding.onboardingViewPager.setCurrentItem(startPage, false)

        // Next button click
        binding.btnNext.setOnClickListener {
            val nextItem = binding.onboardingViewPager.currentItem + 1
            if (nextItem < totalPages) {
                binding.onboardingViewPager.currentItem = nextItem
            }
        }
    }

    fun setBottomLayoutVisibility(visible: Boolean) {
        binding.bottomLayout.visibility = if (visible)View.VISIBLE else View.GONE

    }

}
