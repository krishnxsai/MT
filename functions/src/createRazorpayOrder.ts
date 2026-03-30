/**
 * Server-side Razorpay Order Creation
 *
 * Creates a real Razorpay order using server credentials so clients never
 * fabricate order IDs locally.
 */

import * as functions from "firebase-functions";

interface RazorpayCreateOrderResponse {
    id?: string;
    amount?: number;
    currency?: string;
    receipt?: string;
    status?: string;
    error?: {
        code?: string;
        description?: string;
    };
}

/**
 * Callable Cloud Function for creating Razorpay orders.
 * Must be called by an authenticated user.
 */
export const createRazorpayOrder = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "User must be authenticated to create a payment order"
        );
    }

    const amount = Number(data?.amount);
    if (!Number.isInteger(amount) || amount <= 0) {
        throw new functions.https.HttpsError(
            "invalid-argument",
            "amount must be a positive integer in paise"
        );
    }

    const currency =
        typeof data?.currency === "string" && data.currency.trim().length > 0
            ? data.currency.trim().toUpperCase()
            : "INR";

    if (currency !== "INR") {
        throw new functions.https.HttpsError(
            "invalid-argument",
            "Only INR currency is currently supported"
        );
    }

    const receipt =
        typeof data?.receipt === "string" && data.receipt.trim().length > 0
            ? data.receipt.trim().slice(0, 40)
            : `meditrack_${Date.now()}`;

    const meditrackOrderId =
        typeof data?.meditrackOrderId === "string" ? data.meditrackOrderId.trim() : "";

    const runtimeConfig = functions.config();
    const keyId = process.env.RAZORPAY_KEY_ID || runtimeConfig.razorpay?.key_id;
    const keySecret = process.env.RAZORPAY_KEY_SECRET || runtimeConfig.razorpay?.key_secret;

    if (!keyId || !keySecret) {
        throw new functions.https.HttpsError(
            "internal",
            "Razorpay credentials are not configured on Cloud Functions"
        );
    }

    try {
        const authHeader = Buffer.from(`${keyId}:${keySecret}`).toString("base64");
        const response = await fetch("https://api.razorpay.com/v1/orders", {
            method: "POST",
            headers: {
                "Authorization": `Basic ${authHeader}`,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                amount,
                currency,
                receipt,
                notes: {
                    meditrackOrderId,
                    userId: context.auth.uid,
                },
            }),
        });

        const rawBody = await response.text();
        let parsed: RazorpayCreateOrderResponse = {};

        if (rawBody) {
            try {
                parsed = JSON.parse(rawBody) as RazorpayCreateOrderResponse;
            } catch (parseError: unknown) {
                const err = parseError as { message?: string };
                console.error("Failed to parse Razorpay create-order response", err.message);
            }
        }

        if (!response.ok) {
            const reason =
                parsed.error?.description ||
                parsed.error?.code ||
                `Razorpay create order failed with status ${response.status}`;

            console.error("Razorpay create-order API error", {
                status: response.status,
                reason,
            });

            throw new functions.https.HttpsError("failed-precondition", reason);
        }

        if (!parsed.id) {
            throw new functions.https.HttpsError(
                "internal",
                "Razorpay create order response missing order ID"
            );
        }

        return {
            orderId: parsed.id,
            amount: parsed.amount ?? amount,
            currency: parsed.currency ?? currency,
            receipt: parsed.receipt ?? receipt,
            status: parsed.status ?? "created",
        };
    } catch (error: unknown) {
        if (error instanceof functions.https.HttpsError) {
            throw error;
        }

        const err = error as { message?: string };
        console.error("createRazorpayOrder failed", err.message, error);
        throw new functions.https.HttpsError(
            "internal",
            `Unable to create Razorpay order: ${err.message || "unknown error"}`
        );
    }
});
