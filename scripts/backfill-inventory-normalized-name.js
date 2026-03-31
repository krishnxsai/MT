#!/usr/bin/env node

/**
 * Backfill medicineNameNormalized for pharmacyInventory documents.
 *
 * Why:
 * - Server-side stock sync now resolves inventory by normalized medicine name first.
 * - Older inventory docs may only have medicineName.
 *
 * Usage:
 *   node scripts/backfill-inventory-normalized-name.js           # dry-run
 *   node scripts/backfill-inventory-normalized-name.js --apply   # write changes
 *   node scripts/backfill-inventory-normalized-name.js --apply --verbose
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

function normalizeMedicineName(value) {
    return asString(value).trim().toLowerCase();
}

const admin = loadFirebaseAdmin();
const args = new Set(process.argv.slice(2));
const APPLY = args.has("--apply");
const VERBOSE = args.has("--verbose");

const serviceAccountPath = path.join(__dirname, "..", "serviceAccountKey.json");
if (!fs.existsSync(serviceAccountPath)) {
    console.error("❌ serviceAccountKey.json not found:", serviceAccountPath);
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
        totalDocs: 0,
        missingMedicineName: 0,
        alreadyNormalized: 0,
        wouldWrite: 0,
        written: 0,
        failed: 0,
    };

    console.log(`\n🔎 Backfill mode: ${APPLY ? "APPLY" : "DRY-RUN"}`);

    const snapshot = await db.collection("pharmacyInventory").get();
    stats.totalDocs = snapshot.size;

    let batch = db.batch();
    let batchOps = 0;

    for (const doc of snapshot.docs) {
        try {
            const data = doc.data() || {};
            const medicineName = asString(data.medicineName).trim();

            if (!medicineName) {
                stats.missingMedicineName += 1;
                if (VERBOSE) {
                    console.log(`  ⚠️  skip ${doc.ref.path}: missing medicineName`);
                }
                continue;
            }

            const expectedNormalized = normalizeMedicineName(medicineName);
            const currentNormalizedRaw = asString(data.medicineNameNormalized).trim();
            const currentNormalized = normalizeMedicineName(currentNormalizedRaw);

            if (currentNormalizedRaw && currentNormalized === expectedNormalized) {
                stats.alreadyNormalized += 1;
                continue;
            }

            if (!APPLY) {
                stats.wouldWrite += 1;
                if (VERBOSE) {
                    console.log(`  [DRY] update ${doc.ref.path} -> medicineNameNormalized="${expectedNormalized}"`);
                }
                continue;
            }

            batch.update(doc.ref, {
                medicineNameNormalized: expectedNormalized,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            });
            batchOps += 1;
            stats.written += 1;

            if (batchOps >= 400) {
                await batch.commit();
                batch = db.batch();
                batchOps = 0;
            }

            if (VERBOSE) {
                console.log(`  ✅ updated ${doc.ref.path} -> medicineNameNormalized="${expectedNormalized}"`);
            }
        } catch (error) {
            stats.failed += 1;
            console.error(`  ❌ ${doc.ref.path}: ${error.message || error}`);
        }
    }

    if (APPLY && batchOps > 0) {
        await batch.commit();
    }

    console.log("\n📊 Backfill summary");
    console.log(`- Total pharmacyInventory docs scanned: ${stats.totalDocs}`);
    console.log(`- Missing medicineName: ${stats.missingMedicineName}`);
    console.log(`- Already normalized: ${stats.alreadyNormalized}`);
    console.log(`- ${APPLY ? "Written" : "Would write"}: ${APPLY ? stats.written : stats.wouldWrite}`);
    console.log(`- Failed: ${stats.failed}`);
    console.log("\n✅ Done\n");
}

main().catch((error) => {
    console.error("Fatal error:", error);
    process.exit(1);
});
