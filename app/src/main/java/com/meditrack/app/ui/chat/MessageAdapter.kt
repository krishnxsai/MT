package com.meditrack.app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.data.model.Message
import com.meditrack.app.data.model.MessageType
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val currentUserId: String
) : ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_SENT = 0
        private const val TYPE_RECEIVED = 1
        private const val TYPE_SYSTEM = 2

        private val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(a: Message, b: Message) = a.id == b.id
            override fun areContentsTheSame(a: Message, b: Message) = a == b
        }
    }

    override fun getItemViewType(position: Int): Int {
        val msg = getItem(position)
        return when {
            msg.type == MessageType.SYSTEM -> TYPE_SYSTEM
            msg.senderId == currentUserId -> TYPE_SENT
            else -> TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SENT -> SentViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
            TYPE_SYSTEM -> SystemViewHolder(inflater.inflate(R.layout.item_message_system, parent, false))
            else -> ReceivedViewHolder(inflater.inflate(R.layout.item_message_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is SentViewHolder -> holder.bind(msg)
            is ReceivedViewHolder -> holder.bind(msg)
            is SystemViewHolder -> holder.bind(msg)
        }
    }

    class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.messageText)
        private val timeText: TextView = view.findViewById(R.id.timeText)
        private val readIndicator: View = view.findViewById(R.id.readIndicator)

        fun bind(msg: Message) {
            messageText.text = msg.text
            msg.timestamp?.let {
                timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
            }
            readIndicator.visibility = if (msg.isRead) View.VISIBLE else View.GONE
        }
    }

    class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.messageText)
        private val timeText: TextView = view.findViewById(R.id.timeText)
        private val senderNameText: TextView = view.findViewById(R.id.senderNameText)

        fun bind(msg: Message) {
            messageText.text = msg.text
            senderNameText.text = msg.senderName
            msg.timestamp?.let {
                timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
            }
        }
    }

    class SystemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.systemMessageText)
        private val timeText: TextView = view.findViewById(R.id.systemTimeText)

        fun bind(msg: Message) {
            messageText.text = msg.text
            msg.timestamp?.let {
                timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                timeText.visibility = View.VISIBLE
            } ?: run {
                timeText.visibility = View.GONE
            }
        }
    }
}

