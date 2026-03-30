#!/usr/bin/env node

/**
 * Inject/Update Pharmacy Inventory Data into Firestore
 * Usage: node scripts/inject-pharmacy-inventory.js [config-file]
 *
 * Config file format (JSON):
 * {
 *   "operations": [
 *     {
 *       "pharmacyId": "lakshmi-pharmacy",
 *       "medicineId": "azee-500",
 *       "stockQuantity": 50,
 *       "reorderLevel": 10,
 *       "maxStockLevel": 200
 *     }
 *   ]
 * }
 */

const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

// Initialize Firebase Admin
const serviceAccountPath = path.join(__dirname, '../serviceAccountKey.json');
if (!fs.existsSync(serviceAccountPath)) {
  console.error('❌ serviceAccountKey.json not found at:', serviceAccountPath);
  process.exit(1);
}

const serviceAccount = require(serviceAccountPath);
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: serviceAccount.project_id,
});

const db = admin.firestore();

/**
 * Get all pharmacies
 */
async function getAllPharmacies() {
  try {
    const snapshot = await db.collection('pharmacies').get();
    const pharmacies = [];
    snapshot.forEach((doc) => {
      pharmacies.push({
        id: doc.id,
        ...doc.data(),
      });
    });
    return pharmacies;
  } catch (error) {
    console.error('Error fetching pharmacies:', error);
    throw error;
  }
}

/**
 * Get all medicines
 */
async function getAllMedicines() {
  try {
    const snapshot = await db.collection('medicines').get();
    const medicines = [];
    snapshot.forEach((doc) => {
      medicines.push({
        id: doc.id,
        ...doc.data(),
      });
    });
    return medicines;
  } catch (error) {
    console.error('Error fetching medicines:', error);
    throw error;
  }
}

/**
 * Inject/Update single inventory item
 */
async function injectInventoryItem(pharmacyId, medicineId, data) {
  const docRef = db
    .collection('pharmacies')
    .doc(pharmacyId)
    .collection('inventory')
    .doc(medicineId);

  const defaultData = {
    medicineId,
    stockQuantity: data.stockQuantity || 0,
    reorderLevel: data.reorderLevel || 10,
    maxStockLevel: data.maxStockLevel || 200,
    lastUpdated: admin.firestore.FieldValue.serverTimestamp(),
  };

  try {
    await docRef.set(defaultData, { merge: true });
    return { success: true, pharmacyId, medicineId };
  } catch (error) {
    return { success: false, pharmacyId, medicineId, error: error.message };
  }
}

/**
 * Batch inject inventory items
 */
async function batchInjectInventory(operations) {
  console.log(`\n📦 Injecting ${operations.length} inventory items...\n`);

  const results = [];
  for (const op of operations) {
    const result = await injectInventoryItem(
      op.pharmacyId,
      op.medicineId,
      {
        stockQuantity: op.stockQuantity,
        reorderLevel: op.reorderLevel,
        maxStockLevel: op.maxStockLevel,
      }
    );
    results.push(result);

    if (result.success) {
      console.log(
        `✅ ${op.pharmacyId} → ${op.medicineId}: ${op.stockQuantity} units`
      );
    } else {
      console.log(`❌ ${op.pharmacyId} → ${op.medicineId}: ${result.error}`);
    }
  }

  return results;
}

/**
 * Load config from file or create default scenario
 */
function loadConfig(configFile) {
  if (!configFile) {
    // Default scenario: various stock levels for testing
    return {
      operations: [
        // Out of stock scenario
        {
          pharmacyId: 'lakshmi-pharmacy',
          medicineId: 'azee-500',
          stockQuantity: 0,
          reorderLevel: 20,
          maxStockLevel: 150,
        },
        // Low stock scenario
        {
          pharmacyId: 'lakshmi-pharmacy',
          medicineId: 'calpol-500',
          stockQuantity: 5,
          reorderLevel: 10,
          maxStockLevel: 100,
        },
        // Full stock
        {
          pharmacyId: 'lakshmi-pharmacy',
          medicineId: 'aspirin-500',
          stockQuantity: 150,
          reorderLevel: 20,
          maxStockLevel: 200,
        },
        // Different pharmacy - well stocked
        {
          pharmacyId: 'sri-sai-medicals',
          medicineId: 'azee-500',
          stockQuantity: 120,
          reorderLevel: 20,
          maxStockLevel: 200,
        },
        {
          pharmacyId: 'sri-sai-medicals',
          medicineId: 'calpol-500',
          stockQuantity: 85,
          reorderLevel: 15,
          maxStockLevel: 150,
        },
      ],
    };
  }

  try {
    const content = fs.readFileSync(configFile, 'utf8');
    return JSON.parse(content);
  } catch (error) {
    console.error(`Error reading config file ${configFile}:`, error.message);
    process.exit(1);
  }
}

/**
 * Display inventory status for a pharmacy
 */
async function displayPharmacyInventory(pharmacyId) {
  try {
    const snapshot = await db
      .collection('pharmacies')
      .doc(pharmacyId)
      .collection('inventory')
      .get();

    console.log(`\n📊 Inventory for ${pharmacyId}:`);
    if (snapshot.empty) {
      console.log('  (No items)');
      return;
    }

    snapshot.forEach((doc) => {
      const data = doc.data();
      const stock = data.stockQuantity || 0;
      const reorder = data.reorderLevel || 0;
      const status =
        stock === 0 ? '⛔' : stock <= reorder ? '⚠️ ' : '✅';
      console.log(
        `  ${status} ${doc.id}: ${stock} units (reorder: ${reorder})`
      );
    });
  } catch (error) {
    console.error('Error displaying inventory:', error);
  }
}

/**
 * Main execution
 */
async function main() {
  try {
    console.log('🔌 Connecting to Firestore...');

    // Verify pharmacies and medicines exist
    const pharmacies = await getAllPharmacies();
    const medicines = await getAllMedicines();

    console.log(`\n✅ Found ${pharmacies.length} pharmacies`);
    console.log(`✅ Found ${medicines.length} medicines\n`);

    if (pharmacies.length === 0 || medicines.length === 0) {
      console.warn(
        '⚠️  No pharmacies or medicines found. Run seed-firestore.js first.'
      );
      process.exit(0);
    }

    // Load config
    const configFile = process.argv[2];
    const config = loadConfig(configFile);

    // Inject inventory
    const results = await batchInjectInventory(config.operations);

    // Summary
    const successful = results.filter((r) => r.success).length;
    console.log(
      `\n✅ Successfully injected ${successful}/${results.length} items`
    );

    // Display updated inventory
    const uniquePharmacies = [
      ...new Set(config.operations.map((op) => op.pharmacyId)),
    ];
    for (const pharmacyId of uniquePharmacies) {
      await displayPharmacyInventory(pharmacyId);
    }

    console.log('\n✨ Done!\n');
    process.exit(0);
  } catch (error) {
    console.error('Fatal error:', error);
    process.exit(1);
  }
}

main();
