package com.meditrack.app.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.meditrack.app.core.navigation.RoleBasedNavigator
import com.meditrack.app.databinding.ActivitySplashBinding
import com.meditrack.app.ui.auth.AccountPendingActivity
import com.meditrack.app.ui.auth.LoginActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.navigationTarget.observe(this) { target ->
            when (target) {
                is SplashViewModel.NavigationTarget.Login -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                is SplashViewModel.NavigationTarget.AccountPending -> {
                    val intent = Intent(this, AccountPendingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                is SplashViewModel.NavigationTarget.Dashboard -> {
                    RoleBasedNavigator.navigateToDashboard(this, target.user)
                }
            }
        }

        lifecycleScope.launch {
            delay(2000)
            viewModel.resolveNavigation()
        }
    }
}
