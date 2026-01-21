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

class HistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, HistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title
        holder.date.text = item.date
        holder.aiScore.text = "${item.aiScore}%"

        // Load thumbnail using Glide
        val placeholderRes = when (item.type) {
            "Image" -> R.drawable.verifylabs_logo
            "Video" -> android.R.drawable.ic_menu_slideshow
            "Audio" -> R.drawable.ic_audio
            else -> R.drawable.ic_mic
        }

        val request = if (item.type == "Audio" || item.mediaUri == null) {
            Glide.with(holder.itemView.context).load(placeholderRes)
        } else {
            Glide.with(holder.itemView.context).load(item.mediaUri)
        }
        
        request
            .placeholder(placeholderRes)
            .error(placeholderRes)
            .centerCrop()
            .into(holder.thumbnail)

        // Card gradient based on mode
        val bgRes = if(item.mode == "Human") R.drawable.card_gradient_human
        else R.drawable.card_gradient_machine
        holder.cardRoot.setBackgroundResource(bgRes)

        // AI Score color
        val scoreColor = if(item.mode == "Human") R.color.txtGreen
        else R.color.colorRecordingRed
        holder.aiScore.setTextColor(holder.itemView.context.getColor(scoreColor))

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
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem) = oldItem == newItem
    }
}
