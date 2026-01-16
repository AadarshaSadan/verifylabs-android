package com.verifylabs.ai.presentation.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.verifylabs.ai.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HistoryAdapter

    private val sampleData = listOf(
        HistoryItem(1, "Human Made", "Image", "16/01/2026, 7:24 PM", 22, "Human"),
        HistoryItem(2, "Machine Made", "Image", "15/01/2026, 5:12 PM", 80, "Machine"),
        HistoryItem(3, "Human Made", "Video", "14/01/2026, 9:45 AM", 60, "Human"),
        HistoryItem(4, "Machine Made", "Audio", "13/01/2026, 11:30 AM", 45, "Machine"),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupTabs()
        loadInitialData()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = HistoryAdapter { /* handle click */ }
        binding.recyclerView.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val filteredData = getFilteredData(tab.text.toString())
                adapter.submitList(filteredData)
                updateEmptyState(filteredData.isEmpty(), tab.text.toString())
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun loadInitialData() {
        val initialData = getFilteredData("All")
        adapter.submitList(initialData)
        updateEmptyState(initialData.isEmpty(), "All")
    }

    private fun getFilteredData(tab: String): List<HistoryItem> {
        return when(tab) {
            "All" -> sampleData
            "Images" -> sampleData.filter { it.type == "Image" }
            "Videos" -> sampleData.filter { it.type == "Video" }
            "Audio" -> sampleData.filter { it.type == "Audio" }
            else -> emptyList()
        }
    }

    private fun updateEmptyState(isEmpty: Boolean, tabName: String) {
        if(isEmpty) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            binding.emptyTitle.text = "No $tabName"
            binding.emptySubtitle.text = "Try selecting a different filter"
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
