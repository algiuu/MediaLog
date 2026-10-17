package com.example.medialog.ui.foryou

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.medialog.R
import com.example.medialog.data.local.entity.RecommendationItemEntity
import com.example.medialog.databinding.ItemRecommendationBinding

class RecommendationAdapter(
    private val onAddClick: (RecommendationItemEntity) -> Unit,
    private val onHideClick: (RecommendationItemEntity) -> Unit
) : ListAdapter<RecommendationItemEntity, RecommendationAdapter.RecommendationViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val binding = ItemRecommendationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecommendationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecommendationViewHolder(private val binding: ItemRecommendationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecommendationItemEntity) {
            binding.tvTitle.text = item.title
            binding.tvReason.text = item.reason
            binding.tvTypeBadge.text = item.type.name

            if (!item.posterUrl.isNullOrBlank()) {
                binding.ivPoster.visibility = View.VISIBLE
                binding.tvInitial.visibility = View.GONE
                binding.ivPoster.load(item.posterUrl) {
                    crossfade(true)
                    placeholder(R.color.card_bg_light)
                }
            } else {
                binding.ivPoster.visibility = View.GONE
                binding.tvInitial.visibility = View.VISIBLE
                binding.tvInitial.text = item.title.firstOrNull()?.uppercase() ?: "?"
            }

            binding.btnAddToList.setOnClickListener { onAddClick(item) }
            binding.btnHide.setOnClickListener { onHideClick(item) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RecommendationItemEntity>() {
            override fun areItemsTheSame(oldItem: RecommendationItemEntity, newItem: RecommendationItemEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RecommendationItemEntity, newItem: RecommendationItemEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
