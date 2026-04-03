package com.meditrack.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.meditrack.app.data.model.Cart
import com.meditrack.app.data.model.CartItem
import com.meditrack.app.data.model.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Repository for persisted user cart state.
 *
 * Cart is stored in Firestore collection "carts" with document id = userId.
 */
class CartRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val cartsCollection by lazy { firestore.collection("carts") }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun getCart(): Resource<Cart?> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")
            val snapshot = cartsCollection.document(userId).get().await()

            if (!snapshot.exists()) {
                return@withContext Resource.Success(null)
            }

            val data = snapshot.data ?: return@withContext Resource.Success(null)
            Resource.Success(Cart.fromMap(snapshot.id, data))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load cart", e)
        }
    }

    suspend fun saveCart(items: List<CartItem>, selectedPharmacyId: String?): Resource<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")

                val now = System.currentTimeMillis()
                val expiresAt = Date(now + 24L * 60L * 60L * 1000L)

                val payload = mutableMapOf<String, Any?>(
                    "userId" to userId,
                    "items" to items.map { it.toMap() },
                    "selectedPharmacyId" to selectedPharmacyId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to expiresAt
                )

                cartsCollection.document(userId)
                    .set(payload, SetOptions.merge())
                    .await()

                Resource.Success(Unit)
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Failed to save cart", e)
            }
        }

    suspend fun clearCart(): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId ?: return@withContext Resource.Error("User not logged in")
            cartsCollection.document(userId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to clear cart", e)
        }
    }
}
