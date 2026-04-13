function buildCommunicationDocs(ctx) {
    const { doctorA, doctorB, patientA, patientB } = ctx.identities;

    const conversationRows = [
        {
            key: "d1_p1",
            doctor: doctorA,
            patient: patientA,
            unreadCountDoctor: 0,
            unreadCountPatient: 2,
            baseDayOffset: -3,
        },
        {
            key: "d1_p2",
            doctor: doctorA,
            patient: patientB,
            unreadCountDoctor: 1,
            unreadCountPatient: 0,
            baseDayOffset: -2,
        },
        {
            key: "d2_p2",
            doctor: doctorB,
            patient: patientB,
            unreadCountDoctor: 0,
            unreadCountPatient: 0,
            baseDayOffset: -1,
        },
    ];

    const docs = [];
    const conversationRefs = [];

    for (const row of conversationRows) {
        const conversationId = ctx.buildDocId("conversation", row.key);
        const baseTime = ctx.dateAt(row.baseDayOffset, 9, 0);

        const messages = [
            {
                sender: row.patient,
                senderRole: "PATIENT",
                text: "Hello doctor, sharing my latest readings.",
                offsetMinutes: 0,
                isRead: true,
            },
            {
                sender: row.doctor,
                senderRole: "DOCTOR",
                text: "Received. Any unusual symptoms since yesterday?",
                offsetMinutes: 8,
                isRead: true,
            },
            {
                sender: row.patient,
                senderRole: "PATIENT",
                text: "Mild dizziness in the evening.",
                offsetMinutes: 15,
                isRead: true,
            },
            {
                sender: row.doctor,
                senderRole: "DOCTOR",
                text: "Please log BP twice today and keep hydrated.",
                offsetMinutes: 22,
                isRead: row.key !== "d1_p1",
            },
            {
                sender: row.patient,
                senderRole: "PATIENT",
                text: "Noted. I will update after lunch.",
                offsetMinutes: 30,
                isRead: row.key !== "d1_p2",
            },
            {
                sender: row.doctor,
                senderRole: "DOCTOR",
                text: "System reminder: follow-up window opens tomorrow.",
                offsetMinutes: 36,
                isRead: true,
                type: "SYSTEM",
            },
        ];

        const lastMessage = messages[messages.length - 1];
        const lastTimestamp = ctx.shiftDate(baseTime, 0, 0, lastMessage.offsetMinutes);

        docs.push(
            ctx.makeSeedDoc(
                "communication",
                `conversations/${conversationId}`,
                ctx.withSeedMeta({
                    doctorId: row.doctor.uid,
                    patientId: row.patient.uid,
                    doctorName: row.doctor.displayName,
                    patientName: row.patient.displayName,
                    doctorProfileUrl: "",
                    patientProfileUrl: "",
                    participantIds: [row.doctor.uid, row.patient.uid],
                    lastMessage: lastMessage.text,
                    lastMessageSenderId: lastMessage.sender.uid,
                    lastMessageTimestamp: lastTimestamp,
                    unreadCountDoctor: row.unreadCountDoctor,
                    unreadCountPatient: row.unreadCountPatient,
                    createdAt: baseTime,
                    updatedAt: lastTimestamp,
                }),
                [row.doctor.uid, row.patient.uid, lastMessage.sender.uid]
            )
        );

        messages.forEach((message, index) => {
            const timestamp = ctx.shiftDate(baseTime, 0, 0, message.offsetMinutes);
            const isRead = message.isRead;
            const readAt = isRead ? ctx.shiftDate(timestamp, 0, 0, 1) : null;

            docs.push(
                ctx.makeSeedDoc(
                    "communication",
                    `messages/${ctx.buildDocId("message", `${row.key}_${index + 1}`)}`,
                    ctx.withSeedMeta({
                        conversationId,
                        senderId: message.sender.uid,
                        senderName: message.sender.displayName,
                        senderRole: message.senderRole,
                        text: message.text,
                        type: message.type || "TEXT",
                        isRead,
                        readAt,
                        timestamp,
                    }),
                    [message.sender.uid, row.doctor.uid, row.patient.uid]
                )
            );
        });

        conversationRefs.push({
            id: conversationId,
            doctorId: row.doctor.uid,
            patientId: row.patient.uid,
        });
    }

    ctx.refs.conversations = conversationRefs;

    return docs;
}

module.exports = {
    buildCommunicationDocs,
};
