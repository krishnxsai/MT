# Pharmacy Inventory Injection Guide

## Quick Start

### 1. Install Dependencies
```bash
npm install firebase-admin
```

### 2. Run Inventory Injection
```bash
# Inject inventory for all pharmacies
node seed_inventory.js

# Clear and reinject all inventory
node seed_inventory.js --clear

# Inject for specific pharmacy only
node seed_inventory.js --pharmacy PHARMACY_ID
```

## Test Data Created

### Stock Scenarios
| Medicine | Lakshmi | Sri Sai | MedCare |
|----------|---------|---------|---------|
| Azee 500 | **0** (OOS) | 120 | 5 (low) |
| Amoxicillin 250 | **0** (OOS) | 85 | 3 (very low) |
| Ibuprofen 400 | 50 | 40 | **0** (OOS) |
| Lisinopril 10 | **0** (OOS) | 60 | **0** (OOS) |
| Others | 40-200 | 40-150 | 2-180 |

**Legend:** OOS = Out of Stock, (low) = Below 10 units

### Testing Order Flow

#### Test Case 1: Order Out-of-Stock Item
1. Browse Lakshmi Pharmacy
2. Add Azee 500 (qty 1) to cart
3. Confirm order → **Error Dialog:** "Insufficient stock: Azee 500 (available: 0, requested: 1)"
4. Select Different Pharmacy → Choose Sri Sai
5. Order proceeds to payment ✅

#### Test Case 2: Order Insufficient Stock
1. Browse Lakshmi Pharmacy
2. Add Amoxicillin 250 (qty 10) to cart
3. Confirm order → **Error Dialog:** "Insufficient stock"
4. Switch to Sri Sai Pharmacy (has 85 units) → Succeeds ✅

#### Test Case 3: Multiple Items, Partial Stock
1. Browse Lakshmi Pharmacy
2. Add Azee 500 (qty 6) + Paracetamol 500 (qty 100)
3. Confirm order → **Shows:** Which items are unavailable
4. Select Different Pharmacy with inventory ✅

## Required Credentials

Ensure `serviceAccountKey.json` exists in project root:
```
MediTrack-main/
├── serviceAccountKey.json    ← Required
├── app/
├── functions/
├── seed_inventory.js
└── seed_firestore.js
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `serviceAccountKey.json not found` | Download from Firebase Console → Project Settings |
| `Cannot find module 'firebase-admin'` | Run `npm install firebase-admin` first |
| No pharmacies found | Ensure pharmacies exist in Firestore (run `seed_firestore.js` first) |
| Permission denied | Check Firestore security rules allow writes to `pharmacyInventory` |

## What Gets Created

Each medicine entry includes:
- ✅ Pharmacy ID mapping
- ✅ Medicine name & generic name
- ✅ Manufacturer & description
- ✅ Unit price (in₹)
- ✅ Stock quantity (varies by pharmacy)
- ✅ Min stock level (10 units)
- ✅ Prescription requirement flag
- ✅ Timestamps (server-generated)

## Integration with Order Flow

When users place orders:
1. **UI:** `OrderConfirmationBottomSheet` shows items & prices
2. **Validation:** `UnifiedOrderViewModel.placeOrder()` checks inventory
3. **Error:** If insufficient stock → Shows items with available vs requested
4. **Action:** User either selects different pharmacy or reduces quantity

## Running Tests

After injecting inventory:
```bash
# Terminal 1: Run app tests
./gradlew test

# Terminal 2: Run cloud function tests
cd functions && npm test
```

All inventory validation tests should pass ✅
