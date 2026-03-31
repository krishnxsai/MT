#!/usr/bin/env node

/**
 * Backfill/repair PURCHASE transactions for previously paid orders.
 *
 * Why:
 * - Historical paid orders may be missing pharmacy revenue transaction records.
 * - Some existing PURCHASE transactions may have amount=0 or missing Razorpay refs.
 *
 * Usage:
 *   node scripts/backfill-paid-transactions.js           # dry-run
 *   node scripts/backfill-paid-transactions.js --apply   # write changes
 *   node scripts/backfill-paid-transactions.js --apply --verbose
 */

const fs = require("fs");
const path = require("path");

function loadFirebaseAdmin() {
    try {
        return require("firebase-admin");
    } catch (_e) {
        return require(path.join(__dirname, "..", "functions", "node_modules", "firebase-admin"));
    }
}

const admin = loadFirebaseAdmin();
const args = new Set(process.argv.slice(2));
const APPLY = args.has("--apply");
const VERBOSE = args.has("--verbose");

const serviceAccountPath = path.join(__dirname, "..", "serviceAccountKey.json");
if (!fs.existsSync(serviceAccountPath)) {
    console.error("❌ serviceAccountKey.json not found:", serviceAccountPath);
    process.exit(1);
}

const serviceAccount = require(serviceAccountPath);
admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId: serviceAccount.project_id,
});

const db = admin.firestore();

const PAID_LIKE_STATUSES = new Set([
    "CONFIRMED",
    "PREPARING",
    "READY",
    "SHIPPED",
    "DELIVERED",
    "RETURNED",
    "CANCELLED",
]);

function asString(value) {
    return typeof value === "string" ? value : "";
}

function toPositiveNumber(value) {
    if (typeof value === "number" && Number.isFinite(value) && value > 0) {
        return value;
    }
    if (typeof value === "string" && value.trim()) {
        const parsed = Number(value);
        if (Number.isFinite(parsed) && parsed > 0) {
            return parsed;
        }
    }
    return null;
}

function toPositiveInt(value) {
    if (typeof value === "number" && Number.isFinite(value) && value > 0) {
        return Math.floor(value);
    }
    if (typeof value === "string" && value.trim()) {
        const parsed = Number(value);
        if (Number.isFinite(parsed) && parsed > 0) {
            return Math.floor(parsed);
        }
    }
    return null;
}

function selectCapturedPayment(paymentDocs) {
    if (!paymentDocs.length) {
        return null;
    }

    const captured = paymentDocs.find((doc) => asString(doc.status).trim() === "CAPTURED");
    if (captured) {
        return captured;
    }

    return paymentDocs.find((doc) => {
        const paymentId = asString(doc.paymentId).trim() || asString(doc.razorpayPaymentId).trim();
        return paymentId.length > 0;
    }) || null;
}

function extractMedicineSummary(order, orderId) {
    const fallbackName = `Order #${orderId.slice(-6)}`;
    const rawItems = Array.isArray(order.items) ? order.items : [];

    if (rawItems.length > 0) {
        const first = rawItems.find((item) => item && typeof item === "object") || {};
        const firstName = asString(first.medicineName).trim();
        const firstId = asString(first.medicineId).trim();

        if (firstName) {
            return {
                medicineId: firstId,
                medicineName: rawItems.length > 1 ? `${firstName} +${rawItems.length - 1} more` : firstName,
            };
        }
    }

    return {
        medicineId: asString(order.medicineId).trim(),
        medicineName: asString(order.medicineName).trim() || fallbackName,
    };
}

function getAmountRupees(order, payment) {
    const orderTotal = toPositiveNumber(order.totalAmount);
    if (orderTotal !== null) {
        return orderTotal;
    }

    const paymentAmountPaise = toPositiveInt(payment.amount);
    if (paymentAmountPaise !== null) {
        return paymentAmountPaise / 100;
    }

    const subtotal = toPositiveNumber(order.subtotal);
    if (subtotal !== null) {
        return subtotal;
    }

    return null;
}

function chooseCanonicalPurchaseDoc(purchaseDocs) {
    const withAmount = purchaseDocs.find((doc) => {
        const data = doc.data();
        return toPositiveNumber(data.amount) !== null;
    });

    return withAmount || purchaseDocs[0];
}

function needsRepair(existing, expected) {
    const existingAmount = toPositiveNumber(existing.amount) || 0;
    if (Math.abs(existingAmount - expected.amount) > 0.0001) {
        return true;
    }

    if (asString(existing.status).trim() !== "COMPLETED") {
        return true;
    }

    if (asString(existing.currency).trim() !== expected.currency) {
        return true;
    }

    if (asString(existing.pharmacyId).trim() !== expected.pharmacyId) {
        return true;
    }

    if (!asString(existing.medicineName).trim()) {
        return true;
    }

    if (!asString(existing.razorpayOrderId).trim() && expected.razorpayOrderId) {
        return true;
    }

    if (!asString(existing.razorpayPaymentId).trim() && expected.razorpayPaymentId) {
        return true;
    }

    if (!asString(existing.paymentMethod).trim()) {
        return true;
    }

    return false;
}

async function main() {
    const stats = {
        totalOrders: 0,
        candidateOrders: 0,
        missingPharmacy: 0,
        missingPayment: 0,
        missingAmount: 0,
        duplicatePurchaseDocs: 0,
        alreadyHealthy: 0,
        wouldWrite: 0,
        written: 0,
        failed: 0,
    };

    console.log(`\n🔎 Backfill mode: ${APPLY ? "APPLY" : "DRY-RUN"}`);

    const ordersSnapshot = await db.collection("orders").get();
    stats.totalOrders = ordersSnapshot.size;

    let batch = db.batch();
    let batchOps = 0;

    for (const orderDoc of ordersSnapshot.docs) {
        const orderId = orderDoc.id;
        const order = orderDoc.data() || {};
        const status = asString(order.status).trim();

        if (!PAID_LIKE_STATUSES.has(status)) {
            continue;
        }

        stats.candidateOrders += 1;

        try {
            const pharmacyId = asString(order.pharmacyId).trim();
            if (!pharmacyId) {
                stats.missingPharmacy += 1;
                continue;
            }

            const paymentsSnapshot = await db
                .collection("payments")
                .where("meditrackOrderId", "==", orderId)
                .limit(10)
                .get();

            const payment = selectCapturedPayment(paymentsSnapshot.docs.map((d) => d.data()));
            if (!payment) {
                stats.missingPayment += 1;
                continue;
            }

            const amount = getAmountRupees(order, payment);
            if (amount === null || amount <= 0) {
                stats.missingAmount += 1;
                continue;
            }

            const purchaseSnapshot = await db
                .collection("transactions")
                .where("orderId", "==", orderId)
                .where("type", "==", "PURCHASE")
                .limit(5)
                .get();

            if (purchaseSnapshot.size > 1) {
                stats.duplicatePurchaseDocs += purchaseSnapshot.size - 1;
            }

            const medicine = extractMedicineSummary(order, orderId);
            const paymentId = asString(payment.paymentId).trim() || asString(payment.razorpayPaymentId).trim();
            const razorpayOrderId = asString(payment.razorpayOrderId).trim();
            const paymentMethod = asString(payment.paymentMethod).trim() || "UPI";
            const currency = asString(payment.currency).trim() || "INR";
            const userId = asString(order.userId).trim() || asString(order.patientId).trim() || asString(payment.userId).trim();

            const payload = {
                userId,
                orderId,
                medicineId: medicine.medicineId,
                medicineName: medicine.medicineName,
                type: "PURCHASE",
                amount,
                currency,
                status: "COMPLETED",
                pharmacyId,
                pharmacyName: asString(order.pharmacyName).trim(),
                razorpayOrderId,
                razorpayPaymentId: paymentId,
                paymentMethod,
                transactionRef: paymentId,
                notes: "Backfilled from captured payment",
            };

            let targetRef;
            let existing = null;

            if (!purchaseSnapshot.empty) {
                const canonical = chooseCanonicalPurchaseDoc(purchaseSnapshot.docs);
                targetRef = canonical.ref;
                existing = canonical.data();
            } else {
                targetRef = db.collection("transactions").doc(`purchase_${orderId}`);
            }

            if (!existing || !existing.createdAt) {
                payload.createdAt =
                    payment.completedAt ||
                    payment.updatedAt ||
                    order.updatedAt ||
                    order.createdAt ||
                    admin.firestore.FieldValue.serverTimestamp();
            }

            if (existing && !needsRepair(existing, payload)) {
                stats.alreadyHealthy += 1;
                continue;
            }

            if (!APPLY) {
                stats.wouldWrite += 1;
                if (VERBOSE) {
                    console.log(`  [DRY] upsert PURCHASE for order ${orderId} amount=₹${amount.toFixed(2)}`);
                }
                continue;
            }

            batch.set(targetRef, payload, { merge: true });
            batchOps += 1;
            stats.written += 1;

            if (batchOps >= 400) {
                await batch.commit();
                batch = db.batch();
                batchOps = 0;
            }

            if (VERBOSE) {
                console.log(`  ✅ upserted PURCHASE for order ${orderId} amount=₹${amount.toFixed(2)}`);
            }
        } catch (error) {
            stats.failed += 1;
            console.error(`  ❌ ${orderId}: ${error.message || error}`);
        }
    }

    if (APPLY && batchOps > 0) {
        await batch.commit();
    }

    console.log("\n📊 Backfill summary");
    console.log(`- Total orders scanned: ${stats.totalOrders}`);
    console.log(`- Candidate paid-like orders: ${stats.candidateOrders}`);
    console.log(`- Missing pharmacyId: ${stats.missingPharmacy}`);
    console.log(`- Missing captured payment: ${stats.missingPayment}`);
    console.log(`- Missing positive amount: ${stats.missingAmount}`);
    console.log(`- Duplicate PURCHASE docs seen: ${stats.duplicatePurchaseDocs}`);
    console.log(`- Already healthy PURCHASE docs: ${stats.alreadyHealthy}`);
    console.log(`- ${APPLY ? "Written" : "Would write"}: ${APPLY ? stats.written : stats.wouldWrite}`);
    console.log(`- Failed: ${stats.failed}`);
    console.log("\n✅ Done\n");
}

main().catch((error) => {
    console.error("Fatal error:", error);
    process.exit(1);
});
