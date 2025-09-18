package com.fatdogs.verifylabs.presentation

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.fatdogs.verifylabs.R
import com.fatdogs.verifylabs.databinding.ActivityMainBinding
import com.fatdogs.verifylabs.presentation.viewmodel.MainViewModel
import com.fatdogs.verifylabs.data.base.PreferenceHelper
import com.fatdogs.verifylabs.presentation.home.HomeFragment
import com.fatdogs.verifylabs.presentation.media.MediaFragment
import com.fatdogs.verifylabs.presentation.model.PostResponse
import com.fatdogs.verifylabs.presentation.settings.SettingsFragment
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null // Nullable to allow clearing in onDestroy
    @Inject
    lateinit var preferenceHelper: PreferenceHelper
    private val viewModel: MainViewModel by viewModels()

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding?.root ?: return)

            // Apply system window insets for edge-to-edge layout
            binding?.root?.let { rootView ->
                try {
                    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
                        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                        v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                        insets
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting window insets: ${e.message}", e)
                }
            } ?: Log.e(TAG, "Root view is null, cannot set window insets")

            clearSavedMedia()

            // Load default fragment (Home)
            replaceFragment(HomeFragment())

            // Setup bottom navigation
            binding?.bottomNavigationView?.setOnItemSelectedListener { item ->
                try {
                    when (item.itemId) {
                        R.id.nav_home -> replaceFragment(HomeFragment())
                        R.id.nav_media -> replaceFragment(MediaFragment())
                        R.id.nav_settings -> replaceFragment(SettingsFragment())
                        else -> {
                            Log.w(TAG, "Unknown navigation item selected: ${item.itemId}")
                            false
                        }
                    }
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling navigation item: ${e.message}", e)
                    false
                }
            } ?: Log.e(TAG, "Bottom navigation view is null")

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Initialization error occurred", Toast.LENGTH_SHORT).show()
            finish() // Exit activity to prevent unstable state
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear binding to prevent memory leaks
        binding = null
        // Remove observers to prevent memory leaks
    }

    private fun replaceFragment(fragment: Fragment) {
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null) // Optional: Add to back stack for navigation
                .commit()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error replacing fragment: ${e.message}", e)
            // Optionally use commitAllowingStateLoss() if state loss is acceptable
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error replacing fragment: ${e.message}", e)
        }
    }

    fun showBottomNavigation(show: Boolean) {
        binding?.bottomNavigationView?.visibility = if (show) View.VISIBLE else View.GONE
    }


    private fun clearSavedMedia() {
        try {
            preferenceHelper.setSelectedMediaPath(null)
            preferenceHelper.setSelectedMediaType(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing saved media: ${e.message}", e)
        }
    }
}