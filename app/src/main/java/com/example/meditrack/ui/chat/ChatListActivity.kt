package com.example.meditrack.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meditrack.data.model.Resource
import com.example.meditrack.databinding.ActivityChatListBinding
import com.google.firebase.auth.FirebaseAuth

class ChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatListBinding
    private val viewModel: ChatListViewModel by viewModels()
    private lateinit var adapter: ConversationAdapter

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter(currentUserId) { conversation ->
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversation.id)
                putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, conversation.getOtherName(currentUserId))
                putExtra(ChatActivity.EXTRA_OTHER_USER_IMAGE, conversation.getOtherProfileUrl(currentUserId))
            }
            startActivity(intent)
        }

        binding.conversationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatListActivity)
            adapter = this@ChatListActivity.adapter
        }
    }

    private fun observeViewModel() {
        viewModel.conversations.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    val list = result.data
                    if (list.isEmpty()) {
                        binding.emptyStateContainer.visibility = View.VISIBLE
                        binding.conversationsRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyStateContainer.visibility = View.GONE
                        binding.conversationsRecyclerView.visibility = View.VISIBLE
                        adapter.submitList(list)
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

