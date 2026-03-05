package com.example.meditrack.ui.chat

import androidx.lifecycle.*
import com.example.meditrack.data.model.Message
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.repository.ChatRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for an individual chat conversation screen.
 */
class ChatViewModel : ViewModel() {

    private val chatRepository = ChatRepository()

    private val _messages = MutableLiveData<Resource<List<Message>>>()
    val messages: LiveData<Resource<List<Message>>> = _messages

    private val _sendResult = MutableLiveData<Resource<Message>>()
    val sendResult: LiveData<Resource<Message>> = _sendResult

    private var conversationId: String = ""

    fun init(conversationId: String) {
        this.conversationId = conversationId
        loadMessages()
        markAsRead()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            chatRepository.getMessagesFlow(conversationId).collectLatest { result ->
                _messages.postValue(result)
                // Mark as read whenever new messages arrive
                if (result is Resource.Success) {
                    markAsRead()
                }
            }
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            _sendResult.postValue(Resource.Loading)
            val result = chatRepository.sendMessage(conversationId, text)
            _sendResult.postValue(result)
        }
    }

    fun markAsRead() {
        viewModelScope.launch {
            chatRepository.markMessagesAsRead(conversationId)
        }
    }
}

