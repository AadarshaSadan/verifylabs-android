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

        // Handle system bars insets (edge-to-edge support)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply top inset to the AppBar to keep it below status bar
            // Use setPadding to avoid shrinking content (since it's now wrap_content)
            binding.appbar.root.setPadding(0, systemBars.top, 0, 0)

            // Apply bottom inset to the Floating Bottom Nav layout margin
            val params = binding.floatingBottomNav.layoutParams as ConstraintLayout.LayoutParams
            params.bottomMargin =
                    systemBars.bottom + resources.getDimensionPixelSize(R.dimen.bottom_nav_margin)
            binding.floatingBottomNav.layoutParams = params

            insets
        }

        // Ensure status bar icons are correct based on theme (backward compatible)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val isDarkMode =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
        windowInsetsController.isAppearanceLightStatusBars = !isDarkMode

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

    /** Bottom navigation selection coloring */
    private fun selectNavItem(selected: View) {

        val buttons =
                listOf(
                        binding.navHome,
                        binding.navMedia,
                        binding.navAudio,
                        binding.navHistory,
                        binding.navSettings
                )

        val icons =
                listOf(
                        binding.iconHome,
                        binding.iconMedia,
                        binding.iconAudio,
                        binding.iconHistory,
                        binding.iconSettings
                )

        val texts =
                listOf(
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

                icons[index].setColorFilter(ContextCompat.getColor(this, R.color.txtGreen))
                texts[index].setTextColor(ContextCompat.getColor(this, R.color.txtGreen))
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

    /** Fragment replacement */
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
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
        val navButtons =
                listOf(
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

    fun updateStatusBarColor(colorResId: Int) {
        val color = ContextCompat.getColor(this, colorResId)
        window.statusBarColor = color
        window.navigationBarColor = color

        // Update light/dark status bar icons based on background brightness
        // For ios_settings_background (#F2F2F7 in light), we want dark icons
        // For app_background or black, we want light icons
        val isDarkMode =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // Simple heuristic: if dark mode, icons are light. If light mode, icons are dark.
        // This generally works for our current setup where light mode has light backgrounds.
        windowInsetsController.isAppearanceLightStatusBars = !isDarkMode
        windowInsetsController.isAppearanceLightNavigationBars = !isDarkMode
    }

    fun updateBottomNavColor(colorResId: Int, elevationDp: Float = 8f) {
        try {
            val color = ContextCompat.getColor(this, colorResId)

            // Update CardView background
            binding.floatingBottomNav.setCardBackgroundColor(color)
            
            // Update elevation (remove shadow/overlay for immersive screens if 0 is passed)
            binding.floatingBottomNav.cardElevation = elevationDp * resources.displayMetrics.density

            // Accessing the inner LinearLayout via the ID we just added in XML
            val container = binding.floatingBottomNav.findViewById<View>(R.id.bottomNavContainer)
            
            // Should stay compatible with shape drawable
            val background = container?.background
            if (background is android.graphics.drawable.GradientDrawable) {
                background.mutate()
                background.setColor(color)
            } else if (background is android.graphics.drawable.ColorDrawable) {
                background.color = color
            } else {
                 android.util.Log.w("MainActivity", "Background is not GradientDrawable: ${background?.javaClass?.name}")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error updating bottom nav color", e)
        }
    }

    fun updateAppBarColor(colorResId: Int) {
        val color = ContextCompat.getColor(this, colorResId)
        binding.appbar.root.setBackgroundColor(color)
    }

    fun updateMainBackgroundColor(colorResId: Int) {
        val color = ContextCompat.getColor(this, colorResId)
        binding.main.setBackgroundColor(color)
    }
}
