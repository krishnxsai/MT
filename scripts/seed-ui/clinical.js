function reminderTimesFromFrequency(frequency) {
    const normalized = String(frequency || "").toLowerCase();
    if (normalized.includes("three")) {
        return ["08:00", "14:00", "21:00"];
    }
    if (normalized.includes("twice") || normalized.includes("bid")) {
        return ["08:00", "20:00"];
    }
    if (normalized.includes("bedtime")) {
        return ["22:00"];
    }
    return ["08:00"];
}

function buildClinicalDocs(ctx) {
    const { doctorA, doctorB, patientA, patientB } = ctx.identities;

    const docs = [];

    const availabilityRows = [
        { key: "d1_mon", doctor: doctorA, dayOfWeek: 1, startTime: "09:00", endTime: "13:00", slotDurationMinutes: 30 },
        { key: "d1_wed", doctor: doctorA, dayOfWeek: 3, startTime: "10:00", endTime: "16:00", slotDurationMinutes: 30 },
        { key: "d1_fri", doctor: doctorA, dayOfWeek: 5, startTime: "09:30", endTime: "15:30", slotDurationMinutes: 30 },
        { key: "d1_sat", doctor: doctorA, dayOfWeek: 6, startTime: "10:00", endTime: "12:00", slotDurationMinutes: 20 },
        { key: "d2_tue", doctor: doctorB, dayOfWeek: 2, startTime: "08:30", endTime: "12:30", slotDurationMinutes: 20 },
        { key: "d2_thu", doctor: doctorB, dayOfWeek: 4, startTime: "11:00", endTime: "17:00", slotDurationMinutes: 30 },
        { key: "d2_sat", doctor: doctorB, dayOfWeek: 6, startTime: "09:00", endTime: "14:00", slotDurationMinutes: 30 },
        { key: "d2_sun", doctor: doctorB, dayOfWeek: 7, startTime: "10:00", endTime: "12:00", slotDurationMinutes: 30 },
    ];

    for (const row of availabilityRows) {
        docs.push(
            ctx.makeSeedDoc(
                "clinical",
                `doctorAvailability/${ctx.buildDocId("availability", row.key)}`,
                ctx.withSeedMeta({
                    doctorId: row.doctor.uid,
                    doctorName: row.doctor.displayName,
                    dayOfWeek: row.dayOfWeek,
                    startTime: row.startTime,
                    endTime: row.endTime,
                    slotDurationMinutes: row.slotDurationMinutes,
                    isActive: true,
                    createdAt: ctx.dateAt(-45, 8, 30),
                    updatedAt: ctx.now,
                }),
                [row.doctor.uid]
            )
        );
    }

    const appointmentRows = [
        {
            key: "pending_a",
            doctor: doctorA,
            patient: patientA,
            date: ctx.dateAt(2, 0, 0),
            startTime: "10:00",
            endTime: "10:30",
            status: "PENDING",
            type: "CONSULTATION",
            notes: "Recurring headache in mornings",
            doctorNotes: "",
            cancellationReason: "",
            isTelemedicine: false,
            callUrl: "",
            meetingId: "",
            cancellationFeeApplied: 0,
        },
        {
            key: "confirmed_tele",
            doctor: doctorA,
            patient: patientB,
            date: ctx.dateAt(3, 0, 0),
            startTime: "11:30",
            endTime: "12:00",
            status: "CONFIRMED",
            type: "TELEMEDICINE",
            notes: "Follow-up for BP meds",
            doctorNotes: "Please keep recent BP logs ready",
            cancellationReason: "",
            isTelemedicine: true,
            callUrl: "https://meet.example.com/meditrack-d1p2",
            meetingId: "MEDI-D1P2-001",
            cancellationFeeApplied: 0,
        },
        {
            key: "completed_a",
            doctor: doctorA,
            patient: patientA,
            date: ctx.dateAt(-8, 0, 0),
            startTime: "09:30",
            endTime: "10:00",
            status: "COMPLETED",
            type: "FOLLOW_UP",
            notes: "Discuss sleep quality",
            doctorNotes: "Improved symptom trend",
            cancellationReason: "",
            isTelemedicine: false,
            callUrl: "",
            meetingId: "",
            cancellationFeeApplied: 0,
        },
        {
            key: "rejected_b",
            doctor: doctorB,
            patient: patientB,
            date: ctx.dateAt(-5, 0, 0),
            startTime: "17:00",
            endTime: "17:30",
            status: "REJECTED",
            type: "CHECKUP",
            notes: "Late evening slot preferred",
            doctorNotes: "Slot unavailable, please choose morning",
            cancellationReason: "",
            isTelemedicine: false,
            callUrl: "",
            meetingId: "",
            cancellationFeeApplied: 0,
        },
        {
            key: "cancelled_patient",
            doctor: doctorB,
            patient: patientA,
            date: ctx.dateAt(1, 0, 0),
            startTime: "15:00",
            endTime: "15:30",
            status: "CANCELLED",
            type: "CONSULTATION",
            notes: "Mild fever and fatigue",
            doctorNotes: "",
            cancellationReason: "Cancelled by patient due to travel",
            isTelemedicine: false,
            callUrl: "",
            meetingId: "",
            cancellationFeeApplied: 150,
        },
        {
            key: "completed_tele",
            doctor: doctorB,
            patient: patientB,
            date: ctx.dateAt(-2, 0, 0),
            startTime: "10:00",
            endTime: "10:20",
            status: "COMPLETED",
            type: "TELEMEDICINE",
            notes: "Review glucose trends",
            doctorNotes: "Medication adherence needs improvement",
            cancellationReason: "",
            isTelemedicine: true,
            callUrl: "https://meet.example.com/meditrack-d2p2",
            meetingId: "MEDI-D2P2-002",
            cancellationFeeApplied: 0,
        },
    ];

    for (const row of appointmentRows) {
        docs.push(
            ctx.makeSeedDoc(
                "clinical",
                `appointments/${ctx.buildDocId("appointment", row.key)}`,
                ctx.withSeedMeta({
                    doctorId: row.doctor.uid,
                    patientId: row.patient.uid,
                    doctorName: row.doctor.displayName,
                    patientName: row.patient.displayName,
                    date: row.date,
                    startTime: row.startTime,
                    endTime: row.endTime,
                    status: row.status,
                    type: row.type,
                    notes: row.notes,
                    doctorNotes: row.doctorNotes,
                    cancellationReason: row.cancellationReason,
                    isTelemedicine: row.isTelemedicine,
                    callUrl: row.callUrl,
                    meetingId: row.meetingId,
                    cancellationFee: 150,
                    cancellationDeadlineHours: 2,
                    cancellationFeeApplied: row.cancellationFeeApplied,
                    createdAt: ctx.shiftDate(row.date, 0, -5, 0),
                    updatedAt: ctx.shiftDate(row.date, 0, -1, 30),
                }),
                [row.doctor.uid, row.patient.uid]
            )
        );
    }

    const prescriptionRows = [
        {
            key: "p1_metformin_active",
            patient: patientA,
            doctor: doctorA,
            medicationName: "Metformin",
            dosage: "500",
            unit: "mg",
            frequency: "Twice daily (BID)",
            route: "Oral",
            duration: "90 days",
            instructions: "Take after meals",
            status: "ACTIVE",
            isActive: true,
            version: 1,
            versionHistory: [],
            startDate: ctx.dateAt(-28, 7, 0),
            endDate: ctx.dateAt(62, 7, 0),
            stoppedAt: null,
            stopReason: "",
        },
        {
            key: "p1_atorvastatin_modified",
            patient: patientA,
            doctor: doctorA,
            medicationName: "Atorvastatin",
            dosage: "20",
            unit: "mg",
            frequency: "Once daily",
            route: "Oral",
            duration: "120 days",
            instructions: "Take at bedtime",
            status: "MODIFIED",
            isActive: true,
            version: 2,
            versionHistory: [
                {
                    version: 1,
                    changedFields: ["dosage", "instructions"],
                    changedBy: doctorA.uid,
                    changedAt: ctx.dateAt(-14, 11, 0),
                    previousValues: { dosage: "10", instructions: "Take once at night" },
                    changeNote: "Dose increased after lipid review",
                },
            ],
            startDate: ctx.dateAt(-34, 8, 0),
            endDate: ctx.dateAt(86, 8, 0),
            stoppedAt: null,
            stopReason: "",
        },
        {
            key: "p2_amlodipine_active",
            patient: patientB,
            doctor: doctorB,
            medicationName: "Amlodipine",
            dosage: "5",
            unit: "mg",
            frequency: "Once daily",
            route: "Oral",
            duration: "180 days",
            instructions: "Take each morning",
            status: "ACTIVE",
            isActive: true,
            version: 1,
            versionHistory: [],
            startDate: ctx.dateAt(-40, 7, 30),
            endDate: ctx.dateAt(140, 7, 30),
            stoppedAt: null,
            stopReason: "",
        },
        {
            key: "p2_aspirin_stopped",
            patient: patientB,
            doctor: doctorB,
            medicationName: "Aspirin",
            dosage: "75",
            unit: "mg",
            frequency: "Once daily",
            route: "Oral",
            duration: "60 days",
            instructions: "Take after breakfast",
            status: "STOPPED",
            isActive: false,
            version: 3,
            versionHistory: [
                {
                    version: 1,
                    changedFields: ["frequency"],
                    changedBy: doctorB.uid,
                    changedAt: ctx.dateAt(-24, 12, 0),
                    previousValues: { frequency: "Twice daily (BID)" },
                    changeNote: "Adjusted to once daily",
                },
                {
                    version: 2,
                    changedFields: ["status", "isActive"],
                    changedBy: doctorB.uid,
                    changedAt: ctx.dateAt(-6, 10, 15),
                    previousValues: { status: "MODIFIED", isActive: true },
                    changeNote: "Stopped after adverse gastric symptoms",
                },
            ],
            startDate: ctx.dateAt(-52, 8, 20),
            endDate: ctx.dateAt(-6, 8, 20),
            stoppedAt: ctx.dateAt(-6, 10, 15),
            stopReason: "Gastric irritation reported",
        },
        {
            key: "p1_amoxicillin_completed",
            patient: patientA,
            doctor: doctorB,
            medicationName: "Amoxicillin",
            dosage: "500",
            unit: "mg",
            frequency: "Three times daily (TID)",
            route: "Oral",
            duration: "7 days",
            instructions: "Complete full course",
            status: "COMPLETED",
            isActive: false,
            version: 2,
            versionHistory: [
                {
                    version: 1,
                    changedFields: ["status", "isActive"],
                    changedBy: doctorB.uid,
                    changedAt: ctx.dateAt(-12, 16, 0),
                    previousValues: { status: "ACTIVE", isActive: true },
                    changeNote: "Course completed",
                },
            ],
            startDate: ctx.dateAt(-20, 9, 0),
            endDate: ctx.dateAt(-13, 9, 0),
            stoppedAt: null,
            stopReason: "",
        },
    ];

    const prescriptionRefs = [];
    const medicineRefs = [];

    for (const row of prescriptionRows) {
        const prescriptionId = ctx.buildDocId("prescription", row.key);
        const medicineId = ctx.buildDocId("medicine", row.key);

        prescriptionRefs.push({ key: row.key, id: prescriptionId, patientId: row.patient.uid, doctorId: row.doctor.uid, medicineId, medicationName: row.medicationName });
        medicineRefs.push({ key: row.key, id: medicineId, patientId: row.patient.uid, doctorId: row.doctor.uid, prescriptionId, medicationName: row.medicationName, dosage: `${row.dosage}${row.unit}` });

        docs.push(
            ctx.makeSeedDoc(
                "clinical",
                `prescriptions/${prescriptionId}`,
                ctx.withSeedMeta({
                    patientId: row.patient.uid,
                    doctorId: row.doctor.uid,
                    doctorName: row.doctor.displayName,
                    clinicalDecisionId: ctx.buildDocId("decision", `${row.key}_decision`),
                    medicineId,
                    medicationName: row.medicationName,
                    medicationNameNormalized: row.medicationName.toLowerCase(),
                    dosage: row.dosage,
                    unit: row.unit,
                    frequency: row.frequency,
                    route: row.route,
                    duration: row.duration,
                    instructions: row.instructions,
                    status: row.status,
                    isActive: row.isActive,
                    version: row.version,
                    versionHistory: row.versionHistory,
                    startDate: row.startDate,
                    endDate: row.endDate,
                    stoppedAt: row.stoppedAt,
                    stopReason: row.stopReason,
                    digitalSignature: "c2lnbmVkX2J5X21lZGl0cmFja19kb2N0b3I=",
                    signedAt: ctx.shiftDate(row.startDate, 0, 0, 10),
                    signingCertificate: "CN=MediTrack Doctor Certificate, O=MediTrack",
                    createdAt: ctx.shiftDate(row.startDate, 0, -1, 45),
                    updatedAt: ctx.now,
                }),
                [row.patient.uid, row.doctor.uid]
            )
        );

        docs.push(
            ctx.makeSeedDoc(
                "clinical",
                `medicines/${medicineId}`,
                ctx.withSeedMeta({
                    userId: row.patient.uid,
                    name: row.medicationName,
                    dosage: row.dosage,
                    unit: row.unit,
                    instructions: row.instructions,
                    reminderTimes: reminderTimesFromFrequency(row.frequency),
                    repeatType: "DAILY",
                    color: "blue",
                    isActive: row.isActive,
                    alarmIds: [],
                    prescriptionId,
                    prescribedByDoctor: true,
                    currentQuantity: row.isActive ? 24 : 0,
                    totalQuantity: 30,
                    lowStockThreshold: 5,
                    refillReminderEnabled: true,
                    lastRefillDate: ctx.dateAt(-4, 8, 0),
                    expiryDate: ctx.dateAt(95, 0, 0),
                    expiryMonthYear: "09/2026",
                    expiryWarningDays: 30,
                    createdAt: ctx.shiftDate(row.startDate, 0, -1, 30),
                    updatedAt: ctx.now,
                }),
                [row.patient.uid, row.doctor.uid]
            )
        );
    }

    const decisionRows = [
        {
            key: "p1_prescription_plan",
            doctor: doctorA,
            patient: patientA,
            type: "PRESCRIPTION",
            priority: "HIGH",
            status: "ACTIVE",
            title: "Glycemic control optimization",
            description: "Continue metformin and monitor fasting readings weekly.",
            isPublic: true,
            prescriptions: [
                {
                    medicationName: "Metformin",
                    dosage: "500",
                    unit: "mg",
                    frequency: "Twice daily (BID)",
                    duration: "90 days",
                    route: "Oral",
                    instructions: "Take after breakfast and dinner",
                    startDate: ctx.dateAt(-28, 7, 0),
                    endDate: ctx.dateAt(62, 7, 0),
                    refills: 2,
                    isActive: true,
                    substitutionAllowed: true,
                    warnings: ["Check kidney profile every 3 months"],
                },
            ],
            followUpDate: ctx.dateAt(21, 9, 30),
            followUpInstructions: "Bring home glucose chart.",
            vitalAlerts: [],
            analysisSummary: "Fasting glucose mildly elevated but stable trend.",
            analyzedSymptoms: ["Fatigue"],
            analyzedVitals: {
                heartRateAvg: 76,
                bpSystolicAvg: 128,
                bpDiastolicAvg: 82,
                glucoseAvg: 146,
                overallAssessment: "Moderate glycemic risk",
                riskLevel: "moderate",
                concerns: ["Morning glucose spikes"],
                recommendations: ["Reduce refined carbs after dinner"],
            },
            patientAcknowledged: true,
            acknowledgedAt: ctx.dateAt(-2, 20, 15),
            createdAt: ctx.dateAt(-27, 10, 0),
        },
        {
            key: "p2_followup_visit",
            doctor: doctorB,
            patient: patientB,
            type: "FOLLOW_UP",
            priority: "NORMAL",
            status: "ACTIVE",
            title: "Blood pressure follow-up",
            description: "Track BP morning and evening for two weeks.",
            isPublic: true,
            prescriptions: [],
            followUpDate: ctx.dateAt(14, 11, 0),
            followUpInstructions: "Continue amlodipine and salt restriction.",
            vitalAlerts: [],
            analysisSummary: "BP has improved after adherence correction.",
            analyzedSymptoms: ["Occasional dizziness"],
            analyzedVitals: {
                heartRateAvg: 74,
                bpSystolicAvg: 134,
                bpDiastolicAvg: 86,
                overallAssessment: "Improving control",
                riskLevel: "low",
                concerns: ["Mild evening BP rise"],
                recommendations: ["Keep hydration steady"],
            },
            patientAcknowledged: false,
            acknowledgedAt: null,
            createdAt: ctx.dateAt(-5, 9, 45),
        },
        {
            key: "p1_vital_alert",
            doctor: doctorB,
            patient: patientA,
            type: "VITAL_ALERT",
            priority: "CRITICAL",
            status: "ACTIVE",
            title: "High blood pressure spike",
            description: "Systolic pressure crossed urgent threshold twice.",
            isPublic: true,
            prescriptions: [],
            followUpDate: null,
            followUpInstructions: "",
            vitalAlerts: [
                {
                    vitalType: "BLOOD_PRESSURE_SYSTOLIC",
                    condition: "ABOVE",
                    threshold: 170,
                    message: "Immediate consultation recommended",
                    severity: "CRITICAL",
                },
            ],
            analysisSummary: "Acute elevation likely linked to missed medication.",
            analyzedSymptoms: ["Headache", "Dizziness"],
            analyzedVitals: {
                bpSystolicAvg: 168,
                bpDiastolicAvg: 102,
                overallAssessment: "High cardiovascular risk",
                riskLevel: "high",
                concerns: ["Potential hypertensive urgency"],
                recommendations: ["Seek in-person care if persistent"],
            },
            patientAcknowledged: false,
            acknowledgedAt: null,
            createdAt: ctx.dateAt(-1, 18, 30),
        },
        {
            key: "p2_private_note",
            doctor: doctorA,
            patient: patientB,
            type: "RECOMMENDATION",
            priority: "LOW",
            status: "PENDING_REVIEW",
            title: "Private review draft",
            description: "Internal diagnostic note before publication.",
            isPublic: false,
            prescriptions: [],
            followUpDate: null,
            followUpInstructions: "",
            vitalAlerts: [],
            analysisSummary: "Awaiting additional lab values.",
            analyzedSymptoms: [],
            analyzedVitals: null,
            patientAcknowledged: false,
            acknowledgedAt: null,
            createdAt: ctx.dateAt(-3, 15, 0),
        },
    ];

    for (const row of decisionRows) {
        const decisionId = ctx.buildDocId("decision", row.key);
        docs.push(
            ctx.makeSeedDoc(
                "clinical",
                `clinicalDecisions/${decisionId}`,
                ctx.withSeedMeta({
                    doctorId: row.doctor.uid,
                    doctorName: row.doctor.displayName,
                    patientId: row.patient.uid,
                    patientName: row.patient.displayName,
                    type: row.type,
                    priority: row.priority,
                    status: row.status,
                    title: row.title,
                    description: row.description,
                    isPublic: row.isPublic,
                    prescriptions: row.prescriptions,
                    followUpDate: row.followUpDate,
                    followUpInstructions: row.followUpInstructions,
                    vitalAlerts: row.vitalAlerts,
                    analysisSummary: row.analysisSummary,
                    analyzedSymptoms: row.analyzedSymptoms,
                    analyzedVitals: row.analyzedVitals,
                    patientAcknowledged: row.patientAcknowledged,
                    acknowledgedAt: row.acknowledgedAt,
                    createdAt: row.createdAt,
                    updatedAt: ctx.now,
                }),
                [row.doctor.uid, row.patient.uid]
            )
        );
    }

    const doctorNotesRows = [
        {
            key: "note_p1_general",
            doctor: doctorA,
            patient: patientA,
            note: "Patient reports better sleep after medication timing change.",
            category: "GENERAL",
            isPrivate: false,
            createdAt: ctx.dateAt(-9, 13, 0),
        },
        {
            key: "note_p1_warning",
            doctor: doctorB,
            patient: patientA,
            note: "Observe for persistent headache episodes this week.",
            category: "WARNING",
            isPrivate: false,
            createdAt: ctx.dateAt(-2, 19, 10),
        },
        {
            key: "note_p2_diagnosis",
            doctor: doctorB,
            patient: patientB,
            note: "Early stage hypertension with positive response to therapy.",
            category: "DIAGNOSIS",
            isPrivate: false,
            createdAt: ctx.dateAt(-11, 10, 20),
        },
        {
            key: "note_p2_private",
            doctor: doctorA,
            patient: patientB,
            note: "Internal: verify lab consistency before changing regimen.",
            category: "OBSERVATION",
            isPrivate: true,
            createdAt: ctx.dateAt(-4, 16, 40),
        },
    ];

    for (const row of doctorNotesRows) {
        docs.push(
            ctx.makeSeedDoc(
                "clinical",
                `doctorNotes/${ctx.buildDocId("doctornote", row.key)}`,
                ctx.withSeedMeta({
                    doctorId: row.doctor.uid,
                    doctorName: row.doctor.displayName,
                    patientId: row.patient.uid,
                    patientName: row.patient.displayName,
                    note: row.note,
                    category: row.category,
                    isPrivate: row.isPrivate,
                    createdAt: row.createdAt,
                    updatedAt: ctx.now,
                }),
                [row.doctor.uid, row.patient.uid]
            )
        );
    }

    ctx.refs.prescriptions = prescriptionRefs;
    ctx.refs.prescriptionMedicineRefs = medicineRefs;

    return docs;
}

module.exports = {
    buildClinicalDocs,
};
