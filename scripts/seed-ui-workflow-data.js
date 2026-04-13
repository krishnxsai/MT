#!/usr/bin/env node

const path = require("path");

const { loadIdentities } = require("./seed-ui/identities");
const { buildUserDocs } = require("./seed-ui/users");
const { buildPharmacyDocs } = require("./seed-ui/pharmacies");
const { buildClinicalDocs } = require("./seed-ui/clinical");
const { buildPatientCareDocs } = require("./seed-ui/patientCare");
const { buildCommunicationDocs } = require("./seed-ui/communication");
const { buildCommerceDocs } = require("./seed-ui/commerce");
const { buildSystemDocs } = require("./seed-ui/system");

const {
    configureTargetEnvironment,
    createSeedContext,
    executeSeedDocs,
    loadFirebaseAdmin,
    parseArgs,
    pruneSeededData,
    resolveServiceAccountPath,
    validateUserReferences,
} = require("./seed-ui/utils");

function printBanner(flags, identitySource) {
    console.log("=== MediTrack UI Workflow Seed ===");
    console.log(`mode: ${flags.apply ? "APPLY" : "DRY-RUN"}`);
    console.log(`target: ${flags.target}`);
    console.log(`seedSource: ${flags.seedSource}`);
    console.log(`seedVersion: ${flags.seedVersion}`);
    console.log(`identities: ${identitySource}`);
    console.log("");
}

function printFeatureSummary(summaryByFeature) {
    const features = Object.keys(summaryByFeature).sort();
    console.log("Feature Summary:");

    features.forEach((feature) => {
        const row = summaryByFeature[feature];
        console.log(
            `- ${feature}: planned=${row.planned}, wouldWrite=${row.wouldWrite}, created=${row.created}, updated=${row.updated}, failed=${row.failed}`
        );
    });
}

function printTotals(totals, flags) {
    console.log("");
    console.log("Totals:");
    console.log(`- planned: ${totals.planned}`);
    if (flags.apply) {
        console.log(`- created: ${totals.created}`);
        console.log(`- updated: ${totals.updated}`);
        console.log(`- failed: ${totals.failed}`);
    } else {
        console.log(`- wouldWrite: ${totals.wouldWrite}`);
    }
}

function printPruneSummary(pruneResult, apply) {
    console.log("");
    console.log(`Prune ${apply ? "deleted" : "would delete"}: ${pruneResult.total} docs`);

    Object.keys(pruneResult.byCollection)
        .sort()
        .forEach((collectionName) => {
            const count = pruneResult.byCollection[collectionName];
            if (count > 0) {
                console.log(`- ${collectionName}: ${count}`);
            }
        });
}

async function initFirestore(admin, workspaceRoot) {
    const serviceAccountPath = resolveServiceAccountPath(workspaceRoot);
    // eslint-disable-next-line global-require, import/no-dynamic-require
    const serviceAccount = require(serviceAccountPath);

    if (!admin.apps.length) {
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount),
            projectId: serviceAccount.project_id,
        });
    }

    const db = admin.firestore();
    db.settings({ ignoreUndefinedProperties: true });
    return db;
}

async function run() {
    const workspaceRoot = path.resolve(__dirname, "..");
    const flags = parseArgs(process.argv.slice(2));

    configureTargetEnvironment(flags);

    const identities = loadIdentities({ identitiesFile: flags.identitiesFile });
    printBanner(flags, identities.source);

    const admin = loadFirebaseAdmin(workspaceRoot);
    const db = await initFirestore(admin, workspaceRoot);

    const ctx = createSeedContext({
        admin,
        identities,
        seedSource: flags.seedSource,
        seedVersion: flags.seedVersion,
    });

    const builders = [
        buildUserDocs,
        buildPharmacyDocs,
        buildClinicalDocs,
        buildPatientCareDocs,
        buildCommunicationDocs,
        buildCommerceDocs,
        buildSystemDocs,
    ];

    const plannedDocs = [];
    builders.forEach((builder) => {
        const docs = builder(ctx);
        plannedDocs.push(...docs);
    });

    validateUserReferences(plannedDocs, ctx.userIdSet);

    console.log(`Prepared ${plannedDocs.length} deterministic documents.`);

    if (flags.pruneSeedData) {
        const pruneResult = await pruneSeededData(db, {
            apply: flags.apply,
            verbose: flags.verbose,
            seedSource: flags.seedSource,
        });
        printPruneSummary(pruneResult, flags.apply);
    }

    const result = await executeSeedDocs(db, plannedDocs, {
        apply: flags.apply,
        verbose: flags.verbose,
    });

    console.log("");
    console.log(flags.apply ? "Seed run complete." : "Dry-run complete.");
    printFeatureSummary(result.byFeature);
    printTotals(result.totals, flags);

    if (!flags.apply) {
        console.log("");
        console.log("Re-run with --apply to persist changes.");
    }
}

run()
    .then(() => {
        process.exitCode = 0;
    })
    .catch((error) => {
        console.error("Seed run failed:", error.message);
        if (error && error.stack) {
            console.error(error.stack);
        }
        process.exitCode = 1;
    });
