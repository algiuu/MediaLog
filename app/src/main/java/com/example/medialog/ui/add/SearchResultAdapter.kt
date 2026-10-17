package com.example.medialog.ui.add

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.medialog.R
import com.example.medialog.data.remote.media.MediaSearchResult
import com.example.medialog.databinding.ItemSearchResultBinding

class SearchResultAdapter(
    private val onItemClick: (MediaSearchResult) -> Unit
) : ListAdapter<MediaSearchResult, SearchResultAdapter.SearchViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SearchViewHolder(private val binding: ItemSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaSearchResult) {
            binding.tvTitle.text = item.title
            binding.tvSubtitle.text = buildString {
                if (item.creator != null) append(item.creator)
                if (item.releaseYear != null) {
                    if (isNotEmpty()) append(" · ")
                    append(item.releaseYear.toString())
                }
            }

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

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MediaSearchResult>() {
            override fun areItemsTheSame(oldItem: MediaSearchResult, newItem: MediaSearchResult): Boolean {
                return oldItem.source == newItem.source && oldItem.externalId == newItem.externalId
            }

            override fun areContentsTheSame(oldItem: MediaSearchResult, newItem: MediaSearchResult): Boolean {
                return oldItem == newItem
            }
        }
    }
}
