#!/usr/bin/env node

/**
 * Backfill pharmacy visibility fields based on owner user account status.
 *
 * Default mode is dry-run.
 *
 * Usage:
 *   node scripts/backfill-pharmacy-visibility.js
 *   node scripts/backfill-pharmacy-visibility.js --apply
 *   node scripts/backfill-pharmacy-visibility.js --apply --verbose
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

function asString(value) {
    return typeof value === "string" ? value : "";
}

function mapUserStatusToVisibility(status) {
    switch (status) {
        case "APPROVED":
            return { verificationStatus: "APPROVED", isActive: true };
        case "REJECTED":
        case "SUSPENDED":
            return { verificationStatus: "REJECTED", isActive: false };
        case "PENDING":
        default:
            return { verificationStatus: "PENDING", isActive: true };
    }
}

const admin = loadFirebaseAdmin();
const args = new Set(process.argv.slice(2));
const APPLY = args.has("--apply");
const VERBOSE = args.has("--verbose");

const serviceAccountPath = path.join(__dirname, "..", "serviceAccountKey.json");
if (!fs.existsSync(serviceAccountPath)) {
    console.error("serviceAccountKey.json not found:", serviceAccountPath);
    process.exit(1);
}

const serviceAccount = require(serviceAccountPath);
admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId: serviceAccount.project_id,
});

const db = admin.firestore();

async function main() {
    const stats = {
        pharmacyUsers: 0,
        pharmaciesScanned: 0,
        missingOwnerId: 0,
        ownerUserNotFound: 0,
        unchanged: 0,
        wouldUpdate: 0,
        updated: 0,
        failed: 0,
    };

    console.log(`\nBackfill mode: ${APPLY ? "APPLY" : "DRY-RUN"}`);

    const userSnapshot = await db.collection("users")
        .where("role", "==", "PHARMACY")
        .get();

    stats.pharmacyUsers = userSnapshot.size;

    const statusByOwnerId = new Map();
    userSnapshot.docs.forEach((doc) => {
        const status = asString(doc.get("status")).trim().toUpperCase() || "PENDING";
        statusByOwnerId.set(doc.id, status);
    });

    const pharmaciesSnapshot = await db.collection("pharmacies").get();
    stats.pharmaciesScanned = pharmaciesSnapshot.size;

    let batch = db.batch();
    let batchOps = 0;

    for (const doc of pharmaciesSnapshot.docs) {
        try {
            const data = doc.data() || {};
            const ownerId = asString(data.ownerId).trim();

            if (!ownerId) {
                stats.missingOwnerId += 1;
                if (VERBOSE) {
                    console.log(`  skip ${doc.ref.path}: missing ownerId`);
                }
                continue;
            }

            const ownerStatus = statusByOwnerId.get(ownerId);
            if (!ownerStatus) {
                stats.ownerUserNotFound += 1;
                if (VERBOSE) {
                    console.log(`  skip ${doc.ref.path}: owner user not found (${ownerId})`);
                }
                continue;
            }

            const target = mapUserStatusToVisibility(ownerStatus);

            const currentVerification = asString(data.verificationStatus).trim().toUpperCase() || "PENDING";
            const currentIsActive = typeof data.isActive === "boolean" ? data.isActive : true;

            const needsUpdate =
                currentVerification !== target.verificationStatus ||
                currentIsActive !== target.isActive;

            if (!needsUpdate) {
                stats.unchanged += 1;
                continue;
            }

            stats.wouldUpdate += 1;

            if (!APPLY) {
                if (VERBOSE) {
                    console.log(
                        `  [DRY] ${doc.ref.path}: ` +
                        `verificationStatus ${currentVerification} -> ${target.verificationStatus}, ` +
                        `isActive ${currentIsActive} -> ${target.isActive}`
                    );
                }
                continue;
            }

            batch.update(doc.ref, {
                verificationStatus: target.verificationStatus,
                isActive: target.isActive,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            });
            batchOps += 1;
            stats.updated += 1;

            if (batchOps >= 400) {
                await batch.commit();
                batch = db.batch();
                batchOps = 0;
            }

            if (VERBOSE) {
                console.log(
                    `  updated ${doc.ref.path}: ` +
                    `verificationStatus -> ${target.verificationStatus}, isActive -> ${target.isActive}`
                );
            }
        } catch (error) {
            stats.failed += 1;
            console.error(`  failed ${doc.ref.path}: ${error.message || error}`);
        }
    }

    if (APPLY && batchOps > 0) {
        await batch.commit();
    }

    console.log("\nBackfill summary");
    console.log(`- Pharmacy users scanned: ${stats.pharmacyUsers}`);
    console.log(`- Pharmacies scanned: ${stats.pharmaciesScanned}`);
    console.log(`- Missing ownerId: ${stats.missingOwnerId}`);
    console.log(`- Owner user not found: ${stats.ownerUserNotFound}`);
    console.log(`- Unchanged: ${stats.unchanged}`);
    console.log(`- ${APPLY ? "Updated" : "Would update"}: ${APPLY ? stats.updated : stats.wouldUpdate}`);
    console.log(`- Failed: ${stats.failed}`);
    console.log("\nDone\n");
}

main().catch((error) => {
    console.error("Fatal error:", error);
    process.exit(1);
});
