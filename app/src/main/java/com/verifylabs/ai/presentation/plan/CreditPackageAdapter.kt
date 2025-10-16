package com.verifylabs.ai.presentation.plan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.verifylabs.ai.databinding.ItemCreditPackageBinding

class CreditPackageAdapter(
    private var packages: List<CreditPackage>,
    private val onBuyClick: (CreditPackage) -> Unit
) : RecyclerView.Adapter<CreditPackageAdapter.PackageViewHolder>() {


    inner class PackageViewHolder(val binding: ItemCreditPackageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageViewHolder {
        val binding = ItemCreditPackageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PackageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PackageViewHolder, position: Int) {
        val item = packages[position]

        holder.binding.apply {
            imgCoins.setImageResource(item.imageRes)
            tvCreditsname.text = "${item.name} Credits"
            tvPrice.text = "$${String.format("%.2f", item.priceUsd)}"
            btnBuyCredits.setOnClickListener { onBuyClick(item) }
        }
    }

    override fun getItemCount() = packages.size

    /** 🔄 Allows updating the list dynamically */
    fun updateList(newList: List<CreditPackage>) {
        packages = newList
        notifyDataSetChanged()
    }
}
