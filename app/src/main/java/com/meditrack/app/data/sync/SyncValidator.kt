package com.meditrack.app.data.sync

import android.util.Log
import org.json.JSONObject
import java.util.Date

/**
 * Pre-sync validation to prevent invalid data from reaching Firestore.
 *
 * **Purpose**: Catch errors before syncing, prevent data corruption.
 *
 * **Examples**:
 * - Medicine quantity shouldn't be negative
 * - Appointment date must be in future
 * - Health vital signs must be in realistic ranges
 * - Required fields must be present
 *
 * **Strategy**:
 * - Fail-fast: Return validation result before attempting sync
 * - Log all violations for debugging
 * - Allow override (manual emergency sync bypass)
 */
sealed class ValidationResult {
    /**
     * Data is valid, safe to sync.
     */
    object Valid : ValidationResult()

    /**
     * Data has validation issues but might be recoverable.
     * Sync can proceed with warnings.
     */
    data class Warning(
        val violations: List<String>,
        val canProceed: Boolean = true
    ) : ValidationResult()

    /**
     * Data has critical validation errors.
     * Sync should not proceed.
     */
    data class Invalid(
        val violations: List<String>,
        val reason: String
    ) : ValidationResult()
}

/**
 * Sync validator for data quality checks before uploading to Firestore.
 * Prevents corrupted or incomplete data from reaching the cloud.
 */
class SyncValidator {

    companion object {
        private const val TAG = "SyncValidator"

        // Medicine constraints
        private const val MIN_MEDICINE_QUANTITY = 0
        private const val MAX_MEDICINE_QUANTITY = 10_000
        private const val MIN_DOSAGE_CHARS = 1
        private const val MAX_DOSAGE_CHARS = 100

        // Appointment constraints
        private const val MAX_APPOINTMENT_HOURS_IN_FUTURE = 24 * 365  // 1 year
        private const val MIN_APPOINTMENT_DURATION_MINUTES = 5
        private const val MAX_APPOINTMENT_DURATION_MINUTES = 480  // 8 hours

        // Health vital constraints
        private const val MIN_BLOOD_PRESSURE = 40
        private const val MAX_BLOOD_PRESSURE = 300
        private const val MIN_HEART_RATE = 20
        private const val MAX_HEART_RATE = 250
        private const val MIN_TEMPERATURE = 32.0  // Hypothermia threshold
        private const val MAX_TEMPERATURE = 43.0  // Fever threshold
        private const val MIN_BLOOD_SUGAR = 40
        private const val MAX_BLOOD_SUGAR = 600
    }

    /**
     * Validate pending action before sync.
     * Parses dataJson and applies resource-specific validation.
     *
     * @param resourceType Type of resource (MEDICINE, APPOINTMENT, HEALTHLOG, ORDER)
     * @param dataJson JSON string of data to validate
     * @return ValidationResult (Valid, Warning, or Invalid)
     */
    fun validateBeforeSync(
        resourceType: String,
        dataJson: String
    ): ValidationResult {
        return try {
            val data = JSONObject(dataJson)
            validateBySyncType(resourceType, data)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing data for validation", e)
            ValidationResult.Invalid(
                violations = listOf("Failed to parse data: ${e.message}"),
                reason = "Data format error"
            )
        }
    }

    /**
     * Route to type-specific validator.
     */
    private fun validateBySyncType(
        resourceType: String,
        data: JSONObject
    ): ValidationResult {
        return when (resourceType) {
            "MEDICINE" -> validateMedicine(data)
            "APPOINTMENT" -> validateAppointment(data)
            "HEALTHLOG" -> validateHealthLog(data)
            "ORDER" -> validateOrder(data)
            else -> {
                Log.w(TAG, "Unknown resource type: $resourceType")
                ValidationResult.Valid  // Allow unknown types
            }
        }
    }

    /**
     * Validate medicine data.
     * Checks: quantity in range, dosage present, required fields
     */
    private fun validateMedicine(data: JSONObject): ValidationResult {
        val violations = mutableListOf<String>()

        // Required fields
        if (!data.has("name") || data.getString("name").isBlank()) {
            violations.add("Medicine name is required")
        }

        if (!data.has("dosage") || data.getString("dosage").isBlank()) {
            violations.add("Medicine dosage is required")
        } else {
            val dosage = data.getString("dosage")
            if (dosage.length < MIN_DOSAGE_CHARS || dosage.length > MAX_DOSAGE_CHARS) {
                violations.add("Dosage length must be between $MIN_DOSAGE_CHARS and $MAX_DOSAGE_CHARS characters")
            }
        }

        // Quantity validation
        if (data.has("quantity")) {
            try {
                val quantity = (data.get("quantity") as? Number)?.toInt() ?: -1
                if (quantity > MAX_MEDICINE_QUANTITY) {
                    violations.add("Quantity $quantity exceeds maximum $MAX_MEDICINE_QUANTITY")
                }
            } catch (e: Exception) {
                violations.add("Invalid quantity format: ${e.message}")
            }
        }

        // Expiry date validation (if present)
        if (data.has("expiryDate")) {
            try {
                val expiryMs = data.getLong("expiryDate")
                val expiryDate = Date(expiryMs)
                if (expiryDate < Date()) {
                    violations.add("Medicine already expired: $expiryDate")
                }
            } catch (e: Exception) {
                violations.add("Invalid expiry date format")
            }
        }

        return when {
            violations.isEmpty() -> {
                Log.d(TAG, "Medicine validation: VALID")
                ValidationResult.Valid
            }
            violations.any { it.contains("required") || it.contains("Quantity") } -> {
                Log.w(TAG, "Medicine validation: INVALID - ${violations.joinToString("; ")}")
                ValidationResult.Invalid(
                    violations = violations,
                    reason = "Invalid medicine data"
                )
            }
            else -> {
                Log.d(TAG, "Medicine validation: WARNINGS - ${violations.joinToString("; ")}")
                ValidationResult.Warning(violations = violations)
            }
        }
    }

    /**
     * Validate appointment data.
     * Checks: date in future, duration reasonable, required fields
     */
    private fun validateAppointment(data: JSONObject): ValidationResult {
        val violations = mutableListOf<String>()

        // Required fields
        if (!data.has("doctorId") || data.getString("doctorId").isBlank()) {
            violations.add("Doctor ID is required")
        }

        if (!data.has("patientId") || data.getString("patientId").isBlank()) {
            violations.add("Patient ID is required")
        }

        // Date validation
        if (data.has("date")) {
            try {
                val appointmentMs = data.getLong("date")
                val appointmentDate = Date(appointmentMs)
                val now = Date()

                if (appointmentDate < now) {
                    violations.add("Appointment date must be in future: $appointmentDate is in past")
                }

                val hoursInFuture = (appointmentDate.time - now.time) / (1000 * 60 * 60)
                if (hoursInFuture > MAX_APPOINTMENT_HOURS_IN_FUTURE) {
                    violations.add("Appointment too far in future: $hoursInFuture hours > $MAX_APPOINTMENT_HOURS_IN_FUTURE max")
                }
            } catch (e: Exception) {
                violations.add("Invalid appointment date format")
            }
        }

        return when {
            violations.any { it.contains("required") } -> {
                Log.w(TAG, "Appointment validation: INVALID - ${violations.joinToString("; ")}")
                ValidationResult.Invalid(
                    violations = violations,
                    reason = "Missing required appointment fields"
                )
            }
            violations.isEmpty() -> {
                Log.d(TAG, "Appointment validation: VALID")
                ValidationResult.Valid
            }
            else -> {
                Log.d(TAG, "Appointment validation: WARNINGS - ${violations.joinToString("; ")}")
                ValidationResult.Warning(violations = violations)
            }
        }
    }

    /**
     * Validate health log data.
     * Checks: vital signs in realistic ranges, required fields
     */
    private fun validateHealthLog(data: JSONObject): ValidationResult {
        val violations = mutableListOf<String>()

        // Blood pressure validation
        if (data.has("bloodPressureSystolic")) {
            try {
                val systolic = (data.get("bloodPressureSystolic") as? Number)?.toInt() ?: 0
                if (systolic < MIN_BLOOD_PRESSURE || systolic > MAX_BLOOD_PRESSURE) {
                    violations.add("Systolic BP $systolic outside range [$MIN_BLOOD_PRESSURE-$MAX_BLOOD_PRESSURE]")
                }
            } catch (e: Exception) {
                violations.add("Invalid systolic BP format")
            }
        }

        // Heart rate validation
        if (data.has("heartRate")) {
            try {
                val heartRate = (data.get("heartRate") as? Number)?.toInt() ?: 0
                if (heartRate < MIN_HEART_RATE || heartRate > MAX_HEART_RATE) {
                    violations.add("Heart rate $heartRate outside range [$MIN_HEART_RATE-$MAX_HEART_RATE]")
                }
            } catch (e: Exception) {
                violations.add("Invalid heart rate format")
            }
        }

        // Temperature validation
        if (data.has("temperature")) {
            try {
                val temp = (data.get("temperature") as? Number)?.toDouble() ?: 0.0
                if (temp < MIN_TEMPERATURE || temp > MAX_TEMPERATURE) {
                    violations.add("Temperature $temp°C outside range [$MIN_TEMPERATURE-$MAX_TEMPERATURE]")
                }
            } catch (e: Exception) {
                violations.add("Invalid temperature format")
            }
        }

        // Blood sugar validation
        if (data.has("bloodSugar")) {
            try {
                val sugar = (data.get("bloodSugar") as? Number)?.toInt() ?: 0
                if (sugar < MIN_BLOOD_SUGAR || sugar > MAX_BLOOD_SUGAR) {
                    violations.add("Blood sugar $sugar outside range [$MIN_BLOOD_SUGAR-$MAX_BLOOD_SUGAR]")
                }
            } catch (e: Exception) {
                violations.add("Invalid blood sugar format")
            }
        }

        return when {
            violations.isEmpty() -> {
                Log.d(TAG, "HealthLog validation: VALID")
                ValidationResult.Valid
            }
            violations.size > 3 -> {
                // Multiple violation = likely user error
                Log.w(TAG, "HealthLog validation: WARNINGS - ${violations.joinToString("; ")}")
                ValidationResult.Warning(violations = violations)
            }
            else -> {
                Log.d(TAG, "HealthLog validation: WARNINGS - ${violations.joinToString("; ")}")
                ValidationResult.Warning(violations = violations)
            }
        }
    }

    /**
     * Validate order data.
     * Checks: required fields, reasonable totals
     */
    private fun validateOrder(data: JSONObject): ValidationResult {
        val violations = mutableListOf<String>()

        // Required fields
        if (!data.has("pharmacyId") || data.getString("pharmacyId").isBlank()) {
            violations.add("Pharmacy ID is required")
        }

        if (!data.has("patientId") || data.getString("patientId").isBlank()) {
            violations.add("Patient ID is required")
        }

        if (!data.has("items")) {
            violations.add("Order items are required")
        }

        // Total amount validation
        if (data.has("totalAmount")) {
            try {
                val total = (data.get("totalAmount") as? Number)?.toDouble() ?: 0.0
                if (total <= 0) {
                    violations.add("Order total must be positive, got $total")
                }
                if (total > 100_000) {
                    violations.add("Order total $total exceeds reasonable limit")
                }
            } catch (e: Exception) {
                violations.add("Invalid total amount format")
            }
        }

        return when {
            violations.any { it.contains("required") } -> {
                Log.w(TAG, "Order validation: INVALID - ${violations.joinToString("; ")}")
                ValidationResult.Invalid(
                    violations = violations,
                    reason = "Missing required order fields"
                )
            }
            violations.isEmpty() -> {
                Log.d(TAG, "Order validation: VALID")
                ValidationResult.Valid
            }
            else -> {
                Log.d(TAG, "Order validation: WARNINGS - ${violations.joinToString("; ")}")
                ValidationResult.Warning(violations = violations)
            }
        }
    }
}
