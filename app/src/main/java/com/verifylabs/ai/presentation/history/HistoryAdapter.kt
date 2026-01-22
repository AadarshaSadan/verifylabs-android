package com.verifylabs.ai.presentation.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.verifylabs.ai.R

class HistoryAdapter(private val onItemClick: (HistoryItem) -> Unit) :
        ListAdapter<HistoryItem, HistoryAdapter.ViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view =
                        LayoutInflater.from(parent.context)
                                .inflate(R.layout.history_item, parent, false)
                return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val item = getItem(position)
                holder.title.text = item.title
                holder.date.text = item.date
                holder.aiScore.text = "${item.aiScore}%"

                // Load thumbnail using Glide
                val placeholderRes =
                        when (item.type) {
                                "Image" -> R.drawable.verifylabs_logo
                                "Video" -> android.R.drawable.ic_menu_slideshow
                                "Audio" -> R.drawable.ic_audio
                                else -> R.drawable.ic_mic
                        }

                val request =
                        if (item.type == "Audio" || item.mediaUri == null) {
                                Glide.with(holder.itemView.context).load(placeholderRes)
                        } else {
                                Glide.with(holder.itemView.context).load(item.mediaUri)
                        }

                request.placeholder(placeholderRes)
                        .error(placeholderRes)
                        .centerCrop()
                        .into(holder.thumbnail)

                // Match iOS Logic for color selection
                val score = item.aiScore / 100.0
                val baseColorHex =
                        when {
                                score > 0.95 -> "#E6070D" // VLRed
                                score > 0.85 -> "#FF3B30" // System Red
                                score > 0.65 -> "#8E8E93" // System Gray
                                score > 0.50 -> "#34C759" // System Green
                                else -> "#38B031" // VLGreen
                        }

                val baseColor = android.graphics.Color.parseColor(baseColorHex)
                val startColor =
                        androidx.core.graphics.ColorUtils.setAlphaComponent(
                                baseColor,
                                (0.3 * 255).toInt()
                        )
                val endColor =
                        androidx.core.graphics.ColorUtils.setAlphaComponent(
                                baseColor,
                                (0.1 * 255).toInt()
                        )

                val gradientDrawable =
                        android.graphics.drawable.GradientDrawable(
                                        android.graphics.drawable.GradientDrawable.Orientation
                                                .LEFT_RIGHT,
                                        intArrayOf(startColor, endColor)
                                )
                                .apply {
                                        val radius =
                                                16 *
                                                        holder.itemView
                                                                .context
                                                                .resources
                                                                .displayMetrics
                                                                .density
                                        cornerRadius = radius
                                }

                holder.cardRoot.background = gradientDrawable

                // Ensure text contrast matching iOS row
                holder.title.setTextColor(android.graphics.Color.WHITE)
                holder.date.setTextColor(android.graphics.Color.parseColor("#8E8E93"))
                holder.aiScore.setTextColor(baseColor)

                holder.itemView.setOnClickListener { onItemClick(item) }
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
                val cardRoot: View = itemView.findViewById(R.id.history_item_container)
                val thumbnail: ImageView = itemView.findViewById(R.id.item_thumbnail)
                val title: TextView = itemView.findViewById(R.id.item_title)
                val date: TextView = itemView.findViewById(R.id.item_date)
                val aiScore: TextView = itemView.findViewById(R.id.ai_score)
        }

        class DiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
                override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem) =
                        oldItem.id == newItem.id
                override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem) =
                        oldItem == newItem
        }
}
