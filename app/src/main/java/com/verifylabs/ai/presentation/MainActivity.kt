package com.verifylabs.ai.presentation

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.verifylabs.ai.R
import com.verifylabs.ai.databinding.ActivityMainBinding
import com.verifylabs.ai.presentation.audio.FragmentAudio
import com.verifylabs.ai.presentation.home.HomeFragment
import com.verifylabs.ai.presentation.media.MediaFragment
import com.verifylabs.ai.presentation.settings.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 Enable edge-to-edge (SAFE AREA HANDLING)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔥 Apply system safe insets
//        applySafeInsets()

        // Load default fragment
        replaceFragment(HomeFragment())
        selectNavItem(binding.navHome)

        // Bottom navigation clicks
        binding.navHome.setOnClickListener {
            selectNavItem(it)
            replaceFragment(HomeFragment())
        }

        binding.navMedia.setOnClickListener {
            selectNavItem(it)
            replaceFragment(MediaFragment())
        }

        binding.navAudio.setOnClickListener {
            selectNavItem(it)
            replaceFragment(FragmentAudio())
        }

        binding.navHistory.setOnClickListener {
            selectNavItem(it)
            // replaceFragment(HistoryFragment())
        }

        binding.navSettings.setOnClickListener {
            selectNavItem(it)
            replaceFragment(SettingsFragment())
        }
    }

    /**
     * ✅ SAFE SPACE / SYSTEM INSETS HANDLING
     */
    private fun applySafeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->

            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Appbar top padding
            binding.appbar.root.setPadding(systemBars.top)


            // Bottom inset → Floating Bottom Nav
            binding.floatingBottomNav.translationY = -systemBars.bottom.toFloat()

            insets
        }
    }

    /**
     * Bottom navigation selection coloring
     */
    private fun selectNavItem(selected: View) {

        val buttons = listOf(
            binding.navHome,
            binding.navMedia,
            binding.navAudio,
            binding.navHistory,
            binding.navSettings
        )

        val icons = listOf(
            binding.iconHome,
            binding.iconMedia,
            binding.iconAudio,
            binding.iconHistory,
            binding.iconSettings
        )

        val texts = listOf(
            binding.textHome,
            binding.textMedia,
            binding.textAudio,
            binding.textHistory,
            binding.textSettings
        )

        buttons.forEachIndexed { index, btn ->
            if (btn == selected) {
                icons[index].setColorFilter(
                    ContextCompat.getColor(this, R.color.txtGreen)
                )
                texts[index].setTextColor(
                    ContextCompat.getColor(this, R.color.txtGreen)
                )
            } else {
                icons[index].setColorFilter(
                    ContextCompat.getColor(this, R.color.verifylabs_dots_indicator)
                )
                texts[index].setTextColor(
                    ContextCompat.getColor(this, R.color.verifylabs_dots_indicator)
                )
            }
        }
    }

    /**
     * Fragment replacement
     */
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }
}
