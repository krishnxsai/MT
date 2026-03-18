/**
 * MediTrack Firestore Seed Data — Indian Names & Locations
 *
 * Usage:
 *   1. Place serviceAccountKey.json in this directory
 *   2. Run: npm install firebase-admin
 *   3. Run: node seed_firestore.js
 *
 * Creates test data across all collections for full app testing.
 * WARNING: Overwrites documents with the same IDs.
 */

const admin = require("firebase-admin");
const serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();

// ─── Helpers ────────────────────────────────────────────────────────
function daysAgo(n) {
  return admin.firestore.Timestamp.fromDate(new Date(Date.now() - n * 86400000));
}
function hoursAgo(n) {
  return admin.firestore.Timestamp.fromDate(new Date(Date.now() - n * 3600000));
}
function minutesFromNow(n) {
  return admin.firestore.Timestamp.fromDate(new Date(Date.now() + n * 60000));
}
function now() {
  return admin.firestore.Timestamp.now();
}

// ─── Fixed IDs ──────────────────────────────────────────────────────
const ID = {
  // Users (Firebase Auth UIDs)
  patient1: "6PiDhEGj47SXX2jSbQsnLvDOgkW2",
  patient2: "GuwWWdLMqZfVuEgrv5GI7voWTf52",
  doctor1: "NmL3MJpHEgce1xwHtb68ZERGQ6Z2",
  doctor2: "B6dB17N6m8ej8dE3SC1JhrtyrEG3",
  admin1: "gTVkSU0ctQgi7JP1UJpGS4UX2nV2",
  pharmUser1: "t0oP9GCF95bu12RwtZWvow4c7GI2",
  pharmUser2: "HpHt0Ts9VDNLwymryqSDA3aDuuP2",
  pharmUser3: "favF3khwlyRBq5F2lHHgNadZ0Ph2",

  // Pharmacies
  pharm1: "pharm_sri_sai_medicals",
  pharm2: "pharm_vijaya_drughouse",
  pharm3: "pharm_lakshmi_pharmacy",

  // Medicines (patient1)
  med_dolo: "med_dolo_650",
  med_azee: "med_azee_500",
  med_glycomet: "med_glycomet_500",
  med_mox: "med_mox_250",
  med_pantop: "med_pantop_40",
  med_allegra: "med_allegra_120",
  med_ecosprin: "med_ecosprin_75",
  med_thyronorm: "med_thyronorm_50",

  // Medicines (patient2)
  med_crocin: "med_crocin_500",
  med_shelcal: "med_shelcal_500",

  // Orders
  order1: "ord_preparing_001",
  order2: "ord_pending_002",
  order3: "ord_shipped_003",
  order4: "ord_delivered_004",
  order5: "ord_cancelled_005",
};

// =====================================================================
//  1. USERS
// =====================================================================
async function seedUsers() {
  const users = {
    [ID.patient1]: {
      email: "vikram.mehta@gmail.com",
      displayName: "Vikram Mehta",
      profileImageUrl: "",
      role: "PATIENT",
      status: "APPROVED",
      assignedDoctors: [ID.doctor1],
      assignedDoctorNames: { [ID.doctor1]: "Dr. Ananya Sharma" },
      phoneNumber: "+91 98765 43210",
      fcmToken: "",
      licenseUrl: "",
      verifiedBy: "",
      verifiedAt: null,
      rejectionReason: "",
      createdAt: daysAgo(120),
      updatedAt: now(),
    },
    [ID.patient2]: {
      email: "sneha.iyer@gmail.com",
      displayName: "Sneha Iyer",
      profileImageUrl: "",
      role: "PATIENT",
      status: "APPROVED",
      assignedDoctors: [ID.doctor2],
      assignedDoctorNames: { [ID.doctor2]: "Dr. Rohan Gupta" },
      phoneNumber: "+91 87654 32109",
      fcmToken: "",
      licenseUrl: "",
      verifiedBy: "",
      verifiedAt: null,
      rejectionReason: "",
      createdAt: daysAgo(45),
      updatedAt: now(),
    },
    [ID.doctor1]: {
      email: "dr.ananya.sharma@meditrack.in",
      displayName: "Dr. Ananya Sharma",
      profileImageUrl: "",
      role: "DOCTOR",
      status: "APPROVED",
      assignedDoctors: [],
      assignedDoctorNames: {},
      phoneNumber: "+91 99001 10022",
      fcmToken: "",
      licenseUrl: "https://example.com/ananya_licence.pdf",
      verifiedBy: ID.admin1,
      verifiedAt: daysAgo(200),
      rejectionReason: "",
      createdAt: daysAgo(210),
      updatedAt: now(),
    },
    [ID.doctor2]: {
      email: "dr.rohan.gupta@meditrack.in",
      displayName: "Dr. Rohan Gupta",
      profileImageUrl: "",
      role: "DOCTOR",
      status: "APPROVED",
      assignedDoctors: [],
      assignedDoctorNames: {},
      phoneNumber: "+91 99002 20033",
      fcmToken: "",
      licenseUrl: "https://example.com/rohan_licence.pdf",
      verifiedBy: ID.admin1,
      verifiedAt: daysAgo(150),
      rejectionReason: "",
      createdAt: daysAgo(160),
      updatedAt: now(),
    },
    [ID.admin1]: {
      email: "suresh.admin@meditrack.in",
      displayName: "Suresh Kumar",
      profileImageUrl: "",
      role: "ADMIN",
      status: "APPROVED",
      assignedDoctors: [],
      assignedDoctorNames: {},
      phoneNumber: "+91 90000 00001",
      fcmToken: "",
      licenseUrl: "",
      verifiedBy: "",
      verifiedAt: null,
      rejectionReason: "",
      createdAt: daysAgo(365),
      updatedAt: now(),
    },
    [ID.pharmUser1]: {
      email: "rajesh@srisaimedicals.in",
      displayName: "Rajesh Naidu",
      profileImageUrl: "",
      role: "PHARMACY",
      status: "APPROVED",
      assignedDoctors: [],
      assignedDoctorNames: {},
      phoneNumber: "+91 861 2789 1234",
      fcmToken: "",
      licenseUrl: "https://example.com/srisai_licence.pdf",
      verifiedBy: ID.admin1,
      verifiedAt: daysAgo(180),
      rejectionReason: "",
      createdAt: daysAgo(190),
      updatedAt: now(),
    },
    [ID.pharmUser2]: {
      email: "kavitha@vijayadrugs.in",
      displayName: "Kavitha Reddy",
      profileImageUrl: "",
      role: "PHARMACY",
      status: "APPROVED",
      assignedDoctors: [],
      assignedDoctorNames: {},
      phoneNumber: "+91 861 4567 8900",
      fcmToken: "",
      licenseUrl: "https://example.com/vijaya_licence.pdf",
      verifiedBy: ID.admin1,
      verifiedAt: daysAgo(170),
      rejectionReason: "",
      createdAt: daysAgo(175),
      updatedAt: now(),
    },
    [ID.pharmUser3]: {
      email: "manoj@lakshmipharmacy.in",
      displayName: "Manoj Prasad",
      profileImageUrl: "",
      role: "PHARMACY",
      status: "APPROVED",
      assignedDoctors: [],
      assignedDoctorNames: {},
      phoneNumber: "+91 861 3344 5566",
      fcmToken: "",
      licenseUrl: "https://example.com/lakshmi_licence.pdf",
      verifiedBy: ID.admin1,
      verifiedAt: daysAgo(100),
      rejectionReason: "",
      createdAt: daysAgo(110),
      updatedAt: now(),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(users)) {
    batch.set(db.collection("users").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ users: ${Object.keys(users).length} documents`);
}

// =====================================================================
//  2. PHARMACIES
// =====================================================================
async function seedPharmacies() {
  const pharmacies = {
    [ID.pharm1]: {
      name: "Sri Sai Medicals",
      address: "Shop 4, Beside Hanuman Temple, Ring Road",
      city: "Nellore",
      state: "Andhra Pradesh",
      zipCode: "524001",
      phone: "+91 861 2345 1234",
      email: "rajesh@srisaimedicals.in",
      latitude: 14.4426,
      longitude: 79.9864,
      operatingHours: "Mon–Sat: 7:30 AM – 10:30 PM, Sun: 8 AM – 2 PM",
      isDeliveryAvailable: true,
      isActive: true,
      rating: 4.6,
      ratingCount: 312,
      imageUrl: "",
      estimatedDeliveryTime: "25 min",
      services: ["Prescription filling", "Home delivery", "Blood pressure check", "OTC medicines"],
      ownerId: ID.pharmUser1,
      licenseNumber: "AP/NEL/PHARM/2023/1847",
      licenseDocumentUrl: "https://example.com/srisai_licence.pdf",
      verificationStatus: "APPROVED",
      createdAt: daysAgo(190),
    },
    [ID.pharm2]: {
      name: "Vijaya Drug House",
      address: "Plot 22, Main Road, Nellore",
      city: "Nellore",
      state: "Andhra Pradesh",
      zipCode: "524002",
      phone: "+91 861 4567 8900",
      email: "kavitha@vijayadrugs.in",
      latitude: 14.4430,
      longitude: 79.9885,
      operatingHours: "Mon–Sun: 6 AM – 11 PM",
      isDeliveryAvailable: true,
      isActive: true,
      rating: 4.3,
      ratingCount: 189,
      imageUrl: "",
      estimatedDeliveryTime: "40 min",
      services: ["Prescription filling", "Home delivery", "Ayurvedic medicines"],
      ownerId: ID.pharmUser2,
      licenseNumber: "AP/NEL/PHARM/2023/2154",
      licenseDocumentUrl: "https://example.com/vijaya_licence.pdf",
      verificationStatus: "APPROVED",
      createdAt: daysAgo(175),
    },
    [ID.pharm3]: {
      name: "Lakshmi Pharmacy",
      address: "Opp. IIITDM Bus Stop, Market Road",
      city: "Nellore",
      state: "Andhra Pradesh",
      zipCode: "524001",
      phone: "+91 861 3344 5566",
      email: "manoj@lakshmipharmacy.in",
      latitude: 14.4450,
      longitude: 79.9870,
      operatingHours: "Mon–Sat: 8 AM – 9 PM",
      isDeliveryAvailable: false,
      isActive: true,
      rating: 4.0,
      ratingCount: 74,
      imageUrl: "",
      estimatedDeliveryTime: "Pickup only",
      services: ["Prescription filling", "Health products", "Surgical items"],
      ownerId: ID.pharmUser3,
      licenseNumber: "AP/NEL/PHARM/2024/0312",
      licenseDocumentUrl: "https://example.com/lakshmi_licence.pdf",
      verificationStatus: "APPROVED",
      createdAt: daysAgo(110),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(pharmacies)) {
    batch.set(db.collection("pharmacies").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ pharmacies: ${Object.keys(pharmacies).length} documents`);
}

// =====================================================================
//  3. PHARMACY INVENTORY
// =====================================================================
async function seedPharmacyInventory() {
  const items = [
    // ── Sri Sai Medicals (full stock) ──
    { id: "inv_srisai_dolo", pharmacyId: ID.pharm1, medicineId: ID.med_dolo, medicineName: "Dolo 650", genericName: "paracetamol", dosage: "650mg", quantity: 500, unitPrice: 3.20, isAvailable: true, lowStockThreshold: 50, prescriptionRequired: false, batchNumber: "SSM-B2024-101" },
    { id: "inv_srisai_azee", pharmacyId: ID.pharm1, medicineId: ID.med_azee, medicineName: "Azee 500", genericName: "azithromycin", dosage: "500mg", quantity: 120, unitPrice: 72.50, isAvailable: true, lowStockThreshold: 15, prescriptionRequired: true, batchNumber: "SSM-B2024-102" },
    { id: "inv_srisai_glycomet", pharmacyId: ID.pharm1, medicineId: ID.med_glycomet, medicineName: "Glycomet 500", genericName: "metformin", dosage: "500mg", quantity: 600, unitPrice: 5.80, isAvailable: true, lowStockThreshold: 60, prescriptionRequired: true, batchNumber: "SSM-B2024-103" },
    { id: "inv_srisai_mox", pharmacyId: ID.pharm1, medicineId: ID.med_mox, medicineName: "Mox 250", genericName: "amoxicillin", dosage: "250mg", quantity: 200, unitPrice: 8.50, isAvailable: true, lowStockThreshold: 25, prescriptionRequired: true, batchNumber: "SSM-B2024-104" },
    { id: "inv_srisai_pantop", pharmacyId: ID.pharm1, medicineId: ID.med_pantop, medicineName: "Pantop 40", genericName: "pantoprazole", dosage: "40mg", quantity: 350, unitPrice: 6.40, isAvailable: true, lowStockThreshold: 30, prescriptionRequired: false, batchNumber: "SSM-B2024-105" },
    { id: "inv_srisai_allegra", pharmacyId: ID.pharm1, medicineId: ID.med_allegra, medicineName: "Allegra 120", genericName: "fexofenadine", dosage: "120mg", quantity: 180, unitPrice: 14.00, isAvailable: true, lowStockThreshold: 20, prescriptionRequired: false, batchNumber: "SSM-B2024-106" },
    { id: "inv_srisai_ecosprin", pharmacyId: ID.pharm1, medicineId: ID.med_ecosprin, medicineName: "Ecosprin 75", genericName: "aspirin", dosage: "75mg", quantity: 400, unitPrice: 1.80, isAvailable: true, lowStockThreshold: 40, prescriptionRequired: false, batchNumber: "SSM-B2024-107" },
    { id: "inv_srisai_thyronorm", pharmacyId: ID.pharm1, medicineId: ID.med_thyronorm, medicineName: "Thyronorm 50", genericName: "levothyroxine", dosage: "50mcg", quantity: 250, unitPrice: 3.50, isAvailable: true, lowStockThreshold: 30, prescriptionRequired: true, batchNumber: "SSM-B2024-108" },

    // ── Vijaya Drug House (partial stock, some out) ──
    { id: "inv_vijaya_dolo", pharmacyId: ID.pharm2, medicineId: ID.med_dolo, medicineName: "Dolo 650", genericName: "paracetamol", dosage: "650mg", quantity: 300, unitPrice: 3.50, isAvailable: true, lowStockThreshold: 50, prescriptionRequired: false, batchNumber: "VDH-B2024-201" },
    { id: "inv_vijaya_azee", pharmacyId: ID.pharm2, medicineId: ID.med_azee, medicineName: "Azee 500", genericName: "azithromycin", dosage: "500mg", quantity: 0, unitPrice: 78.00, isAvailable: false, lowStockThreshold: 15, prescriptionRequired: true, batchNumber: "VDH-B2024-202" },
    { id: "inv_vijaya_mox", pharmacyId: ID.pharm2, medicineId: ID.med_mox, medicineName: "Mox 250", genericName: "amoxicillin", dosage: "250mg", quantity: 80, unitPrice: 9.00, isAvailable: true, lowStockThreshold: 10, prescriptionRequired: true, batchNumber: "VDH-B2024-203" },
    { id: "inv_vijaya_allegra", pharmacyId: ID.pharm2, medicineId: ID.med_allegra, medicineName: "Allegra 120", genericName: "fexofenadine", dosage: "120mg", quantity: 8, unitPrice: 15.00, isAvailable: true, lowStockThreshold: 10, prescriptionRequired: false, batchNumber: "VDH-B2024-204" },
    { id: "inv_vijaya_pantop", pharmacyId: ID.pharm2, medicineId: ID.med_pantop, medicineName: "Pantop 40", genericName: "pantoprazole", dosage: "40mg", quantity: 150, unitPrice: 7.00, isAvailable: true, lowStockThreshold: 20, prescriptionRequired: false, batchNumber: "VDH-B2024-205" },

    // ── Lakshmi Pharmacy (pickup only, small stock) ──
    { id: "inv_lakshmi_dolo", pharmacyId: ID.pharm3, medicineId: ID.med_dolo, medicineName: "Dolo 650", genericName: "paracetamol", dosage: "650mg", quantity: 60, unitPrice: 3.00, isAvailable: true, lowStockThreshold: 10, prescriptionRequired: false, batchNumber: "LP-B2024-301" },
    { id: "inv_lakshmi_pantop", pharmacyId: ID.pharm3, medicineId: ID.med_pantop, medicineName: "Pantop 40", genericName: "pantoprazole", dosage: "40mg", quantity: 40, unitPrice: 6.00, isAvailable: true, lowStockThreshold: 5, prescriptionRequired: false, batchNumber: "LP-B2024-302" },
    { id: "inv_lakshmi_ecosprin", pharmacyId: ID.pharm3, medicineId: ID.med_ecosprin, medicineName: "Ecosprin 75", genericName: "aspirin", dosage: "75mg", quantity: 100, unitPrice: 1.50, isAvailable: true, lowStockThreshold: 10, prescriptionRequired: false, batchNumber: "LP-B2024-303" },
  ];

  const batch = db.batch();
  for (const item of items) {
    const { id, ...data } = item;
    data.updatedAt = now();
    batch.set(db.collection("pharmacyInventory").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ pharmacyInventory: ${items.length} documents`);
}

// =====================================================================
//  4. MEDICINES (patient's medicine list)
// =====================================================================
async function seedMedicines() {
  const medicines = {
    // ── Patient 1: Vikram Mehta ──
    [ID.med_dolo]: {
      userId: ID.patient1,
      name: "Dolo 650",
      dosage: "650mg",
      unit: "tablet",
      instructions: "Khana khane ke baad lena. 24 ghante mein 4 se zyada nahi.",
      reminderTimes: ["08:00", "20:00"],
      repeatType: "DAILY",
      color: "teal",
      isActive: true,
      alarmIds: [1001, 1002],
      prescriptionId: "",
      prescribedByDoctor: false,
      currentQuantity: 2,          // 🔴 OUT OF STOCK (below threshold)
      totalQuantity: 30,
      lowStockThreshold: 5,
      refillReminderEnabled: true,
      lastRefillDate: daysAgo(28),
      createdAt: daysAgo(90),
      updatedAt: now(),
    },
    [ID.med_azee]: {
      userId: ID.patient1,
      name: "Azee 500",
      dosage: "500mg",
      unit: "tablet",
      instructions: "Khali pet lena — khana khane se 1 ghanta pehle. Pura course khatam karna.",
      reminderTimes: ["09:00"],
      repeatType: "DAILY",
      color: "blue",
      isActive: true,
      alarmIds: [1003],
      prescriptionId: "rx_azee_001",
      prescribedByDoctor: true,
      currentQuantity: 0,          // 🔴 ZERO STOCK
      totalQuantity: 6,
      lowStockThreshold: 2,
      refillReminderEnabled: true,
      lastRefillDate: daysAgo(8),
      createdAt: daysAgo(8),
      updatedAt: now(),
    },
    [ID.med_glycomet]: {
      userId: ID.patient1,
      name: "Glycomet 500",
      dosage: "500mg",
      unit: "tablet",
      instructions: "Khana khane ke saath lena. Pet kharab na ho isliye.",
      reminderTimes: ["08:00", "14:00", "20:00"],
      repeatType: "DAILY",
      color: "purple",
      isActive: true,
      alarmIds: [1004, 1005, 1006],
      prescriptionId: "rx_glycomet_002",
      prescribedByDoctor: true,
      currentQuantity: 52,         // ✅ Healthy
      totalQuantity: 90,
      lowStockThreshold: 10,
      refillReminderEnabled: true,
      lastRefillDate: daysAgo(12),
      createdAt: daysAgo(120),
      updatedAt: now(),
    },
    [ID.med_mox]: {
      userId: ID.patient1,
      name: "Mox 250",
      dosage: "250mg",
      unit: "capsule",
      instructions: "Har 8 ghante mein lena. Antibiotic ka pura course khatam karna zaroori hai.",
      reminderTimes: ["07:00", "15:00", "23:00"],
      repeatType: "DAILY",
      color: "orange",
      isActive: true,
      alarmIds: [1007, 1008, 1009],
      prescriptionId: "rx_mox_003",
      prescribedByDoctor: true,
      currentQuantity: 3,          // 🟡 LOW STOCK
      totalQuantity: 21,
      lowStockThreshold: 5,
      refillReminderEnabled: true,
      lastRefillDate: daysAgo(6),
      createdAt: daysAgo(6),
      updatedAt: now(),
    },
    [ID.med_pantop]: {
      userId: ID.patient1,
      name: "Pantop 40",
      dosage: "40mg",
      unit: "tablet",
      instructions: "Subah khali pet — nashta se 30 minute pehle.",
      reminderTimes: ["07:00"],
      repeatType: "DAILY",
      color: "green",
      isActive: true,
      alarmIds: [1010],
      prescriptionId: "",
      prescribedByDoctor: false,
      currentQuantity: 22,         // ✅ Healthy
      totalQuantity: 30,
      lowStockThreshold: 5,
      refillReminderEnabled: true,
      lastRefillDate: daysAgo(8),
      createdAt: daysAgo(60),
      updatedAt: now(),
    },
    [ID.med_allegra]: {
      userId: ID.patient1,
      name: "Allegra 120",
      dosage: "120mg",
      unit: "tablet",
      instructions: "Din mein ek baar. Neend aa sakti hai.",
      reminderTimes: ["21:00"],
      repeatType: "AS_NEEDED",
      color: "pink",
      isActive: true,
      alarmIds: [1011],
      prescriptionId: "",
      prescribedByDoctor: false,
      currentQuantity: -1,         // Tracking not enabled
      totalQuantity: -1,
      lowStockThreshold: 5,
      refillReminderEnabled: false,
      lastRefillDate: null,
      createdAt: daysAgo(30),
      updatedAt: now(),
    },
    [ID.med_ecosprin]: {
      userId: ID.patient1,
      name: "Ecosprin 75",
      dosage: "75mg",
      unit: "tablet",
      instructions: "Raat ko khana khane ke baad lena.",
      reminderTimes: ["21:30"],
      repeatType: "DAILY",
      color: "red",
      isActive: true,
      alarmIds: [1012],
      prescriptionId: "rx_ecosprin_004",
      prescribedByDoctor: true,
      currentQuantity: 18,         // ✅ Healthy
      totalQuantity: 30,
      lowStockThreshold: 5,
      refillReminderEnabled: true,
      lastRefillDate: daysAgo(12),
      createdAt: daysAgo(90),
      updatedAt: now(),
    },
    [ID.med_thyronorm]: {
      userId: ID.patient1,
      name: "Thyronorm 50",
      dosage: "50mcg",
      unit: "tablet",
      instructions: "Subah sabse pehle khali pet lena. Nashta 30 min baad.",
      reminderTimes: ["06:30"],
      repeatType: "DAILY",
      color: "cyan",
      isActive: true,
      alarmIds: [1013],
      prescriptionId: "rx_thyro_005",
      prescribedByDoctor: true,
      currentQuantity: 4,          // 🟡 LOW STOCK
      totalQuantity: 30,
      lowStockThreshold: 5,
      refillReminderEnabled: true,
      lastRefillDate: daysAgo(26),
      createdAt: daysAgo(120),
      updatedAt: now(),
    },

    // ── Patient 2: Sneha Iyer ──
    [ID.med_crocin]: {
      userId: ID.patient2,
      name: "Crocin Advance",
      dosage: "500mg",
      unit: "tablet",
      instructions: "Bukhar aane par lena. Khana khane ke baad.",
      reminderTimes: ["08:00", "20:00"],
      repeatType: "AS_NEEDED",
      color: "teal",
      isActive: true,
      alarmIds: [2001, 2002],
      prescriptionId: "",
      prescribedByDoctor: false,
      currentQuantity: 15,
      totalQuantity: 20,
      lowStockThreshold: 5,
      refillReminderEnabled: true,
      lastRefillDate: daysAgo(10),
      createdAt: daysAgo(30),
      updatedAt: now(),
    },
    [ID.med_shelcal]: {
      userId: ID.patient2,
      name: "Shelcal 500",
      dosage: "500mg",
      unit: "tablet",
      instructions: "Dopahar ko khana khane ke baad ek tablet.",
      reminderTimes: ["13:00"],
      repeatType: "DAILY",
      color: "yellow",
      isActive: true,
      alarmIds: [2003],
      prescriptionId: "rx_shelcal_006",
      prescribedByDoctor: true,
      currentQuantity: 8,
      totalQuantity: 30,
      lowStockThreshold: 5,
      refillReminderEnabled: true,
      lastRefillDate: daysAgo(22),
      createdAt: daysAgo(45),
      updatedAt: now(),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(medicines)) {
    batch.set(db.collection("medicines").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ medicines: ${Object.keys(medicines).length} documents`);
}

// =====================================================================
//  5. ORDERS
// =====================================================================
async function seedOrders() {
  const orders = {
    // ── ACTIVE: Preparing (multi-item, delivery address) ──
    [ID.order1]: {
      userId: ID.patient1,
      medicineId: "",
      medicineName: "",
      medicineDosage: "",
      medicineUnit: "",
      quantity: 0,
      status: "PREPARING",
      notes: "Gate pe phone karna, bell kharab hai",
      prescriptionId: "",
      pharmacyName: "Sri Sai Medicals",
      pharmacyId: ID.pharm1,
      items: [
        { medicineId: ID.med_dolo, medicineName: "Dolo 650", medicineDosage: "650mg", quantity: 30, unitPrice: 3.20, totalPrice: 96.00, prescriptionRequired: false, prescriptionId: null, prescriptionVerified: false },
        { medicineId: ID.med_azee, medicineName: "Azee 500", medicineDosage: "500mg", quantity: 6, unitPrice: 72.50, totalPrice: 435.00, prescriptionRequired: true, prescriptionId: "rx_azee_001", prescriptionVerified: true },
      ],
      subtotal: 531.00,
      deliveryFee: 25.00,
      discount: 0.0,
      totalAmount: 556.00,
      deliveryAddress: {
        label: "Ghar",
        fullAddress: "Flat 302, Sri Vari Enclave, Ring Road, Nellore",
        landmark: "Balaji Sweets ke paas",
        latitude: 14.4430,
        longitude: 79.9885,
        contactPhone: "+91 98765 43210",
      },
      deliveryType: "DELIVERY",
      estimatedDeliveryMinutes: 25,
      currentLocation: null,
      lastLocationUpdate: null,
      statusHistory: [
        { status: "PENDING", changedAt: hoursAgo(2), note: "Sri Sai Medicals mein order diya" },
        { status: "CONFIRMED", changedAt: hoursAgo(1.8), note: "Pharmacy ne accept kiya" },
        { status: "PREPARING", changedAt: hoursAgo(1.5), note: "Dawai tayyar ho rahi hai" },
      ],
      cancelReason: "",
      estimatedDelivery: minutesFromNow(20),
      deliveredAt: null,
      createdAt: hoursAgo(2),
      updatedAt: now(),
    },

    // ── ACTIVE: Pending (just placed) ──
    [ID.order2]: {
      userId: ID.patient1,
      medicineId: ID.med_mox,
      medicineName: "Mox 250",
      medicineDosage: "250mg",
      medicineUnit: "capsule",
      quantity: 21,
      status: "PENDING",
      notes: "",
      prescriptionId: "rx_mox_003",
      pharmacyName: "Vijaya Drug House",
      pharmacyId: ID.pharm2,
      items: [
        { medicineId: ID.med_mox, medicineName: "Mox 250", medicineDosage: "250mg", quantity: 21, unitPrice: 9.00, totalPrice: 189.00, prescriptionRequired: true, prescriptionId: "rx_mox_003", prescriptionVerified: false },
        { medicineId: ID.med_thyronorm, medicineName: "Thyronorm 50", medicineDosage: "50mcg", quantity: 30, unitPrice: 3.50, totalPrice: 105.00, prescriptionRequired: true, prescriptionId: "rx_thyro_005", prescriptionVerified: true },
      ],
      subtotal: 294.00,
      deliveryFee: 25.00,
      discount: 0.0,
      totalAmount: 319.00,
      deliveryAddress: {
        label: "Office",
        fullAddress: "Wing B, 4th Floor, Landmark Business Park, Main Road, Nellore",
        landmark: "Starbucks ke upar",
        latitude: 14.4430,
        longitude: 79.9885,
        contactPhone: "+91 98765 43210",
      },
      deliveryType: "DELIVERY",
      estimatedDeliveryMinutes: 40,
      currentLocation: null,
      lastLocationUpdate: null,
      statusHistory: [
        { status: "PENDING", changedAt: now(), note: "Vijaya Drug House mein order diya" },
      ],
      cancelReason: "",
      estimatedDelivery: minutesFromNow(40),
      deliveredAt: null,
      createdAt: now(),
      updatedAt: now(),
    },

    // ── ACTIVE: Shipped (out for delivery) ──
    [ID.order3]: {
      userId: ID.patient1,
      medicineId: ID.med_ecosprin,
      medicineName: "Ecosprin 75",
      medicineDosage: "75mg",
      medicineUnit: "tablet",
      quantity: 30,
      status: "SHIPPED",
      notes: "",
      prescriptionId: "rx_ecosprin_004",
      pharmacyName: "Sri Sai Medicals",
      pharmacyId: ID.pharm1,
      items: [
        { medicineId: ID.med_ecosprin, medicineName: "Ecosprin 75", medicineDosage: "75mg", quantity: 30, unitPrice: 1.80, totalPrice: 54.00, prescriptionRequired: false, prescriptionId: null, prescriptionVerified: false },
        { medicineId: ID.med_pantop, medicineName: "Pantop 40", medicineDosage: "40mg", quantity: 30, unitPrice: 6.40, totalPrice: 192.00, prescriptionRequired: false, prescriptionId: null, prescriptionVerified: false },
      ],
      subtotal: 246.00,
      deliveryFee: 25.00,
      discount: 0.0,
      totalAmount: 271.00,
      deliveryAddress: {
        label: "Ghar",
        fullAddress: "Flat 302, Sri Vari Enclave, Ring Road, Nellore",
        landmark: "Balaji Sweets ke paas",
        latitude: 14.4430,
        longitude: 79.9885,
        contactPhone: "+91 98765 43210",
      },
      deliveryType: "DELIVERY",
      estimatedDeliveryMinutes: 25,
      currentLocation: new admin.firestore.GeoPoint(14.44, 79.98),
      lastLocationUpdate: now(),
      statusHistory: [
        { status: "PENDING", changedAt: hoursAgo(5), note: "Order diya" },
        { status: "CONFIRMED", changedAt: hoursAgo(4.5), note: "Accept hua" },
        { status: "PREPARING", changedAt: hoursAgo(4), note: "Pack ho raha hai" },
        { status: "READY", changedAt: hoursAgo(3), note: "Ready for dispatch" },
        { status: "SHIPPED", changedAt: hoursAgo(0.5), note: "Delivery boy nikal gaya" },
      ],
      cancelReason: "",
      estimatedDelivery: minutesFromNow(10),
      deliveredAt: null,
      createdAt: hoursAgo(5),
      updatedAt: now(),
    },

    // ── PAST: Delivered ──
    [ID.order4]: {
      userId: ID.patient1,
      medicineId: ID.med_glycomet,
      medicineName: "Glycomet 500",
      medicineDosage: "500mg",
      medicineUnit: "tablet",
      quantity: 90,
      status: "DELIVERED",
      notes: "",
      prescriptionId: "rx_glycomet_002",
      pharmacyName: "Sri Sai Medicals",
      pharmacyId: ID.pharm1,
      items: [
        { medicineId: ID.med_glycomet, medicineName: "Glycomet 500", medicineDosage: "500mg", quantity: 90, unitPrice: 5.80, totalPrice: 522.00, prescriptionRequired: true, prescriptionId: "rx_glycomet_002", prescriptionVerified: true },
      ],
      subtotal: 522.00,
      deliveryFee: 25.00,
      discount: 0.0,
      totalAmount: 547.00,
      deliveryAddress: {
        label: "Ghar",
        fullAddress: "Flat 302, Sri Vari Enclave, Ring Road, Nellore",
        landmark: "Balaji Sweets ke paas",
        latitude: 14.4430,
        longitude: 79.9885,
        contactPhone: "+91 98765 43210",
      },
      deliveryType: "DELIVERY",
      estimatedDeliveryMinutes: 25,
      currentLocation: null,
      lastLocationUpdate: null,
      statusHistory: [
        { status: "PENDING", changedAt: daysAgo(12), note: "Order diya" },
        { status: "CONFIRMED", changedAt: daysAgo(11.95), note: "Accept hua" },
        { status: "PREPARING", changedAt: daysAgo(11.9), note: "Pack ho raha hai" },
        { status: "READY", changedAt: daysAgo(11.85), note: "Ready" },
        { status: "SHIPPED", changedAt: daysAgo(11.8), note: "Nikal gaya" },
        { status: "DELIVERED", changedAt: daysAgo(11.75), note: "Deliver ho gaya" },
      ],
      cancelReason: "",
      estimatedDelivery: daysAgo(11.75),
      deliveredAt: daysAgo(11.75),
      createdAt: daysAgo(12),
      updatedAt: daysAgo(11.75),
    },

    // ── PAST: Cancelled ──
    [ID.order5]: {
      userId: ID.patient1,
      medicineId: ID.med_allegra,
      medicineName: "Allegra 120",
      medicineDosage: "120mg",
      medicineUnit: "tablet",
      quantity: 10,
      status: "CANCELLED",
      notes: "",
      prescriptionId: "",
      pharmacyName: "Lakshmi Pharmacy",
      pharmacyId: ID.pharm3,
      items: [
        { medicineId: ID.med_allegra, medicineName: "Allegra 120", medicineDosage: "120mg", quantity: 10, unitPrice: 14.00, totalPrice: 140.00, prescriptionRequired: false, prescriptionId: null, prescriptionVerified: false },
      ],
      subtotal: 140.00,
      deliveryFee: 0.0,
      discount: 0.0,
      totalAmount: 140.00,
      deliveryAddress: null,
      deliveryType: "PICKUP",
      estimatedDeliveryMinutes: 0,
      currentLocation: null,
      lastLocationUpdate: null,
      statusHistory: [
        { status: "PENDING", changedAt: daysAgo(3), note: "Lakshmi Pharmacy mein order diya" },
        { status: "CANCELLED", changedAt: daysAgo(2.9), note: "Patient ne cancel kiya" },
      ],
      cancelReason: "Paas ki dukaan mein mil gaya",
      estimatedDelivery: null,
      deliveredAt: null,
      createdAt: daysAgo(3),
      updatedAt: daysAgo(2.9),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(orders)) {
    batch.set(db.collection("orders").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ orders: ${Object.keys(orders).length} documents`);
}

// =====================================================================
//  6. HEALTH LOGS (7 days for patient1, 5 for patient2)
// =====================================================================
async function seedHealthLogs() {
  const logs = [];

  for (let i = 6; i >= 0; i--) {
    logs.push({
      id: `hl_vikram_day${i}`,
      data: {
        userId: ID.patient1,
        date: daysAgo(i),
        heartRate: 70 + Math.floor(Math.random() * 12),
        bloodPressureSystolic: 118 + Math.floor(Math.random() * 12),
        bloodPressureDiastolic: 74 + Math.floor(Math.random() * 8),
        glucoseLevel: +(95 + Math.random() * 25).toFixed(1),
        weight: +(74.0 + (Math.random() - 0.5) * 0.8).toFixed(1),
        temperature: +(36.4 + Math.random() * 0.5).toFixed(1),
        symptoms: i === 2 ? ["Headache", "Fatigue"] : (i === 5 ? ["Dizziness"] : []),
        notes: i === 2 ? "Dopahar ka khana skip kiya, sir dard ho gaya" : "",
        createdAt: daysAgo(i),
        updatedAt: daysAgo(i),
      },
    });
  }

  for (let i = 4; i >= 0; i--) {
    logs.push({
      id: `hl_sneha_day${i}`,
      data: {
        userId: ID.patient2,
        date: daysAgo(i),
        heartRate: 65 + Math.floor(Math.random() * 10),
        bloodPressureSystolic: 110 + Math.floor(Math.random() * 10),
        bloodPressureDiastolic: 70 + Math.floor(Math.random() * 8),
        glucoseLevel: +(88 + Math.random() * 18).toFixed(1),
        weight: +(58.5 + (Math.random() - 0.5) * 0.6).toFixed(1),
        temperature: +(36.3 + Math.random() * 0.4).toFixed(1),
        symptoms: i === 1 ? ["Body aches"] : [],
        notes: "",
        createdAt: daysAgo(i),
        updatedAt: daysAgo(i),
      },
    });
  }

  const batch = db.batch();
  for (const log of logs) {
    batch.set(db.collection("healthLogs").doc(log.id), log.data);
  }
  await batch.commit();
  console.log(`  ✅ healthLogs: ${logs.length} documents`);
}

// =====================================================================
//  7. REFILL ALERTS
// =====================================================================
async function seedRefillAlerts() {
  const alerts = {
    alert_dolo: {
      userId: ID.patient1,
      medicineId: ID.med_dolo,
      medicineName: "Dolo 650",
      alertType: "LOW_STOCK",
      urgency: "URGENT",
      message: "Sirf 2 tablet bachi hain. Aaj order kar do.",
      daysRemaining: 1,
      stockPercentage: 7,
      isRead: false,
      isActioned: true,
      orderId: ID.order1,
      createdAt: hoursAgo(6),
    },
    alert_azee: {
      userId: ID.patient1,
      medicineId: ID.med_azee,
      medicineName: "Azee 500",
      alertType: "OUT_OF_STOCK",
      urgency: "OUT_OF_STOCK",
      message: "Azee 500 khatam ho gaya hai. Course complete karna zaroori hai!",
      daysRemaining: 0,
      stockPercentage: 0,
      isRead: false,
      isActioned: true,
      orderId: ID.order1,
      createdAt: hoursAgo(5),
    },
    alert_mox: {
      userId: ID.patient1,
      medicineId: ID.med_mox,
      medicineName: "Mox 250",
      alertType: "LOW_STOCK",
      urgency: "LOW",
      message: "Sirf 3 capsule bache hain. 1 din ka stock hai.",
      daysRemaining: 1,
      stockPercentage: 14,
      isRead: true,
      isActioned: true,
      orderId: ID.order2,
      createdAt: hoursAgo(3),
    },
    alert_thyronorm: {
      userId: ID.patient1,
      medicineId: ID.med_thyronorm,
      medicineName: "Thyronorm 50",
      alertType: "LOW_STOCK",
      urgency: "LOW",
      message: "4 tablet bachi hain. 4 din ka stock hai.",
      daysRemaining: 4,
      stockPercentage: 13,
      isRead: false,
      isActioned: false,
      orderId: "",
      createdAt: daysAgo(1),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(alerts)) {
    batch.set(db.collection("refillAlerts").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ refillAlerts: ${Object.keys(alerts).length} documents`);
}

// =====================================================================
//  MAIN
// =====================================================================
// =====================================================================
//  8. APPOINTMENTS & DOCTOR AVAILABILITY
// =====================================================================
async function seedAppointments() {
  const docAvailability = {
    avail_ananya_mon: {
      doctorId: ID.doctor1,
      doctorName: "Dr. Ananya Sharma",
      dayOfWeek: 1, // Monday
      startTime: "09:00",
      endTime: "17:00",
      slotDurationMinutes: 30,
      isActive: true,
      createdAt: daysAgo(200),
      updatedAt: now(),
    },
    avail_ananya_tue: {
      doctorId: ID.doctor1,
      doctorName: "Dr. Ananya Sharma",
      dayOfWeek: 2, // Tuesday
      startTime: "10:00",
      endTime: "18:00",
      slotDurationMinutes: 30,
      isActive: true,
      createdAt: daysAgo(200),
      updatedAt: now(),
    },
    avail_rohan_wed: {
      doctorId: ID.doctor2,
      doctorName: "Dr. Rohan Gupta",
      dayOfWeek: 3, // Wednesday
      startTime: "09:00",
      endTime: "16:00",
      slotDurationMinutes: 45,
      isActive: true,
      createdAt: daysAgo(150),
      updatedAt: now(),
    },
  };

  const appointments = {
    apt_001: {
      doctorId: ID.doctor1,
      patientId: ID.patient1,
      doctorName: "Dr. Ananya Sharma",
      patientName: "Vikram Mehta",
      date: daysAgo(2),
      startTime: "10:00",
      endTime: "10:30",
      status: "CONFIRMED",
      type: "CONSULTATION",
      notes: "Regular checkup and prescription review",
      doctorNotes: "Patient doing well. Continue current medication.",
      cancellationReason: "",
      createdAt: daysAgo(10),
      updatedAt: daysAgo(8),
    },
    apt_002: {
      doctorId: ID.doctor2,
      patientId: ID.patient2,
      doctorName: "Dr. Rohan Gupta",
      patientName: "Sneha Iyer",
      date: daysAgo(7),
      startTime: "14:00",
      endTime: "14:45",
      status: "COMPLETED",
      type: "FOLLOW_UP",
      notes: "Follow-up for previous treatment",
      doctorNotes: "Condition improved. Reduce medication dosage.",
      cancellationReason: "",
      createdAt: daysAgo(15),
      updatedAt: daysAgo(5),
    },
    apt_003: {
      doctorId: ID.doctor1,
      patientId: ID.patient1,
      doctorName: "Dr. Ananya Sharma",
      patientName: "Vikram Mehta",
      date: daysAgo(-5), // 5 days in future
      startTime: "11:00",
      endTime: "11:30",
      status: "PENDING",
      type: "CHECKUP",
      notes: "Routine health checkup",
      doctorNotes: "",
      cancellationReason: "",
      createdAt: now(),
      updatedAt: now(),
    },
  };

  let batch = db.batch();
  for (const [id, data] of Object.entries(docAvailability)) {
    batch.set(db.collection("doctorAvailability").doc(id), data);
  }
  for (const [id, data] of Object.entries(appointments)) {
    batch.set(db.collection("appointments").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ doctorAvailability: ${Object.keys(docAvailability).length} documents`);
  console.log(`  ✅ appointments: ${Object.keys(appointments).length} documents`);
}

// =====================================================================
//  9. DOCTOR NOTES
// =====================================================================
async function seedDoctorNotes() {
  const notes = {
    note_001: {
      doctorId: ID.doctor1,
      doctorName: "Dr. Ananya Sharma",
      patientId: ID.patient1,
      patientName: "Vikram Mehta",
      note: "Patient reports occasional headaches. Recommended stress management and regular exercise.",
      category: "GENERAL",
      isPrivate: false,
      createdAt: daysAgo(5),
      updatedAt: daysAgo(5),
    },
    note_002: {
      doctorId: ID.doctor1,
      doctorName: "Dr. Ananya Sharma",
      patientId: ID.patient1,
      patientName: "Vikram Mehta",
      note: "BP readings stable. Continue current antihypertensive medication. Schedule follow-up in 1 month.",
      category: "DIAGNOSIS",
      isPrivate: false,
      createdAt: daysAgo(2),
      updatedAt: daysAgo(2),
    },
    note_003: {
      doctorId: ID.doctor2,
      doctorName: "Dr. Rohan Gupta",
      patientId: ID.patient2,
      patientName: "Sneha Iyer",
      note: "Lab reports reviewed. Calcium levels slightly low. Increased Shelcal dosage.",
      category: "LAB_RESULTS",
      isPrivate: false,
      createdAt: daysAgo(3),
      updatedAt: daysAgo(3),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(notes)) {
    batch.set(db.collection("doctorNotes").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ doctorNotes: ${Object.keys(notes).length} documents`);
}

// =====================================================================
//  10. PRESCRIPTIONS
// =====================================================================
async function seedPrescriptions() {
  const prescriptions = {
    presc_001: {
      patientId: ID.patient1,
      doctorId: ID.doctor1,
      doctorName: "Dr. Ananya Sharma",
      clinicalDecisionId: "",
      medicineId: ID.med_glycomet,
      medicationName: "Glycomet 500",
      medicationNameNormalized: "glycomet 500",
      dosage: "500mg",
      unit: "tablet",
      frequency: "Twice daily (BID)",
      route: "Oral",
      duration: "Indefinite",
      instructions: "Take with meals. Do not skip doses.",
      status: "ACTIVE",
      isActive: true,
      version: 1,
      versionHistory: [],
      startDate: daysAgo(120),
      endDate: null,
      stoppedAt: null,
      stopReason: "",
      createdAt: daysAgo(120),
      updatedAt: now(),
    },
    presc_002: {
      patientId: ID.patient1,
      doctorId: ID.doctor1,
      doctorName: "Dr. Ananya Sharma",
      clinicalDecisionId: "",
      medicineId: ID.med_azee,
      medicationName: "Azee 500",
      medicationNameNormalized: "azee 500",
      dosage: "500mg",
      unit: "tablet",
      frequency: "Once daily",
      route: "Oral",
      duration: "5 days",
      instructions: "Take on empty stomach. Complete full course.",
      status: "ACTIVE",
      isActive: true,
      version: 1,
      versionHistory: [],
      startDate: daysAgo(8),
      endDate: daysAgo(3),
      stoppedAt: null,
      stopReason: "",
      createdAt: daysAgo(8),
      updatedAt: daysAgo(3),
    },
    presc_003: {
      patientId: ID.patient2,
      doctorId: ID.doctor2,
      doctorName: "Dr. Rohan Gupta",
      clinicalDecisionId: "",
      medicineId: ID.med_shelcal,
      medicationName: "Shelcal 500",
      medicationNameNormalized: "shelcal 500",
      dosage: "500mg",
      unit: "tablet",
      frequency: "Once daily",
      route: "Oral",
      duration: "3 months",
      instructions: "Take after lunch. May cause constipation.",
      status: "ACTIVE",
      isActive: true,
      version: 1,
      versionHistory: [],
      startDate: daysAgo(45),
      endDate: null,
      stoppedAt: null,
      stopReason: "",
      createdAt: daysAgo(45),
      updatedAt: now(),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(prescriptions)) {
    batch.set(db.collection("prescriptions").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ prescriptions: ${Object.keys(prescriptions).length} documents`);
}

// =====================================================================
//  11. CONVERSATIONS & MESSAGES
// =====================================================================
async function seedConversations() {
  const conversations = {
    conv_001: {
      doctorId: ID.doctor1,
      patientId: ID.patient1,
      doctorName: "Dr. Ananya Sharma",
      patientName: "Vikram Mehta",
      doctorProfileUrl: "",
      patientProfileUrl: "",
      participantIds: [ID.doctor1, ID.patient1],
      lastMessage: "Please take your medication regularly and follow the diet plan I shared.",
      lastMessageSenderId: ID.doctor1,
      lastMessageTimestamp: daysAgo(1),
      unreadCountDoctor: 0,
      unreadCountPatient: 1,
      createdAt: daysAgo(30),
      updatedAt: daysAgo(1),
    },
    conv_002: {
      doctorId: ID.doctor2,
      patientId: ID.patient2,
      doctorName: "Dr. Rohan Gupta",
      patientName: "Sneha Iyer",
      doctorProfileUrl: "",
      patientProfileUrl: "",
      participantIds: [ID.doctor2, ID.patient2],
      lastMessage: "Thanks doctor, I'll start the new dosage today.",
      lastMessageSenderId: ID.patient2,
      lastMessageTimestamp: daysAgo(3),
      unreadCountDoctor: 1,
      unreadCountPatient: 0,
      createdAt: daysAgo(15),
      updatedAt: daysAgo(3),
    },
  };

  const messages = {
    msg_001: {
      conversationId: "conv_001",
      senderId: ID.patient1,
      senderName: "Vikram Mehta",
      senderRole: "PATIENT",
      text: "Doctor, I have been feeling dizzy lately. Is it related to my medication?",
      type: "TEXT",
      isRead: true,
      readAt: daysAgo(2),
      timestamp: daysAgo(2),
    },
    msg_002: {
      conversationId: "conv_001",
      senderId: ID.doctor1,
      senderName: "Dr. Ananya Sharma",
      senderRole: "DOCTOR",
      text: "It could be a side effect. Let's adjust your dosage. Please visit the clinic for a checkup.",
      type: "TEXT",
      isRead: true,
      readAt: daysAgo(1),
      timestamp: daysAgo(1),
    },
    msg_003: {
      conversationId: "conv_001",
      senderId: ID.doctor1,
      senderName: "Dr. Ananya Sharma",
      senderRole: "DOCTOR",
      text: "Please take your medication regularly and follow the diet plan I shared.",
      type: "TEXT",
      isRead: false,
      readAt: null,
      timestamp: daysAgo(1),
    },
    msg_004: {
      conversationId: "conv_002",
      senderId: ID.doctor2,
      senderName: "Dr. Rohan Gupta",
      senderRole: "DOCTOR",
      text: "Your calcium levels need to be improved. I'm increasing your Shelcal dosage to 2 tablets daily.",
      type: "TEXT",
      isRead: true,
      readAt: daysAgo(4),
      timestamp: daysAgo(4),
    },
    msg_005: {
      conversationId: "conv_002",
      senderId: ID.patient2,
      senderName: "Sneha Iyer",
      senderRole: "PATIENT",
      text: "Thanks doctor, I'll start the new dosage today.",
      type: "TEXT",
      isRead: true,
      readAt: daysAgo(3),
      timestamp: daysAgo(3),
    },
  };

  let batch = db.batch();
  for (const [id, data] of Object.entries(conversations)) {
    batch.set(db.collection("conversations").doc(id), data);
  }
  for (const [id, data] of Object.entries(messages)) {
    batch.set(db.collection("messages").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ conversations: ${Object.keys(conversations).length} documents`);
  console.log(`  ✅ messages: ${Object.keys(messages).length} documents`);
}

// =====================================================================
//  12. CLINICAL DECISIONS
// =====================================================================
async function seedClinicalDecisions() {
  const decisions = {
    cd_001: {
      doctorId: ID.doctor1,
      doctorName: "Dr. Ananya Sharma",
      patientId: ID.patient1,
      patientName: "Vikram Mehta",
      type: "PRESCRIPTION",
      priority: "NORMAL",
      status: "ACTIVE",
      title: "Diabetes Management Plan",
      description: "Comprehensive diabetes management with medication and lifestyle changes",
      isPublic: true,
      prescriptions: [
        {
          medicationName: "Glycomet 500",
          dosage: "500mg",
          unit: "tablet",
          frequency: "Twice daily (BID)",
          duration: "Indefinite",
          route: "Oral",
          instructions: "Take with meals",
          startDate: daysAgo(120),
          endDate: null,
          refills: 0,
          isActive: true,
          substitutionAllowed: true,
          warnings: ["May cause GI upset"],
        },
      ],
      followUpDate: daysAgo(-15),
      followUpInstructions: "Schedule next appointment after 1 month. Check blood glucose levels.",
      vitalAlerts: [
        { vitalType: "GLUCOSE_LEVEL", condition: "ABOVE", threshold: 200, message: "Blood glucose above normal", severity: "HIGH" },
      ],
      analysisSummary: "Patient shows good medication adherence. Glucose levels improving.",
      analyzedSymptoms: ["Fatigue"],
      analyzedVitals: {
        glucoseAvg: 145,
        glucoseTrend: "decreasing",
        overallAssessment: "Well-controlled diabetes",
        riskLevel: "low",
        concerns: [],
        recommendations: ["Continue current medication", "Maintain regular exercise"],
      },
      patientAcknowledged: true,
      acknowledgedAt: daysAgo(5),
      createdAt: daysAgo(120),
      updatedAt: daysAgo(2),
    },
    cd_002: {
      doctorId: ID.doctor2,
      doctorName: "Dr. Rohan Gupta",
      patientId: ID.patient2,
      patientName: "Sneha Iyer",
      type: "VITAL_ALERT",
      priority: "HIGH",
      status: "ACTIVE",
      title: "Calcium Deficiency Alert",
      description: "Patient's calcium levels below normal range. Medication adjustment required.",
      isPublic: true,
      prescriptions: [],
      followUpDate: daysAgo(-30),
      followUpInstructions: "Recheck calcium levels in 2 weeks.",
      vitalAlerts: [
        { vitalType: "WEIGHT", condition: "BELOW", threshold: 56, message: "Calcium deficiency detected", severity: "HIGH" },
      ],
      analysisSummary: "Low calcium affecting bone health",
      analyzedSymptoms: [],
      analyzedVitals: null,
      patientAcknowledged: true,
      acknowledgedAt: daysAgo(2),
      createdAt: daysAgo(10),
      updatedAt: daysAgo(2),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(decisions)) {
    batch.set(db.collection("clinicalDecisions").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ clinicalDecisions: ${Object.keys(decisions).length} documents`);
}

// =====================================================================
//  13. RISK SCORES
// =====================================================================
async function seedRiskScores() {
  const scores = {
    risk_vikram: {
      userId: ID.patient1,
      overallScore: 35,
      category: "LOW",
      vitalScore: 40,
      adherenceScore: 30,
      symptomScore: 35,
      dataQualityScore: 90,
      contributingFactorCount: 2,
      topFactor: "Occasional headaches",
      correlationRulesTriggered: ["BP-Headache correlation"],
      computedAt: now(),
    },
    risk_sneha: {
      userId: ID.patient2,
      overallScore: 52,
      category: "MODERATE",
      vitalScore: 60,
      adherenceScore: 45,
      symptomScore: 50,
      dataQualityScore: 85,
      contributingFactorCount: 3,
      topFactor: "Low calcium levels",
      correlationRulesTriggered: ["Calcium-Weight correlation", "Calcium-Symptom correlation"],
      computedAt: now(),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(scores)) {
    batch.set(db.collection("riskScores").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ riskScores: ${Object.keys(scores).length} documents`);
}

// =====================================================================
//  14. TRANSACTIONS
// =====================================================================
async function seedTransactions() {
  const txns = {
    txn_001: {
      userId: ID.patient1,
      orderId: ID.order1,
      medicineId: ID.med_dolo,
      medicineName: "Dolo 650",
      type: "PURCHASE",
      amount: 96.0,
      currency: "INR",
      status: "COMPLETED",
      pharmacyId: ID.pharm1,
      pharmacyName: "Sri Sai Medicals",
      prescriptionVerified: false,
      prescriptionId: "",
      notes: "Order payment successful",
      createdAt: hoursAgo(2),
    },
    txn_002: {
      userId: ID.patient1,
      orderId: ID.order1,
      medicineId: ID.med_azee,
      medicineName: "Azee 500",
      type: "PURCHASE",
      amount: 435.0,
      currency: "INR",
      status: "COMPLETED",
      pharmacyId: ID.pharm1,
      pharmacyName: "Sri Sai Medicals",
      prescriptionVerified: true,
      prescriptionId: "rx_azee_001",
      notes: "Prescription verified",
      createdAt: hoursAgo(2),
    },
    txn_003: {
      userId: ID.patient1,
      orderId: ID.order4,
      medicineId: ID.med_glycomet,
      medicineName: "Glycomet 500",
      type: "PURCHASE",
      amount: 522.0,
      currency: "INR",
      status: "COMPLETED",
      pharmacyId: ID.pharm1,
      pharmacyName: "Sri Sai Medicals",
      prescriptionVerified: true,
      prescriptionId: "rx_glycomet_002",
      notes: "Delivered successfully",
      createdAt: daysAgo(12),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(txns)) {
    batch.set(db.collection("transactions").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ transactions: ${Object.keys(txns).length} documents`);
}

// =====================================================================
//  15. MEDICINE INTAKES (medicine reminder log)
// =====================================================================
async function seedMedicineIntakes() {
  const intakes = {
    intake_001: {
      userId: ID.patient1,
      medicineId: ID.med_glycomet,
      medicineName: "Glycomet 500",
      dosage: "500mg",
      scheduledTime: "08:00",
      actualTime: "08:15",
      taken: true,
      skipped: false,
      missedReason: "",
      date: daysAgo(1),
      notes: "Taken with breakfast",
      createdAt: daysAgo(1),
    },
    intake_002: {
      userId: ID.patient1,
      medicineId: ID.med_glycomet,
      medicineName: "Glycomet 500",
      dosage: "500mg",
      scheduledTime: "14:00",
      actualTime: "14:45",
      taken: true,
      skipped: false,
      missedReason: "",
      date: daysAgo(1),
      notes: "Late but taken",
      createdAt: daysAgo(1),
    },
    intake_003: {
      userId: ID.patient1,
      medicineId: ID.med_pantop,
      medicineName: "Pantop 40",
      dosage: "40mg",
      scheduledTime: "07:00",
      actualTime: null,
      taken: false,
      skipped: true,
      missedReason: "Forgot",
      date: daysAgo(0),
      notes: "",
      createdAt: daysAgo(0),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(intakes)) {
    batch.set(db.collection("medicineIntakes").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ medicineIntakes: ${Object.keys(intakes).length} documents`);
}

// =====================================================================
//  16. AUDIT LOGS (admin tracking)
// =====================================================================
async function seedAuditLogs() {
  const logs = {
    audit_001: {
      actorId: ID.admin1,
      actorName: "Suresh Kumar",
      actorRole: "ADMIN",
      action: "VERIFIED_DOCTOR",
      resourceType: "User",
      resourceId: ID.doctor1,
      resourceName: "Dr. Ananya Sharma",
      changes: { status: "PENDING→APPROVED", verifiedAt: daysAgo(200) },
      ipAddress: "192.168.1.100",
      userAgent: "Mozilla/5.0",
      createdAt: daysAgo(200),
    },
    audit_002: {
      actorId: ID.admin1,
      actorName: "Suresh Kumar",
      actorRole: "ADMIN",
      action: "VERIFIED_PHARMACY",
      resourceType: "Pharmacy",
      resourceId: ID.pharm1,
      resourceName: "Sri Sai Medicals",
      changes: { verificationStatus: "PENDING→APPROVED" },
      ipAddress: "192.168.1.100",
      userAgent: "Mozilla/5.0",
      createdAt: daysAgo(190),
    },
  };

  const batch = db.batch();
  for (const [id, data] of Object.entries(logs)) {
    batch.set(db.collection("auditLogs").doc(id), data);
  }
  await batch.commit();
  console.log(`  ✅ auditLogs: ${Object.keys(logs).length} documents`);
}

// =====================================================================
//  MAIN
// =====================================================================
async function main() {
  console.log("🚀 MediTrack Firestore Seed — All 18 Collections\n");

  try {
    await seedUsers();
    await seedPharmacies();
    await seedPharmacyInventory();
    await seedMedicines();
    await seedOrders();
    await seedHealthLogs();
    await seedRefillAlerts();
    await seedAppointments();
    await seedDoctorNotes();
    await seedPrescriptions();
    await seedConversations();
    await seedClinicalDecisions();
    await seedRiskScores();
    await seedTransactions();
    await seedMedicineIntakes();
    await seedAuditLogs();

    console.log("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    console.log("✅ ALL 18 COLLECTIONS SEEDED SUCCESSFULLY");
    console.log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

    console.log("📊 Collections:");
    console.log("   1. users (8 docs)");
    console.log("   2. pharmacies (3 docs)");
    console.log("   3. pharmacyInventory (18 docs)");
    console.log("   4. medicines (10 docs)");
    console.log("   5. orders (5 docs)");
    console.log("   6. healthLogs (12 docs)");
    console.log("   7. refillAlerts (4 docs)");
    console.log("   8. doctorAvailability (3 docs)");
    console.log("   9. appointments (3 docs)");
    console.log("   10. doctorNotes (3 docs)");
    console.log("   11. prescriptions (3 docs)");
    console.log("   12. conversations (2 docs)");
    console.log("   13. messages (5 docs)");
    console.log("   14. clinicalDecisions (2 docs)");
    console.log("   15. riskScores (2 docs)");
    console.log("   16. transactions (3 docs)");
    console.log("   17. medicineIntakes (3 docs)");
    console.log("   18. auditLogs (2 docs)");
    console.log("   ─────────────────────────────");
    console.log("   TOTAL: ~103 documents seeded\n");

    console.log("👤 Test Accounts (create in Firebase Auth with password Test@123):\n");
    console.log("   PATIENT   vikram.mehta@gmail.com         Vikram Mehta");
    console.log("   PATIENT   sneha.iyer@gmail.com           Sneha Iyer");
    console.log("   DOCTOR    dr.ananya.sharma@meditrack.in   Dr. Ananya Sharma");
    console.log("   DOCTOR    dr.rohan.gupta@meditrack.in     Dr. Rohan Gupta");
    console.log("   ADMIN     suresh.admin@meditrack.in       Suresh Kumar");
    console.log("   PHARMACY  rajesh@srisaimedicals.in        Rajesh Naidu (Sri Sai Medicals)");
    console.log("   PHARMACY  kavitha@vijayadrugs.in          Kavitha Reddy (Vijaya Drug House)");
    console.log("   PHARMACY  manoj@lakshmipharmacy.in        Manoj Prasad (Lakshmi Pharmacy)");

    console.log("\n💊 Low Stock Alerts (Vikram):");
    console.log("   🔴 Dolo 650     — 2/30 tablets (URGENT)");
    console.log("   🔴 Azee 500     — 0/6 tablets  (OUT OF STOCK)");
    console.log("   🟡 Mox 250      — 3/21 capsules (LOW)");
    console.log("   🟡 Thyronorm 50 — 4/30 tablets  (LOW)");

    console.log("\n📦 Orders (Vikram):");
    console.log("   PREPARING  → Dolo + Azee at Sri Sai Medicals (with delivery address)");
    console.log("   PENDING    → Mox + Thyronorm at Vijaya Drug House (with delivery address)");
    console.log("   SHIPPED    → Ecosprin + Pantop at Sri Sai (out for delivery)");
    console.log("   DELIVERED  → Glycomet at Sri Sai (12 days ago)");
    console.log("   CANCELLED  → Allegra at Lakshmi Pharmacy (pickup, cancelled)");

    console.log("\n🏪 Pharmacies (Nellore):");
    console.log("   Sri Sai Medicals  — Ring Road     (delivery, full stock)");
    console.log("   Vijaya Drug House — Main Road     (delivery, partial stock)");
    console.log("   Lakshmi Pharmacy  — Market Road   (pickup only, small stock)");
    console.log("");

  } catch (error) {
    console.error("❌ Seed failed:", error);
  }

  process.exit(0);
}

main();
