package com.verifylabs.ai.presentation.history

import com.verifylabs.ai.presentation.MainActivity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

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
                    .replace(R.id.container, fragment)
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
                    private val background = ColorDrawable()
                    private val backgroundColor = Color.parseColor("#f44336")
                    private val clearPaint =
                            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

                    override fun onMove(
                            recyclerView: RecyclerView,
                            viewHolder: RecyclerView.ViewHolder,
                            target: RecyclerView.ViewHolder
                    ): Boolean = false

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        val position = viewHolder.adapterPosition
                        val item = adapter.currentList[position]
                        viewModel.deleteHistoryItem(item.id)
                        // The LiveData observation in loadDataForTab will automatically update the
                        // adapter
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
                        val itemHeight = itemView.bottom - itemView.top
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

                        // Draw the red delete background
                        background.color = backgroundColor
                        background.setBounds(
                                itemView.right + dX.toInt(),
                                itemView.top,
                                itemView.right,
                                itemView.bottom
                        )
                        background.draw(c)

                        // Calculate position of delete icon
                        // Fix: Force 24dp size instead of using intrinsic width/height which is too
                        // large (800dp)
                        val iconSize =
                                (24 * requireContext().resources.displayMetrics.density).toInt()

                        val deleteIconTop = itemView.top + (itemHeight - iconSize) / 2
                        val deleteIconMargin = (itemHeight - iconSize) / 2
                        val deleteIconLeft = itemView.right - deleteIconMargin - iconSize
                        val deleteIconRight = itemView.right - deleteIconMargin
                        val deleteIconBottom = deleteIconTop + iconSize

                        // Draw the delete icon
                        deleteIcon?.setBounds(
                                deleteIconLeft,
                                deleteIconTop,
                                deleteIconRight,
                                deleteIconBottom
                        )
                        deleteIcon?.setTint(Color.WHITE)
                        deleteIcon?.draw(c)

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
        if (isEmpty) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            binding.emptyTitle.text = "No $tabName"
            binding.emptySubtitle.text = "Try selecting a different filter"
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.updateStatusBarColor(R.color.ios_settings_background)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
