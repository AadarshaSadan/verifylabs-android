package com.fatdogs.verifylabs.presentation.splashscreen

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fatdogs.verifylabs.R
import com.fatdogs.verifylabs.core.util.BaseActivity
import com.fatdogs.verifylabs.data.base.PreferenceHelper
import com.fatdogs.verifylabs.data.base.PreferencesHelperImpl
import com.fatdogs.verifylabs.databinding.ActivitySplashBinding
import com.fatdogs.verifylabs.presentation.MainActivity
import com.fatdogs.verifylabs.presentation.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
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

        // Apply window insets safely
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Check login status
        if (preferenceHelper.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(R.anim.slide_in_up, R.anim.nothing_ani)
            return
        }

        // Delayed navigation to OnboardingActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            overridePendingTransition(R.anim.slide_in_up, R.anim.nothing_ani)
        }, 2000)
    }
}

