package com.meditrack.app.ui.pharmacy

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meditrack.app.data.model.Pharmacy
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.repository.PharmacyRepository
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class PharmacyProfileSetupViewModel @Inject constructor(
    private val pharmacyRepository: PharmacyRepository,
    private val storage: FirebaseStorage
) : ViewModel() {

    private val _pharmacy = MutableLiveData<Resource<Pharmacy?>>()
    val pharmacy: LiveData<Resource<Pharmacy?>> = _pharmacy

    private val _saveResult = MutableLiveData<Resource<Pharmacy>?>()
    val saveResult: LiveData<Resource<Pharmacy>?> = _saveResult

    private val _uploadResult = MutableLiveData<Resource<String>>()
    val uploadResult: LiveData<Resource<String>> = _uploadResult

    fun loadPharmacy(pharmacyId: String?) {
        if (pharmacyId == null) {
            loadMyPharmacy()
            return
        }
        _pharmacy.value = Resource.Loading
        viewModelScope.launch {
            when (val result = pharmacyRepository.getPharmacy(pharmacyId)) {
                is Resource.Success -> _pharmacy.value = Resource.Success(result.data)
                is Resource.Error -> _pharmacy.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadMyPharmacy() {
        _pharmacy.value = Resource.Loading
        viewModelScope.launch {
            when (val result = pharmacyRepository.getMyPharmacy()) {
                is Resource.Success -> _pharmacy.value = Resource.Success(result.data)
                is Resource.Error -> _pharmacy.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun saveProfile(pharmacy: Pharmacy) {
        _saveResult.value = Resource.Loading
        viewModelScope.launch {
            _saveResult.value = pharmacyRepository.savePharmacyProfile(pharmacy)
        }
    }

    fun uploadFile(uri: Uri, path: String) {
        _uploadResult.value = Resource.Loading
        viewModelScope.launch {
            try {
                val ref = storage.reference.child(path)
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await()
                _uploadResult.value = Resource.Success(downloadUrl.toString())
            } catch (e: Exception) {
                _uploadResult.value = Resource.Error("Upload failed: ${e.message}", e)
            }
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }
}
