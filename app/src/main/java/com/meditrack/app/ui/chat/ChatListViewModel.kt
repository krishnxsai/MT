package com.meditrack.app.ui.chat

import androidx.lifecycle.*
import com.meditrack.app.data.model.Conversation
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.collectLatest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the conversation list screen.
 */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

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

