package com.example.meditrack.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.data.model.UserRole
import com.example.meditrack.databinding.ActivitySplashBinding
import com.example.meditrack.ui.admin.AdminDashboardActivity
import com.example.meditrack.ui.auth.LoginActivity
import com.example.meditrack.ui.doctor.DoctorDashboardActivity
import com.example.meditrack.ui.main.HomeDashboardActivity
import com.example.meditrack.ui.pharmacy.PharmacyDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, 2000)
    }

    private fun navigateToNextScreen() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Check user role and navigate accordingly
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val userDoc = withContext(Dispatchers.IO) {
                        firestore.collection("users")
                            .document(currentUser.uid)
                            .get()
                            .await()
                    }

                    val role = userDoc.getString("role")
                    val intent = when (role) {
                        UserRole.DOCTOR.name -> Intent(this@SplashActivity, DoctorDashboardActivity::class.java)
                        UserRole.ADMIN.name -> Intent(this@SplashActivity, AdminDashboardActivity::class.java)
                        UserRole.PHARMACY.name -> Intent(this@SplashActivity, PharmacyDashboardActivity::class.java)
                        else -> Intent(this@SplashActivity, HomeDashboardActivity::class.java)
                    }
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    // Default to patient dashboard on error
                    startActivity(Intent(this@SplashActivity, HomeDashboardActivity::class.java))
                    finish()
                }
            }
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}

