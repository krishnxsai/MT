/**
 * Server-side Razorpay Payment Verification
 *
 * Verifies payment signatures using the Razorpay API secret stored in Firebase Secret Manager.
 * This prevents attackers from forging valid signatures on the client side.
 *
 * Formula: HMAC-SHA256(`orderId|paymentId`, secretKey)
 *
 * @param orderId Razorpay order ID from client
 * @param paymentId Razorpay payment ID from response
 * @param signature Razorpay signature from response
 * @param meditrackOrderId MediTrack order ID for tracking
 */

import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import * as crypto from "crypto";

/**
 * Callable Cloud Function for payment verification.
 * Must be called from authenticated client.
 */
export const verifyRazorpayPayment = functions.https.onCall(
  async (data, context) => {
    const db = admin.firestore();
    // ─────────────── Validate Request ───────────────
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "User must be authenticated to verify payment"
      );
    }

    const userId = context.auth.uid;
    const { orderId, paymentId, signature, meditrackOrderId } = data;

    // Validate inputs
    if (!orderId || typeof orderId !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "orderId is required and must be a string"
      );
    }

    if (!paymentId || typeof paymentId !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "paymentId is required and must be a string"
      );
    }

    if (!signature || typeof signature !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "signature is required and must be a string"
      );
    }

    if (!meditrackOrderId || typeof meditrackOrderId !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "meditrackOrderId is required and must be a string"
      );
    }

    try {
      // ─────────────── Get Razorpay Secret ───────────────
      // For now, we'll use environment variable or Remote Config
      // In production: Use Firebase Secret Manager
      // Reference: https://firebase.google.com/docs/functions/config-env
      const runtimeConfig = functions.config();
      let keySecret = process.env.RAZORPAY_KEY_SECRET || runtimeConfig.razorpay?.key_secret;

      if (!keySecret) {
        console.warn("RAZORPAY_KEY_SECRET not set in environment");
        // Fallback to Remote Config if Secret Manager not available
        // NOTE: This is NOT RECOMMENDED for production
        // Always use Secret Manager: gcloud secrets create razorpay-secret --data-file=-
        // Then reference in functions: firebase functions:config:set
        throw new functions.https.HttpsError(
          "internal",
          "Razorpay secret not configured"
        );
      }

      // ─────────────── Verify Signature ───────────────
      // Razorpay sends: HMAC-SHA256(orderId|paymentId, keySecret)
      const message = `${orderId}|${paymentId}`;
      const expectedSignature = crypto
        .createHmac("sha256", keySecret)
        .update(message)
        .digest("hex");

      const isSignatureValid = expectedSignature === signature;

      console.log(
        `Payment verification: orderId=${orderId}, paymentId=${paymentId}, valid=${isSignatureValid}`
      );

      if (!isSignatureValid) {
        // Log suspicious activity
        await db.collection("suspiciousPayments").add({
          orderId,
          paymentId,
          userId,
          providedSignature: signature,
          expectedSignature,
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          reason: "Signature mismatch",
        });

        throw new functions.https.HttpsError(
          "failed-precondition",
          "Payment signature verification failed"
        );
      }

      // ─────────────── Check Idempotency ───────────────
      // Prevent duplicate payments for the same order
      const existingPayment = await db
        .collection("payments")
        .where("meditrackOrderId", "==", meditrackOrderId)
        .where("razorpayPaymentId", "==", paymentId)
        .limit(1)
        .get();

      if (!existingPayment.empty) {
        console.log(`Payment already verified: ${paymentId}`);
        const existing = existingPayment.docs[0].data();
        return {
          isValid: true,
          paymentId,
          orderId,
          reason: "Payment already verified (idempotent)",
          existingStatus: existing.status,
        };
      }

      // ─────────────── Update Payment Record ───────────────
      // Mark payment as verified server-side
      const paymentQuery = await db
        .collection("payments")
        .where("meditrackOrderId", "==", meditrackOrderId)
        .where("razorpayOrderId", "==", orderId)
        .limit(1)
        .get();

      if (paymentQuery.empty) {
        throw new functions.https.HttpsError(
          "not-found",
          "Payment record not found in database"
        );
      }

      const paymentDocId = paymentQuery.docs[0].id;

      // Update with server-verified signature
      await db.collection("payments").doc(paymentDocId).update({
        razorpayPaymentId: paymentId,
        serverVerifiedSignature: signature,
        signatureValidatedAt: admin.firestore.FieldValue.serverTimestamp(),
        status: "CAPTURED",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      console.log(
        `Payment verified and updated: ${paymentDocId}, paymentId=${paymentId}`
      );

      // ─────────────── Return Result ───────────────
      return {
        isValid: true,
        paymentId,
        orderId,
        meditrackOrderId,
        message: "Payment signature verified successfully",
      };
    } catch (error: unknown) {
      const err = error as { message?: string; code?: string };
      console.error(`Payment verification error: ${err.message}`, error);

      // Re-throw HttpsErrors as-is
      if (error instanceof functions.https.HttpsError) {
        throw error;
      }

      // Wrap other errors
      throw new functions.https.HttpsError(
        "internal",
        `Payment verification failed: ${err.message}`
      );
    }
  }
);

/**
 * Helper: HMAC-SHA256 signature generation (for testing)
 * Not called in production flow, but useful for test utilities
 */
export function generateRazorpaySignature(
  orderId: string,
  paymentId: string,
  keySecret: string
): string {
  const message = `${orderId}|${paymentId}`;
  return crypto
    .createHmac("sha256", keySecret)
    .update(message)
    .digest("hex");
}
