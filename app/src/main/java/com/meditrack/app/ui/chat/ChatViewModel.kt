package com.meditrack.app.ui.chat

import androidx.lifecycle.*
import com.meditrack.app.data.model.Message
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.collectLatest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for an individual chat conversation screen.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

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

