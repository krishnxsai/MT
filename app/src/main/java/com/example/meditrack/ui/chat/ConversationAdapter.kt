package com.example.meditrack.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.meditrack.R
import com.example.meditrack.data.model.Conversation
import java.text.SimpleDateFormat
import java.util.*

class ConversationAdapter(
    private val currentUserId: String,
    private val onClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.ConversationViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Conversation>() {
            override fun areItemsTheSame(a: Conversation, b: Conversation) = a.id == b.id
            override fun areContentsTheSame(a: Conversation, b: Conversation) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConversationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatarImage: ImageView = view.findViewById(R.id.avatarImage)
        private val nameText: TextView = view.findViewById(R.id.nameText)
        private val lastMessageText: TextView = view.findViewById(R.id.lastMessageText)
        private val timeText: TextView = view.findViewById(R.id.timeText)
        private val unreadBadge: TextView = view.findViewById(R.id.unreadBadge)

        fun bind(conversation: Conversation) {
            val otherName = conversation.getOtherName(currentUserId)
            val otherProfileUrl = conversation.getOtherProfileUrl(currentUserId)
            val unreadCount = conversation.getUnreadCount(currentUserId)

            nameText.text = otherName
            lastMessageText.text = conversation.lastMessage.ifEmpty { "No messages yet" }

            conversation.lastMessageTimestamp?.let {
                timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                timeText.visibility = View.VISIBLE
            } ?: run {
                timeText.visibility = View.GONE
            }

            if (unreadCount > 0) {
                unreadBadge.text = unreadCount.toString()
                unreadBadge.visibility = View.VISIBLE
            } else {
                unreadBadge.visibility = View.GONE
            }

            if (otherProfileUrl.isNotEmpty()) {
                Glide.with(avatarImage.context)
                    .load(otherProfileUrl)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(avatarImage)
            } else {
                avatarImage.setImageResource(R.drawable.ic_person)
            }

            itemView.setOnClickListener { onClick(conversation) }
        }
    }
}

