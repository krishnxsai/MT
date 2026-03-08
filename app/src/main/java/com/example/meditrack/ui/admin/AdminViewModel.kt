package com.example.meditrack.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.User
import com.example.meditrack.data.model.UserRole
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AdminViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    private val _allUsers = MutableLiveData<Resource<List<User>>>()
    val allUsers: LiveData<Resource<List<User>>> = _allUsers

    private val _userCounts = MutableLiveData<Map<String, Int>>()
    val userCounts: LiveData<Map<String, Int>> = _userCounts

    private var cachedUsers: List<User> = emptyList()

    fun loadAllUsers() {
        _allUsers.value = Resource.Loading
        viewModelScope.launch {
            try {
                val users = withContext(Dispatchers.IO) {
                    val snapshot = usersCollection.get().await()
                    snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { data ->
                            User.fromMap(doc.id, data)
                        }
                    }
                }
                cachedUsers = users
                _allUsers.value = Resource.Success(users)
                calculateCounts(users)
            } catch (e: Exception) {
                _allUsers.value = Resource.Error(e.message ?: "Failed to load users", e)
            }
        }
    }

    fun filterUsers(query: String, roleFilter: UserRole?) {
        val filtered = cachedUsers.filter { user ->
            val matchesQuery = query.isEmpty() ||
                    user.displayName.contains(query, ignoreCase = true) ||
                    user.email.contains(query, ignoreCase = true)
            val matchesRole = roleFilter == null || user.role == roleFilter
            matchesQuery && matchesRole
        }
        _allUsers.value = Resource.Success(filtered)
    }

    private fun calculateCounts(users: List<User>) {
        val counts = mutableMapOf(
            "total" to users.size,
            "patients" to users.count { it.role == UserRole.PATIENT },
            "doctors" to users.count { it.role == UserRole.DOCTOR },
            "pharmacies" to users.count { it.role == UserRole.PHARMACY },
            "admins" to users.count { it.role == UserRole.ADMIN }
        )
        _userCounts.value = counts
    }
}

