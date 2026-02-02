package com.verifylabs.ai.presentation.plan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.verifylabs.ai.R
import com.verifylabs.ai.databinding.ItemCreditPackageBinding

class CreditPackageAdapter(
    private val onPackageClick: (CreditPackage) -> Unit
) : ListAdapter<CreditPackage, CreditPackageAdapter.VH>(DiffCallback()) {

    inner class VH(val binding: ItemCreditPackageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCreditPackageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val context = holder.binding.root.context

        holder.binding.apply {
            imgCoins.setImageResource(item.imageRes)

            tvCreditsname.text = item.name
            tvPrice.text = item.formattedPrice
            tvExtraText.text = item.description

            // Best Value Badge (Not used in this design)
            // tvBadge.visibility = View.GONE

            // Button Style: Buy vs Subscribe
            tvBuyCredits.text = item.buttonText
            
            // Apply capsule colors
            if (item.isSubscription) {
                btnBuyCredits.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorPurple))
            } else {
                btnBuyCredits.setCardBackgroundColor(ContextCompat.getColor(context, R.color.txtBlue))
            }

            root.setOnClickListener { onPackageClick(item) }
            btnBuyCredits.setOnClickListener { onPackageClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CreditPackage>() {
        override fun areItemsTheSame(old: CreditPackage, new: CreditPackage) =
            old.rcPackage.identifier == new.rcPackage.identifier

        override fun areContentsTheSame(old: CreditPackage, new: CreditPackage) = old == new
    }
}