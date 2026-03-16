package com.meditrack.app.ui.auth

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountPendingViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _userStatus = MutableLiveData<Resource<User>>()
    val userStatus: LiveData<Resource<User>> = _userStatus

    private val _uploadResult = MutableLiveData<Resource<String>>()
    val uploadResult: LiveData<Resource<String>> = _uploadResult

    fun loadUserStatus(forceServerRefresh: Boolean = false) {
        _userStatus.value = Resource.Loading
        viewModelScope.launch {
            _userStatus.value = authRepository.getCurrentUser(forceServerRefresh = forceServerRefresh)
        }
    }

    fun uploadLicense(documentUri: Uri) {
        _uploadResult.value = Resource.Loading
        viewModelScope.launch {
            _uploadResult.value = authRepository.uploadLicenseDocument(documentUri)
        }
    }

    fun signOut() {
        authRepository.signOutSync()
    }
}
