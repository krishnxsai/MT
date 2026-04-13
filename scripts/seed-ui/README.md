# MediTrack UI Workflow Seed

This seed pipeline writes deterministic Firestore data for UI rendering and workflow testing across:

- users and role statuses
- pharmacy profiles and inventory states
- doctor availability, appointments, prescriptions, decisions, and notes
- patient care data (medicines, health logs, intakes, alerts, risk scores)
- conversations and messages
- orders, payments, transactions, cancellations, and delivery tracking
- system data (notification preferences, carts, audit logs, notification logs)

## 1) Prepare identities

Do not create auth users from this script. Use existing Firebase Auth users only.

Create `scripts/seed-ui/identities.local.json` from `scripts/seed-ui/identities.sample.json` and replace each UID/email/phone with your real accounts.

Required shape and counts are strict:

- 1 admin
- 2 doctors
- 2 patients
- 3 pharmacies

## 2) Dry run (recommended first)

From workspace root:

```bash
node scripts/seed-ui-workflow-data.js --target emulator --identities-file scripts/seed-ui/identities.local.json
```

This prints planned writes only and performs no Firestore mutations.

## 3) Apply to emulator

```bash
node scripts/seed-ui-workflow-data.js --apply --target emulator --identities-file scripts/seed-ui/identities.local.json
```

If `FIRESTORE_EMULATOR_HOST` is not set, this script defaults it to `127.0.0.1:8080`.

## 4) Apply to cloud (guarded)

```bash
node scripts/seed-ui-workflow-data.js --apply --target cloud --allow-cloud-write --identities-file scripts/seed-ui/identities.local.json
```

Cloud writes are blocked unless `--allow-cloud-write` is provided.

## 5) Optional prune of prior seeded docs

To remove docs previously created by the same `seedSource`:

```bash
node scripts/seed-ui-workflow-data.js --target emulator --prune-seed-data --identities-file scripts/seed-ui/identities.local.json
```

Apply prune + write in one run:

```bash
node scripts/seed-ui-workflow-data.js --apply --target emulator --prune-seed-data --identities-file scripts/seed-ui/identities.local.json
```

## Flags

- `--apply`: persist changes; omitted means dry-run.
- `--target emulator|cloud`: selects destination.
- `--identities-file <path>`: identity map file.
- `--prune-seed-data`: prune docs by matching `seedSource`.
- `--seed-source <value>`: default `ui-workflow-seed`.
- `--seed-version <value>`: default `2026-04-ui-workflow-v1`.
- `--allow-cloud-write`: required for cloud apply.
- `--verbose`: logs each write/prune batch.
