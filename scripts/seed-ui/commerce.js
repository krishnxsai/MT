function buildStatusHistory(baseTime, status) {
    const chains = {
        PENDING: ["PENDING"],
        CONFIRMED: ["PENDING", "CONFIRMED"],
        PREPARING: ["PENDING", "CONFIRMED", "PREPARING"],
        READY: ["PENDING", "CONFIRMED", "PREPARING", "READY"],
        SHIPPED: ["PENDING", "CONFIRMED", "PREPARING", "READY", "SHIPPED"],
        DELIVERED: ["PENDING", "CONFIRMED", "PREPARING", "READY", "SHIPPED", "DELIVERED"],
        CANCELLED: ["PENDING", "CONFIRMED", "CANCELLED"],
        RETURNED: ["PENDING", "CONFIRMED", "PREPARING", "READY", "SHIPPED", "DELIVERED", "RETURNED"],
    };

    const selected = chains[status] || ["PENDING"];

    return selected.map((entry, index) => ({
        status: entry,
        changedAt: new Date(baseTime.getTime() + index * 25 * 60 * 1000),
        note: `Seeded status transition: ${entry}`,
    }));
}

function buildCommerceDocs(ctx) {
    const { patientA, patientB } = ctx.identities;

    if (!ctx.refs.pharmacies || !ctx.refs.pharmacies.approved) {
        throw new Error("Pharmacy references missing. Run pharmacy seed builder before commerce builder.");
    }

    const pharmacyA = ctx.refs.pharmacies.approved;
    const pharmacyB = ctx.refs.pharmacies.pending;
    const pharmacyC = ctx.refs.pharmacies.rejected;

    const medicineByPatient = ctx.refs.patientMedicines || {};

    const patientMedicineLookup = {};
    Object.entries(medicineByPatient).forEach(([patientId, medicineRows]) => {
        const byName = {};
        (medicineRows || []).forEach((row) => {
            byName[String(row.medicationName || "").toLowerCase()] = row;
        });
        patientMedicineLookup[patientId] = byName;
    });

    function resolveItem(patient, medicineName, quantity, unitPrice, overrideDosage) {
        const lookup = patientMedicineLookup[patient.uid] || {};
        const source = lookup[String(medicineName).toLowerCase()];
        const dosage = overrideDosage || (source ? source.dosage : "500mg");
        const medicineId = source ? source.id : ctx.buildDocId("medicine", `${patient.uid}_${medicineName}`);
        const prescriptionId = source && source.prescriptionId ? source.prescriptionId : null;
        const requiresPrescription = Boolean(prescriptionId);

        return {
            medicineId,
            medicineName,
            medicineDosage: dosage,
            quantity,
            unitPrice,
            totalPrice: Number((quantity * unitPrice).toFixed(2)),
            prescriptionRequired: requiresPrescription,
            prescriptionId,
            prescriptionVerified: requiresPrescription,
        };
    }

    const orderRows = [
        {
            key: "pending_primary",
            status: "PENDING",
            patient: patientA,
            pharmacy: pharmacyA,
            dayOffset: -1,
            items: [
                resolveItem(patientA, "Metformin", 1, 8.5),
            ],
            paymentStatus: "CREATED",
            paymentMethod: "",
            cancelReason: "",
        },
        {
            key: "confirmed_primary",
            status: "CONFIRMED",
            patient: patientA,
            pharmacy: pharmacyA,
            dayOffset: -1,
            items: [
                resolveItem(patientA, "Atorvastatin", 2, 9.25),
            ],
            paymentStatus: "CAPTURED",
            paymentMethod: "UPI",
            cancelReason: "",
        },
        {
            key: "preparing_primary",
            status: "PREPARING",
            patient: patientA,
            pharmacy: pharmacyA,
            dayOffset: -1,
            items: [
                resolveItem(patientA, "Vitamin B12", 1, 28),
                resolveItem(patientA, "Metformin", 1, 8.5),
            ],
            paymentStatus: "CAPTURED",
            paymentMethod: "CARD",
            cancelReason: "",
        },
        {
            key: "ready_primary",
            status: "READY",
            patient: patientB,
            pharmacy: pharmacyA,
            dayOffset: -1,
            items: [
                resolveItem(patientB, "Amlodipine", 2, 6.75),
            ],
            paymentStatus: "CAPTURED",
            paymentMethod: "UPI",
            cancelReason: "",
        },
        {
            key: "shipped_primary",
            status: "SHIPPED",
            patient: patientA,
            pharmacy: pharmacyA,
            dayOffset: 0,
            items: [
                resolveItem(patientA, "Metformin", 2, 8.5),
                resolveItem(patientA, "Vitamin B12", 1, 28),
            ],
            paymentStatus: "CAPTURED",
            paymentMethod: "UPI",
            cancelReason: "",
        },
        {
            key: "delivered_primary",
            status: "DELIVERED",
            patient: patientA,
            pharmacy: pharmacyA,
            dayOffset: -2,
            items: [
                resolveItem(patientA, "Atorvastatin", 1, 9.25),
                resolveItem(patientA, "Metformin", 1, 8.5),
            ],
            paymentStatus: "CAPTURED",
            paymentMethod: "NETBANKING",
            cancelReason: "",
        },
        {
            key: "cancelled_pre_ship",
            status: "CANCELLED",
            patient: patientB,
            pharmacy: pharmacyA,
            dayOffset: -2,
            items: [
                resolveItem(patientB, "Amlodipine", 1, 6.75),
            ],
            paymentStatus: "FAILED",
            paymentMethod: "UPI",
            cancelReason: "Patient requested cancellation before dispatch",
        },
        {
            key: "returned_post_delivery",
            status: "RETURNED",
            patient: patientB,
            pharmacy: pharmacyA,
            dayOffset: -4,
            items: [
                resolveItem(patientB, "Aspirin", 1, 5.25, "75mg"),
            ],
            paymentStatus: "REFUNDED",
            paymentMethod: "CARD",
            cancelReason: "Returned due to packaging damage",
        },
        {
            key: "delivered_secondary",
            status: "DELIVERED",
            patient: patientB,
            pharmacy: pharmacyB,
            dayOffset: -3,
            items: [
                resolveItem(patientB, "Probiotic", 1, 22),
            ],
            paymentStatus: "CAPTURED",
            paymentMethod: "UPI",
            cancelReason: "",
        },
        {
            key: "shipped_secondary",
            status: "SHIPPED",
            patient: patientB,
            pharmacy: pharmacyB,
            dayOffset: 0,
            items: [
                resolveItem(patientB, "Amlodipine", 1, 6.75),
                resolveItem(patientB, "Probiotic", 1, 22),
            ],
            paymentStatus: "CAPTURED",
            paymentMethod: "CARD",
            cancelReason: "",
        },
        {
            key: "confirmed_secondary",
            status: "CONFIRMED",
            patient: patientA,
            pharmacy: pharmacyB,
            dayOffset: -1,
            items: [
                resolveItem(patientA, "Amoxicillin", 1, 12, "500mg"),
            ],
            paymentStatus: "CAPTURED",
            paymentMethod: "UPI",
            cancelReason: "",
        },
        {
            key: "pending_secondary",
            status: "PENDING",
            patient: patientB,
            pharmacy: pharmacyC,
            dayOffset: 0,
            items: [
                resolveItem(patientB, "Aspirin", 1, 5.25, "75mg"),
            ],
            paymentStatus: "CREATED",
            paymentMethod: "",
            cancelReason: "",
        },
    ];

    const docs = [];
    const orderRefs = [];

    for (const row of orderRows) {
        const orderId = ctx.buildDocId("order", row.key);
        const paymentDocId = ctx.buildDocId("payment", row.key);
        const history = buildStatusHistory(ctx.dateAt(row.dayOffset, 9, 10), row.status);
        const latestHistory = history[history.length - 1];

        const subtotal = Number(row.items.reduce((sum, item) => sum + item.totalPrice, 0).toFixed(2));
        const deliveryFee = row.status === "PENDING" ? 0 : 35;
        const discount = row.items.length > 1 ? 10 : 0;
        const totalAmount = Number((subtotal + deliveryFee - discount).toFixed(2));

        const hasTracking = ["SHIPPED", "DELIVERED", "RETURNED"].includes(row.status);
        const trackingId = hasTracking ? `ongoing_${orderId}` : "";
        const deliveryPersonId = hasTracking ? row.pharmacy.owner.uid : "";
        const deliveryPersonName = hasTracking ? row.pharmacy.owner.displayName : "";
        const deliveryPersonPhone = hasTracking ? row.pharmacy.owner.phoneNumber : "";

        const estimatedDelivery = ctx.shiftDate(latestHistory.changedAt, 0, 2, 15);
        const deliveredAt = row.status === "DELIVERED" || row.status === "RETURNED"
            ? ctx.shiftDate(latestHistory.changedAt, 0, 0, 5)
            : null;

        const orderDoc = ctx.withSeedMeta({
            userId: row.patient.uid,
            patientId: row.patient.uid,
            medicineId: row.items[0].medicineId,
            medicineName: row.items[0].medicineName,
            medicineDosage: row.items[0].medicineDosage,
            medicineUnit: "tablets",
            quantity: row.items[0].quantity,
            status: row.status,
            notes: `Seed scenario ${row.key}`,
            prescriptionId: row.items[0].prescriptionId || "",
            pharmacyName: row.pharmacy.name,
            pharmacyId: row.pharmacy.id,
            paymentId: paymentDocId,
            items: row.items,
            subtotal,
            deliveryFee,
            discount,
            totalAmount,
            deliveryAddress: {
                label: "Home",
                fullAddress: `${row.patient.displayName}, 24 MG Road, Bengaluru`,
                landmark: "Near Metro Station",
                latitude: row.patient.uid === patientA.uid ? 12.9685 : 12.9354,
                longitude: row.patient.uid === patientA.uid ? 77.6057 : 77.6178,
                contactPhone: row.patient.phoneNumber,
            },
            deliveryLocation: {
                lat: row.patient.uid === patientA.uid ? 12.9685 : 12.9354,
                lng: row.patient.uid === patientA.uid ? 77.6057 : 77.6178,
            },
            deliveryType: "DELIVERY",
            estimatedDeliveryMinutes: 45,
            estimatedDeliveryTime: 45,
            deliveryWindow: {
                type: row.status === "SHIPPED" ? "EVENING" : "FLEXIBLE",
                startTime: row.status === "SHIPPED" ? "18:00" : "06:00",
                endTime: row.status === "SHIPPED" ? "22:00" : "22:00",
            },
            preferredDeliveryDate: ctx.dateAt(row.dayOffset + 1, 0, 0),
            currentLocation: hasTracking ? ctx.geoPoint(row.pharmacy.latitude + 0.002, row.pharmacy.longitude - 0.002) : null,
            lastLocationUpdate: hasTracking ? ctx.shiftDate(latestHistory.changedAt, 0, 0, 2) : null,
            deliveryTrackingId: trackingId,
            pharmacyLocation: ctx.geoPoint(row.pharmacy.latitude, row.pharmacy.longitude),
            statusHistory: history,
            cancelReason: row.status === "CANCELLED" || row.status === "RETURNED" ? row.cancelReason : "",
            estimatedDelivery,
            deliveredAt,
            trackingStatus: row.status === "SHIPPED" ? "ACTIVE" : (hasTracking ? "ENDED" : ""),
            trackingStartedAt: hasTracking ? history.find((entry) => entry.status === "SHIPPED")?.changedAt || latestHistory.changedAt : null,
            trackingEndedAt: row.status === "DELIVERED" || row.status === "RETURNED" ? deliveredAt : null,
            deliveryPersonId,
            deliveryPersonName,
            deliveryPersonPhone,
            createdAt: history[0].changedAt,
            updatedAt: ctx.now,
        });

        docs.push(
            ctx.makeSeedDoc(
                "commerce",
                `orders/${orderId}`,
                orderDoc,
                [row.patient.uid, row.pharmacy.owner.uid, deliveryPersonId]
            )
        );

        const amountInPaise = Math.round(totalAmount * 100);
        const capturedPaymentId = row.paymentStatus === "CAPTURED" || row.paymentStatus === "REFUNDED"
            ? `pay_${orderId.slice(-10)}`
            : "";

        docs.push(
            ctx.makeSeedDoc(
                "commerce",
                `payments/${paymentDocId}`,
                ctx.withSeedMeta({
                    razorpayOrderId: `order_${orderId.slice(-10)}`,
                    meditrackOrderId: orderId,
                    amount: amountInPaise,
                    currency: "INR",
                    userId: row.patient.uid,
                    customerEmail: row.patient.email,
                    customerPhone: row.patient.phoneNumber,
                    customerName: row.patient.displayName,
                    status: row.paymentStatus,
                    paymentId: capturedPaymentId,
                    signatureId: capturedPaymentId ? `sig_${orderId.slice(-10)}` : "",
                    paymentMethod: row.paymentMethod,
                    errorCode: row.paymentStatus === "FAILED" ? "BAD_REQUEST_ERROR" : "",
                    errorDescription: row.paymentStatus === "FAILED" ? "User cancelled before authentication" : "",
                    errorSource: row.paymentStatus === "FAILED" ? "customer" : "",
                    attempts: row.paymentStatus === "FAILED" ? 2 : 1,
                    receipt: orderId,
                    completedAt: row.paymentStatus === "CREATED" ? null : ctx.shiftDate(latestHistory.changedAt, 0, 0, 3),
                    createdAt: history[0].changedAt,
                    updatedAt: ctx.now,
                }),
                [row.patient.uid]
            )
        );

        if (row.paymentStatus !== "CREATED") {
            const purchaseStatus = row.paymentStatus === "FAILED" ? "FAILED" : "COMPLETED";
            docs.push(
                ctx.makeSeedDoc(
                    "commerce",
                    `transactions/${ctx.buildDocId("transaction", `purchase_${row.key}`)}`,
                    ctx.withSeedMeta({
                        userId: row.patient.uid,
                        orderId,
                        medicineId: row.items[0].medicineId,
                        medicineName: row.items[0].medicineName,
                        type: "PURCHASE",
                        amount: totalAmount,
                        currency: "INR",
                        status: purchaseStatus,
                        pharmacyId: row.pharmacy.id,
                        pharmacyName: row.pharmacy.name,
                        prescriptionVerified: row.items.some((item) => item.prescriptionRequired),
                        prescriptionId: row.items[0].prescriptionId || "",
                        razorpayOrderId: `order_${orderId.slice(-10)}`,
                        razorpayPaymentId: capturedPaymentId,
                        paymentMethod: row.paymentMethod || "UPI",
                        transactionRef: capturedPaymentId ? `txn_${orderId.slice(-10)}` : "",
                        notes: `Purchase transaction for ${row.key}`,
                        createdAt: ctx.shiftDate(latestHistory.changedAt, 0, 0, 4),
                    }),
                    [row.patient.uid]
                )
            );
        }

        if (row.paymentStatus === "REFUNDED") {
            docs.push(
                ctx.makeSeedDoc(
                    "commerce",
                    `transactions/${ctx.buildDocId("transaction", `refund_${row.key}`)}`,
                    ctx.withSeedMeta({
                        userId: row.patient.uid,
                        orderId,
                        medicineId: row.items[0].medicineId,
                        medicineName: row.items[0].medicineName,
                        type: "REFUND",
                        amount: totalAmount,
                        currency: "INR",
                        status: "REFUNDED",
                        pharmacyId: row.pharmacy.id,
                        pharmacyName: row.pharmacy.name,
                        prescriptionVerified: false,
                        prescriptionId: "",
                        razorpayOrderId: `order_${orderId.slice(-10)}`,
                        razorpayPaymentId: capturedPaymentId,
                        paymentMethod: row.paymentMethod || "CARD",
                        transactionRef: `rfnd_${orderId.slice(-10)}`,
                        notes: `Refund issued for ${row.key}`,
                        createdAt: ctx.shiftDate(latestHistory.changedAt, 0, 0, 8),
                    }),
                    [row.patient.uid]
                )
            );
        }

        if (hasTracking) {
            const isActiveTracking = row.status === "SHIPPED";
            const terminalStatus = row.status === "SHIPPED" ? "ACTIVE" : row.status;
            const startedAt = history.find((entry) => entry.status === "SHIPPED")?.changedAt || history[0].changedAt;
            const endedAt = isActiveTracking ? null : ctx.shiftDate(latestHistory.changedAt, 0, 0, 2);

            docs.push(
                ctx.makeSeedDoc(
                    "commerce",
                    `deliveryTracking/${trackingId}`,
                    ctx.withSeedMeta({
                        orderId,
                        deliveryPersonId,
                        latitude: row.pharmacy.latitude + 0.004,
                        longitude: row.pharmacy.longitude - 0.003,
                        speed: isActiveTracking ? 24.5 : 0,
                        bearing: 125,
                        accuracy: 8.3,
                        isActive: isActiveTracking,
                        trackingStatus: terminalStatus,
                        updatedAt: ctx.now,
                        startedAt,
                        endedAt,
                        deliveryPersonName,
                        deliveryPersonPhone,
                    }),
                    [deliveryPersonId]
                )
            );

            const pointOffsets = [0, 6, 13, 19];
            pointOffsets.forEach((offset, index) => {
                const pointTime = ctx.shiftDate(startedAt, 0, 0, offset);
                const pointActive = isActiveTracking || index < pointOffsets.length - 1;
                const pointStatus = pointActive ? "ACTIVE" : terminalStatus;

                docs.push(
                    ctx.makeSeedDoc(
                        "commerce",
                        `deliveryTracking/${trackingId}/points/${ctx.buildDocId("trackingpoint", `${row.key}_${index + 1}`)}`,
                        ctx.withSeedMeta({
                            orderId,
                            deliveryPersonId,
                            latitude: row.pharmacy.latitude + 0.001 * (index + 1),
                            longitude: row.pharmacy.longitude - 0.0012 * (index + 1),
                            speed: pointActive ? 21 + index * 2 : 0,
                            bearing: 125 + index * 3,
                            accuracy: 7 + index,
                            trackingStatus: pointStatus,
                            isActive: pointActive,
                            recordedAt: pointTime,
                        }),
                        [deliveryPersonId]
                    )
                );
            });
        }

        orderRefs.push({
            id: orderId,
            key: row.key,
            status: row.status,
            patientId: row.patient.uid,
            pharmacyId: row.pharmacy.id,
            pharmacyOwnerId: row.pharmacy.owner.uid,
            totalAmount,
            paymentDocId,
            paymentStatus: row.paymentStatus,
            paymentId: capturedPaymentId,
        });
    }

    const cancellationRows = [
        {
            key: "cancelled_pre_ship",
            requestStatus: "COMPLETED",
            refundStatus: "SKIPPED",
            approvalNote: "Cancelled before shipping; no refund settlement required.",
        },
        {
            key: "returned_post_delivery",
            requestStatus: "APPROVED",
            refundStatus: "COMPLETED",
            approvalNote: "Return approved after pharmacy QA review.",
        },
        {
            key: "shipped_secondary",
            requestStatus: "PENDING",
            refundStatus: "PENDING",
            approvalNote: "Awaiting pharmacy review for in-transit cancellation.",
        },
    ];

    cancellationRows.forEach((row) => {
        const orderRef = orderRefs.find((entry) => entry.key === row.key);
        if (!orderRef) {
            return;
        }

        docs.push(
            ctx.makeSeedDoc(
                "commerce",
                `cancellationRequests/${ctx.buildDocId("cancelrequest", row.key)}`,
                ctx.withSeedMeta({
                    userId: orderRef.patientId,
                    orderId: orderRef.id,
                    orderStatus: orderRef.status,
                    reason: row.key === "shipped_secondary" ? "Need to modify delivery address" : "Order no longer required",
                    requestStatus: row.requestStatus,
                    approvedBy: row.requestStatus === "PENDING" ? "" : orderRef.pharmacyOwnerId,
                    approvalNote: row.approvalNote,
                    approvedAt: row.requestStatus === "PENDING" ? null : ctx.dateAt(-1, 15, 5),
                    refundAmount: orderRef.totalAmount,
                    refundStatus: row.refundStatus,
                    razorpayPaymentId: orderRef.paymentId,
                    razorpayRefundId: row.refundStatus === "COMPLETED" ? `rfnd_${orderRef.id.slice(-8)}` : "",
                    refundIdempotencyKey: `refund_${orderRef.id}`,
                    refundProcessedAt: row.refundStatus === "COMPLETED" ? ctx.dateAt(-1, 16, 0) : null,
                    refundFailureReason: "",
                    inventoryRestored: row.requestStatus === "COMPLETED" || row.requestStatus === "APPROVED",
                    restoredAt: row.requestStatus === "PENDING" ? null : ctx.dateAt(-1, 16, 10),
                    createdAt: ctx.dateAt(-1, 14, 30),
                    updatedAt: ctx.now,
                }),
                [orderRef.patientId, orderRef.pharmacyOwnerId]
            )
        );
    });

    ctx.refs.orders = orderRefs;

    return docs;
}

module.exports = {
    buildCommerceDocs,
};
