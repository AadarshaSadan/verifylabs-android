package com.fatdogs.verifylabs.presentation.onboarding

import OnboardingAdapter
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fatdogs.verifylabs.R
import com.fatdogs.verifylabs.data.base.PreferencesHelperImpl
import com.fatdogs.verifylabs.databinding.ActivityOnboardingBinding
import com.fatdogs.verifylabs.presentation.MainActivity
import com.fatdogs.verifylabs.presentation.auth.AuthBaseActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // 1. Setup adapter with layout resources
        val pages = listOf(
            OnboardingPage(R.layout.onboarding_page1),
            OnboardingPage(R.layout.onboarding_page2),
//            OnboardingPage(R.layout.onboarding_page3)
        )
        adapter = OnboardingAdapter(pages)
        binding.onboardingViewPager.adapter = adapter

        // 2. Setup dots indicator
        binding.springDotsIndicator.setViewPager2(binding.onboardingViewPager)

        // 3. Next button click
        binding.btnNext.setOnClickListener {
            val nextItem = binding.onboardingViewPager.currentItem + 1
            if (nextItem < adapter.itemCount) {
                binding.onboardingViewPager.currentItem = nextItem
            } else {
                // Last page -> Navigate to MainActivity
                startActivity(Intent(this, AuthBaseActivity::class.java))
                finish()
            }
        }
    }
}
