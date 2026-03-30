# RAZORPAY PAYMENT FIX - CLOUD SECRET MANAGER INTEGRATION

## 🔐 Architecture Overview

Your Razorpay API key is managed securely through **Google Cloud Secret Manager**:

```
┌─────────────────────────────────────────────────────────────┐
│ Google Cloud Secret Manager                                 │
│ └─ razorpay_key_id (encrypted, rotated)                    │
└────────┬────────────────────────────────────────────────────┘
         │
         │ Backend Service reads secret
         ↓
┌─────────────────────────────────────────────────────────────┐
│ Firebase Remote Config                                      │
│ └─ razorpay_key_id parameter (populated from Secret Mgr)   │
└────────┬────────────────────────────────────────────────────┘
         │
         │ Android app fetches config
         ↓
┌─────────────────────────────────────────────────────────────┐
│ Android App (ConfigRepository)                              │
│ ├─ Primary: Firebase Remote Config                          │
│ └─ Fallback: AndroidManifest meta-data (empty for prod)     │
└────────────────────────────────────────────────────────────┘
```

---

## ✅ Changes Made

### 1. **ConfigRepository.kt** - NEW FILE
**Location:** `app/src/main/java/com/meditrack/app/data/repository/ConfigRepository.kt`

Provides centralized management of sensitive configurations:
```kotlin
class ConfigRepository @Inject constructor() {
    suspend fun getRazorpayKeyId(): String
    // Fetches from Firebase Remote Config
    // Falls back to manifest if unavailable
}
```

**Features:**
- ✅ Fetches from Firebase Remote Config (server-side source)
- ✅ Handles network errors gracefully
- ✅ Logs for diagnostics and monitoring
- ✅ Non-blocking availability check

### 2. **PaymentViewModel.kt** - UPDATED
**File:** `app/src/main/java/com/meditrack/app/ui/payment/PaymentViewModel.kt`

Added ConfigRepository injection:
```kotlin
@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val razorpayRepository: RazorpayRepository,
    private val orderRepository: OrderRepository,
    private val configRepository: ConfigRepository  // ← NEW
) : ViewModel()
```

### 3. **AndroidManifest.xml** - UPDATED COMMENT
**File:** `app/src/main/AndroidManifest.xml`

Updated to reference Cloud Secret Manager integration:
```xml
<meta-data
    android:name="com.razorpay.api_key"
    android:value="@string/razorpay_key_id" />
```

Comment explains the full architecture.

### 4. **strings.xml** - UPDATED
**File:** `app/src/main/res/values/strings.xml`

Empty placeholder (will be overridden by Remote Config):
```xml
<string name="razorpay_key_id"></string>
<!-- Populated by Firebase Remote Config from Cloud Secret Manager -->
```

---

## 🛠️ Backend Setup Required

### Step 1: Store Secret in Google Cloud Secret Manager

```bash
# Create secret
gcloud secrets create razorpay-key-id --data-file=<(echo "rzp_live_xxxxx")

# Grant permissions
gcloud secrets add-iam-policy-binding razorpay-key-id \
  --member=serviceAccount:YOUR-SERVICE-ACCOUNT@PROJECT.iam.gserviceaccount.com \
  --role=roles/secretmanager.secretAccessor
```

### Step 2: Backend Service Populates Firebase Remote Config

Your backend service should:

```javascript
// Node.js example
const admin = require('firebase-admin');
const secretManager = require('@google-cloud/secret-manager');

async function syncRazorpayKey() {
    // Step 1: Get secret from Cloud Secret Manager
    const client = new secretManager.SecretManagerServiceClient();
    const secretPath = `projects/PROJECT_ID/secrets/razorpay-key-id/versions/latest`;
    const [response] = await client.accessSecretVersion({ name: secretPath });
    const razorpayKey = response.payload.data.toString('utf8');

    // Step 2: Update Firebase Remote Config
    const remoteConfig = admin.remoteConfig();
    const template = await remoteConfig.getTemplate();

    template.parameters = {
        ...template.parameters,
        razorpay_key_id: {
            defaultValue: {
                value: razorpayKey
            },
            description: 'Razorpay API Key (from Cloud Secret Manager)'
        }
    };

    await remoteConfig.publishTemplate(template);
    console.log('✅ Razorpay key updated in Firebase Remote Config');
}

// Call on scheduled basis (Cloud Scheduler / Cloud Tasks)
exports.syncSecretsToRemoteConfig = async (req, res) => {
    try {
        await syncRazorpayKey();
        res.status(200).json({ status: 'success' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
};
```

### Step 3: Configure Firebase Remote Config Parameter

1. Go to Firebase Console → Remote Config
2. Add parameter: `razorpay_key_id`
3. Type: String
4. Default value: (empty - will be populated by backend)

---

## 🧪 Testing & Verification

### Local Development (Override in build.gradle)

For testing with a test key, add to `build.gradle`:

```gradle
android {
    defaultConfig {
        resValue "string", "razorpay_key_id", "rzp_test_xxxxx"  // Test key
    }
}
```

### Production
- No hardcoded key needed
- Backend automatically syncs from Cloud Secret Manager
- App fetches from Firebase Remote Config
- Falls back to manifest meta-data if needed

### Verify Configuration

Check logs during app startup:
```
D/ConfigRepository: ✅ Razorpay key loaded from Remote Config (length: 30)
// OR
W/ConfigRepository: ⚠️ Remote Config: razorpay_key_id not found - SDK will use manifest meta-data
```

---

## 🔄 Key Rotation Strategy

### When to Rotate Razorpay Key

1. Suspected compromise
2. Regular security practice (quarterly/semi-annual)
3. After developer access changes

### Rotation Process

```bash
# Step 1: Generate new key in Razorpay dashboard
# Settings → API Keys → Generate New Key

# Step 2: Update Cloud Secret Manager
gcloud secrets versions add razorpay-key-id --data-file=<(echo "rzp_live_NEW_KEY")

# Step 3: Backend syncs automatically to Remote Config
# (Happens on next scheduled sync or manual trigger)

# Step 4: Apps fetch new key from Remote Config
# (Next time app calls ConfigRepository.getRazorpayKeyId())

# Step 5: Deactivate old key in Razorpay dashboard
# (After verifying all apps using new key in logs)
```

**Zero downtime rotation:** Users automatically get new key via Remote Config

---

## 📊 Monitoring & Diagnostics

### Key Availability Check

```kotlin
// In any Activity/Fragment
viewModel.viewModelScope.launch {
    val isAvailable = configRepository.isRazorpayKeyAvailable()
    if (isAvailable) {
        Log.i("PaymentFlow", "✅ Razorpay key is available")
    } else {
        Log.w("PaymentFlow", "⚠️ Fallback to manifest meta-data")
    }
}
```

### Firebase Analytics Integration

```kotlin
analytics.logEvent("razorpay_key_loaded", Bundle().apply {
    putString("source", if (isAvailable) "remote_config" else "manifest")
})
```

---

## 🚀 Deployment Checklist

- [ ] ✅ **ConfigRepository.kt** created with Hilt injection
- [ ] ✅ **PaymentViewModel.kt** updated to inject ConfigRepository
- [ ] ✅ **AndroidManifest.xml** meta-data comment updated
- [ ] ✅ **strings.xml** has empty razorpay_key_id placeholder
- [ ] ✅ Build compiles successfully: `./gradlew build`
- [ ] ⏳ **Backend Setup:** Implement secret sync from Cloud Secret Manager → Firebase Remote Config
- [ ] ⏳ **Firebase Remote Config:** Add razorpay_key_id parameter
- [ ] ✅ **Local Dev:** Override in build.gradle with test key
- [ ] ⏳ **Production:** Backend populates Remote Config, no hardcoding

---

## 🔒 Security Best Practices

### ✅ What This Architecture Provides

1. **No hardcoded secrets in source code** - ConfigRepository pattern
2. **No secrets in strings.xml/build configs** - Empty placeholder only
3. **Server-side key management** - Cloud Secret Manager
4. **Audit trail** - All secret access logged in Cloud Secret Manager
5. **Key rotation without app update** - Remote Config propagation
6. **Encrypted at rest** - Cloud Secret Manager encryption
7. **Encrypted in transit** - Firebase Remote Config uses HTTPS

### ⚠️ Common Mistakes to Avoid

❌ Don't hardcode key in strings.xml
❌ Don't commit key to git
❌ Don't use `key_secret` instead of `key_id`
❌ Don't skip Remote Config setup - test with empty strings.xml value

---

## 📚 Related Documentation

- [Google Cloud Secret Manager](https://cloud.google.com/secret-manager/docs)
- [Firebase Remote Config](https://firebase.google.com/docs/remote-config)
- [Razorpay Android SDK](https://github.com/razorpay/razorpay-android)
- [Previous Fix: Inventory-Order Mismatch](./RAZORPAY_FIX_DEPLOYMENT_GUIDE.md)

---

## 🎯 Summary

| Component | Before | After |
|-----------|--------|-------|
| Key Storage | Hardcoded string | Cloud Secret Manager |
| Android Config | Build-time injection | Firebase Remote Config |
| Fallback | None | Manifest meta-data |
| Rotation | Manual app update | Automatic via Remote Config |
| Security | ⚠️ Exposed in code | ✅ Encrypted & managed |
