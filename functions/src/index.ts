/**
 * MediTrack Cloud Functions
 *
 * Firestore triggers for order status notifications.
 * Sends FCM push notifications to patients when their order status changes.
 */

import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

// Initialize Firebase Admin SDK
admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

const INVENTORY_ADJUSTMENTS_COLLECTION = "inventoryAdjustments";
const PAYMENTS_COLLECTION = "payments";
const TRANSACTIONS_COLLECTION = "transactions";
const PURCHASE_TRANSACTION_DOC_PREFIX = "purchase_";
const TRACKING_COLLECTION = "deliveryTracking";
const ROLLBACK_ELIGIBLE_STATUSES = new Set([
  "CONFIRMED",
  "PREPARING",
  "READY",
  "SHIPPED",
]);
const ACTIVE_TRACKING_ORDER_STATUS = "SHIPPED";
const TERMINAL_TRACKING_ORDER_STATUSES = new Set([
  "DELIVERED",
  "CANCELLED",
  "RETURNED",
]);
const TRACKING_RETENTION_DAYS = 90;

interface InventoryOrderItem {
  medicineNameRaw: string;
  medicineNameNormalized: string;
  medicineName: string;
  quantity: number;
}

type InventoryMatchMethod = "medicineNameNormalized" | "medicineName";

interface InventoryMatchRecord {
  medicineName: string;
  quantity: number;
  matchedBy: InventoryMatchMethod;
  inventoryDocPath: string;
}

interface ResolvedInventoryItem {
  item: InventoryOrderItem;
  inventoryDoc: FirebaseFirestore.QueryDocumentSnapshot;
  matchedBy: InventoryMatchMethod;
}

interface MedicineSummary {
  medicineId: string;
  medicineName: string;
}

function parseInteger(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return Math.floor(value);
  }

  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return Math.floor(parsed);
    }
  }

  return null;
}

function toInt(value: unknown, fallback = 0): number {
  const parsed = parseInteger(value);
  return parsed === null ? fallback : parsed;
}

function toPositiveInt(value: unknown): number | null {
  const parsed = parseInteger(value);
  return parsed !== null && parsed > 0 ? parsed : null;
}

function toPositiveNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    return value;
  }

  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed;
    }
  }

  return null;
}

function asString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function normalizeMedicineName(value: unknown): string {
  return asString(value).trim().toLowerCase();
}

function extractInventoryItems(
  orderData: FirebaseFirestore.DocumentData
): InventoryOrderItem[] {
  const extracted: InventoryOrderItem[] = [];
  const rawItems = orderData.items;

  if (Array.isArray(rawItems)) {
    for (const rawItem of rawItems) {
      if (!rawItem || typeof rawItem !== "object") {
        continue;
      }

      const item = rawItem as Record<string, unknown>;
      const medicineNameRaw = asString(item.medicineName).trim();
      const medicineNameNormalized = normalizeMedicineName(item.medicineName);
      const quantity = toPositiveInt(item.quantity);

      if (medicineNameRaw && medicineNameNormalized && quantity !== null) {
        extracted.push({
          medicineNameRaw,
          medicineNameNormalized,
          medicineName: medicineNameRaw,
          quantity,
        });
      }
    }
  }

  if (extracted.length > 0) {
    return extracted;
  }

  const legacyMedicineNameRaw = asString(orderData.medicineName).trim();
  const legacyMedicineNameNormalized = normalizeMedicineName(orderData.medicineName);
  const legacyQuantity = toPositiveInt(orderData.quantity);

  if (legacyMedicineNameRaw && legacyMedicineNameNormalized && legacyQuantity !== null) {
    extracted.push({
      medicineNameRaw: legacyMedicineNameRaw,
      medicineNameNormalized: legacyMedicineNameNormalized,
      medicineName: legacyMedicineNameRaw,
      quantity: legacyQuantity,
    });
  }

  return extracted;
}

function extractMedicineSummary(
  orderData: FirebaseFirestore.DocumentData,
  orderId: string
): MedicineSummary {
  const fallbackName = `Order #${orderId.slice(-6)}`;
  const rawItems = orderData.items;

  if (Array.isArray(rawItems)) {
    const items = rawItems
      .filter((rawItem) => rawItem && typeof rawItem === "object")
      .map((rawItem) => rawItem as Record<string, unknown>);

    const first = items[0];
    const firstName = asString(first?.medicineName).trim();
    const firstId = asString(first?.medicineId).trim();

    if (firstName) {
      const displayName =
        items.length > 1 ? `${firstName} +${items.length - 1} more` : firstName;
      return {
        medicineId: firstId,
        medicineName: displayName,
      };
    }
  }

  const legacyName = asString(orderData.medicineName).trim();
  const legacyId = asString(orderData.medicineId).trim();

  return {
    medicineId: legacyId,
    medicineName: legacyName || fallbackName,
  };
}

function getCapturedPaymentRecord(
  paymentSnapshot: FirebaseFirestore.QuerySnapshot
): FirebaseFirestore.DocumentData | null {
  if (paymentSnapshot.empty) {
    return null;
  }

  const docs = paymentSnapshot.docs.map((doc) => doc.data());
  const captured = docs.find((doc) => asString(doc.status) === "CAPTURED");
  if (captured) {
    return captured;
  }

  const fallback = docs.find((doc) => {
    const paymentId = asString(doc.paymentId).trim() || asString(doc.razorpayPaymentId).trim();
    return paymentId.length > 0;
  });

  return fallback || null;
}

function getTransactionAmountRupees(
  orderData: FirebaseFirestore.DocumentData,
  paymentData: FirebaseFirestore.DocumentData
): number | null {
  const orderTotal = toPositiveNumber(orderData.totalAmount);
  if (orderTotal !== null) {
    return orderTotal;
  }

  const paymentAmountPaise = toPositiveInt(paymentData.amount);
  if (paymentAmountPaise !== null) {
    return paymentAmountPaise / 100;
  }

  const subtotal = toPositiveNumber(orderData.subtotal);
  if (subtotal !== null) {
    return subtotal;
  }

  return null;
}

async function syncRevenueTransactionForConfirmedOrder(
  orderId: string,
  oldStatus: string,
  newStatus: string,
  orderData: FirebaseFirestore.DocumentData
): Promise<void> {
  if (newStatus !== "CONFIRMED" || oldStatus === "CONFIRMED") {
    return;
  }

  const pharmacyId = asString(orderData.pharmacyId).trim();
  if (!pharmacyId) {
    console.warn(`Order ${orderId}: missing pharmacyId, skipping revenue sync`);
    return;
  }

  const orderRef = db.collection("orders").doc(orderId);
  const paymentsQuery = db.collection(PAYMENTS_COLLECTION)
    .where("meditrackOrderId", "==", orderId)
    .limit(5);
  const purchaseTxQuery = db.collection(TRANSACTIONS_COLLECTION)
    .where("orderId", "==", orderId)
    .where("type", "==", "PURCHASE")
    .limit(5);

  await db.runTransaction(async (transaction) => {
    const latestOrderSnapshot = await transaction.get(orderRef);
    const latestStatus = asString(latestOrderSnapshot.get("status"));

    if (latestStatus !== "CONFIRMED") {
      console.log(
        `Order ${orderId}: skip revenue sync because latest status is ${latestStatus || "UNKNOWN"}`
      );
      return;
    }

    const paymentSnapshot = await transaction.get(paymentsQuery);
    const paymentData = getCapturedPaymentRecord(paymentSnapshot);
    if (!paymentData) {
      console.log(`Order ${orderId}: no captured payment record found, skipping revenue sync`);
      return;
    }

    const amount = getTransactionAmountRupees(orderData, paymentData);
    if (amount === null || amount <= 0) {
      console.warn(`Order ${orderId}: missing positive amount, skipping revenue sync`);
      return;
    }

    const paymentId = asString(paymentData.paymentId).trim() ||
      asString(paymentData.razorpayPaymentId).trim();
    const razorpayOrderId = asString(paymentData.razorpayOrderId).trim();
    const paymentMethod = asString(paymentData.paymentMethod).trim() || "UPI";
    const currency = asString(paymentData.currency).trim() || "INR";
    const userId = asString(orderData.userId).trim() ||
      asString(orderData.patientId).trim() ||
      asString(paymentData.userId).trim();
    const medicineSummary = extractMedicineSummary(orderData, orderId);

    const purchaseTxSnapshot = await transaction.get(purchaseTxQuery);
    const transactionRef = purchaseTxSnapshot.empty
      ? db.collection(TRANSACTIONS_COLLECTION).doc(`${PURCHASE_TRANSACTION_DOC_PREFIX}${orderId}`)
      : purchaseTxSnapshot.docs[0].ref;

    const payload: Record<string, unknown> = {
      userId,
      orderId,
      medicineId: medicineSummary.medicineId,
      medicineName: medicineSummary.medicineName,
      type: "PURCHASE",
      amount,
      currency,
      status: "COMPLETED",
      pharmacyId,
      pharmacyName: asString(orderData.pharmacyName).trim(),
      razorpayOrderId,
      razorpayPaymentId: paymentId,
      paymentMethod,
      transactionRef: paymentId,
      notes: "Payment captured and order confirmed",
    };

    if (purchaseTxSnapshot.empty) {
      payload.createdAt = admin.firestore.FieldValue.serverTimestamp();
    }

    transaction.set(transactionRef, payload, { merge: true });
  });
}

async function syncDeliveryTrackingForOrderStatusChange(
  orderId: string,
  newStatus: string,
  orderData: FirebaseFirestore.DocumentData
): Promise<void> {
  const trackingId = `ongoing_${orderId}`;
  const trackingRef = db.collection(TRACKING_COLLECTION).doc(trackingId);

  if (newStatus === ACTIVE_TRACKING_ORDER_STATUS) {
    const deliveryPersonId = asString(orderData.deliveryPersonId).trim();
    const deliveryPersonName = asString(orderData.deliveryPersonName).trim();
    const deliveryPersonPhone = asString(orderData.deliveryPersonPhone).trim();

    const payload: Record<string, unknown> = {
      orderId,
      isActive: true,
      trackingStatus: "ACTIVE",
      startedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    if (deliveryPersonId) {
      payload.deliveryPersonId = deliveryPersonId;
    }
    if (deliveryPersonName) {
      payload.deliveryPersonName = deliveryPersonName;
    }
    if (deliveryPersonPhone) {
      payload.deliveryPersonPhone = deliveryPersonPhone;
    }

    await trackingRef.set(payload, { merge: true });
    return;
  }

  if (!TERMINAL_TRACKING_ORDER_STATUSES.has(newStatus)) {
    return;
  }

  await trackingRef.set(
    {
      orderId,
      isActive: false,
      trackingStatus: newStatus,
      endedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );
}

async function deleteTrackingPoints(
  trackingRef: FirebaseFirestore.DocumentReference
): Promise<number> {
  let deletedCount = 0;

  while (true) {
    const pointsSnapshot = await trackingRef.collection("points").limit(400).get();

    if (pointsSnapshot.empty) {
      break;
    }

    const batch = db.batch();
    pointsSnapshot.docs.forEach((pointDoc) => {
      batch.delete(pointDoc.ref);
    });
    await batch.commit();
    deletedCount += pointsSnapshot.size;

    if (pointsSnapshot.size < 400) {
      break;
    }
  }

  return deletedCount;
}

async function applyInventoryItemsDelta(
  transaction: FirebaseFirestore.Transaction,
  orderId: string,
  pharmacyId: string,
  items: InventoryOrderItem[],
  direction: 1 | -1
): Promise<InventoryMatchRecord[]> {
  const resolvedItems: ResolvedInventoryItem[] = [];

  for (const item of items) {
    const normalizedQuery = db.collection("pharmacyInventory")
      .where("pharmacyId", "==", pharmacyId)
      .where("medicineNameNormalized", "==", item.medicineNameNormalized)
      .limit(2);

    let inventorySnapshot: FirebaseFirestore.QuerySnapshot | null = null;
    let matchedBy: InventoryMatchMethod = "medicineNameNormalized";

    try {
      inventorySnapshot = await transaction.get(normalizedQuery);
    } catch (error: unknown) {
      const err = error as { message?: string };
      console.warn(
        `Order ${orderId}: normalized inventory query failed for ${item.medicineNameRaw}. ` +
        `Falling back to legacy name query. ${err.message || ""}`
      );
    }

    if (!inventorySnapshot || inventorySnapshot.empty) {
      const legacyNameQuery = db.collection("pharmacyInventory")
        .where("pharmacyId", "==", pharmacyId)
        .where("medicineName", "==", item.medicineNameRaw)
        .limit(2);

      inventorySnapshot = await transaction.get(legacyNameQuery);
      matchedBy = "medicineName";
    }

    if (inventorySnapshot.empty) {
      throw new Error(
        `Inventory not found for "${item.medicineNameRaw}" in pharmacy ${pharmacyId}`
      );
    }

    if (inventorySnapshot.size > 1) {
      throw new Error(
        `Ambiguous inventory match for "${item.medicineNameRaw}" in pharmacy ${pharmacyId}`
      );
    }

    resolvedItems.push({
      item,
      inventoryDoc: inventorySnapshot.docs[0],
      matchedBy,
    });
  }

  const aggregatedByDoc = new Map<string, {
    inventoryDoc: FirebaseFirestore.QueryDocumentSnapshot;
    totalQuantity: number;
  }>();

  for (const resolved of resolvedItems) {
    const docPath = resolved.inventoryDoc.ref.path;
    const current = aggregatedByDoc.get(docPath);
    if (current) {
      current.totalQuantity += resolved.item.quantity;
    } else {
      aggregatedByDoc.set(docPath, {
        inventoryDoc: resolved.inventoryDoc,
        totalQuantity: resolved.item.quantity,
      });
    }
  }

  for (const { inventoryDoc, totalQuantity } of aggregatedByDoc.values()) {
    const stockValue = inventoryDoc.get("stockQuantity");
    const legacyValue = inventoryDoc.get("quantity");
    const hasStockQuantity = stockValue !== undefined && stockValue !== null;
    const hasLegacyQuantity = legacyValue !== undefined && legacyValue !== null;

    if (direction === -1 && !hasStockQuantity && !hasLegacyQuantity) {
      throw new Error(
        `Inventory document ${inventoryDoc.ref.path} has no stock field for deduction`
      );
    }

    const currentQuantity = hasStockQuantity ? toInt(stockValue) : toInt(legacyValue);
    if (direction === -1 && currentQuantity < totalQuantity) {
      throw new Error(
        `Insufficient stock for ${inventoryDoc.ref.path}: available=${currentQuantity}, requested=${totalQuantity}`
      );
    }

    const nextQuantity = currentQuantity + (direction * totalQuantity);
    const updates: Record<string, unknown> = {
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    // Keep legacy and canonical fields aligned using one computed quantity.
    if (hasStockQuantity || !hasLegacyQuantity) {
      updates.stockQuantity = nextQuantity;
    }

    if (hasLegacyQuantity) {
      updates.quantity = nextQuantity;
    }

    transaction.update(inventoryDoc.ref, updates);
  }

  return resolvedItems.map((resolved) => ({
    medicineName: resolved.item.medicineName,
    quantity: resolved.item.quantity,
    matchedBy: resolved.matchedBy,
    inventoryDocPath: resolved.inventoryDoc.ref.path,
  }));
}

async function syncInventoryForOrderStatusChange(
  orderId: string,
  oldStatus: string,
  newStatus: string,
  orderData: FirebaseFirestore.DocumentData
): Promise<void> {
  const isConfirmTransition = newStatus === "CONFIRMED";
  const isCancelRollbackTransition =
    newStatus === "CANCELLED" && ROLLBACK_ELIGIBLE_STATUSES.has(oldStatus);

  if (!isConfirmTransition && !isCancelRollbackTransition) {
    return;
  }

  const pharmacyId = asString(orderData.pharmacyId).trim();
  if (!pharmacyId) {
    console.warn(`Order ${orderId}: missing pharmacyId, skipping inventory sync`);
    return;
  }

  const items = extractInventoryItems(orderData);
  if (items.length === 0) {
    console.warn(`Order ${orderId}: no inventory items found, skipping inventory sync`);
    return;
  }

  const adjustmentRef = db.collection(INVENTORY_ADJUSTMENTS_COLLECTION).doc(orderId);
  const orderRef = db.collection("orders").doc(orderId);

  await db.runTransaction(async (transaction) => {
    const latestOrderSnapshot = await transaction.get(orderRef);
    const latestStatus = asString(latestOrderSnapshot.get("status"));

    if (isConfirmTransition && latestStatus !== "CONFIRMED") {
      console.log(
        `Order ${orderId}: skip CONFIRMED inventory sync because latest status is ${latestStatus || "UNKNOWN"}`
      );
      return;
    }

    if (isCancelRollbackTransition && latestStatus !== "CANCELLED") {
      console.log(
        `Order ${orderId}: skip CANCELLED inventory rollback because latest status is ${latestStatus || "UNKNOWN"}`
      );
      return;
    }

    const adjustmentSnapshot = await transaction.get(adjustmentRef);
    const adjustmentData =
      (adjustmentSnapshot.data() as Record<string, unknown> | undefined) || {};

    const alreadyReduced = adjustmentData.confirmedApplied === true;
    const alreadyRolledBack = adjustmentData.cancelledRollbackApplied === true;

    if (isConfirmTransition) {
      if (alreadyReduced) {
        console.log(`Order ${orderId}: inventory reduction already applied`);
        return;
      }

      const confirmedMatchRecords = await applyInventoryItemsDelta(
        transaction,
        orderId,
        pharmacyId,
        items,
        -1
      );

      transaction.set(adjustmentRef, {
        confirmedApplied: true,
        confirmedAppliedAt: admin.firestore.FieldValue.serverTimestamp(),
        confirmedMatchRecords,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
      return;
    }

    if (!alreadyReduced) {
      console.log(`Order ${orderId}: skipping rollback because reduction was not applied`);
      return;
    }

    if (alreadyRolledBack) {
      console.log(`Order ${orderId}: inventory rollback already applied`);
      return;
    }

    const rollbackMatchRecords = await applyInventoryItemsDelta(
      transaction,
      orderId,
      pharmacyId,
      items,
      1
    );

    transaction.set(adjustmentRef, {
      cancelledRollbackApplied: true,
      cancelledRollbackAppliedAt: admin.firestore.FieldValue.serverTimestamp(),
      rollbackMatchRecords,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
  });
}

// ═══════════════════════════════════════════════════════════════════════════
// Order Status Notification Trigger
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Triggered when an order document is updated in Firestore.
 * Sends a push notification to the patient if the status changed.
 */
export const onOrderStatusChange = functions.firestore
  .document("orders/{orderId}")
  .onUpdate(async (change, context) => {
    const orderId = context.params.orderId;
    const beforeData = change.before.data() as FirebaseFirestore.DocumentData;
    const afterData = change.after.data() as FirebaseFirestore.DocumentData;

    // Check if status actually changed
    const oldStatus = asString(beforeData.status);
    const newStatus = asString(afterData.status);

    if (oldStatus === newStatus) {
      console.log(`Order ${orderId}: Status unchanged (${oldStatus})`);
      return null;
    }

    console.log(`Order ${orderId}: Status changed from ${oldStatus} to ${newStatus}`);

    // Inventory changes are applied server-side to avoid client permission issues.
    await syncInventoryForOrderStatusChange(orderId, oldStatus, newStatus, afterData);

    // Revenue transaction sync runs on paid confirmation transitions.
    await syncRevenueTransactionForConfirmedOrder(orderId, oldStatus, newStatus, afterData);

    // Keep delivery tracking lifecycle aligned with order state transitions.
    await syncDeliveryTrackingForOrderStatusChange(orderId, newStatus, afterData);

    // Get patient's FCM token
    const patientId = afterData.patientId;
    if (!patientId) {
      console.error(`Order ${orderId}: No patientId found`);
      return null;
    }

    const patientDoc = await db.collection("users").doc(patientId).get();
    if (!patientDoc.exists) {
      console.error(`Patient ${patientId} not found`);
      return null;
    }

    const patientData = patientDoc.data();
    const fcmToken = patientData?.fcmToken;

    if (!fcmToken) {
      console.log(`Patient ${patientId} has no FCM token registered`);
      return null;
    }

    // Build notification content based on status
    const pharmacyName = afterData.pharmacyName || "Pharmacy";
    const notification = getNotificationContent(newStatus, pharmacyName);

    if (!notification) {
      console.log(`No notification configured for status: ${newStatus}`);
      return null;
    }

    // Send the push notification
    try {
      const message: admin.messaging.Message = {
        token: fcmToken,
        notification: {
          title: notification.title,
          body: notification.body,
        },
        data: {
          type: "order_status",
          orderId: orderId,
          status: newStatus,
          pharmacyName: pharmacyName,
          click_action: "OPEN_ORDER_TRACKING",
        },
        android: {
          priority: "high",
          notification: {
            channelId: "meditrack_orders",
            priority: "high",
            defaultSound: true,
            defaultVibrateTimings: true,
          },
        },
        apns: {
          payload: {
            aps: {
              sound: "default",
              badge: 1,
            },
          },
        },
      };

      const response = await messaging.send(message);
      console.log(`Notification sent successfully: ${response}`);

      // Log the notification in Firestore for audit
      await db.collection("notificationLogs").add({
        orderId: orderId,
        patientId: patientId,
        status: newStatus,
        title: notification.title,
        body: notification.body,
        sentAt: admin.firestore.FieldValue.serverTimestamp(),
        fcmResponse: response,
      });

      return { success: true, messageId: response };
    } catch (error: unknown) {
      const err = error as { code?: string; message?: string };
      console.error(`Failed to send notification: ${err.message}`);

      // Handle invalid token (user uninstalled app or token expired)
      if (err.code === "messaging/invalid-registration-token" ||
        err.code === "messaging/registration-token-not-registered") {
        console.log(`Clearing invalid FCM token for patient ${patientId}`);
        await db.collection("users").doc(patientId).update({
          fcmToken: admin.firestore.FieldValue.delete(),
        });
      }

      return { success: false, error: err.message };
    }
  });

// ═══════════════════════════════════════════════════════════════════════════
// New Order Notification (for Pharmacy)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Triggered when a new order is created.
 * Sends a push notification to the pharmacy owner.
 */
export const onNewOrder = functions.firestore
  .document("orders/{orderId}")
  .onCreate(async (snapshot, context) => {
    const orderId = context.params.orderId;
    const orderData = snapshot.data();

    console.log(`New order created: ${orderId}`);

    const pharmacyId = orderData.pharmacyId;
    if (!pharmacyId) {
      console.error(`Order ${orderId}: No pharmacyId found`);
      return null;
    }

    // Get pharmacy owner's FCM token
    const pharmacyDoc = await db.collection("pharmacies").doc(pharmacyId).get();
    if (!pharmacyDoc.exists) {
      console.error(`Pharmacy ${pharmacyId} not found`);
      return null;
    }

    const pharmacyData = pharmacyDoc.data();
    const ownerId = pharmacyData?.ownerId;

    if (!ownerId) {
      console.error(`Pharmacy ${pharmacyId} has no owner`);
      return null;
    }

    const ownerDoc = await db.collection("users").doc(ownerId).get();
    if (!ownerDoc.exists) {
      console.error(`Owner ${ownerId} not found`);
      return null;
    }

    const ownerData = ownerDoc.data();
    const fcmToken = ownerData?.fcmToken;

    if (!fcmToken) {
      console.log(`Pharmacy owner ${ownerId} has no FCM token`);
      return null;
    }

    // Build notification
    const patientName = orderData.patientName || "A customer";
    const itemCount = orderData.items?.length || 1;
    const totalAmount = orderData.totalAmount || 0;

    try {
      const message: admin.messaging.Message = {
        token: fcmToken,
        notification: {
          title: "New Order Received!",
          body: `${patientName} ordered ${itemCount} item(s) - Rs.${totalAmount.toFixed(2)}`,
        },
        data: {
          type: "new_order",
          orderId: orderId,
          click_action: "OPEN_PHARMACY_ORDERS",
        },
        android: {
          priority: "high",
          notification: {
            channelId: "meditrack_orders",
            priority: "high",
            defaultSound: true,
          },
        },
      };

      const response = await messaging.send(message);
      console.log(`New order notification sent: ${response}`);
      return { success: true, messageId: response };
    } catch (error: unknown) {
      const err = error as { message?: string };
      console.error(`Failed to send new order notification: ${err.message}`);
      return { success: false, error: err.message };
    }
  });

// ═══════════════════════════════════════════════════════════════════════════
// Helper Functions
// ═══════════════════════════════════════════════════════════════════════════

interface NotificationContent {
  title: string;
  body: string;
}

function getNotificationContent(
  status: string,
  pharmacyName: string
): NotificationContent | null {
  switch (status) {
    case "CONFIRMED":
      return {
        title: "Order Accepted",
        body: `Your order from ${pharmacyName} has been accepted and is being processed.`,
      };

    case "PREPARING":
      return {
        title: "Preparing Your Medicine",
        body: `${pharmacyName} is preparing your order. It will be ready soon.`,
      };

    case "READY":
      return {
        title: "Medicine Ready!",
        body: `Your medicine is ready for pickup at ${pharmacyName}.`,
      };

    case "SHIPPED":
      return {
        title: "Out for Delivery",
        body: `Your order from ${pharmacyName} is on the way!`,
      };

    case "DELIVERED":
      return {
        title: "Order Delivered",
        body: `Your order from ${pharmacyName} has been delivered. Stay healthy!`,
      };

    case "CANCELLED":
      return {
        title: "Order Cancelled",
        body: `Your order from ${pharmacyName} has been cancelled.`,
      };

    default:
      return null;
  }
}

// ═══════════════════════════════════════════════════════════════════════════
// Scheduled Cleanup (Optional)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Scheduled function to clean up old notification logs.
 * Runs daily at midnight.
 */
export const cleanupNotificationLogs = functions.pubsub
  .schedule("0 0 * * *")
  .timeZone("Asia/Kolkata")
  .onRun(async () => {
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    const oldLogs = await db
      .collection("notificationLogs")
      .where("sentAt", "<", thirtyDaysAgo)
      .limit(500)
      .get();

    if (oldLogs.empty) {
      console.log("No old notification logs to clean up");
      return null;
    }

    const batch = db.batch();
    oldLogs.docs.forEach((doc) => {
      batch.delete(doc.ref);
    });

    await batch.commit();
    console.log(`Cleaned up ${oldLogs.size} old notification logs`);
    return null;
  });

/**
 * Scheduled function to clean up delivery tracking history.
 * Retains data for TRACKING_RETENTION_DAYS and prunes both points and ended tracking docs.
 */
export const cleanupDeliveryTrackingHistory = functions.pubsub
  .schedule("30 2 * * *")
  .timeZone("Asia/Kolkata")
  .onRun(async () => {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - TRACKING_RETENTION_DAYS);

    let deletedPoints = 0;
    let deletedTrackingDocs = 0;

    while (true) {
      const stalePointsSnapshot = await db
        .collectionGroup("points")
        .where("recordedAt", "<", cutoffDate)
        .limit(400)
        .get();

      if (stalePointsSnapshot.empty) {
        break;
      }

      const batch = db.batch();
      stalePointsSnapshot.docs.forEach((pointDoc) => {
        batch.delete(pointDoc.ref);
      });
      await batch.commit();
      deletedPoints += stalePointsSnapshot.size;

      if (stalePointsSnapshot.size < 400) {
        break;
      }
    }

    while (true) {
      const staleTrackingSnapshot = await db
        .collection(TRACKING_COLLECTION)
        .where("isActive", "==", false)
        .where("endedAt", "<", cutoffDate)
        .limit(100)
        .get();

      if (staleTrackingSnapshot.empty) {
        break;
      }

      const batch = db.batch();

      for (const trackingDoc of staleTrackingSnapshot.docs) {
        deletedPoints += await deleteTrackingPoints(trackingDoc.ref);
        batch.delete(trackingDoc.ref);
      }

      await batch.commit();
      deletedTrackingDocs += staleTrackingSnapshot.size;

      if (staleTrackingSnapshot.size < 100) {
        break;
      }
    }

    console.log(
      `cleanupDeliveryTrackingHistory: deletedTrackingDocs=${deletedTrackingDocs}, deletedPoints=${deletedPoints}`
    );
    return null;
  });

// ═══════════════════════════════════════════════════════════════════════════
// Payment Verification (Server-side)
// ═══════════════════════════════════════════════════════════════════════════

// Export the payment verification function from the separate module
export { verifyRazorpayPayment } from "./verifyRazorpayPayment";
export { createRazorpayOrder } from "./createRazorpayOrder";

// ═══════════════════════════════════════════════════════════════════════════
// Cloud Secret Manager Sync
// ═══════════════════════════════════════════════════════════════════════════

// Export the secrets sync functions
export {
  syncSecretsFromCloudSecretManager,
  onSyncSecretsScheduled,
  verifySyncConfiguration,
} from "./syncSecretsToRemoteConfig";
