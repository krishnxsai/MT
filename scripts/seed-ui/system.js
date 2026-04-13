function buildSystemDocs(ctx) {
    const docs = [];

    const allUsers = ctx.identities.allUsers;

    allUsers.forEach((user, index) => {
        const quietHoursEnabled = user.role === "DOCTOR" || user.role === "PHARMACY";
        docs.push(
            ctx.makeSeedDoc(
                "system",
                `notificationPreferences/${user.uid}`,
                ctx.withSeedMeta({
                    userId: user.uid,
                    appointmentReminders: true,
                    prescriptionNotifications: true,
                    orderStatusUpdates: user.role !== "ADMIN",
                    promotionalMessages: user.role === "PATIENT",
                    healthAlerts: user.role !== "PHARMACY",
                    quietHoursEnabled,
                    quietHourStart: "22:00",
                    quietHourEnd: "07:00",
                    notificationChannels: {
                        APPOINTMENT: true,
                        PRESCRIPTION: true,
                        ORDER_STATUS: user.role !== "ADMIN",
                        PROMOTIONAL: user.role === "PATIENT",
                        HEALTH_ALERT: user.role !== "PHARMACY",
                    },
                    createdAt: ctx.dateAt(-25 + index, 8, 0),
                    updatedAt: ctx.now,
                }),
                [user.uid]
            )
        );
    });

    const patientA = ctx.identities.patientA;
    const patientB = ctx.identities.patientB;

    const patientAMedicines = (ctx.refs.patientMedicines && ctx.refs.patientMedicines[patientA.uid]) || [];
    const patientBMedicines = (ctx.refs.patientMedicines && ctx.refs.patientMedicines[patientB.uid]) || [];

    const approvedPharmacy = ctx.refs.pharmacies.approved;
    const pendingPharmacy = ctx.refs.pharmacies.pending;

    const cartRows = [
        {
            user: patientA,
            selectedPharmacyId: approvedPharmacy.id,
            items: [
                {
                    source: patientAMedicines.find((entry) => String(entry.medicationName || "").toLowerCase() === "metformin"),
                    quantity: 2,
                    fromLowStockAlert: false,
                    alertId: null,
                },
                {
                    source: patientAMedicines.find((entry) => String(entry.medicationName || "").toLowerCase() === "atorvastatin"),
                    quantity: 1,
                    fromLowStockAlert: true,
                    alertId: ctx.buildDocId("refillalert", "p1_out_atorvastatin"),
                },
            ],
            updatedAt: ctx.dateAt(0, 8, 40),
        },
        {
            user: patientB,
            selectedPharmacyId: pendingPharmacy.id,
            items: [
                {
                    source: patientBMedicines.find((entry) => String(entry.medicationName || "").toLowerCase() === "probiotic"),
                    quantity: 1,
                    fromLowStockAlert: true,
                    alertId: ctx.buildDocId("refillalert", "p2_urgent_probiotic"),
                },
            ],
            updatedAt: ctx.dateAt(0, 9, 10),
        },
    ];

    cartRows.forEach((row) => {
        const cartItems = row.items
            .filter((entry) => Boolean(entry.source))
            .map((entry) => ({
                medicineId: entry.source.id,
                medicineName: entry.source.medicationName,
                medicineDosage: entry.source.dosage || "",
                medicineUnit: "tablets",
                quantity: entry.quantity,
                fromLowStockAlert: entry.fromLowStockAlert,
                alertId: entry.alertId,
                prescriptionId: entry.source.prescriptionId || null,
                prescriptionRequired: Boolean(entry.source.prescriptionId),
            }));

        docs.push(
            ctx.makeSeedDoc(
                "system",
                `carts/${row.user.uid}`,
                ctx.withSeedMeta({
                    userId: row.user.uid,
                    items: cartItems,
                    selectedPharmacyId: row.selectedPharmacyId,
                    createdAt: ctx.dateAt(-1, 20, 0),
                    updatedAt: row.updatedAt,
                    expiresAt: ctx.shiftDate(row.updatedAt, 1, 0, 0),
                }),
                [row.user.uid]
            )
        );
    });

    const admin = ctx.identities.admin;
    const auditRows = [
        {
            key: "approve_doctor_a",
            userId: admin.uid,
            action: "PERMISSION_CHANGE",
            targetId: ctx.identities.doctorA.uid,
            targetType: "user",
            details: { action: "approve", newStatus: "APPROVED" },
            success: true,
            errorMessage: null,
            timestamp: ctx.dateAt(-70, 12, 30),
        },
        {
            key: "approve_pharmacy_a",
            userId: admin.uid,
            action: "PERMISSION_CHANGE",
            targetId: ctx.identities.pharmacyA.uid,
            targetType: "user",
            details: { action: "approve", newStatus: "APPROVED" },
            success: true,
            errorMessage: null,
            timestamp: ctx.dateAt(-55, 11, 0),
        },
        {
            key: "reject_pharmacy_c",
            userId: admin.uid,
            action: "PERMISSION_CHANGE",
            targetId: ctx.identities.pharmacyC.uid,
            targetType: "user",
            details: { action: "reject", reason: "License mismatch in submission" },
            success: true,
            errorMessage: null,
            timestamp: ctx.dateAt(-20, 10, 30),
        },
        {
            key: "doctor_access_patient",
            userId: ctx.identities.doctorA.uid,
            action: "PATIENT_VIEW",
            targetId: patientA.uid,
            targetType: "patient",
            details: { dataType: "profile" },
            success: true,
            errorMessage: null,
            timestamp: ctx.dateAt(-2, 11, 5),
        },
        {
            key: "security_warning",
            userId: ctx.identities.doctorB.uid,
            action: "SECURITY_WARNING",
            targetId: patientB.uid,
            targetType: "patient",
            details: { warning: "Attempted access outside assignment scope" },
            success: false,
            errorMessage: "Unauthorized patient access attempt",
            timestamp: ctx.dateAt(-1, 13, 15),
        },
        {
            key: "medicine_update",
            userId: patientA.uid,
            action: "MEDICINE_UPDATE",
            targetId: ctx.buildDocId("medicine", "p1_vitamin_b12"),
            targetType: "medicine",
            details: { field: "currentQuantity", newValue: 12 },
            success: true,
            errorMessage: null,
            timestamp: ctx.dateAt(-1, 8, 55),
        },
    ];

    auditRows.forEach((row) => {
        docs.push(
            ctx.makeSeedDoc(
                "system",
                `auditLogs/${ctx.buildDocId("audit", row.key)}`,
                ctx.withSeedMeta({
                    userId: row.userId,
                    action: row.action,
                    targetId: row.targetId,
                    targetType: row.targetType,
                    details: row.details,
                    ipAddress: "127.0.0.1",
                    deviceInfo: "SeedScript",
                    timestamp: row.timestamp,
                    success: row.success,
                    errorMessage: row.errorMessage,
                }),
                [row.userId]
            )
        );
    });

    const notificationRows = [
        {
            key: "order_shipped",
            userId: patientA.uid,
            orderId: ctx.buildDocId("order", "shipped_primary"),
            title: "Order shipped",
            body: "Your order is on the way.",
            status: "SENT",
            sentAt: ctx.dateAt(0, 10, 15),
        },
        {
            key: "order_delivered",
            userId: patientA.uid,
            orderId: ctx.buildDocId("order", "delivered_primary"),
            title: "Order delivered",
            body: "Delivery completed successfully.",
            status: "SENT",
            sentAt: ctx.dateAt(-2, 13, 40),
        },
        {
            key: "payment_failed",
            userId: patientB.uid,
            orderId: ctx.buildDocId("order", "cancelled_pre_ship"),
            title: "Payment failed",
            body: "Please retry payment to continue the order.",
            status: "FAILED",
            sentAt: ctx.dateAt(-2, 14, 10),
        },
    ];

    notificationRows.forEach((row) => {
        docs.push(
            ctx.makeSeedDoc(
                "system",
                `notificationLogs/${ctx.buildDocId("notificationlog", row.key)}`,
                ctx.withSeedMeta({
                    userId: row.userId,
                    orderId: row.orderId,
                    title: row.title,
                    body: row.body,
                    status: row.status,
                    sentAt: row.sentAt,
                    timestamp: row.sentAt,
                }),
                [row.userId]
            )
        );
    });

    return docs;
}

module.exports = {
    buildSystemDocs,
};
