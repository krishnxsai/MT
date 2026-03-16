package com.meditrack.app.domain.usecase

import com.meditrack.app.data.model.AccountStatus
import com.meditrack.app.data.model.User
import com.meditrack.app.data.model.UserRole
import javax.inject.Inject

class FilterUsersUseCase @Inject constructor() {
    fun execute(
        users: List<User>,
        query: String = "",
        roleFilter: UserRole? = null,
        statusFilter: AccountStatus? = null
    ): List<User> {
        return users.filter { user ->
            val matchesQuery = query.isEmpty() ||
                    user.displayName.contains(query, ignoreCase = true) ||
                    user.email.contains(query, ignoreCase = true)
            val matchesRole = roleFilter == null || user.role == roleFilter
            val matchesStatus = statusFilter == null || user.status == statusFilter
            matchesQuery && matchesRole && matchesStatus
        }
    }
}
