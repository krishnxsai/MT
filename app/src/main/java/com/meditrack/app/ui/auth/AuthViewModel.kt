package com.meditrack.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.data.model.UserRole
import com.meditrack.app.data.repository.AuthRepository
import com.google.firebase.auth.AuthCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _loginResult = MutableLiveData<Resource<User>>()
    val loginResult: LiveData<Resource<User>> = _loginResult

    private val _signupResult = MutableLiveData<Resource<User>>()
    val signupResult: LiveData<Resource<User>> = _signupResult

    private val _resetPasswordResult = MutableLiveData<Resource<Unit>>()
    val resetPasswordResult: LiveData<Resource<Unit>> = _resetPasswordResult

    private val _googleSignInResult = MutableLiveData<Resource<User>>()
    val googleSignInResult: LiveData<Resource<User>> = _googleSignInResult

    val isLoggedIn: Boolean
        get() = repository.isLoggedIn

    fun signInWithEmail(email: String, password: String) {
        _loginResult.value = Resource.Loading
        viewModelScope.launch {
            _loginResult.value = repository.signInWithEmail(email, password)
        }
    }

    fun signUpWithEmail(email: String, password: String, displayName: String, role: UserRole) {
        _signupResult.value = Resource.Loading
        viewModelScope.launch {
            _signupResult.value = repository.signUpWithEmail(email, password, displayName, role)
        }
    }

    fun sendPasswordResetEmail(email: String) {
        _resetPasswordResult.value = Resource.Loading
        viewModelScope.launch {
            _resetPasswordResult.value = repository.sendPasswordResetEmail(email)
        }
    }

    fun signInWithGoogleCredential(credential: AuthCredential, role: UserRole? = null) {
        _googleSignInResult.value = Resource.Loading
        viewModelScope.launch {
            _googleSignInResult.value = repository.signInWithCredential(credential, role)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
        }
    }
}

