/**
 * Seed script: Adds sample pharmacies to the Firestore `pharmacies` collection.
 *
 * Usage (requires Firebase CLI + Node.js):
 *   1. cd to project root
 *   2. node seed_pharmacies.js
 *
 * Or run from Firebase Admin SDK context.
 *
 * NOTE: Since Firestore rules block client-side writes to `pharmacies`,
 *       this script uses the Firebase Admin SDK (bypasses rules).
 *       Make sure you have a service account key or run via
 *       `firebase emulators:exec` or the Firebase Console.
 */

const admin = require("firebase-admin");

// Initialize — uses Application Default Credentials when deployed,
// or GOOGLE_APPLICATION_CREDENTIALS env var locally.
admin.initializeApp({
  projectId: "meditrack-635e2",
});

const db = admin.firestore();

const pharmacies = [
  {
    name: "MedPlus Pharmacy",
    address: "123 Health Street",
    city: "Hyderabad",
    state: "Telangana",
    zipCode: "500001",
    phone: "+91 40 1234 5678",
    email: "orders@medplus.in",
    latitude: 17.385,
    longitude: 78.4867,
    operatingHours: "Mon–Sat: 8AM–10PM, Sun: 9AM–6PM",
    isDeliveryAvailable: true,
    isActive: true,
    rating: 4.5,
    ratingCount: 234,
    imageUrl: "",
    estimatedDeliveryTime: "30–60 min",
    services: ["Home Delivery", "Prescription Upload", "Generic Alternatives"],
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  },
  {
    name: "Apollo Pharmacy",
    address: "456 Wellness Road",
    city: "Hyderabad",
    state: "Telangana",
    zipCode: "500034",
    phone: "+91 40 2345 6789",
    email: "care@apollopharmacy.in",
    latitude: 17.4225,
    longitude: 78.5438,
    operatingHours: "Open 24/7",
    isDeliveryAvailable: true,
    isActive: true,
    rating: 4.7,
    ratingCount: 512,
    imageUrl: "",
    estimatedDeliveryTime: "45 min",
    services: [
      "Home Delivery",
      "24/7 Service",
      "Prescription Refills",
      "Health Check-ups",
    ],
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  },
  {
    name: "Netmeds Store",
    address: "789 Care Avenue",
    city: "Hyderabad",
    state: "Telangana",
    zipCode: "500072",
    phone: "+91 40 3456 7890",
    email: "help@netmeds.com",
    latitude: 17.3616,
    longitude: 78.4747,
    operatingHours: "Mon–Sat: 9AM–9PM",
    isDeliveryAvailable: true,
    isActive: true,
    rating: 4.3,
    ratingCount: 178,
    imageUrl: "",
    estimatedDeliveryTime: "Same day",
    services: ["Home Delivery", "Generic Medicines", "Subscription Refills"],
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  },
  {
    name: "Wellness Forever",
    address: "101 Pharma Lane",
    city: "Hyderabad",
    state: "Telangana",
    zipCode: "500018",
    phone: "+91 40 4567 8901",
    email: "info@wellnessforever.in",
    latitude: 17.4399,
    longitude: 78.4983,
    operatingHours: "Mon–Sun: 8AM–11PM",
    isDeliveryAvailable: false,
    isActive: true,
    rating: 4.1,
    ratingCount: 89,
    imageUrl: "",
    estimatedDeliveryTime: "",
    services: ["Walk-in", "Prescription Refills", "Health Products"],
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  },
  {
    name: "PharmEasy Pickup Point",
    address: "55 Digital Plaza",
    city: "Hyderabad",
    state: "Telangana",
    zipCode: "500081",
    phone: "+91 40 5678 9012",
    email: "support@pharmeasy.in",
    latitude: 17.395,
    longitude: 78.534,
    operatingHours: "Mon–Sat: 10AM–8PM",
    isDeliveryAvailable: true,
    isActive: true,
    rating: 4.4,
    ratingCount: 305,
    imageUrl: "",
    estimatedDeliveryTime: "1–2 hours",
    services: [
      "Home Delivery",
      "Online Ordering",
      "Discount on First Order",
      "Subscription Plans",
    ],
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  },
];

async function seed() {
  console.log("🏥 Seeding pharmacies...\n");

  const batch = db.batch();

  for (const pharmacy of pharmacies) {
    const ref = db.collection("pharmacies").doc();
    batch.set(ref, pharmacy);
    console.log(`  ✅ ${pharmacy.name} (${ref.id})`);
  }

  await batch.commit();
  console.log(`\n🎉 Successfully seeded ${pharmacies.length} pharmacies!`);
  process.exit(0);
}

seed().catch((err) => {
  console.error("❌ Seed failed:", err);
  process.exit(1);
});

