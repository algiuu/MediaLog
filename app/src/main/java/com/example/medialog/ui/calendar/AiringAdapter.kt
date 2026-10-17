package com.example.medialog.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.medialog.R
import com.example.medialog.data.remote.media.AiringItem
import com.example.medialog.databinding.ItemAiringBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiringAdapter(
    private val onItemClick: (AiringItem) -> Unit = {}
) : ListAdapter<AiringItem, AiringAdapter.AiringViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AiringViewHolder {
        val binding = ItemAiringBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AiringViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: AiringViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AiringViewHolder(
        private val binding: ItemAiringBinding,
        private val onItemClick: (AiringItem) -> Unit
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AiringItem) {
            binding.tvTitle.text = item.title
            binding.tvEpisode.text = "Episode ${item.episode}"

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.tvTime.text = timeFormat.format(Date(item.airingAt * 1000))

            if (!item.posterUrl.isNullOrBlank()) {
                binding.ivPoster.load(item.posterUrl) {
                    crossfade(true)
                    placeholder(R.color.card_bg_light)
                }
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AiringItem>() {
            override fun areItemsTheSame(oldItem: AiringItem, newItem: AiringItem): Boolean {
                return oldItem.mediaId == newItem.mediaId && oldItem.episode == newItem.episode
            }

            override fun areContentsTheSame(oldItem: AiringItem, newItem: AiringItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
