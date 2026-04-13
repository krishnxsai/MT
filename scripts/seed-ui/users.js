function buildUserDocs(ctx) {
    const {
        admin,
        doctorA,
        doctorB,
        patientA,
        patientB,
        pharmacyA,
        pharmacyB,
        pharmacyC,
    } = ctx.identities;

    const assignedDoctors = [doctorA.uid, doctorB.uid];
    const assignedDoctorNames = {
        [doctorA.uid]: doctorA.displayName,
        [doctorB.uid]: doctorB.displayName,
    };

    const createdBase = ctx.dateAt(-120, 9, 0);

    const userRows = [
        {
            user: admin,
            role: "ADMIN",
            status: "APPROVED",
            assignedDoctors: [],
            assignedDoctorNames: {},
            verifiedBy: "",
            verifiedAt: null,
            rejectionReason: "",
            licenseUrl: "",
            createdAt: ctx.shiftDate(createdBase, 0),
        },
        {
            user: doctorA,
            role: "DOCTOR",
            status: "APPROVED",
            assignedDoctors: [],
            assignedDoctorNames: {},
            verifiedBy: admin.uid,
            verifiedAt: ctx.dateAt(-70, 12, 30),
            rejectionReason: "",
            licenseUrl: "https://example.com/licenses/doctor-a.pdf",
            createdAt: ctx.shiftDate(createdBase, 4),
        },
        {
            user: doctorB,
            role: "DOCTOR",
            status: "APPROVED",
            assignedDoctors: [],
            assignedDoctorNames: {},
            verifiedBy: admin.uid,
            verifiedAt: ctx.dateAt(-68, 14, 0),
            rejectionReason: "",
            licenseUrl: "https://example.com/licenses/doctor-b.pdf",
            createdAt: ctx.shiftDate(createdBase, 6),
        },
        {
            user: patientA,
            role: "PATIENT",
            status: "APPROVED",
            assignedDoctors,
            assignedDoctorNames,
            verifiedBy: "",
            verifiedAt: null,
            rejectionReason: "",
            licenseUrl: "",
            createdAt: ctx.shiftDate(createdBase, 10),
        },
        {
            user: patientB,
            role: "PATIENT",
            status: "APPROVED",
            assignedDoctors,
            assignedDoctorNames,
            verifiedBy: "",
            verifiedAt: null,
            rejectionReason: "",
            licenseUrl: "",
            createdAt: ctx.shiftDate(createdBase, 12),
        },
        {
            user: pharmacyA,
            role: "PHARMACY",
            status: "APPROVED",
            assignedDoctors: [],
            assignedDoctorNames: {},
            verifiedBy: admin.uid,
            verifiedAt: ctx.dateAt(-55, 11, 0),
            rejectionReason: "",
            licenseUrl: "https://example.com/licenses/pharmacy-a.pdf",
            createdAt: ctx.shiftDate(createdBase, 16),
        },
        {
            user: pharmacyB,
            role: "PHARMACY",
            status: "PENDING",
            assignedDoctors: [],
            assignedDoctorNames: {},
            verifiedBy: "",
            verifiedAt: null,
            rejectionReason: "",
            licenseUrl: "https://example.com/licenses/pharmacy-b.pdf",
            createdAt: ctx.shiftDate(createdBase, 18),
        },
        {
            user: pharmacyC,
            role: "PHARMACY",
            status: "REJECTED",
            assignedDoctors: [],
            assignedDoctorNames: {},
            verifiedBy: admin.uid,
            verifiedAt: ctx.dateAt(-20, 10, 30),
            rejectionReason: "License mismatch in submission",
            licenseUrl: "https://example.com/licenses/pharmacy-c.pdf",
            createdAt: ctx.shiftDate(createdBase, 20),
        },
    ];

    const docs = userRows.map((row, index) => {
        const userDoc = ctx.withSeedMeta({
            email: row.user.email,
            displayName: row.user.displayName,
            profileImageUrl: "",
            role: row.role,
            status: row.status,
            assignedDoctors: row.assignedDoctors,
            assignedDoctorNames: row.assignedDoctorNames,
            phoneNumber: row.user.phoneNumber,
            fcmToken: `seed_fcm_${index + 1}`,
            licenseUrl: row.licenseUrl,
            verifiedBy: row.verifiedBy,
            verifiedAt: row.verifiedAt,
            rejectionReason: row.rejectionReason,
            createdAt: row.createdAt,
            updatedAt: ctx.now,
        });

        return ctx.makeSeedDoc(
            "users",
            `users/${row.user.uid}`,
            userDoc,
            [row.user.uid, row.verifiedBy]
        );
    });

    ctx.refs.userStatuses = {
        [admin.uid]: "APPROVED",
        [doctorA.uid]: "APPROVED",
        [doctorB.uid]: "APPROVED",
        [patientA.uid]: "APPROVED",
        [patientB.uid]: "APPROVED",
        [pharmacyA.uid]: "APPROVED",
        [pharmacyB.uid]: "PENDING",
        [pharmacyC.uid]: "REJECTED",
    };

    return docs;
}

module.exports = {
    buildUserDocs,
};
