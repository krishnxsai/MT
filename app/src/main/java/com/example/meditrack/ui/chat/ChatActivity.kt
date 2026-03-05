package com.example.meditrack.ui.chat

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.meditrack.R
import com.example.meditrack.data.model.Resource
import com.example.meditrack.databinding.ActivityChatBinding
import com.google.firebase.auth.FirebaseAuth

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_OTHER_USER_NAME = "extra_other_user_name"
        const val EXTRA_OTHER_USER_IMAGE = "extra_other_user_image"
    }

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: MessageAdapter

    private var conversationId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
        val otherName = intent.getStringExtra(EXTRA_OTHER_USER_NAME) ?: "Chat"
        val otherImage = intent.getStringExtra(EXTRA_OTHER_USER_IMAGE) ?: ""

        if (conversationId.isEmpty()) {
            Toast.makeText(this, "Invalid conversation", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar(otherName, otherImage)
        setupRecyclerView()
        setupSendButton()
        observeViewModel()

        viewModel.init(conversationId)
    }

    private fun setupToolbar(name: String, imageUrl: String) {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.otherUserName.text = name

        if (imageUrl.isNotEmpty()) {
            Glide.with(this).load(imageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.otherUserAvatar)
        }
    }

    private fun setupRecyclerView() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        adapter = MessageAdapter(userId)

        val layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true
            stackFromEnd = false
        }

        binding.messagesRecyclerView.apply {
            this.layoutManager = layoutManager
            adapter = this@ChatActivity.adapter
        }
    }

    private fun setupSendButton() {
        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                viewModel.sendMessage(text)
                binding.messageInput.text?.clear()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { result ->
            when (result) {
                is Resource.Loading -> { /* Already showing previous messages */ }
                is Resource.Success -> {
                    adapter.submitList(result.data)
                    // Scroll to bottom on new message
                    if (result.data.isNotEmpty()) {
                        binding.messagesRecyclerView.scrollToPosition(0)
                    }
                }
                is Resource.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.sendResult.observe(this) { result ->
            when (result) {
                is Resource.Error -> {
                    Toast.makeText(this, "Failed to send: ${result.message}", Toast.LENGTH_SHORT).show()
                }
                else -> { /* Loading / Success handled by messages flow */ }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (conversationId.isNotEmpty()) {
            viewModel.markAsRead()
        }
    }
}

