package com.example.meditrack.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.meditrack.R
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.User
import com.example.meditrack.data.model.UserRole
import com.example.meditrack.databinding.ActivityEditProfileBinding

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val viewModel: ProfileViewModel by viewModels()

    private var currentUser: User? = null
    private var selectedDoctors: MutableMap<String, String> = mutableMapOf() // uid -> name
    private var doctorsList: List<User> = emptyList()

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadProfileImage(it)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openImagePicker()
        } else {
            Toast.makeText(this, "Permission required to select image", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.changePhotoButton.setOnClickListener {
            checkPermissionAndPickImage()
        }

        binding.saveButton.setOnClickListener {
            saveProfile()
        }

        binding.doctorDropdown.setOnClickListener {
            showDoctorMultiSelectDialog()
        }
        // Disable the default autocomplete behavior
        binding.doctorDropdown.isFocusable = false
        binding.doctorDropdown.isFocusableInTouchMode = false
    }

    private fun observeViewModel() {
        viewModel.currentUser.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.saveButton.visibility = View.INVISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.saveButton.visibility = View.VISIBLE
                    currentUser = result.data
                    populateForm(result.data)

                    // Load doctors if user is a patient
                    if (result.data.role == UserRole.PATIENT) {
                        binding.doctorLayout.visibility = View.VISIBLE
                        viewModel.loadDoctors()
                    }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.saveButton.visibility = View.VISIBLE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.updateResult.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.saveButton.visibility = View.INVISIBLE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.saveButton.visibility = View.VISIBLE
                    Toast.makeText(this, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
                    finish()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.saveButton.visibility = View.VISIBLE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.imageUploadResult.observe(this) { result ->
            when (result) {
                is Resource.Loading -> {
                    binding.imageProgressBar.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    binding.imageProgressBar.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.image_uploaded), Toast.LENGTH_SHORT).show()
                    Glide.with(this)
                        .load(result.data)
                        .placeholder(R.drawable.ic_person)
                        .into(binding.profileImage)
                }
                is Resource.Error -> {
                    binding.imageProgressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.doctorsList.observe(this) { result ->
            when (result) {
                is Resource.Success -> {
                    doctorsList = result.data
                    setupDoctorsDropdown(result.data)
                }
                is Resource.Error -> {
                    Toast.makeText(this, "Failed to load doctors", Toast.LENGTH_SHORT).show()
                }
                is Resource.Loading -> { }
            }
        }
    }

    private fun populateForm(user: User) {
        binding.nameEditText.setText(user.displayName)
        binding.phoneEditText.setText(user.phoneNumber)

        selectedDoctors = user.assignedDoctorNames.toMutableMap()

        updateDoctorDropdownText()

        if (user.profileImageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(user.profileImageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(binding.profileImage)
        }
    }

    private fun setupDoctorsDropdown(doctors: List<User>) {
        doctorsList = doctors
        updateDoctorDropdownText()
    }

    private fun updateDoctorDropdownText() {
        val names = selectedDoctors.values.filter { it.isNotEmpty() }
        binding.doctorDropdown.setText(
            if (names.isEmpty()) "" else names.joinToString(", "),
            false
        )
    }

    private fun showDoctorMultiSelectDialog() {
        if (doctorsList.isEmpty()) {
            Toast.makeText(this, "No doctors available", Toast.LENGTH_SHORT).show()
            return
        }
        val doctorNames = doctorsList.map { it.displayName.ifEmpty { it.email } }.toTypedArray()
        val checked = BooleanArray(doctorsList.size) { i ->
            selectedDoctors.containsKey(doctorsList[i].uid)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Doctors")
            .setMultiChoiceItems(doctorNames, checked) { _, which, isChecked ->
                val doc = doctorsList[which]
                if (isChecked) {
                    selectedDoctors[doc.uid] = doc.displayName
                } else {
                    selectedDoctors.remove(doc.uid)
                }
            }
            .setPositiveButton("OK") { _, _ ->
                updateDoctorDropdownText()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkPermissionAndPickImage() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                openImagePicker()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "Permission required to select image", Toast.LENGTH_SHORT).show()
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun openImagePicker() {
        pickImageLauncher.launch("image/*")
    }

    private fun saveProfile() {
        val name = binding.nameEditText.text.toString().trim()
        val phone = binding.phoneEditText.text.toString().trim()

        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.error_empty_name)
            return
        } else {
            binding.nameLayout.error = null
        }

        viewModel.updateProfile(
            displayName = name,
            phoneNumber = phone,
            assignedDoctors = if (currentUser?.role == UserRole.PATIENT) selectedDoctors.keys.toList() else null,
            assignedDoctorNames = if (currentUser?.role == UserRole.PATIENT) selectedDoctors else null
        )
    }
}

