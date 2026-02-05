package com.verifylabs.ai.presentation.history

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.verifylabs.ai.R
import com.verifylabs.ai.databinding.FragmentHistoryBinding
import com.verifylabs.ai.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding
        get() = _binding!!
    private lateinit var adapter: HistoryAdapter
    private lateinit var viewModel: HistoryViewModel

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var isGlobalEmpty = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        // Observe global history to determine if "No History Yet" should be shown
        viewModel.allHistory.observe(viewLifecycleOwner) { historyList ->
            isGlobalEmpty = historyList.isEmpty()
            val currentTab =
                    binding.tabLayout
                            .getTabAt(binding.tabLayout.selectedTabPosition)
                            ?.text
                            .toString()
            if (currentTab == "All" || isGlobalEmpty) {
                updateEmptyState(
                        if (currentTab == "All") historyList.isEmpty()
                        else (binding.recyclerView.adapter?.itemCount == 0),
                        currentTab
                )
            }
        }

        setupRecyclerView()
        setupSwipeToDelete()
        setupTabs()
        setupMenu()
        loadInitialData()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
                object : MenuProvider {
                    override fun onCreateMenu(
                            menu: android.view.Menu,
                            menuInflater: android.view.MenuInflater
                    ) {
                        menuInflater.inflate(R.menu.menu_history, menu)
                        updateStorageInfo(menu)
                    }

                    override fun onMenuItemSelected(menuItem: android.view.MenuItem): Boolean {
                        return when (menuItem.itemId) {
                            R.id.action_clear_all -> {
                                showClearHistoryDialog()
                                true
                            }
                            R.id.action_cleanup -> {
                                viewModel.cleanupExpired()
                                true
                            }
                            else -> false
                        }
                    }
                },
                viewLifecycleOwner,
                Lifecycle.State.RESUMED
        )
    }

    private fun updateStorageInfo(menu: android.view.Menu) {
        viewLifecycleOwner.lifecycleScope.launch {
            val sizeKb = viewModel.getStorageSizeKb()
            val sizeText = formatSize(sizeKb)
            menu.findItem(R.id.action_storage)?.title = "Storage: $sizeText"
        }
    }

    private fun formatSize(kb: Long): String {
        return when {
            kb >= 1024 * 1024 -> String.format("%.1f GB", kb / (1024.0 * 1024.0))
            kb >= 1024 -> String.format("%.1f MB", kb / 1024.0)
            else -> "$kb KB"
        }
    }

    private fun showClearHistoryDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Clear All History")
                .setMessage(
                        "This will permanently delete all verification history and associated files. This action cannot be undone."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear All") { _, _ -> viewModel.purgeHistory() }
                .show()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = HistoryAdapter { historyItem ->
            // Navigate to detail fragment
            val fragment = HistorySingleFragment.newInstance(historyItem.id.toLong())
            parentFragmentManager
                    .beginTransaction()
                    .setCustomAnimations(
                            R.anim.slide_in_right, // enter
                            R.anim.fade_out, // exit
                            R.anim.fade_in, // pop enter
                            R.anim.slide_out_right // pop exit
                    )
                    .add(R.id.container, fragment)
                    .addToBackStack("HistorySingleFragment")
                    .commit()
        }
        binding.recyclerView.adapter = adapter
    }

    private fun setupSwipeToDelete() {
        val swipeHandler =
                object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                    private val deleteIcon =
                            ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)
                    private val intrinsicWidth = deleteIcon?.intrinsicWidth ?: 0
                    private val intrinsicHeight = deleteIcon?.intrinsicHeight ?: 0
                    private val backgroundColor = Color.parseColor("#f44336") // Previous Red
                    private val clearPaint =
                            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
                    private val boxMargin = 24f // Initial margin

                    override fun onMove(
                            recyclerView: RecyclerView,
                            viewHolder: RecyclerView.ViewHolder,
                            target: RecyclerView.ViewHolder
                    ): Boolean = false

                    // --- CONFIGURATION ---
                    private val configTextSize = 20f // Change this to increase/decrease text size
                    private val iconScale = 1.0f // Change this to 0.8f, 1.2f etc. to resize icon
                    private val visibilityThreshold =
                            120f // Swipe distance to start showing text/icon
                    private val boxVerticalMargin =
                            48f // Increase this to make the background thinner (less height)
                    private val customCornerRadius =
                            -1f // Set to > 0 to use a fixed radius. Set to -1f for automatic pill
                    // shape (full circle ends).
                    private val textColorHex = "#8E8E93" // Configurable text color
                    // ---------------------

                    private val textPaint =
                            Paint().apply {
                                color = Color.WHITE
                                textSize = configTextSize
                                isAntiAlias = true
                                textAlign = Paint.Align.CENTER
                                typeface =
                                        android.graphics.Typeface.create(
                                                "sans-serif-medium",
                                                android.graphics.Typeface.NORMAL
                                        )
                            }

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        val position = viewHolder.bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            val item = adapter.currentList[position]
                            viewModel.deleteHistoryItem(item.id)
                            // Provide haptic feedback
                            view?.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.CONTEXT_CLICK
                            )
                        }
                    }

                    override fun onChildDraw(
                            c: Canvas,
                            recyclerView: RecyclerView,
                            viewHolder: RecyclerView.ViewHolder,
                            dX: Float,
                            dY: Float,
                            actionState: Int,
                            isCurrentlyActive: Boolean
                    ) {
                        val itemView = viewHolder.itemView
                        val isCanceled = dX == 0f && !isCurrentlyActive

                        if (isCanceled) {
                            clearCanvas(
                                    c,
                                    itemView.right + dX,
                                    itemView.top.toFloat(),
                                    itemView.right.toFloat(),
                                    itemView.bottom.toFloat()
                            )
                            super.onChildDraw(
                                    c,
                                    recyclerView,
                                    viewHolder,
                                    dX,
                                    dY,
                                    actionState,
                                    isCurrentlyActive
                            )
                            return
                        }

                        val isLeftSwipe = dX < 0
                        if (isLeftSwipe) {
                            val swipeWidth = kotlin.math.abs(dX)

                            // The minimal swipe width needed before the red box appears (due to
                            // margins)
                            val startAppearance = boxMargin * 2

                            // Define button bounds with margins
                            val buttonLeft = itemView.right + dX + boxMargin
                            val buttonTop = itemView.top + boxVerticalMargin
                            val buttonRight = itemView.right - boxMargin
                            val buttonBottom = itemView.bottom - boxVerticalMargin

                            // Only draw if we have positive width
                            if (buttonLeft < buttonRight) {
                                // ANIMATION LOGIC
                                // Calculate progress based on swipe width relative to
                                // visibilityThreshold
                                // We offset by startAppearance so animation starts smoothly when
                                // box appears
                                val animationRange = visibilityThreshold - startAppearance
                                val safeRange =
                                        if (animationRange > 0) animationRange
                                        else 1f // avoid div/0

                                val rawProgress = (swipeWidth - startAppearance) / safeRange
                                val progress = rawProgress.coerceIn(0f, 1f)

                                // Calculate Scale for Background
                                // We want the background to grow from center.
                                val maxButtonHeight = buttonBottom - buttonTop
                                val currentButtonHeight = maxButtonHeight * progress

                                // Recalculate Top/Bottom to center it
                                val centerY = (buttonTop + buttonBottom) / 2
                                val currentTop = centerY - (currentButtonHeight / 2)
                                val currentBottom = centerY + (currentButtonHeight / 2)

                                val rect =
                                        android.graphics.RectF(
                                                buttonLeft,
                                                currentTop,
                                                buttonRight,
                                                currentBottom
                                        )
                                // Calculate radius: Use custom if set, otherwise dynamic pill shape
                                // based on current height
                                val dynamicRadius =
                                        if (customCornerRadius > 0) customCornerRadius
                                        else currentButtonHeight / 2f

                                // Calculate Background Alpha
                                val bgAlpha = (255 * progress).toInt()

                                val paint =
                                        Paint().apply {
                                            color = backgroundColor
                                            alpha = bgAlpha // Apply Fade In
                                            isAntiAlias = true
                                        }

                                // Only draw if visible
                                if (currentButtonHeight > 0 && bgAlpha > 0) {
                                    c.drawRoundRect(rect, dynamicRadius, dynamicRadius, paint)
                                }

                                // Icon Animation: Scale and Fade
                                // Scale: 0 -> iconScale
                                // Alpha: 0 -> 255
                                val currentScale = iconScale * progress
                                val currentAlpha = (255 * progress).toInt()

                                if (currentAlpha > 0) {
                                    val iconW = (intrinsicWidth * currentScale).toInt()
                                    val iconH = (intrinsicHeight * currentScale).toInt()
                                    val iconMarginBottom = 8f

                                    // Center coordinates
                                    val centerX = (buttonLeft + buttonRight) / 2
                                    val centerY = (buttonTop + buttonBottom) / 2

                                    // Icon Position: STRICTLY CENTERED in the pill
                                    val iconTop = centerY - (iconH / 2)
                                    val iconBottom = iconTop + iconH
                                    val iconLeft = centerX - (iconW / 2)
                                    val iconRight = iconLeft + iconW

                                    deleteIcon?.setBounds(
                                            iconLeft.toInt(),
                                            iconTop.toInt(),
                                            iconRight.toInt(),
                                            iconBottom.toInt()
                                    )
                                    deleteIcon?.setTint(Color.WHITE)
                                    deleteIcon?.alpha = currentAlpha
                                    deleteIcon?.draw(c)

                                    // Text Animation: Fade in
                                    // Start fading text slightly later, e.g., when icon is 50%
                                    // visible
                                    val textStartProgress = 0.5f
                                    val textRawProgress =
                                            (rawProgress - textStartProgress) /
                                                    (1f - textStartProgress)
                                    val textProgress = textRawProgress.coerceIn(0f, 1f)
                                    val textAlpha = (255 * textProgress).toInt()

                                    if (textAlpha > 0) {
                                        val text = "Delete"
                                        val textBounds = android.graphics.Rect()
                                        textPaint.getTextBounds(text, 0, text.length, textBounds)
                                        val textHeight = textBounds.height()

                                        // Text Position: OUTSIDE and BELOW the background
                                        val textTopMargin = 8f
                                        val textY = buttonBottom + textTopMargin + textHeight

                                        val originalColor = textPaint.color
                                        val originalAlpha = textPaint.alpha

                                        try {
                                            textPaint.color = Color.parseColor(textColorHex)
                                        } catch (e: Exception) {
                                            textPaint.color = Color.GRAY
                                        }
                                        textPaint.alpha = textAlpha

                                        c.drawText(text, centerX, textY, textPaint)

                                        // Restore
                                        textPaint.color = originalColor
                                    }
                                }
                            }
                        }

                        super.onChildDraw(
                                c,
                                recyclerView,
                                viewHolder,
                                dX,
                                dY,
                                actionState,
                                isCurrentlyActive
                        )
                    }

                    private fun clearCanvas(
                            c: Canvas?,
                            left: Float,
                            top: Float,
                            right: Float,
                            bottom: Float
                    ) {
                        c?.drawRect(left, top, right, bottom, clearPaint)
                    }
                }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(
                object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab) {
                        val tabName = tab.text.toString()
                        loadDataForTab(tabName)
                    }
                    override fun onTabUnselected(tab: TabLayout.Tab) {}
                    override fun onTabReselected(tab: TabLayout.Tab) {}
                }
        )
    }

    private fun loadInitialData() {
        // Load "All" tab by default
        loadDataForTab("All")
    }

    private fun loadDataForTab(tabName: String) {
        val liveData =
                when (tabName) {
                    "All" -> viewModel.allHistory
                    "Images" -> viewModel.getHistoryByType("Image")
                    "Videos" -> viewModel.getHistoryByType("Video")
                    "Audio" -> viewModel.getHistoryByType("Audio")
                    else -> viewModel.allHistory
                }

        liveData.observe(viewLifecycleOwner) { historyList ->
            adapter.submitList(historyList)
            updateEmptyState(historyList.isEmpty(), tabName)
        }
    }

    private fun updateEmptyState(isEmpty: Boolean, tabName: String) {
        if (isGlobalEmpty) {
            // Global Empty State
            binding.tabLayout.visibility = View.GONE
            binding.recyclerView.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE

            binding.emptyTitle.text = getString(R.string.no_history_yet)
            binding.emptySubtitle.text = getString(R.string.no_history_desc)
            binding.emptyTitle.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.header_text)
            )

            val icon =
                    ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_clock_badge_questionmark
                    )
            binding.emptyIcon.setImageDrawable(icon)
        } else {
            binding.tabLayout.visibility = View.VISIBLE
            if (isEmpty) {
                // Filtered Empty State
                binding.recyclerView.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyTitle.text = "No $tabName"
                binding.emptySubtitle.text = getString(R.string.no_items_subtitle)
                binding.emptyTitle.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.header_text)
                )
                val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_no_items)
                binding.emptyIcon.setImageDrawable(icon)
            } else {
                // List has items
                binding.recyclerView.visibility = View.VISIBLE
                binding.emptyState.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.updateStatusBarColor(R.color.ios_settings_background)
        // Pass 0f elevation to remove the white surface tint in dark mode
        (activity as? MainActivity)?.updateBottomNavColor(R.color.ios_settings_background, 0f)
        (activity as? MainActivity)?.updateAppBarColor(R.color.ios_settings_background)
        (activity as? MainActivity)?.updateMainBackgroundColor(R.color.ios_settings_background)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            (activity as? MainActivity)?.updateStatusBarColor(R.color.ios_settings_background)
            (activity as? MainActivity)?.updateBottomNavColor(R.color.ios_settings_background, 0f)
            (activity as? MainActivity)?.updateAppBarColor(R.color.ios_settings_background)
            (activity as? MainActivity)?.updateMainBackgroundColor(R.color.ios_settings_background)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
