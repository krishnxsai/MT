package com.example.meditrack.ui.profile

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.User
import com.example.meditrack.data.repository.AuthRepository
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _currentUser = MutableLiveData<Resource<User>>()
    val currentUser: LiveData<Resource<User>> = _currentUser

    private val _updateResult = MutableLiveData<Resource<User>>()
    val updateResult: LiveData<Resource<User>> = _updateResult

    private val _imageUploadResult = MutableLiveData<Resource<String>>()
    val imageUploadResult: LiveData<Resource<String>> = _imageUploadResult

    private val _doctorsList = MutableLiveData<Resource<List<User>>>()
    val doctorsList: LiveData<Resource<List<User>>> = _doctorsList

    init {
        loadCurrentUser()
    }

    fun loadCurrentUser() {
        _currentUser.value = Resource.Loading
        viewModelScope.launch {
            _currentUser.value = repository.getCurrentUser()
        }
    }

    fun updateProfile(
        displayName: String? = null,
        phoneNumber: String? = null,
        assignedDoctors: List<String>? = null,
        assignedDoctorNames: Map<String, String>? = null
    ) {
        _updateResult.value = Resource.Loading
        viewModelScope.launch {
            _updateResult.value = repository.updateUserProfile(
                displayName = displayName,
                phoneNumber = phoneNumber,
                assignedDoctors = assignedDoctors,
                assignedDoctorNames = assignedDoctorNames
            )
        }
    }

    fun uploadProfileImage(imageUri: Uri) {
        _imageUploadResult.value = Resource.Loading
        viewModelScope.launch {
            _imageUploadResult.value = repository.uploadProfileImage(imageUri)
        }
    }

    fun loadDoctors() {
        _doctorsList.value = Resource.Loading
        viewModelScope.launch {
            _doctorsList.value = repository.getDoctors()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
        }
    }
}

