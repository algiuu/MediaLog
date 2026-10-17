package com.example.medialog.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.medialog.R
import com.example.medialog.data.local.entity.EntryStatus
import com.example.medialog.data.local.entity.MediaEntryEntity
import com.example.medialog.data.local.entity.MediaType
import com.example.medialog.databinding.ItemMediaEntryBinding

class MediaEntryAdapter(
    private val onItemClick: (MediaEntryEntity) -> Unit,
    private val onItemLongClick: (MediaEntryEntity) -> Unit
) : ListAdapter<MediaEntryEntity, MediaEntryAdapter.MediaViewHolder>(DIFF_CALLBACK) {

    var selectionMode: Boolean = false
        private set

    fun setSelectionMode(active: Boolean) {
        selectionMode = active
        if (!active) selectedIds.clear()
        notifyDataSetChanged()
    }
    private val selectedIds = mutableSetOf<Long>()

    val selectedCount: Int get() = selectedIds.size
    val selectedEntryIds: List<Long> get() = selectedIds.toList()

    fun enterSelectionMode(initialId: Long) {
        selectionMode = true
        selectedIds.add(initialId)
        notifyDataSetChanged()
    }

    fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) selectionMode = false
        notifyDataSetChanged()
    }

    fun selectAll(ids: List<Long>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        selectionMode = true
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MediaViewHolder(private val binding: ItemMediaEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaEntryEntity) {
            binding.tvTitle.text = item.title

            val typeText = when (item.type) {
                MediaType.ANIME -> "Anime"
                MediaType.MOVIE -> "Movie"
                MediaType.SERIES -> "Series"
                MediaType.BOOK -> "Book"
            }
            binding.tvTypeBadge.text = typeText

            val creator = item.creator ?: ""
            val year = item.releaseYear?.toString() ?: ""
            val subtitle = listOf(creator, year).filter { it.isNotBlank() }.joinToString(" · ")
            binding.tvSubtitle.text = subtitle.ifBlank { "Media Item" }

            binding.tvRating.text = if (item.myRating != null) "★ ${item.myRating}" else "★ -"

            val statusText = when (item.status) {
                EntryStatus.PLAN -> "Plan"
                EntryStatus.ONGOING -> "Watching"
                EntryStatus.COMPLETED -> "Completed"
                EntryStatus.DROPPED -> "Dropped"
            }
            binding.tvStatus.text = statusText

            if (item.status == EntryStatus.ONGOING && item.totalUnits != null && item.totalUnits > 0) {
                binding.progressIndicator.visibility = View.VISIBLE
                val progressPct = ((item.progress.toFloat() / item.totalUnits) * 100).toInt()
                binding.progressIndicator.progress = progressPct
            } else {
                binding.progressIndicator.visibility = View.GONE
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
                binding.tvInitial.text = item.title.firstOrNull()?.uppercase() ?: "M"
            }

            // Selection mode
            if (selectionMode) {
                binding.ivSelectCheck.visibility = View.VISIBLE
                val isSelected = selectedIds.contains(item.id)
                binding.ivSelectCheck.isSelected = isSelected
                binding.ivSelectCheck.setBackgroundResource(
                    if (isSelected) R.drawable.bg_check_circle else R.drawable.bg_check_circle_unselected
                )
                binding.ivSelectCheck.setImageResource(if (isSelected) R.drawable.ic_check else 0)
                binding.root.isActivated = isSelected
            } else {
                binding.ivSelectCheck.visibility = View.GONE
                binding.root.isActivated = false
            }

            binding.root.setOnClickListener {
                if (selectionMode) {
                    toggleSelection(item.id)
                } else {
                    onItemClick(item)
                }
            }
            binding.root.setOnLongClickListener {
                if (!selectionMode) {
                    enterSelectionMode(item.id)
                } else {
                    toggleSelection(item.id)
                }
                true
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MediaEntryEntity>() {
            override fun areItemsTheSame(oldItem: MediaEntryEntity, newItem: MediaEntryEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: MediaEntryEntity, newItem: MediaEntryEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
