package com.meditrack.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.AccountStatus
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.data.model.UserRole
import com.meditrack.app.data.repository.AdminRepository
import com.meditrack.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: AdminRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _allUsers = MutableLiveData<Resource<List<User>>>()
    val allUsers: LiveData<Resource<List<User>>> = _allUsers

    private val _pendingUsers = MutableLiveData<Resource<List<User>>>()
    val pendingUsers: LiveData<Resource<List<User>>> = _pendingUsers

    private val _userCounts = MutableLiveData<Map<String, Int>>()
    val userCounts: LiveData<Map<String, Int>> = _userCounts

    private val _actionResult = MutableLiveData<Resource<Unit>>()
    val actionResult: LiveData<Resource<Unit>> = _actionResult

    private val _auditLogs = MutableLiveData<Resource<List<Map<String, Any?>>>>()
    val auditLogs: LiveData<Resource<List<Map<String, Any?>>>> = _auditLogs

    private var cachedUsers: List<User> = emptyList()

    fun loadAllUsers() {
        _allUsers.value = Resource.Loading
        viewModelScope.launch {
            when (val result = repository.getAllUsers()) {
                is Resource.Success -> {
                    cachedUsers = result.data
                    _allUsers.value = Resource.Success(result.data)
                    calculateCounts(result.data)
                }
                is Resource.Error -> _allUsers.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun loadPendingUsers() {
        _pendingUsers.value = Resource.Loading
        viewModelScope.launch {
            _pendingUsers.value = repository.getPendingUsers()
        }
    }

    fun filterUsers(query: String, roleFilter: UserRole?, statusFilter: AccountStatus? = null) {
        val filtered = cachedUsers.filter { user ->
            val matchesQuery = query.isEmpty() ||
                    user.displayName.contains(query, ignoreCase = true) ||
                    user.email.contains(query, ignoreCase = true)
            val matchesRole = roleFilter == null || user.role == roleFilter
            val matchesStatus = statusFilter == null || user.status == statusFilter
            matchesQuery && matchesRole && matchesStatus
        }
        _allUsers.value = Resource.Success(filtered)
    }

    fun approveUser(userId: String) {
        _actionResult.value = Resource.Loading
        viewModelScope.launch {
            _actionResult.value = repository.approveUser(userId)
            // Refresh both lists
            loadAllUsers()
            loadPendingUsers()
        }
    }

    fun rejectUser(userId: String, reason: String) {
        _actionResult.value = Resource.Loading
        viewModelScope.launch {
            _actionResult.value = repository.rejectUser(userId, reason)
            loadAllUsers()
            loadPendingUsers()
        }
    }

    fun suspendUser(userId: String) {
        _actionResult.value = Resource.Loading
        viewModelScope.launch {
            _actionResult.value = repository.suspendUser(userId)
            loadAllUsers()
        }
    }

    fun reactivateUser(userId: String) {
        _actionResult.value = Resource.Loading
        viewModelScope.launch {
            _actionResult.value = repository.reactivateUser(userId)
            loadAllUsers()
        }
    }

    fun loadAuditLogs() {
        _auditLogs.value = Resource.Loading
        viewModelScope.launch {
            _auditLogs.value = repository.getAuditLogs()
        }
    }

    private fun calculateCounts(users: List<User>) {
        val counts = mutableMapOf(
            "total" to users.size,
            "patients" to users.count { it.role == UserRole.PATIENT },
            "doctors" to users.count { it.role == UserRole.DOCTOR },
            "pharmacies" to users.count { it.role == UserRole.PHARMACY },
            "admins" to users.count { it.role == UserRole.ADMIN },
            "pending" to users.count { it.status == AccountStatus.PENDING },
            "suspended" to users.count { it.status == AccountStatus.SUSPENDED }
        )
        _userCounts.value = counts
    }

    fun signOut() {
        authRepository.signOutSync()
    }
}

