package com.meditrack.app.data.repository

import android.content.Context
import android.net.Uri
import com.meditrack.app.data.model.AccountStatus
import com.meditrack.app.data.model.Resource
import com.meditrack.app.data.model.User
import com.meditrack.app.data.model.UserRole
import com.meditrack.app.data.security.AuditLogger
import com.meditrack.app.data.security.SecurityManager
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.Source
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthRepository(private val context: Context? = null) {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    private val usersCollection by lazy { firestore.collection("users") }

    private val securityManager: SecurityManager? by lazy {
        context?.let { SecurityManager(it) }
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        role: UserRole
    ): Resource<User> = withContext(Dispatchers.IO) {
        try {
            // Admin accounts cannot be self-registered — they are seeded via script
            if (role == UserRole.ADMIN) {
                return@withContext Resource.Error("Admin accounts cannot be created through signup. Contact system administrator.")
            }

            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return@withContext Resource.Error("User creation failed")

            // Update display name in Firebase Auth
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            // Create user document in Firestore
            // Patients are auto-approved; Doctors/Pharmacies require admin approval
            val accountStatus = when (role) {
                UserRole.PATIENT -> AccountStatus.APPROVED
                UserRole.DOCTOR, UserRole.PHARMACY -> AccountStatus.PENDING
                UserRole.ADMIN -> AccountStatus.APPROVED // unreachable — blocked above
            }

            val user = User(
                uid = firebaseUser.uid,
                email = email,
                displayName = displayName,
                role = role,
                status = accountStatus
            )

            createUserDocument(user)

            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Sign up failed", e)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Resource<User> = withContext(Dispatchers.IO) {
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return@withContext Resource.Error("Sign in failed")

            val user = getUserFromFirestore(firebaseUser.uid)
            if (user != null) {
                Resource.Success(user)
            } else {
                Resource.Error("User profile not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Sign in failed", e)
        }
    }

    suspend fun signInWithCredential(credential: AuthCredential, role: UserRole? = null): Resource<User> = withContext(Dispatchers.IO) {
        try {
            // Admin accounts cannot be self-registered via Google sign-in
            if (role == UserRole.ADMIN) {
                return@withContext Resource.Error("Admin accounts cannot be created through signup. Contact system administrator.")
            }

            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user ?: return@withContext Resource.Error("Sign in failed")

            // Check if user already exists in Firestore
            var user = getUserFromFirestore(firebaseUser.uid)

            if (user == null) {
                // New user - create document
                val selectedRole = role ?: UserRole.PATIENT
                val accountStatus = when (selectedRole) {
                    UserRole.PATIENT -> AccountStatus.APPROVED
                    UserRole.DOCTOR, UserRole.PHARMACY -> AccountStatus.PENDING
                    UserRole.ADMIN -> AccountStatus.APPROVED // unreachable — blocked above
                }

                user = User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    displayName = firebaseUser.displayName ?: "",
                    profileImageUrl = firebaseUser.photoUrl?.toString() ?: "",
                    role = selectedRole,
                    status = accountStatus
                )
                createUserDocument(user)
            }

            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Sign in failed", e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.sendPasswordResetEmail(email).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send reset email", e)
        }
    }

    suspend fun getCurrentUser(forceServerRefresh: Boolean = false): Resource<User> = withContext(Dispatchers.IO) {
        try {
            val firebaseUser = auth.currentUser ?: return@withContext Resource.Error("Not logged in")
            val user = getUserFromFirestore(firebaseUser.uid, forceServerRefresh)
            if (user != null) {
                Resource.Success(user)
            } else {
                Resource.Error("User profile not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get user", e)
        }
    }

    suspend fun updateUserProfile(
        displayName: String? = null,
        phoneNumber: String? = null,
        assignedDoctors: List<String>? = null,
        assignedDoctorNames: Map<String, String>? = null
    ): Resource<User> = withContext(Dispatchers.IO) {
        try {
            val firebaseUser = auth.currentUser ?: return@withContext Resource.Error("Not logged in")

            val updates = mutableMapOf<String, Any>()
            displayName?.let {
                updates["displayName"] = it
                // Also update Firebase Auth profile
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(it)
                    .build()
                firebaseUser.updateProfile(profileUpdates).await()
            }
            phoneNumber?.let { updates["phoneNumber"] = it }
            assignedDoctors?.let { updates["assignedDoctors"] = it }
            assignedDoctorNames?.let { updates["assignedDoctorNames"] = it }
            updates["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

            usersCollection.document(firebaseUser.uid).update(updates).await()

            val updatedUser = getUserFromFirestore(firebaseUser.uid)
            if (updatedUser != null) {
                Resource.Success(updatedUser)
            } else {
                Resource.Error("Failed to retrieve updated profile")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update profile", e)
        }
    }

    suspend fun uploadProfileImage(imageUri: Uri): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val firebaseUser = auth.currentUser ?: return@withContext Resource.Error("Not logged in")

            val filename = "profile_images/${firebaseUser.uid}/${UUID.randomUUID()}.jpg"
            val storageRef = storage.reference.child(filename)

            storageRef.putFile(imageUri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // Update user document with new image URL
            usersCollection.document(firebaseUser.uid)
                .update(
                    mapOf(
                        "profileImageUrl" to downloadUrl,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()

            // Update Firebase Auth profile photo
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setPhotoUri(Uri.parse(downloadUrl))
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            Resource.Success(downloadUrl)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to upload image", e)
        }
    }

    suspend fun getDoctors(): Resource<List<User>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = usersCollection
                .whereEqualTo("role", UserRole.DOCTOR.name)
                .whereEqualTo("status", AccountStatus.APPROVED.name)
                .get()
                .await()

            val doctors = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { User.fromMap(doc.id, it) }
            }

            Resource.Success(doctors)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to get doctors", e)
        }
    }

    /**
     * Upload a license/verification document for Doctor or Pharmacy accounts.
     * Stores the file in Firebase Storage and updates the user document.
     */
    suspend fun uploadLicenseDocument(documentUri: Uri): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val firebaseUser = auth.currentUser ?: return@withContext Resource.Error("Not logged in")

            val filename = "license_documents/${firebaseUser.uid}/${UUID.randomUUID()}.pdf"
            val storageRef = storage.reference.child(filename)

            storageRef.putFile(documentUri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // Update user document with license URL
            usersCollection.document(firebaseUser.uid)
                .update(
                    mapOf(
                        "licenseUrl" to downloadUrl,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).await()

            Resource.Success(downloadUrl)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to upload license document", e)
        }
    }

    /**
     * Sign out user and clear all local data
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            // Log the logout action
            AuditLogger.logSuccess(AuditLogger.AuditAction.LOGOUT)

            // Clear secure session data
            securityManager?.clearSessionData()

            // Sign out from Firebase
            auth.signOut()
        } catch (e: Exception) {
            // Still sign out even if audit fails
            auth.signOut()
        }
    }

    /**
     * Sign out synchronously (for simple cases)
     */
    fun signOutSync() {
        auth.signOut()
        securityManager?.clearSessionData()
    }

    private suspend fun createUserDocument(user: User) {
        val userData = hashMapOf(
            "email" to user.email,
            "displayName" to user.displayName,
            "profileImageUrl" to user.profileImageUrl,
            "role" to user.role.name,
            "status" to user.status.name,
            "assignedDoctors" to user.assignedDoctors,
            "assignedDoctorNames" to user.assignedDoctorNames,
            "phoneNumber" to user.phoneNumber,
            "licenseUrl" to user.licenseUrl,
            "verifiedBy" to user.verifiedBy,
            "rejectionReason" to user.rejectionReason,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        usersCollection.document(user.uid).set(userData).await()
    }

    private suspend fun getUserFromFirestore(uid: String, forceServerRefresh: Boolean = false): User? {
        return try {
            val document = if (forceServerRefresh) {
                usersCollection.document(uid).get(Source.SERVER).await()
            } else {
                usersCollection.document(uid).get().await()
            }
            if (document.exists()) {
                document.data?.let { User.fromMap(uid, it) }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Batch-fetch display names for a list of user IDs.
     * Uses Firestore whereIn (max 30 per batch).
     */
    suspend fun getUserDisplayNames(userIds: List<String>): Resource<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            if (userIds.isEmpty()) return@withContext Resource.Success(emptyMap())

            val names = mutableMapOf<String, String>()
            // Firestore whereIn supports max 30 items per query
            for (batch in userIds.distinct().chunked(30)) {
                val snapshot = usersCollection
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), batch)
                    .get()
                    .await()
                for (doc in snapshot.documents) {
                    val name = doc.getString("displayName") ?: doc.id.take(8)
                    names[doc.id] = name
                }
            }
            Resource.Success(names)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch user names")
        }
    }
}

