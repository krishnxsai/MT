const fs = require("fs");
const path = require("path");

const DEFAULT_SEED_SOURCE = "ui-workflow-seed";
const DEFAULT_SEED_VERSION = "2026-04-ui-workflow-v1";
const BATCH_LIMIT = 350;

function loadFirebaseAdmin(workspaceRoot) {
    const direct = "firebase-admin";
    const fallback = path.join(workspaceRoot, "functions", "node_modules", "firebase-admin");

    try {
        return require(direct);
    } catch (_error) {
        return require(fallback);
    }
}

function parseArgs(argv) {
    const flags = {
        apply: false,
        verbose: false,
        pruneSeedData: false,
        target: "emulator",
        identitiesFile: "",
        seedVersion: DEFAULT_SEED_VERSION,
        seedSource: DEFAULT_SEED_SOURCE,
        allowCloudWrite: false,
    };

    for (let index = 0; index < argv.length; index += 1) {
        const token = argv[index];
        if (!token.startsWith("--")) {
            continue;
        }

        const [rawKey, inlineValue] = token.replace(/^--/, "").split("=");
        const key = rawKey.trim();
        const nextValue = inlineValue !== undefined ? inlineValue : argv[index + 1];

        switch (key) {
            case "apply":
                flags.apply = true;
                break;
            case "verbose":
                flags.verbose = true;
                break;
            case "prune-seed-data":
                flags.pruneSeedData = true;
                break;
            case "allow-cloud-write":
                flags.allowCloudWrite = true;
                break;
            case "target":
                flags.target = normalizeTarget(nextValue);
                if (inlineValue === undefined) {
                    index += 1;
                }
                break;
            case "identities-file":
                flags.identitiesFile = nextValue || "";
                if (inlineValue === undefined) {
                    index += 1;
                }
                break;
            case "seed-version":
                flags.seedVersion = String(nextValue || DEFAULT_SEED_VERSION).trim();
                if (inlineValue === undefined) {
                    index += 1;
                }
                break;
            case "seed-source":
                flags.seedSource = String(nextValue || DEFAULT_SEED_SOURCE).trim();
                if (inlineValue === undefined) {
                    index += 1;
                }
                break;
            default:
                throw new Error(`Unknown argument: --${key}`);
        }
    }

    if (!flags.seedVersion) {
        throw new Error("--seed-version cannot be empty");
    }
    if (!flags.seedSource) {
        throw new Error("--seed-source cannot be empty");
    }

    return flags;
}

function normalizeTarget(target) {
    const normalized = String(target || "emulator").trim().toLowerCase();
    if (normalized !== "emulator" && normalized !== "cloud") {
        throw new Error("--target must be 'emulator' or 'cloud'");
    }
    return normalized;
}

function configureTargetEnvironment(flags) {
    if (flags.target === "emulator" && !process.env.FIRESTORE_EMULATOR_HOST) {
        process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
    }

    if (flags.target === "cloud" && flags.apply && !flags.allowCloudWrite) {
        throw new Error(
            "Cloud apply blocked. Re-run with --allow-cloud-write to confirm intentional writes to cloud Firestore."
        );
    }
}

function resolveServiceAccountPath(workspaceRoot) {
    const serviceAccountPath = path.join(workspaceRoot, "serviceAccountKey.json");
    if (!fs.existsSync(serviceAccountPath)) {
        throw new Error(`serviceAccountKey.json not found: ${serviceAccountPath}`);
    }
    return serviceAccountPath;
}

function sanitizeFragment(value) {
    return String(value)
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "_")
        .replace(/^_+|_+$/g, "");
}

function buildDocId(feature, key) {
    const featurePart = sanitizeFragment(feature);
    const keyPart = sanitizeFragment(key);
    if (!featurePart || !keyPart) {
        throw new Error(`Invalid deterministic id parts feature='${feature}' key='${key}'`);
    }
    return `seed_${featurePart}_${keyPart}`;
}

function shiftDate(baseDate, days = 0, hours = 0, minutes = 0) {
    const next = new Date(baseDate.getTime());
    next.setDate(next.getDate() + days);
    next.setHours(next.getHours() + hours);
    next.setMinutes(next.getMinutes() + minutes);
    return next;
}

function dateAt(baseDate, daysOffset, hour, minute = 0) {
    const next = new Date(baseDate.getTime());
    next.setDate(next.getDate() + daysOffset);
    next.setHours(hour, minute, 0, 0);
    return next;
}

function createSeedContext({ admin, identities, seedSource, seedVersion }) {
    const now = new Date();
    const userIdSet = new Set(identities.allUserIds);

    return {
        admin,
        identities,
        now,
        seedSource,
        seedVersion,
        userIdSet,
        refs: {},
        buildDocId,
        shiftDate: (baseDate, days = 0, hours = 0, minutes = 0) => shiftDate(baseDate, days, hours, minutes),
        dateAt: (daysOffset, hour, minute = 0) => dateAt(now, daysOffset, hour, minute),
        geoPoint: (lat, lng) => new admin.firestore.GeoPoint(lat, lng),
        withSeedMeta: (data) => ({
            ...data,
            seedSource,
            seedVersion,
            seedUpdatedAt: now,
        }),
        makeSeedDoc: (feature, pathValue, data, userRefs = []) => ({
            feature,
            path: pathValue,
            data,
            userRefs,
        }),
    };
}

function ensureUniqueDocPaths(seedDocs) {
    const seen = new Set();
    for (const doc of seedDocs) {
        if (seen.has(doc.path)) {
            throw new Error(`Duplicate seed doc path detected: ${doc.path}`);
        }
        seen.add(doc.path);
    }
}

function validateUserReferences(seedDocs, allowedUserIds) {
    for (const doc of seedDocs) {
        const refs = Array.isArray(doc.userRefs) ? doc.userRefs : [];
        for (const userId of refs) {
            if (!userId) {
                continue;
            }
            if (!allowedUserIds.has(userId)) {
                throw new Error(`Unknown user UID '${userId}' referenced by ${doc.path}`);
            }
        }
    }
}

function initFeatureSummary(seedDocs) {
    const summary = {};
    for (const doc of seedDocs) {
        if (!summary[doc.feature]) {
            summary[doc.feature] = {
                planned: 0,
                wouldWrite: 0,
                created: 0,
                updated: 0,
                failed: 0,
            };
        }
        summary[doc.feature].planned += 1;
    }
    return summary;
}

async function executeSeedDocs(db, seedDocs, options) {
    ensureUniqueDocPaths(seedDocs);

    const summaryByFeature = initFeatureSummary(seedDocs);
    const totals = {
        planned: seedDocs.length,
        wouldWrite: 0,
        created: 0,
        updated: 0,
        failed: 0,
    };

    if (!options.apply) {
        for (const doc of seedDocs) {
            summaryByFeature[doc.feature].wouldWrite += 1;
            totals.wouldWrite += 1;
            if (options.verbose) {
                console.log(`[DRY] ${doc.path}`);
            }
        }
        return { totals, byFeature: summaryByFeature };
    }

    let batch = db.batch();
    let pending = [];

    async function commitBatch() {
        if (pending.length === 0) {
            return;
        }

        try {
            await batch.commit();
            if (options.verbose) {
                console.log(`Committed batch (${pending.length} docs)`);
            }
        } catch (error) {
            for (const doc of pending) {
                summaryByFeature[doc.feature].failed += 1;
                totals.failed += 1;
            }
            throw error;
        } finally {
            batch = db.batch();
            pending = [];
        }
    }

    for (const doc of seedDocs) {
        const ref = db.doc(doc.path);
        const existing = await ref.get();

        if (existing.exists) {
            summaryByFeature[doc.feature].updated += 1;
            totals.updated += 1;
        } else {
            summaryByFeature[doc.feature].created += 1;
            totals.created += 1;
        }

        batch.set(ref, doc.data, { merge: true });
        pending.push(doc);

        if (options.verbose) {
            console.log(`[WRITE] ${doc.path}`);
        }

        if (pending.length >= BATCH_LIMIT) {
            await commitBatch();
        }
    }

    await commitBatch();

    return { totals, byFeature: summaryByFeature };
}

async function deleteQueryInBatches(query, apply, verbose) {
    let total = 0;

    while (true) {
        const snapshot = await query.limit(BATCH_LIMIT).get();
        if (snapshot.empty) {
            break;
        }

        total += snapshot.size;

        if (apply) {
            const batch = snapshot.docs[0].ref.firestore.batch();
            snapshot.docs.forEach((doc) => batch.delete(doc.ref));
            await batch.commit();
        }

        if (verbose) {
            console.log(`${apply ? "[PRUNE]" : "[PRUNE-DRY]"} ${snapshot.size} docs`);
        }

        if (snapshot.size < BATCH_LIMIT) {
            break;
        }
    }

    return total;
}

async function pruneSeededData(db, options) {
    const collections = [
        "users",
        "pharmacies",
        "pharmacyInventory",
        "doctorAvailability",
        "appointments",
        "prescriptions",
        "medicines",
        "clinicalDecisions",
        "doctorNotes",
        "healthLogs",
        "medicineIntakes",
        "refillAlerts",
        "riskScores",
        "conversations",
        "messages",
        "orders",
        "payments",
        "transactions",
        "cancellationRequests",
        "deliveryTracking",
        "notificationPreferences",
        "carts",
        "auditLogs",
        "notificationLogs",
    ];

    const byCollection = {};
    let total = 0;

    for (const collectionName of collections) {
        const query = db
            .collection(collectionName)
            .where("seedSource", "==", options.seedSource);

        const count = await deleteQueryInBatches(query, options.apply, options.verbose);
        byCollection[collectionName] = count;
        total += count;
    }

    try {
        const pointsQuery = db
            .collectionGroup("points")
            .where("seedSource", "==", options.seedSource);
        const pointsCount = await deleteQueryInBatches(pointsQuery, options.apply, options.verbose);
        byCollection["deliveryTracking.points"] = pointsCount;
        total += pointsCount;
    } catch (error) {
        byCollection["deliveryTracking.points"] = 0;
        if (options.verbose) {
            console.warn(`Skipping points collectionGroup prune: ${error.message}`);
        }
    }

    return { total, byCollection };
}

module.exports = {
    DEFAULT_SEED_SOURCE,
    DEFAULT_SEED_VERSION,
    buildDocId,
    configureTargetEnvironment,
    createSeedContext,
    executeSeedDocs,
    loadFirebaseAdmin,
    parseArgs,
    pruneSeededData,
    resolveServiceAccountPath,
    validateUserReferences,
};
