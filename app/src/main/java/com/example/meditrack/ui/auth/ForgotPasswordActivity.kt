package com.example.meditrack.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.meditrack.R
import com.example.meditrack.data.model.Resource
import com.example.meditrack.databinding.ActivityForgotPasswordBinding

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.resetButton.setOnClickListener {
            if (validateInput()) {
                val email = binding.emailEditText.text.toString().trim()
                viewModel.sendPasswordResetEmail(email)
            }
        }

        binding.signInText.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.resetPasswordResult.observe(this) { result ->
            when (result) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    Toast.makeText(this, getString(R.string.reset_email_sent), Toast.LENGTH_LONG).show()
                    finish()
                }
                is Resource.Error -> {
                    showLoading(false)
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun validateInput(): Boolean {
        val email = binding.emailEditText.text.toString().trim()

        if (email.isEmpty()) {
            binding.emailLayout.error = getString(R.string.error_empty_email)
            return false
        } else {
            binding.emailLayout.error = null
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = getString(R.string.error_invalid_email)
            return false
        } else {
            binding.emailLayout.error = null
        }

        return true
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.resetButton.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }
}

