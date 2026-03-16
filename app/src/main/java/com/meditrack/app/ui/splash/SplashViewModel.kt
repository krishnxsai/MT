package com.meditrack.app.ui.splash

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.AccountStatus
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _navigationTarget = MutableLiveData<NavigationTarget>()
    val navigationTarget: LiveData<NavigationTarget> = _navigationTarget

    fun resolveNavigation() {
        viewModelScope.launch {
            if (!authRepository.isLoggedIn) {
                _navigationTarget.value = NavigationTarget.Login
                return@launch
            }
            when (val result = authRepository.getCurrentUser()) {
                is Resource.Success -> {
                    val user = result.data
                    val target = if (user.status != AccountStatus.APPROVED) {
                        NavigationTarget.AccountPending
                    } else {
                        NavigationTarget.Dashboard(user)
                    }
                    _navigationTarget.value = target
                }
                is Resource.Error -> {
                    _navigationTarget.value = NavigationTarget.Login
                }
                is Resource.Loading -> { /* wait */ }
            }
        }
    }

    sealed class NavigationTarget {
        object Login : NavigationTarget()
        object AccountPending : NavigationTarget()
        data class Dashboard(val user: User) : NavigationTarget()
    }
}
