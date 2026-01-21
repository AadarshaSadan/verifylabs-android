package com.verifylabs.ai.presentation

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
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
import com.verifylabs.ai.presentation.history.HistoryFragment
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
            replaceFragment(HistoryFragment())
        }

        binding.navSettings.setOnClickListener {
            selectNavItem(it)
            replaceFragment(SettingsFragment())
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
                // Selected → set watercolor ripple background
                btn.setBackgroundResource(R.drawable.nav_overlay)

                icons[index].setColorFilter(
                    ContextCompat.getColor(this, R.color.txtGreen)
                )
                texts[index].setTextColor(
                    ContextCompat.getColor(this, R.color.txtGreen)
                )


            } else {
                btn.background = null

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

    fun setBottomNavVisibility(isVisible: Boolean) {
        if (isVisible) {
            binding.floatingBottomNav.visibility = View.VISIBLE
        } else {
            binding.floatingBottomNav.visibility = View.GONE
        }
    }

    fun setAppBarVisibility(isVisible: Boolean) {
        if (isVisible) {
            binding.appbar.root.visibility = View.VISIBLE
        } else {
            binding.appbar.root.visibility = View.GONE
        }
    }

    fun navigateToTab(index: Int) {
        val navButtons = listOf(
            binding.navHome,
            binding.navMedia,
            binding.navAudio,
            binding.navHistory,
            binding.navSettings
        )
        if (index in navButtons.indices) {
            navButtons[index].performClick()
        }
    }
}
