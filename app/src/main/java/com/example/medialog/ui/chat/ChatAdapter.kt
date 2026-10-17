package com.example.medialog.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medialog.R
import com.example.medialog.databinding.ItemChatMessageBinding

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatMessage) {
            binding.tvBubble.text = item.text

            val params = binding.tvBubble.layoutParams as FrameLayout.LayoutParams
            if (item.fromUser) {
                params.gravity = Gravity.END
                binding.tvBubble.setBackgroundResource(R.drawable.bg_bubble_user)
                binding.tvBubble.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.white)
                )
            } else {
                params.gravity = Gravity.START
                binding.tvBubble.setBackgroundResource(R.drawable.bg_bubble_ai)
                binding.tvBubble.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.text_primary_light)
                )
            }
            binding.tvBubble.layoutParams = params
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
                return oldItem == newItem
            }
        }
    }
}
