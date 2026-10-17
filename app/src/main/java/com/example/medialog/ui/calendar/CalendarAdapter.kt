package com.example.medialog.ui.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medialog.databinding.ItemCalendarDayBinding

class CalendarAdapter(
    private val onItemClick: (com.example.medialog.data.remote.media.AiringItem) -> Unit = {}
) : ListAdapter<AiringDay, CalendarAdapter.DayViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = ItemCalendarDayBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DayViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DayViewHolder(
        private val binding: ItemCalendarDayBinding,
        private val onItemClick: (com.example.medialog.data.remote.media.AiringItem) -> Unit
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(day: AiringDay) {
            val count = day.items.size
            binding.tvDayLabel.text = when {
                count == 0 -> day.dayLabel
                count == 1 -> "${day.dayLabel} · 1 anime"
                else -> "${day.dayLabel} · $count anime"
            }
            // Tunjukkan petunjuk geser kalau ada kartu yang keluar dari layar
            binding.tvDayHint.visibility = if (count > 4) View.VISIBLE else View.GONE

            if (day.items.isEmpty()) {
                binding.tvDayEmpty.visibility = View.VISIBLE
                binding.rvAiring.visibility = View.GONE
            } else {
                binding.tvDayEmpty.visibility = View.GONE
                binding.rvAiring.visibility = View.VISIBLE
                binding.rvAiring.layoutManager =
                    LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
                binding.rvAiring.adapter = AiringAdapter(onItemClick).also { it.submitList(day.items) }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AiringDay>() {
            override fun areItemsTheSame(oldItem: AiringDay, newItem: AiringDay): Boolean {
                return oldItem.dayLabel == newItem.dayLabel
            }

            override fun areContentsTheSame(oldItem: AiringDay, newItem: AiringDay): Boolean {
                return oldItem == newItem
            }
        }
    }
}
