package com.meditrack.app.data.sync

import androidx.room.*
import com.google.firebase.Timestamp
import java.util.Date

/**
 * Type converter for Room database to handle Date objects.
 */
class DateConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

/**
 * Room database entity for storing pending/failed payment attempts.
 * Used for offline payment retry when connectivity restored.
 *
 * When payment fails or user is offline:
 * 1. Store payment attempt in local Room database
 * 2. When connectivity restored, DataSyncWorker retries all pending payments
 * 3. On success, remove from queue
 * 4. After 3 failed attempts, notify user and stop retrying
 */
@Entity(tableName = "pending_payments")
data class PendingPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // ── Order Details ─────────────────────────────────
    val meditrackOrderId: String,      // Our order ID
    val razorpayOrderId: String,       // Razorpay order ID (if created)
    val amount: Double,                // Amount in rupees
    val currency: String = "INR",

    // ── Payment Details ───────────────────────────────
    val razorpayPaymentId: String = "",     // Set after payment
    val razorpaySignature: String = "",     // Set after payment
    val paymentMethod: String = "UPI",

    // ── Customer Info ─────────────────────────────────
    val userEmail: String,
    val userPhone: String,
    val userName: String,

    // ── Retry Tracking ────────────────────────────────
    val attemptCount: Int = 0,         // Number of retry attempts
    val maxAttempts: Int = 3,          // Stop retrying after 3 attempts
    val lastAttemptAt: Date? = null,   // When we last tried
    val nextRetryAt: Date? = null,     // When to retry next (exponential backoff)

    // ── Status ────────────────────────────────────────
    val status: PaymentQueueStatus = PaymentQueueStatus.PENDING,   // PENDING, PROCESSING, SUCCESS, FAILED
    val errorMessage: String = "",

    // ── Timestamps ────────────────────────────────────
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {
    constructor() : this(
        id = 0,
        meditrackOrderId = "",
        razorpayOrderId = "",
        amount = 0.0,
        userEmail = "",
        userPhone = "",
        userName = ""
    )
}

enum class PaymentQueueStatus {
    PENDING,      // Waiting to be processed
    PROCESSING,   // Currently being processed
    SUCCESS,      // Payment succeeded
    FAILED,       // Payment failed after max attempts
    RETRYING      // Scheduled for retry
}

/**
 * Room DAO for PendingPayment operations.
 */
@Dao
interface PendingPaymentDao {

    @Insert
    suspend fun insertPayment(payment: PendingPayment): Long

    @Update
    suspend fun updatePayment(payment: PendingPayment)

    @Delete
    suspend fun deletePayment(payment: PendingPayment)

    @Query("SELECT * FROM pending_payments WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getPaymentsByStatus(status: PaymentQueueStatus): List<PendingPayment>

    @Query("SELECT * FROM pending_payments WHERE meditrackOrderId = :orderId")
    suspend fun getPaymentByOrderId(orderId: String): PendingPayment?

    @Query("SELECT * FROM pending_payments WHERE status IN (:statuses) ORDER BY nextRetryAt ASC LIMIT :limit")
    suspend fun getPendingPaymentsForRetry(
        statuses: List<PaymentQueueStatus> = listOf(PaymentQueueStatus.PENDING, PaymentQueueStatus.RETRYING),
        limit: Int = 10
    ): List<PendingPayment>

    @Query("SELECT COUNT(*) FROM pending_payments WHERE status = :status")
    suspend fun countPaymentsByStatus(status: PaymentQueueStatus): Int

    @Query("DELETE FROM pending_payments WHERE status = :status AND updatedAt < :beforeDate")
    suspend fun deleteOldPayments(status: PaymentQueueStatus, beforeDate: Date): Int

    @Query("SELECT * FROM pending_payments ORDER BY createdAt DESC")
    suspend fun getAllPayments(): List<PendingPayment>
}

/**
 * In-memory Room database for offline payment management.
 * Data is NOT persisted across app restarts (intentional - payments are temporary state).
 * For persistent storage, use a real database with migration strategy.
 */
@Database(
    entities = [PendingPayment::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class OfflinePaymentDatabase : RoomDatabase() {
    abstract fun pendingPaymentDao(): PendingPaymentDao
}

/**
 * Repository for managing offline payment queue.
 */
class OfflinePaymentQueueRepository(private val dao: PendingPaymentDao) {

    /**
     * Add a payment to the retry queue (when payment fails or offline).
     */
    suspend fun queuePayment(payment: PendingPayment) {
        val existing = dao.getPaymentByOrderId(payment.meditrackOrderId)
        if (existing != null) {
            // Update existing payment with new attempt
            dao.updatePayment(
                existing.copy(
                    status = PaymentQueueStatus.PENDING,
                    attemptCount = existing.attemptCount + 1,
                    lastAttemptAt = Date(),
                    nextRetryAt = calculateNextRetryTime(existing.attemptCount),
                    updatedAt = Date()
                )
            )
        } else {
            // Insert new payment
            dao.insertPayment(
                payment.copy(
                    status = PaymentQueueStatus.PENDING,
                    createdAt = Date(),
                    updatedAt = Date()
                )
            )
        }
    }

    /**
     * Get payments ready to retry.
     */
    suspend fun getPaymentsReadyForRetry(): List<PendingPayment> {
        val payments = dao.getPendingPaymentsForRetry()
        return payments.filter { payment ->
            payment.attemptCount < payment.maxAttempts &&
            (payment.nextRetryAt == null || payment.nextRetryAt!! <= Date())
        }
    }

    /**
     * Mark payment as processing.
     */
    suspend fun markProcessing(payment: PendingPayment) {
        dao.updatePayment(
            payment.copy(
                status = PaymentQueueStatus.PROCESSING,
                updatedAt = Date()
            )
        )
    }

    /**
     * Mark payment as successful.
     */
    suspend fun markSuccessful(payment: PendingPayment) {
        dao.updatePayment(
            payment.copy(
                status = PaymentQueueStatus.SUCCESS,
                updatedAt = Date()
            )
        )
    }

    /**
     * Mark payment as failed (give up after max attempts).
     */
    suspend fun markFailed(payment: PendingPayment, errorMessage: String) {
        dao.updatePayment(
            payment.copy(
                status = PaymentQueueStatus.FAILED,
                errorMessage = errorMessage,
                updatedAt = Date()
            )
        )
    }

    /**
     * Remove successful payment from queue.
     */
    suspend fun removePayment(payment: PendingPayment) {
        dao.deletePayment(payment)
    }

    /**
     * Get count of pending payments.
     */
    suspend fun getPendingCount(): Int {
        return dao.countPaymentsByStatus(PaymentQueueStatus.PENDING)
    }

    /**
     * Calculate next retry time using exponential backoff.
     * Attempt 1: 5 minutes
     * Attempt 2: 15 minutes
     * Attempt 3: 30 minutes
     */
    private fun calculateNextRetryTime(attemptCount: Int): Date {
        val delayMillis = when (attemptCount) {
            0 -> 5 * 60 * 1000L      // 5 minutes
            1 -> 15 * 60 * 1000L     // 15 minutes
            2 -> 30 * 60 * 1000L     // 30 minutes
            else -> 60 * 60 * 1000L  // 1 hour
        }
        return Date(System.currentTimeMillis() + delayMillis)
    }
}
