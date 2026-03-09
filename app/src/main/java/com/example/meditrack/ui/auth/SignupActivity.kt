package com.example.meditrack.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.meditrack.R
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.User
import com.example.meditrack.data.model.UserRole
import com.example.meditrack.databinding.ActivitySignupBinding
import com.example.meditrack.ui.admin.AdminDashboardActivity
import com.example.meditrack.ui.doctor.DoctorDashboardActivity
import com.example.meditrack.ui.main.HomeDashboardActivity
import com.example.meditrack.ui.pharmacy.PharmacyDashboardActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val viewModel: AuthViewModel by viewModels()
    private lateinit var credentialManager: CredentialManager

    private var selectedRole: UserRole = UserRole.PATIENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialManager = CredentialManager.create(this)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.roleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedRole = when {
                checkedIds.contains(R.id.doctorChip) -> UserRole.DOCTOR
                checkedIds.contains(R.id.pharmacyChip) -> UserRole.PHARMACY
                // Admin role cannot be self-registered — created only via seed script
                else -> UserRole.PATIENT
            }
        }

        binding.signupButton.setOnClickListener {
            if (validateInputs()) {
                val name = binding.nameEditText.text.toString().trim()
                val email = binding.emailEditText.text.toString().trim()
                val password = binding.passwordEditText.text.toString()
                viewModel.signUpWithEmail(email, password, name, selectedRole)
            }
        }

        binding.googleSignUpButton.setOnClickListener {
            signUpWithGoogle()
        }

        binding.signInText.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.signupResult.observe(this) { result ->
            when (result) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    navigateForUser(result.data)
                }
                is Resource.Error -> {
                    showLoading(false)
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.googleSignInResult.observe(this) { result ->
            when (result) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    navigateForUser(result.data)
                }
                is Resource.Error -> {
                    showLoading(false)
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun signUpWithGoogle() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@SignupActivity
                )
                handleGoogleSignUp(result)
            } catch (e: NoCredentialException) {
                Toast.makeText(
                    this@SignupActivity,
                    "No Google accounts found. Please add a Google account to your device.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: GetCredentialException) {
                android.util.Log.e("SignupActivity", "Google Sign-Up failed: ${e.type} - ${e.message}", e)
                Toast.makeText(
                    this@SignupActivity,
                    "Google Sign-Up failed: ${e.message ?: getString(R.string.error_google_sign_in)}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleGoogleSignUp(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val firebaseCredential = GoogleAuthProvider.getCredential(
                            googleIdTokenCredential.idToken,
                            null
                        )
                        viewModel.signInWithGoogleCredential(firebaseCredential, selectedRole)
                    } catch (e: GoogleIdTokenParsingException) {
                        Toast.makeText(this, getString(R.string.error_google_sign_in), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun validateInputs(): Boolean {
        val name = binding.nameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.error_empty_name)
            return false
        } else {
            binding.nameLayout.error = null
        }

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

        if (password.isEmpty()) {
            binding.passwordLayout.error = getString(R.string.error_empty_password)
            return false
        } else {
            binding.passwordLayout.error = null
        }

        if (password.length < 6) {
            binding.passwordLayout.error = getString(R.string.error_password_too_short)
            return false
        } else {
            binding.passwordLayout.error = null
        }

        if (password != confirmPassword) {
            binding.confirmPasswordLayout.error = getString(R.string.error_passwords_dont_match)
            return false
        } else {
            binding.confirmPasswordLayout.error = null
        }

        return true
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.signupButton.visibility = if (show) View.INVISIBLE else View.VISIBLE
        binding.googleSignUpButton.isEnabled = !show
    }

    private fun navigateForUser(user: User) {
        val intent = if (!user.isAccountActive) {
            // Account not approved — send to pending/rejected/suspended gate
            Intent(this, AccountPendingActivity::class.java)
        } else {
            when (user.role) {
                UserRole.DOCTOR -> Intent(this, DoctorDashboardActivity::class.java)
                UserRole.ADMIN -> Intent(this, AdminDashboardActivity::class.java)
                UserRole.PHARMACY -> Intent(this, PharmacyDashboardActivity::class.java)
                UserRole.PATIENT -> Intent(this, HomeDashboardActivity::class.java)
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

