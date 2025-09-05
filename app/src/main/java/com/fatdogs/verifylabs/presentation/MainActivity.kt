package com.fatdogs.verifylabs.presentation

import android.os.Bundle
import android.util.Log
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
import com.fatdogs.verifylabs.core.util.Status
import com.fatdogs.verifylabs.presentation.home.HomeFragment
import com.fatdogs.verifylabs.presentation.model.PostResponse
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply system window insets for edge-to-edge layout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Load default fragment (Home)
        replaceFragment(HomeFragment())

        // Setup bottom navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> replaceFragment(HomeFragment())
                R.id.nav_media -> replaceFragment(HomeFragment())
                R.id.nav_settings -> replaceFragment(HomeFragment())
            }
            true
        }

        initViewModel()
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    private fun initViewModel() {
        // Trigger posts API call
        viewModel.getPosts()

        // Observe posts API response
        viewModel.getPostsAPIObserver().observe(this) { resource ->
            when (resource.status) {
                Status.LOADING -> Log.d("MainActivity", "Posts loading...")

                Status.SUCCESS -> if (resource.data != null) {
                    Log.d("MainActivity", "Posts success: ${resource.data}")
                    try {
                        val response = Gson().fromJson(
                            resource.data.toString(),
                            PostResponse::class.java
                        )
                        Log.d("MainActivity", "Posts parsed: ${response.id}")
                        Toast.makeText(
                            this,
                            "Posts parsed: ${response.title}",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Posts parsing error: ${e.message}")
                    }
                } else {
                    Log.d("MainActivity", "Posts success but data is null")
                }

                Status.ERROR -> Log.e("MainActivity", "Posts error: ${resource.message}")
            }
        }
    }
}
