package com.verifylabs.ai.presentation.splashscreen

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.verifylabs.ai.R
import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.databinding.ActivitySplashBinding
import com.verifylabs.ai.presentation.MainActivity
import com.verifylabs.ai.presentation.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    @Inject
    lateinit var preferenceHelper: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate binding
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)


        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Apply window insets safely
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        navigateNext()
    }

    private fun navigateNext() {
        // Check login status and navigate
        if (preferenceHelper.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_up, R.anim.nothing_ani)
            return
        }

        // Delayed navigation to OnboardingActivity using lifecycleScope
        lifecycleScope.launch {
            delay(2000)
            startActivity(Intent(this@SplashActivity, OnboardingActivity::class.java))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_up, R.anim.nothing_ani)
        }
    }
}

