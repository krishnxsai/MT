const { initializeApp } = require('firebase/app');
const { getAuth, createUserWithEmailAndPassword, signInWithEmailAndPassword, updateProfile } = require('firebase/auth');
const { getFirestore, doc, setDoc, updateDoc, deleteField, serverTimestamp } = require('firebase/firestore');

const firebaseConfig = {
  apiKey: 'AIzaSyD_0Ce6ijs0dwL4JR06UOQWXblZe6HugVw',
  authDomain: 'meditrack-635e2.firebaseapp.com',
  projectId: 'meditrack-635e2',
  storageBucket: 'meditrack-635e2.firebasestorage.app',
  messagingSenderId: '356123071358',
  appId: '1:356123071358:android:b6376cf4877bfbad4ad92f',
};

const ADMIN_EMAIL = 'admin@meditrack.com';
const ADMIN_PASSWORD = 'Admin@123456';
const ADMIN_DISPLAY_NAME = 'MediTrack Admin';
const BOOTSTRAP_KEY = 'MEDITRACK_BOOTSTRAP_2026';

async function seedAdmin() {
  console.log('Creating default admin account...');
  const app = initializeApp(firebaseConfig);
  const auth = getAuth(app);
  const db = getFirestore(app);
  let uid;
  try {
    const cred = await createUserWithEmailAndPassword(auth, ADMIN_EMAIL, ADMIN_PASSWORD);
    uid = cred.user.uid;
    console.log('Auth user created: ' + uid);
  } catch (err) {
    if (err.code === 'auth/email-already-in-use') {
      const cred = await signInWithEmailAndPassword(auth, ADMIN_EMAIL, ADMIN_PASSWORD);
      uid = cred.user.uid;
      console.log('Auth user already exists: ' + uid);
    } else {
      throw err;
    }
  }
  await updateProfile(auth.currentUser, { displayName: ADMIN_DISPLAY_NAME });
  console.log('Display name set');
  await setDoc(doc(db, 'users', uid), {
    email: ADMIN_EMAIL,
    displayName: ADMIN_DISPLAY_NAME,
    profileImageUrl: '',
    role: 'ADMIN',
    status: 'APPROVED',
    assignedDoctors: [],
    assignedDoctorNames: {},
    phoneNumber: '',
    licenseUrl: '',
    verifiedBy: 'SYSTEM',
    rejectionReason: '',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    bootstrapKey: BOOTSTRAP_KEY,
  }, { merge: true });
  console.log('Firestore document created');
  await updateDoc(doc(db, 'users', uid), { bootstrapKey: deleteField() });
  console.log('Bootstrap key removed');
  console.log('');
  console.log('Admin account ready!');
  console.log('Email:    ' + ADMIN_EMAIL);
  console.log('Password: ' + ADMIN_PASSWORD);
  console.log('UID:      ' + uid);
  console.log('Role:     ADMIN');
  console.log('Status:   APPROVED');
  console.log('');
  console.log('IMPORTANT: Change the password after first login!');
  process.exit(0);
}

seedAdmin().catch(function(err) {
  console.error('Admin seed failed: ' + (err.message || err));
  process.exit(1);
});
