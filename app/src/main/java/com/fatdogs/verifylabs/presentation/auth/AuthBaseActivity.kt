package com.fatdogs.verifylabs.presentation.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fatdogs.verifylabs.databinding.ActivityAuthBaseBinding
import com.fatdogs.verifylabs.presentation.auth.login.LoginFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AuthBaseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBaseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityAuthBaseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showFragment(LoginFragment())

        // Apply system bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }



    // Helper to show LoginFragment / SignupFragment
    fun showFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(binding.authFragmentContainer.id, fragment)
            .commit()
    }
}
