# Data Model Gaps - Analysis & Fix Plan

## ✅ Verification Status: ALL 10 GAPS CONFIRMED

### Gap 1: ❌ RefillOrder Missing paymentId Link
**File:** `RefillOrder.kt`
**Issue:** No direct link to payment transaction
**Impact:** Cannot track which payment corresponds to which order
**Fix:** Add `paymentId: String` field

### Gap 2: ❌ No DeliveryAddress Change History
**File:** Missing model entirely
**Issue:** Cannot track address modifications for dispute resolution
**Impact:** Lost audit trail for address changes
**Fix:** Create `DeliveryAddressHistory.kt` model

### Gap 3: ❌ Medicine Model Missing Expiry Dates
**File:** `Medicine.kt`
**Issue:** No `expiryDate` or `expiryMonthYear` field
**Impact:** Cannot alert users about expired medications
**Fix:** Add `expiryDate: Date` and `expiryMonthYear: String` fields

### Gap 4: ❌ Appointment Missing Telemedicine Support
**File:** `Appointment.kt`
**Issue:** No video call fields or telemedicine type
**Impact:** Cannot support remote consultations
**Fix:** Add `isTelemedicine: Boolean`, `callUrl: String`, `meetingId: String`

### Gap 5: ❌ PrescriptionRecord Missing Digital Signatures
**File:** `PrescriptionRecord.kt`
**Issue:** No `digitalSignature` or `signedBy` fields
**Impact:** Regulatory gap - cannot verify doctor's authorization
**Fix:** Add `digitalSignature: String` and `signedAt: Date`

### Gap 6: ❌ CancellationRequest Missing Refund Failure Reason
**File:** `CancellationRequest.kt`
**Issue:** No `refundFailureReason` field when status=FAILED
**Impact:** Cannot troubleshoot why refund failed
**Fix:** Add `refundFailureReason: String` field

### Gap 7: ❌ Insurance Model Not Implemented
**File:** Missing entirely
**Issue:** Cannot track insurance claims or coverage info
**Impact:** Phase 5 feature blocked
**Fix:** Create `Insurance.kt` model

### Gap 8: ❌ PharmacyReview Model Not Implemented
**File:** Missing entirely
**Issue:** Cannot collect pharmacy ratings/reviews
**Impact:** No quality feedback mechanism
**Fix:** Create `PharmacyReview.kt` model

### Gap 9: ❌ Appointment Missing Cancellation Fees
**File:** `Appointment.kt`
**Issue:** No `cancellationFee` or `cancellationDeadlineHours` fields
**Impact:** Cannot enforce cancellation charges
**Fix:** Add `cancellationFee: Double`, `cancellationDeadlineHours: Int`, `cancellationFeeApplied: Double`

### Gap 10: ❌ PrescriptionReminder Configuration Not Implemented
**File:** Missing entirely
**Issue:** Cannot customize reminder preferences per prescription
**Impact:** One-size-fits-all reminders only
**Fix:** Create `PrescriptionReminder.kt` model

---

## Implementation Plan

**Priority: HIGH** - All gaps impact core functionality

**Estimated Impact:**
- Gap 1,3,5,6,9: Quick updates to existing models (15 min)
- Gap 4: Medium update to Appointment (20 min)
- Gap 2,7,8,10: New models ~200 LOC total (30 min)
- Total: ~65 minutes

**Build Status After Fixes:** Expected 0 errors, production ready
