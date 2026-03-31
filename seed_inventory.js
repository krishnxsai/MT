#!/usr/bin/env node

/**
 * Pharmacy Inventory Injection Script
 * Injects or updates inventory items for existing pharmacies in Firestore
 *
 * Usage:
 *   node seed_inventory.js                 // Default: inject test inventory
 *   node seed_inventory.js --clear         // Clear all inventory first
 *   node seed_inventory.js --pharmacy <id> // Target specific pharmacy
 */

const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

// Initialize Firebase Admin
const serviceAccountPath = path.join(__dirname, 'serviceAccountKey.json');
if (!fs.existsSync(serviceAccountPath)) {
    console.error('❌ serviceAccountKey.json not found');
    process.exit(1);
}

const serviceAccount = require(serviceAccountPath);
admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId: serviceAccount.project_id
});

const db = admin.firestore();

function normalizeMedicineName(value) {
    return typeof value === 'string' ? value.trim().toLowerCase() : '';
}

// Test medicines with various stock scenarios
const TEST_MEDICINES = [
    {
        medicineName: 'Azee 500',
        genericName: 'Azithromycin',
        manufacturer: 'Cipla',
        description: 'Antibiotic for bacterial infections',
        unitPrice: 45.50,
        scenarios: {
            'lakshmi': 0,          // Out of stock
            'sri_sai': 120,        // Well stocked
            'medcare': 5           // Low stock
        }
    },
    {
        medicineName: 'Paracetamol 500',
        genericName: 'Acetaminophen',
        manufacturer: 'GSK',
        description: 'Pain reliever and fever reducer',
        unitPrice: 15.00,
        scenarios: {
            'lakshmi': 200,
            'sri_sai': 150,
            'medcare': 180
        }
    },
    {
        medicineName: 'Amoxicillin 250',
        genericName: 'Amoxicillin',
        manufacturer: 'Abbott',
        description: 'Penicillin-based antibiotic',
        unitPrice: 22.75,
        scenarios: {
            'lakshmi': 0,          // Out of stock
            'sri_sai': 85,
            'medcare': 3           // Very low
        }
    },
    {
        medicineName: 'Ibuprofen 400',
        genericName: 'Ibuprofen',
        manufacturer: 'Pfizer',
        description: 'Anti-inflammatory pain reliever',
        unitPrice: 12.50,
        scenarios: {
            'lakshmi': 50,
            'sri_sai': 40,
            'medcare': 0           // Out of stock
        }
    },
    {
        medicineName: 'Crocin 650',
        genericName: 'Paracetamol',
        manufacturer: 'GSK',
        description: 'Over-the-counter fever reducer',
        unitPrice: 18.00,
        scenarios: {
            'lakshmi': 75,
            'sri_sai': 100,
            'medcare': 2           // Low stock
        }
    },
    {
        medicineName: 'Metformin 500',
        genericName: 'Metformin HCl',
        manufacturer: 'Torrent',
        description: 'Diabetes management',
        unitPrice: 35.00,
        scenarios: {
            'lakshmi': 150,
            'sri_sai': 120,
            'medcare': 8
        }
    },
    {
        medicineName: 'Lisinopril 10',
        genericName: 'Lisinopril',
        manufacturer: 'Lupin',
        description: 'ACE inhibitor for hypertension',
        unitPrice: 28.50,
        scenarios: {
            'lakshmi': 0,          // Out of stock
            'sri_sai': 60,
            'medcare': 0           // Out of stock
        }
    },
    {
        medicineName: 'Omeprazole 20',
        genericName: 'Omeprazole',
        manufacturer: 'Cipla',
        description: 'Proton pump inhibitor',
        unitPrice: 32.00,
        scenarios: {
            'lakshmi': 40,
            'sri_sai': 55,
            'medcare': 25
        }
    }
];

// Pharmacy mapping
const PHARMACY_MAP = {
    'lakshmi': 'Lakshmi Pharmacy',
    'sri_sai': 'Sri Sai Medicals',
    'medcare': 'MedCare Pharmacy'
};

async function getPharmacies() {
    console.log('\n📍 Fetching pharmacies from Firestore...');
    try {
        const snapshot = await db.collection('pharmacies').get();
        const pharmacies = [];
        snapshot.forEach(doc => {
            const data = doc.data();
            pharmacies.push({
                id: doc.id,
                name: data.name,
                shortName: data.name.toLowerCase().replace(/\s+/g, '_').substring(0, 10)
            });
        });
        console.log(`✅ Found ${pharmacies.length} pharmacies`);
        pharmacies.forEach(p => console.log(`   - ${p.name} (ID: ${p.id})`));
        return pharmacies;
    } catch (error) {
        console.error('❌ Error fetching pharmacies:', error.message);
        return [];
    }
}

async function clearInventory(pharmacyId) {
    console.log(`\n🗑️  Clearing inventory for pharmacy...`);
    try {
        const snapshot = await db.collection('pharmacyInventory')
            .where('pharmacyId', '==', pharmacyId)
            .get();

        let count = 0;
        for (const doc of snapshot.docs) {
            await doc.ref.delete();
            count++;
        }
        console.log(`✅ Deleted ${count} inventory items`);
    } catch (error) {
        console.error('❌ Error clearing inventory:', error.message);
    }
}

async function injectInventory(pharmacyId, pharmacyName) {
    console.log(`\n💉 Injecting inventory for ${pharmacyName}...`);

    let successCount = 0;
    let errorCount = 0;

    for (const medicine of TEST_MEDICINES) {
        try {
            // Determine stock level based on pharmacy
            let stockQuantity = 0;
            for (const [shortName, quantity] of Object.entries(medicine.scenarios)) {
                if (pharmacyName.toLowerCase().includes(shortName.replace('_', ' ')) ||
                    pharmacyName.toLowerCase().includes(shortName)) {
                    stockQuantity = quantity;
                    break;
                }
            }

            // Use pharmacy name matching as fallback
            if (stockQuantity === 0 && !medicine.scenarios[pharmacyName]) {
                // Try fuzzy matching
                const nameLower = pharmacyName.toLowerCase();
                if (nameLower.includes('lakshmi')) {
                    stockQuantity = medicine.scenarios.lakshmi || 0;
                } else if (nameLower.includes('sri') || nameLower.includes('sai')) {
                    stockQuantity = medicine.scenarios.sri_sai || 0;
                } else if (nameLower.includes('med') || nameLower.includes('care')) {
                    stockQuantity = medicine.scenarios.medcare || 0;
                }
            }

            const inventoryData = {
                pharmacyId: pharmacyId,
                medicineId: medicine.medicineName.toLowerCase().replace(/\s+/g, '_'),
                medicineName: medicine.medicineName,
                medicineNameNormalized: normalizeMedicineName(medicine.medicineName),
                genericName: medicine.genericName,
                manufacturer: medicine.manufacturer,
                description: medicine.description,
                unitPrice: medicine.unitPrice,
                stockQuantity: stockQuantity,
                minStockLevel: 10,
                isActive: true,
                prescriptionRequired: medicine.genericName.includes('Metformin') ||
                    medicine.genericName.includes('Lisinopril'),
                createdAt: admin.firestore.FieldValue.serverTimestamp(),
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
            };

            await db.collection('pharmacyInventory').add(inventoryData);
            console.log(`   ✅ ${medicine.medicineName} (Stock: ${stockQuantity})`);
            successCount++;

        } catch (error) {
            console.error(`   ❌ Error adding ${medicine.medicineName}:`, error.message);
            errorCount++;
        }
    }

    console.log(`\n📊 Results: ${successCount} injected, ${errorCount} failed`);
    return successCount;
}

async function main() {
    const args = process.argv.slice(2);
    const clearFlag = args.includes('--clear');
    const pharmacyFlag = args.indexOf('--pharmacy');
    const targetPharmacyId = pharmacyFlag !== -1 ? args[pharmacyFlag + 1] : null;

    console.log('═'.repeat(60));
    console.log('🏥 PHARMACY INVENTORY INJECTION SCRIPT');
    console.log('═'.repeat(60));

    try {
        const pharmacies = await getPharmacies();

        if (pharmacies.length === 0) {
            console.error('❌ No pharmacies found in Firestore');
            process.exit(1);
        }

        let targetPharmacies = pharmacies;
        if (targetPharmacyId) {
            targetPharmacies = pharmacies.filter(p => p.id === targetPharmacyId);
            if (targetPharmacies.length === 0) {
                console.error(`❌ Pharmacy ID not found: ${targetPharmacyId}`);
                process.exit(1);
            }
        }

        // Process each pharmacy
        for (const pharmacy of targetPharmacies) {
            if (clearFlag) {
                await clearInventory(pharmacy.id);
            }
            await injectInventory(pharmacy.id, pharmacy.name);
        }

        console.log('\n' + '═'.repeat(60));
        console.log('✨ Inventory injection complete!');
        console.log('═'.repeat(60));
        console.log('\n📝 Test Scenarios Created:');
        console.log('   • Azee 500: Out of stock at Lakshmi, 120 units at Sri Sai');
        console.log('   • Amoxicillin 250: Out of stock at Lakshmi, 85 units at Sri Sai');
        console.log('   • Multiple low-stock items for testing edge cases');
        console.log('\n🧪 Ready to test order flow with inventory validation!');

        process.exit(0);

    } catch (error) {
        console.error('❌ Fatal error:', error.message);
        process.exit(1);
    }
}

main();
