# Pharmacy Inventory Scripts

## Overview

These scripts help you manage test data for pharmacy inventory in Firestore:
- **inject-pharmacy-inventory.js** - Inject or update inventory for existing pharmacies
- **backfill-paid-transactions.js** - Backfill/repair pharmacy PURCHASE transactions for historical paid orders
- **backfill-inventory-normalized-name.js** - Backfill normalized medicine-name key for existing inventory docs

## Prerequisites

1. **Firebase Admin SDK**
   ```bash
   npm install firebase-admin
   ```

2. **Service Account Key**
   - Download from Firebase Console → Project Settings → Service Accounts
   - Save as `serviceAccountKey.json` in the project root
   - **⚠️ Never commit this file to git** (already in .gitignore)

## Usage

### Option 1: Default Test Scenario (Recommended)

Run with default inventory levels (out-of-stock, low stock, full stock mix):

```bash
node scripts/inject-pharmacy-inventory.js
```

**Default scenario creates:**
- Lakshmi Pharmacy:
  - Azee 500: 0 units (out of stock) ⛔
  - Calpol 500: 5 units (low stock) ⚠️
  - Aspirin 500: 150 units (well stocked) ✅
- Sri Sai Medicals:
  - Azee 500: 120 units ✅
  - Calpol 500: 85 units ✅
  - Aspirin 500: 200 units ✅

### Option 2: Custom Config File

Create your own config file:

```bash
cp scripts/inventory-config.example.json my-inventory.json
# Edit my-inventory.json with your desired inventory levels
node scripts/inject-pharmacy-inventory.js my-inventory.json
```

## Historical Revenue Backfill

If older paid orders are missing pharmacy transaction/revenue entries, run:

```bash
# Preview only (no writes)
node scripts/backfill-paid-transactions.js

# Apply changes
node scripts/backfill-paid-transactions.js --apply

# Verbose output
node scripts/backfill-paid-transactions.js --apply --verbose
```

What it does:
- Scans historical orders in paid-like lifecycle states
- Looks up captured payment records from `/payments`
- Upserts one canonical `PURCHASE` transaction in `/transactions` per order
- Repairs zero/missing amount and missing Razorpay reference fields when needed

Safety:
- Default mode is dry-run
- Idempotent upsert behavior (safe to re-run)
- Does not touch inventory stock levels (revenue/transaction backfill only)

## Inventory Name Normalization Backfill

If existing inventory records do not have `medicineNameNormalized`, run:

```bash
# Preview only (no writes)
node scripts/backfill-inventory-normalized-name.js

# Apply changes
node scripts/backfill-inventory-normalized-name.js --apply

# Verbose output
node scripts/backfill-inventory-normalized-name.js --apply --verbose
```

What it does:
- Scans `/pharmacyInventory` documents
- Computes `medicineNameNormalized = trim(lowercase(medicineName))`
- Updates only documents missing/outdated normalized values

Safety:
- Default mode is dry-run
- Idempotent updates (safe to re-run)
- Does not alter stock quantity values

### Config File Format

```json
{
  "operations": [
    {
      "pharmacyId": "lakshmi-pharmacy",
      "medicineId": "azee-500",
      "stockQuantity": 50,
      "reorderLevel": 20,
      "maxStockLevel": 150
    }
  ]
}
```

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `pharmacyId` | string | Pharmacy's Firestore doc ID |
| `medicineId` | string | Medicine's Firestore doc ID |
| `stockQuantity` | number | Current stock units |
| `reorderLevel` | number | Alert when stock falls below this |
| `maxStockLevel` | number | Maximum capacity for this medicine |

## Test Scenarios

### Scenario 1: Out of Stock Testing
```json
{
  "operations": [
    {
      "pharmacyId": "lakshmi-pharmacy",
      "medicineId": "azee-500",
      "stockQuantity": 0,
      "reorderLevel": 20,
      "maxStockLevel": 150
    }
  ]
}
```

**Expected behavior:** User gets "Insufficient stock" error when trying to order Azee 500 from Lakshmi Pharmacy

### Scenario 2: Low Stock Alert
```json
{
  "operations": [
    {
      "pharmacyId": "lakshmi-pharmacy",
      "medicineId": "calpol-500",
      "stockQuantity": 5,
      "reorderLevel": 10,
      "maxStockLevel": 100
    }
  ]
}
```

**Expected behavior:** Pharmacy sees reorder warning (stock below reorder level)

### Scenario 3: Multiple Pharmacies (Fall Back)
```json
{
  "operations": [
    {
      "pharmacyId": "lakshmi-pharmacy",
      "medicineId": "azee-500",
      "stockQuantity": 0
    },
    {
      "pharmacyId": "sri-sai-medicals",
      "medicineId": "azee-500",
      "stockQuantity": 100
    }
  ]
}
```

**Expected behavior:** User can't order from Lakshmi but can fall back to Sri Sai Medicals

## Output Example

```
🔌 Connecting to Firestore...

✅ Found 3 pharmacies
✅ Found 10 medicines

📦 Injecting 6 inventory items...

✅ lakshmi-pharmacy → azee-500: 0 units
✅ lakshmi-pharmacy → calpol-500: 5 units
✅ lakshmi-pharmacy → aspirin-500: 150 units
✅ sri-sai-medicals → azee-500: 120 units
✅ sri-sai-medicals → calpol-500: 85 units
✅ sri-sai-medicals → aspirin-500: 200 units

✅ Successfully injected 6/6 items

📊 Inventory for lakshmi-pharmacy:
  ⛔ azee-500: 0 units (reorder: 20)
  ⚠️  calpol-500: 5 units (reorder: 10)
  ✅ aspirin-500: 150 units (reorder: 20)

📊 Inventory for sri-sai-medicals:
  ✅ azee-500: 120 units (reorder: 20)
  ✅ calpol-500: 85 units (reorder: 15)
  ✅ aspirin-500: 200 units (reorder: 25)

✨ Done!
```

## Common Use Cases

### 1. Test Order Failure Scenario
```bash
# Make Azee out of stock at one pharmacy
cat > test-outofstock.json << 'EOF'
{
  "operations": [
    {
      "pharmacyId": "lakshmi-pharmacy",
      "medicineId": "azee-500",
      "stockQuantity": 0,
      "reorderLevel": 20,
      "maxStockLevel": 150
    }
  ]
}
EOF
node scripts/inject-pharmacy-inventory.js test-outofstock.json
```

### 2. Reset All Inventory to Full
```bash
cat > reset-full.json << 'EOF'
{
  "operations": [
    {
      "pharmacyId": "lakshmi-pharmacy",
      "medicineId": "azee-500",
      "stockQuantity": 150,
      "reorderLevel": 20,
      "maxStockLevel": 150
    },
    {
      "pharmacyId": "lakshmi-pharmacy",
      "medicineId": "calpol-500",
      "stockQuantity": 100,
      "reorderLevel": 10,
      "maxStockLevel": 100
    }
  ]
}
EOF
node scripts/inject-pharmacy-inventory.js reset-full.json
```

### 3. Verify Current Inventory
Just run without arguments to see current state printed out

## Troubleshooting

**Error: "serviceAccountKey.json not found"**
- Download service account from Firebase Console
- Save to project root as `serviceAccountKey.json`

**Error: "No pharmacies found"**
- Run `seed-firestore.js` first to create test data

**Error: Permission denied**
- Check Firebase Firestore security rules allow writes to `pharmacies/{id}/inventory/{id}`
- Verify service account has Editor role in Firebase Console

## Integration with Testing

Use these scripts in your test suite:

```bash
# Before running tests
node scripts/inject-pharmacy-inventory.js test-scenarios/order-flow.json

# Run tests
npm test

# Or in CI/CD pipeline (GitHub Actions example)
- name: Inject test inventory
  run: node scripts/inject-pharmacy-inventory.js
```
