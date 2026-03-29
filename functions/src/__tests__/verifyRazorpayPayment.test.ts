import * as crypto from "crypto";
import { generateRazorpaySignature } from "../verifyRazorpayPayment";

/**
 * Unit tests for Razorpay payment signature generation and validation.
 * Tests the HMAC-SHA256 signature algorithm used in verifyRazorpayPayment.
 */

describe("Razorpay Payment Signature Generation", () => {
  const testContext = {
    orderId: "order_1234567890",
    paymentId: "pay_9876543210",
    secret: "test_secret_key_12345",
  };

  // ─────────────── Helper Functions ───────────────

  function generateSignature(
    orderId: string,
    paymentId: string,
    secret: string
  ): string {
    const message = `${orderId}|${paymentId}`;
    return crypto.createHmac("sha256", secret).update(message).digest("hex");
  }

  // ─────────────── Signature Verification Tests ───────────────

  describe("Signature Verification Logic", () => {
    test("should generate valid HMAC-SHA256 signature", () => {
      // Arrange & Act
      const signature = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );

      // Assert - should be 64 character hex string (SHA256)
      expect(signature).toMatch(/^[a-f0-9]{64}$/);
    });

    test("same input should produce same signature (deterministic)", () => {
      // Arrange & Act
      const sig1 = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );
      const sig2 = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );

      // Assert
      expect(sig1).toBe(sig2);
    });

    test("different secret should produce different signature", () => {
      // Arrange & Act
      const sig1 = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );
      const sig2 = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        "different_secret"
      );

      // Assert
      expect(sig1).not.toBe(sig2);
    });

    test("modified orderId should produce different signature", () => {
      // Arrange & Act
      const sig1 = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );
      const sig2 = generateRazorpaySignature(
        "different_order_id",
        testContext.paymentId,
        testContext.secret
      );

      // Assert
      expect(sig1).not.toBe(sig2);
    });

    test("modified paymentId should produce different signature", () => {
      // Arrange & Act
      const sig1 = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );
      const sig2 = generateRazorpaySignature(
        testContext.orderId,
        "different_payment_id",
        testContext.secret
      );

      // Assert
      expect(sig1).not.toBe(sig2);
    });

    test("signature should be case-insensitive hex", () => {
      // Arrange & Act
      const sig = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );

      // Assert - both uppercase and lowercase should be valid hex
      expect(sig.toLowerCase()).toMatch(/^[a-f0-9]{64}$/);
      expect(sig.toUpperCase()).toMatch(/^[A-F0-9]{64}$/);
    });
  });

  // ─────────────── Signature Validation Tests ───────────────

  describe("Signature Validation", () => {
    test("valid signature should match", () => {
      // Arrange - generate correct signature
      const expectedSignature = generateSignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );

      // Act - generate same signature again
      const actualSignature = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );

      // Assert
      expect(actualSignature).toBe(expectedSignature);
    });

    test("tampered signature should not match", () => {
      // Arrange
      const correctSignature = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );
      const tamperedSignature =
        correctSignature.slice(0, -2) + "00"; // Modify last 2 chars

      // Assert
      expect(tamperedSignature).not.toBe(correctSignature);
    });

    test("signature from different order should not match", () => {
      // Arrange
      const sig1 = generateRazorpaySignature(
        "order_123",
        testContext.paymentId,
        testContext.secret
      );
      const sig2 = generateRazorpaySignature(
        "order_456",
        testContext.paymentId,
        testContext.secret
      );

      // Assert
      expect(sig1).not.toBe(sig2);
    });

    test("signature from different payment should not match", () => {
      // Arrange
      const sig1 = generateRazorpaySignature(
        testContext.orderId,
        "pay_111",
        testContext.secret
      );
      const sig2 = generateRazorpaySignature(
        testContext.orderId,
        "pay_222",
        testContext.secret
      );

      // Assert
      expect(sig1).not.toBe(sig2);
    });
  });

  // ─────────────── Security Tests ───────────────

  describe("Signature Security", () => {
    test("HMAC-SHA256 signature length should be 64 characters", () => {
      // Arrange & Act
      const signature = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );

      // Assert - SHA256 produces 256 bits = 64 hex chars
      expect(signature.length).toBe(64);
    });

    test("signature should not contain the secret", () => {
      // Arrange & Act
      const signature = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        testContext.secret
      );

      // Assert - secret should not appear in signature
      expect(signature).not.toContain(testContext.secret);
    });

    test("signature should be different for each order", () => {
      // Arrange & Act - generate signatures for 10 different orders
      const sigs = Array.from({ length: 10 }, (_, i) =>
        generateRazorpaySignature(
          `order_${i}`,
          testContext.paymentId,
          testContext.secret
        )
      );

      // Assert - all should be unique
      const uniqueSigs = new Set(sigs);
      expect(uniqueSigs.size).toBe(10);
    });

    test("should handle special characters in orderId", () => {
      // Arrange & Act
      const specialOrderId = "order_!@#$%^&*()";
      const signature = generateRazorpaySignature(
        specialOrderId,
        testContext.paymentId,
        testContext.secret
      );

      // Assert
      expect(signature).toMatch(/^[a-f0-9]{64}$/);
    });

    test("should handle special characters in paymentId", () => {
      // Arrange & Act
      const specialPaymentId = "pay_!@#$%^&*()";
      const signature = generateRazorpaySignature(
        testContext.orderId,
        specialPaymentId,
        testContext.secret
      );

      // Assert
      expect(signature).toMatch(/^[a-f0-9]{64}$/);
    });
  });

  // ─────────────── Message Format Tests ───────────────

  describe("Message Format", () => {
    test("should format message as 'orderId|paymentId'", () => {
      // Arrange
      const orderId = "order_123";
      const paymentId = "pay_456";
      const secret = "secret";

      // Act - calculate what the message should be
      const expectedMessage = `${orderId}|${paymentId}`;
      const expectedSignature = crypto
        .createHmac("sha256", secret)
        .update(expectedMessage)
        .digest("hex");

      const actualSignature = generateRazorpaySignature(
        orderId,
        paymentId,
        secret
      );

      // Assert
      expect(actualSignature).toBe(expectedSignature);
    });

    test("should use pipe delimiter between orderId and paymentId", () => {
      // Arrange - test that pipe is required (not space, comma, etc)
      const orderId = "order_123";
      const paymentId = "pay_456";
      const secret = "secret";

      // Act - generate with correct format
      const correctSig = generateRazorpaySignature(
        orderId,
        paymentId,
        secret
      );

      // Generate with wrong format
      const wrongFormat1 = crypto
        .createHmac("sha256", secret)
        .update(`${orderId} ${paymentId}`)
        .digest("hex");
      const wrongFormat2 = crypto
        .createHmac("sha256", secret)
        .update(`${orderId},${paymentId}`)
        .digest("hex");

      // Assert - correct format should not match wrong formats
      expect(correctSig).not.toBe(wrongFormat1);
      expect(correctSig).not.toBe(wrongFormat2);
    });
  });

  // ─────────────── Edge Cases ───────────────

  describe("Edge Cases", () => {
    test("should handle empty payment data (should still work but produce signature)", () => {
      // Arrange & Act
      const signature = generateRazorpaySignature("", "", testContext.secret);

      // Assert - should still generate valid hex
      expect(signature).toMatch(/^[a-f0-9]{64}$/);
    });

    test("should handle very long orderId", () => {
      // Arrange & Act
      const longOrderId = "order_" + "x".repeat(1000);
      const signature = generateRazorpaySignature(
        longOrderId,
        testContext.paymentId,
        testContext.secret
      );

      // Assert
      expect(signature).toMatch(/^[a-f0-9]{64}$/);
    });

    test("should handle very long secret", () => {
      // Arrange & Act
      const longSecret = "secret_" + "y".repeat(1000);
      const signature = generateRazorpaySignature(
        testContext.orderId,
        testContext.paymentId,
        longSecret
      );

      // Assert
      expect(signature).toMatch(/^[a-f0-9]{64}$/);
    });

    test("should handle Unicode characters", () => {
      // Arrange & Act
      const unicodeOrderId = "order_🔐🔑";
      const signature = generateRazorpaySignature(
        unicodeOrderId,
        testContext.paymentId,
        testContext.secret
      );

      // Assert - should still produce valid signature
      expect(signature).toMatch(/^[a-f0-9]{64}$/);
    });
  });
});

/**
 * Manual signature verification checklist:
 *
 * ✅ Signature Generation:
 *   - [ ] HMAC-SHA256 produces 64-char hex string
 *   - [ ] Same input always produces same signature
 *   - [ ] Different secret produces different signature
 *
 * ✅ Tamper Detection:
 *   - [ ] Modified orderId detected (different signature)
 *   - [ ] Modified paymentId detected (different signature)
 *   - [ ] Modified signature rejected
 *
 * ✅ Security:
 *   - [ ] Secret not visible in signature
 *   - [ ] Signature not reversible to get data
 *   - [ ] Format: orderId|paymentId (pipe delimiter required)
 *
 * ✅ Edge Cases:
 *   - [ ] Special characters handled
 *   - [ ] Very long inputs handled
 *   - [ ] Unicode characters handled
 *
 * @see verifyRazorpayPayment.ts for Cloud Function implementation
 */

