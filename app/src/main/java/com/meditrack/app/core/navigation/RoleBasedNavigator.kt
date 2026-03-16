package com.meditrack.app.core.navigation

import android.app.Activity
import android.content.Intent
import com.meditrack.app.data.model.User
import com.meditrack.app.data.model.UserRole
import com.meditrack.app.ui.admin.AdminDashboardActivity
import com.meditrack.app.ui.doctor.DoctorDashboardActivity
import com.meditrack.app.ui.main.HomeDashboardActivity
import com.meditrack.app.ui.pharmacy.PharmacyDashboardActivity

object RoleBasedNavigator {

    fun getDashboardIntent(activity: Activity, user: User): Intent {
        return when (user.role) {
            UserRole.PATIENT -> Intent(activity, HomeDashboardActivity::class.java)
            UserRole.DOCTOR -> Intent(activity, DoctorDashboardActivity::class.java)
            UserRole.ADMIN -> Intent(activity, AdminDashboardActivity::class.java)
            UserRole.PHARMACY -> Intent(activity, PharmacyDashboardActivity::class.java)
        }
    }

    fun navigateToDashboard(activity: Activity, user: User) {
        val intent = getDashboardIntent(activity, user).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }
}
