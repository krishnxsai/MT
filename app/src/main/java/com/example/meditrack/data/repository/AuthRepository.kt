package com.example.meditrack.data.repository

import android.content.Context
import android.net.Uri
import com.example.meditrack.data.model.Resource
import com.example.meditrack.data.model.User
import com.example.meditrack.data.model.UserRole
import com.example.meditrack.data.security.AuditLogger
import com.example.meditrack.data.security.SecurityManager
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return@withContext Resource.Error("User creation failed")

            // Update display name in Firebase Auth
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            // Create user document in Firestore
            val user = User(
                uid = firebaseUser.uid,
                email = email,
                displayName = displayName,
                role = role
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
            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user ?: return@withContext Resource.Error("Sign in failed")

            // Check if user already exists in Firestore
            var user = getUserFromFirestore(firebaseUser.uid)

            if (user == null) {
                // New user - create document
                user = User(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    displayName = firebaseUser.displayName ?: "",
                    profileImageUrl = firebaseUser.photoUrl?.toString() ?: "",
                    role = role ?: UserRole.PATIENT
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

    suspend fun getCurrentUser(): Resource<User> = withContext(Dispatchers.IO) {
        try {
            val firebaseUser = auth.currentUser ?: return@withContext Resource.Error("Not logged in")
            val user = getUserFromFirestore(firebaseUser.uid)
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
            "assignedDoctors" to user.assignedDoctors,
            "assignedDoctorNames" to user.assignedDoctorNames,
            "phoneNumber" to user.phoneNumber,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        usersCollection.document(user.uid).set(userData).await()
    }

    private suspend fun getUserFromFirestore(uid: String): User? {
        return try {
            val document = usersCollection.document(uid).get().await()
            if (document.exists()) {
                document.data?.let { User.fromMap(uid, it) }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

