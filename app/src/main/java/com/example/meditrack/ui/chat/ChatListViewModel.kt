package com.example.meditrack.ui.chat

import androidx.lifecycle.*
import com.example.meditrack.data.model.Conversation
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.ChatRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for the conversation list screen.
 */
class ChatListViewModel : ViewModel() {

    private val chatRepository = ChatRepository()

    private val _conversations = MutableLiveData<Resource<List<Conversation>>>()
    val conversations: LiveData<Resource<List<Conversation>>> = _conversations

    private val _totalUnread = MutableLiveData(0)
    val totalUnread: LiveData<Int> = _totalUnread

    init {
        loadConversations()
        observeUnreadCount()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            chatRepository.getConversationsFlow().collectLatest { result ->
                _conversations.postValue(result)
            }
        }
    }

    private fun observeUnreadCount() {
        viewModelScope.launch {
            chatRepository.getTotalUnreadCountFlow().collectLatest { count ->
                _totalUnread.postValue(count)
            }
        }
    }

    fun getOrCreateConversation(otherUserId: String, callback: (Resource<Conversation>) -> Unit) {
        viewModelScope.launch {
            callback(chatRepository.getOrCreateConversation(otherUserId))
        }
    }
}

