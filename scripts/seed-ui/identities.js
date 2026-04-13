const fs = require("fs");
const path = require("path");

const REQUIRED_COUNTS = {
    admins: 1,
    doctors: 2,
    patients: 2,
    pharmacies: 3,
};

const PLACEHOLDER_PREFIX = "REPLACE_";

function readJsonFile(filePath) {
    const raw = fs.readFileSync(filePath, "utf8");
    return JSON.parse(raw);
}

function resolveIdentitySource(identitiesFile) {
    if (identitiesFile) {
        const absolute = path.isAbsolute(identitiesFile)
            ? identitiesFile
            : path.resolve(process.cwd(), identitiesFile);
        if (!fs.existsSync(absolute)) {
            throw new Error(`Identities file not found: ${absolute}`);
        }
        return { source: `file:${absolute}`, data: readJsonFile(absolute) };
    }

    if (process.env.SEED_IDENTITIES_JSON) {
        try {
            const parsed = JSON.parse(process.env.SEED_IDENTITIES_JSON);
            return { source: "env:SEED_IDENTITIES_JSON", data: parsed };
        } catch (error) {
            throw new Error(`Invalid SEED_IDENTITIES_JSON: ${error.message}`);
        }
    }

    const localPath = path.resolve(__dirname, "identities.local.json");
    if (fs.existsSync(localPath)) {
        return { source: `file:${localPath}`, data: readJsonFile(localPath) };
    }

    throw new Error(
        "No identity source found. Provide --identities-file, set SEED_IDENTITIES_JSON, or create scripts/seed-ui/identities.local.json from identities.sample.json"
    );
}

function normalizeUser(rawUser, role, fallbackLabel) {
    if (!rawUser || typeof rawUser !== "object") {
        throw new Error(`Invalid identity entry for ${fallbackLabel}`);
    }

    const uid = String(rawUser.uid || "").trim();
    if (!uid) {
        throw new Error(`Missing uid for ${fallbackLabel}`);
    }
    if (uid.startsWith(PLACEHOLDER_PREFIX)) {
        throw new Error(`Placeholder uid found for ${fallbackLabel}: ${uid}`);
    }

    return {
        uid,
        role,
        email: String(rawUser.email || "").trim(),
        displayName: String(rawUser.displayName || fallbackLabel).trim(),
        phoneNumber: String(rawUser.phoneNumber || "").trim(),
    };
}

function ensureArray(value, key) {
    if (!Array.isArray(value)) {
        throw new Error(`Expected array for '${key}'`);
    }
    return value;
}

function validateCounts(identities) {
    if (identities.admins.length !== REQUIRED_COUNTS.admins) {
        throw new Error(`Expected exactly ${REQUIRED_COUNTS.admins} admin account`);
    }
    if (identities.doctors.length !== REQUIRED_COUNTS.doctors) {
        throw new Error(`Expected exactly ${REQUIRED_COUNTS.doctors} doctor accounts`);
    }
    if (identities.patients.length !== REQUIRED_COUNTS.patients) {
        throw new Error(`Expected exactly ${REQUIRED_COUNTS.patients} patient accounts`);
    }
    if (identities.pharmacies.length !== REQUIRED_COUNTS.pharmacies) {
        throw new Error(`Expected exactly ${REQUIRED_COUNTS.pharmacies} pharmacy accounts`);
    }
}

function validateUidUniqueness(allUsers) {
    const seen = new Set();
    for (const user of allUsers) {
        if (seen.has(user.uid)) {
            throw new Error(`Duplicate UID detected: ${user.uid}`);
        }
        seen.add(user.uid);
    }
}

function buildIdentityIndex(identities) {
    const admin = identities.admins[0];
    const doctorA = identities.doctors[0];
    const doctorB = identities.doctors[1];
    const patientA = identities.patients[0];
    const patientB = identities.patients[1];
    const pharmacyA = identities.pharmacies[0];
    const pharmacyB = identities.pharmacies[1];
    const pharmacyC = identities.pharmacies[2];

    const allUsers = [
        admin,
        doctorA,
        doctorB,
        patientA,
        patientB,
        pharmacyA,
        pharmacyB,
        pharmacyC,
    ];

    const byUid = new Map(allUsers.map((user) => [user.uid, user]));

    return {
        admin,
        doctorA,
        doctorB,
        patientA,
        patientB,
        pharmacyA,
        pharmacyB,
        pharmacyC,
        allUsers,
        allUserIds: allUsers.map((user) => user.uid),
        byUid,
    };
}

function loadIdentities(options = {}) {
    const { source, data } = resolveIdentitySource(options.identitiesFile);

    const admins = [normalizeUser(data.admin, "ADMIN", "System Admin")];
    const doctors = ensureArray(data.doctors, "doctors").map((entry, index) =>
        normalizeUser(entry, "DOCTOR", `Doctor ${index + 1}`)
    );
    const patients = ensureArray(data.patients, "patients").map((entry, index) =>
        normalizeUser(entry, "PATIENT", `Patient ${index + 1}`)
    );
    const pharmacies = ensureArray(data.pharmacies, "pharmacies").map((entry, index) =>
        normalizeUser(entry, "PHARMACY", `Pharmacy ${index + 1}`)
    );

    const identities = { admins, doctors, patients, pharmacies };
    validateCounts(identities);

    const allUsers = [...admins, ...doctors, ...patients, ...pharmacies];
    validateUidUniqueness(allUsers);

    const index = buildIdentityIndex(identities);

    return {
        source,
        ...identities,
        ...index,
    };
}

module.exports = {
    loadIdentities,
};
