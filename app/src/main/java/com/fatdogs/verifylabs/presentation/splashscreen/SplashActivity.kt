package com.fatdogs.verifylabs.presentation.splashscreen

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    // 1. Declare the binding
    private lateinit var binding: ActivitySplashBinding

    @Inject
    lateinit var preferenceHelper: PreferenceHelper


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Inflate the binding
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)


        if(preferenceHelper.isLoggedIn())
        {
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
            overridePendingTransition(R.anim.slide_in_up, R.anim.nothing_ani)
            return
        }

        // 3. Delayed execution to navigate to MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this@SplashActivity, OnboardingActivity::class.java))
            finish()
            overridePendingTransition(R.anim.slide_in_up, R.anim.nothing_ani)
        }, 2000)
    }
}

