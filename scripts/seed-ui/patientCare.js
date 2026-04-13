function buildPatientCareDocs(ctx) {
    const { patientA, patientB } = ctx.identities;
    const docs = [];

    const prescribedRefs = ctx.refs.prescriptionMedicineRefs || [];

    const selfManagedMedicines = [
        {
            key: "p1_vitamin_b12",
            patient: patientA,
            name: "Vitamin B12",
            dosage: "1500",
            unit: "mcg",
            instructions: "Take after breakfast",
            reminderTimes: ["08:30"],
            repeatType: "DAILY",
            color: "teal",
            currentQuantity: 12,
            totalQuantity: 30,
            lowStockThreshold: 10,
            refillReminderEnabled: true,
            expiryDate: ctx.dateAt(120, 0, 0),
            expiryMonthYear: "08/2026",
            prescribedByDoctor: false,
            prescriptionId: "",
            isActive: true,
            createdAt: ctx.dateAt(-32, 9, 0),
        },
        {
            key: "p2_probiotic",
            patient: patientB,
            name: "Probiotic",
            dosage: "1",
            unit: "capsule",
            instructions: "Take after lunch",
            reminderTimes: ["14:00"],
            repeatType: "DAILY",
            color: "orange",
            currentQuantity: 3,
            totalQuantity: 20,
            lowStockThreshold: 5,
            refillReminderEnabled: true,
            expiryDate: ctx.dateAt(85, 0, 0),
            expiryMonthYear: "07/2026",
            prescribedByDoctor: false,
            prescriptionId: "",
            isActive: true,
            createdAt: ctx.dateAt(-26, 10, 0),
        },
        {
            key: "p1_sos_pain",
            patient: patientA,
            name: "Pain Relief SOS",
            dosage: "400",
            unit: "mg",
            instructions: "Use only when required",
            reminderTimes: ["20:30"],
            repeatType: "AS_NEEDED",
            color: "red",
            currentQuantity: -1,
            totalQuantity: -1,
            lowStockThreshold: 5,
            refillReminderEnabled: false,
            expiryDate: ctx.dateAt(70, 0, 0),
            expiryMonthYear: "06/2026",
            prescribedByDoctor: false,
            prescriptionId: "",
            isActive: true,
            createdAt: ctx.dateAt(-10, 18, 0),
        },
    ];

    for (const medicine of selfManagedMedicines) {
        const medicineId = ctx.buildDocId("medicine", medicine.key);
        docs.push(
            ctx.makeSeedDoc(
                "patientCare",
                `medicines/${medicineId}`,
                ctx.withSeedMeta({
                    userId: medicine.patient.uid,
                    name: medicine.name,
                    dosage: medicine.dosage,
                    unit: medicine.unit,
                    instructions: medicine.instructions,
                    reminderTimes: medicine.reminderTimes,
                    repeatType: medicine.repeatType,
                    color: medicine.color,
                    isActive: medicine.isActive,
                    alarmIds: [],
                    prescriptionId: medicine.prescriptionId,
                    prescribedByDoctor: medicine.prescribedByDoctor,
                    currentQuantity: medicine.currentQuantity,
                    totalQuantity: medicine.totalQuantity,
                    lowStockThreshold: medicine.lowStockThreshold,
                    refillReminderEnabled: medicine.refillReminderEnabled,
                    lastRefillDate: ctx.dateAt(-3, 8, 0),
                    expiryDate: medicine.expiryDate,
                    expiryMonthYear: medicine.expiryMonthYear,
                    expiryWarningDays: 30,
                    createdAt: medicine.createdAt,
                    updatedAt: ctx.now,
                }),
                [medicine.patient.uid]
            )
        );

        prescribedRefs.push({
            key: medicine.key,
            id: medicineId,
            patientId: medicine.patient.uid,
            doctorId: "",
            prescriptionId: medicine.prescriptionId,
            medicationName: medicine.name,
        });
    }

    const healthSeries = [
        {
            patient: patientA,
            values: [
                { day: -14, hr: 78, sys: 126, dia: 82, glucose: 142, weight: 68.5, temp: 36.7, symptoms: ["Fatigue"] },
                { day: -12, hr: 82, sys: 132, dia: 86, glucose: 149, weight: 68.7, temp: 36.8, symptoms: ["Headache"] },
                { day: -10, hr: 88, sys: 148, dia: 95, glucose: 168, weight: 68.9, temp: 37.0, symptoms: ["Headache", "Dizziness"] },
                { day: -8, hr: 80, sys: 134, dia: 87, glucose: 152, weight: 68.6, temp: 36.7, symptoms: [] },
                { day: -6, hr: 76, sys: 128, dia: 84, glucose: 145, weight: 68.4, temp: 36.6, symptoms: [] },
                { day: -4, hr: 92, sys: 171, dia: 104, glucose: 176, weight: 68.8, temp: 37.1, symptoms: ["Severe headache"] },
                { day: -2, hr: 79, sys: 130, dia: 85, glucose: 147, weight: 68.3, temp: 36.6, symptoms: ["Fatigue"] },
                { day: -1, hr: 77, sys: 127, dia: 82, glucose: 144, weight: 68.2, temp: 36.5, symptoms: [] },
            ],
        },
        {
            patient: patientB,
            values: [
                { day: -14, hr: 74, sys: 144, dia: 92, glucose: 118, weight: 81.2, temp: 36.8, symptoms: ["Dizziness"] },
                { day: -12, hr: 76, sys: 142, dia: 90, glucose: 121, weight: 81.1, temp: 36.7, symptoms: [] },
                { day: -10, hr: 72, sys: 139, dia: 88, glucose: 119, weight: 81.0, temp: 36.8, symptoms: [] },
                { day: -8, hr: 71, sys: 136, dia: 86, glucose: 117, weight: 80.8, temp: 36.6, symptoms: [] },
                { day: -6, hr: 70, sys: 134, dia: 84, glucose: 115, weight: 80.7, temp: 36.6, symptoms: [] },
                { day: -4, hr: 73, sys: 138, dia: 87, glucose: 120, weight: 80.8, temp: 36.7, symptoms: ["Mild headache"] },
                { day: -2, hr: 69, sys: 132, dia: 83, glucose: 114, weight: 80.6, temp: 36.5, symptoms: [] },
                { day: -1, hr: 68, sys: 130, dia: 82, glucose: 112, weight: 80.4, temp: 36.5, symptoms: [] },
            ],
        },
    ];

    for (const series of healthSeries) {
        for (const item of series.values) {
            const id = ctx.buildDocId("healthlog", `${series.patient.uid}_${Math.abs(item.day)}`);
            docs.push(
                ctx.makeSeedDoc(
                    "patientCare",
                    `healthLogs/${id}`,
                    ctx.withSeedMeta({
                        userId: series.patient.uid,
                        date: ctx.dateAt(item.day, 7, 30),
                        heartRate: item.hr,
                        bloodPressureSystolic: item.sys,
                        bloodPressureDiastolic: item.dia,
                        glucoseLevel: item.glucose,
                        weight: item.weight,
                        temperature: item.temp,
                        symptoms: item.symptoms,
                        notes: item.symptoms.length > 0 ? "Patient noted mild discomfort." : "Routine self-check entry.",
                        createdAt: ctx.dateAt(item.day, 7, 35),
                        updatedAt: ctx.now,
                    }),
                    [series.patient.uid]
                )
            );
        }
    }

    const medicineByPatient = {
        [patientA.uid]: prescribedRefs.filter((entry) => entry.patientId === patientA.uid),
        [patientB.uid]: prescribedRefs.filter((entry) => entry.patientId === patientB.uid),
    };

    const intakeRows = [];
    Object.entries(medicineByPatient).forEach(([patientId, medicines]) => {
        medicines
            .filter((entry) => !entry.key.includes("stopped") && !entry.key.includes("completed"))
            .slice(0, 4)
            .forEach((medicine, medicineIndex) => {
                for (let day = -6; day <= 0; day += 1) {
                    const scheduledTime = medicineIndex % 2 === 0 ? "08:00" : "20:00";
                    const taken = (day + medicineIndex) % 3 !== 0;
                    const stamp = ctx.dateAt(day, scheduledTime === "08:00" ? 8 : 20, 10 + medicineIndex);
                    intakeRows.push({
                        patientId,
                        medicine,
                        day,
                        scheduledTime,
                        taken,
                        stamp,
                    });
                }
            });
    });

    intakeRows.forEach((row, index) => {
        docs.push(
            ctx.makeSeedDoc(
                "patientCare",
                `medicineIntakes/${ctx.buildDocId("intake", `${row.patientId}_${row.medicine.id}_${index + 1}`)}`,
                ctx.withSeedMeta({
                    userId: row.patientId,
                    medicineId: row.medicine.id,
                    medicineName: row.medicine.medicationName,
                    scheduledTime: row.scheduledTime,
                    taken: row.taken,
                    status: row.taken ? "TAKEN" : "SKIPPED",
                    takenAt: row.stamp,
                    wasOnTime: row.taken && row.stamp.getMinutes() <= 20,
                    source: row.taken ? "manual_entry" : "notification_action_skipped",
                    createdAt: row.stamp,
                    updatedAt: ctx.now,
                }),
                [row.patientId]
            )
        );
    });

    const refillAlerts = [
        {
            key: "p1_low_vitamin",
            patient: patientA,
            medicineName: "Vitamin B12",
            medicineKey: "p1_vitamin_b12",
            dosage: "1500mcg",
            quantity: 12,
            daysLeft: 6,
            urgency: "LOW",
            isRead: true,
            isDismissed: false,
            actionTakenAt: null,
            orderId: null,
            createdAt: ctx.dateAt(-2, 9, 20),
        },
        {
            key: "p2_urgent_probiotic",
            patient: patientB,
            medicineName: "Probiotic",
            medicineKey: "p2_probiotic",
            dosage: "1 capsule",
            quantity: 3,
            daysLeft: 1,
            urgency: "URGENT",
            isRead: false,
            isDismissed: false,
            actionTakenAt: null,
            orderId: null,
            createdAt: ctx.dateAt(-1, 8, 50),
        },
        {
            key: "p1_out_atorvastatin",
            patient: patientA,
            medicineName: "Atorvastatin",
            medicineKey: "p1_atorvastatin_modified",
            dosage: "20mg",
            quantity: 0,
            daysLeft: 0,
            urgency: "OUT_OF_STOCK",
            isRead: true,
            isDismissed: true,
            actionTakenAt: ctx.dateAt(-1, 12, 10),
            orderId: ctx.buildDocId("order", "delivered_primary"),
            createdAt: ctx.dateAt(-3, 10, 15),
        },
    ];

    refillAlerts.forEach((alert) => {
        const medicineRef = prescribedRefs.find((entry) => entry.key === alert.medicineKey);
        docs.push(
            ctx.makeSeedDoc(
                "patientCare",
                `refillAlerts/${ctx.buildDocId("refillalert", alert.key)}`,
                ctx.withSeedMeta({
                    userId: alert.patient.uid,
                    medicineId: medicineRef ? medicineRef.id : ctx.buildDocId("medicine", alert.medicineKey),
                    medicineName: alert.medicineName,
                    medicineDosage: alert.dosage,
                    currentQuantity: alert.quantity,
                    estimatedDaysLeft: alert.daysLeft,
                    urgencyLevel: alert.urgency,
                    isRead: alert.isRead,
                    isDismissed: alert.isDismissed,
                    actionTakenAt: alert.actionTakenAt,
                    orderId: alert.orderId,
                    createdAt: alert.createdAt,
                }),
                [alert.patient.uid]
            )
        );
    });

    const riskRows = [
        { key: "p1_low", patient: patientA, day: -30, overall: 28, category: "LOW", vital: 30, adherence: 82, symptom: 12, quality: 90, factors: 1, topFactor: "Stable blood pressure", rules: ["bp_stable"] },
        { key: "p1_moderate", patient: patientA, day: -14, overall: 49, category: "MODERATE", vital: 52, adherence: 76, symptom: 35, quality: 85, factors: 3, topFactor: "Morning glucose spikes", rules: ["glucose_rise_7d", "adherence_below_80"] },
        { key: "p1_high", patient: patientA, day: -4, overall: 78, category: "HIGH", vital: 84, adherence: 61, symptom: 70, quality: 79, factors: 5, topFactor: "Hypertensive episode", rules: ["bp_critical", "symptom_cluster_headache"] },
        { key: "p2_moderate", patient: patientB, day: -20, overall: 55, category: "MODERATE", vital: 60, adherence: 72, symptom: 28, quality: 88, factors: 2, topFactor: "Elevated systolic trend", rules: ["bp_rise_14d"] },
        { key: "p2_low", patient: patientB, day: -8, overall: 34, category: "LOW", vital: 36, adherence: 80, symptom: 18, quality: 90, factors: 1, topFactor: "Improving BP control", rules: ["bp_downtrend"] },
        { key: "p2_critical", patient: patientB, day: -1, overall: 91, category: "CRITICAL", vital: 95, adherence: 58, symptom: 82, quality: 76, factors: 6, topFactor: "Persistent severe dizziness", rules: ["critical_alert_pending", "adherence_drop"] },
    ];

    riskRows.forEach((row) => {
        docs.push(
            ctx.makeSeedDoc(
                "patientCare",
                `riskScores/${ctx.buildDocId("riskscore", row.key)}`,
                ctx.withSeedMeta({
                    userId: row.patient.uid,
                    overallScore: row.overall,
                    category: row.category,
                    vitalScore: row.vital,
                    adherenceScore: row.adherence,
                    symptomScore: row.symptom,
                    dataQualityScore: row.quality,
                    contributingFactorCount: row.factors,
                    topFactor: row.topFactor,
                    correlationRulesTriggered: row.rules,
                    computedAt: ctx.dateAt(row.day, 6, 45),
                }),
                [row.patient.uid]
            )
        );
    });

    ctx.refs.patientMedicines = medicineByPatient;

    return docs;
}

module.exports = {
    buildPatientCareDocs,
};
