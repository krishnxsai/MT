/**
 * PHASE 3: REPOSITORY INTEGRATION - OFFLINE-FIRST ARCHITECTURE
 *
 * This guide demonstrates how to use the offline-enabled repositories
 * to implement offline-first data access across the MediTrack app.
 *
 * ============================================================================
 * ARCHITECTURE LAYERS
 * ============================================================================
 *
 * Layer 1: Room Database (Local SQLite)
 *   └─ CachedMedicine, CachedAppointment, CachedHealthLog
 *   └─ PendingAction (write queue)
 *   └─ Max age: MEDICINE_TTL_MS (24h), HEALTHLOG_TTL_MS (7 days)
 *
 * Layer 2: Repositories (Business Logic)
 *   └─ MedicineRepositoryOfflineEnabled
 *   └─ AppointmentRepositoryOfflineEnabled
 *   └─ HealthLogRepositoryOfflineEnabled
 *   └─ Tries online first, falls back to cache when offline
 *   └─ Queues writes using PendingActionRepository
 *
 * Layer 3: Sync Infrastructure (Queue Management)
 *   └─ PendingActionRepository (CRUD on queue)
 *   └─ SyncHelper (sync orchestration)
 *   └─ SyncValidator (pre-sync validation)
 *   └─ ConflictResolver (last-write-wins)
 *   └─ InventoryConflictHandler (order-specific)
 *
 * Layer 4: Cloud (Firestore)
 *   └─ Source of truth
 *   └─ Updated after successful sync
 *
 * ============================================================================
 * USAGE EXAMPLES
 * ============================================================================
 *
 * 1. ADD DATA OFFLINE (Automatic Queueing)
 * ────────────────────────────────────────
 *
 *   val medicineRepo = MedicineRepositoryOfflineEnabled(context)
 *
 *   // Works online OR offline:
 *   val result = medicineRepo.addMedicine(medicine)
 *
 *   Online path:  medicine → Firestore ✓
 *                  ↓ (cache updated)
 *
 *   Offline path: medicine → PendingAction queue
 *                  ↓ (cache updated)
 *                  ↓ (auto-sync when network returns)
 *
 * 2. READ DATA (Cache-First Fallback)
 * ────────────────────────────────────
 *
 *   val result = medicineRepo.getMedicines()
 *
 *   Online path:  Fetch from Firestore → update cache ✓
 *
 *   Offline path: Cache miss? Network error!
 *                  ↓ (fallback)
 *                  Read from Room (up to 24h old)
 *
 * 3. REACTIVE UPDATES (Flow-Based)
 * ──────────────────────────────────
 *
 *   medicineRepo.getMedicinesFlow()
 *       .collect { result ->
 *           // Emits cached medicines immediately
 *           // Updates when network data arrives
 *           updateUI(result)
 *       }
 *
 * 4. ANALYTICS ON CACHE (Works Offline!)
 * ─────────────────────────────────────────
 *
 *   val avgBP = healthLogRepo.getAverageBPSystolic()
 *   // Calculates from cached health logs
 *   // No network required
 *
 * ============================================================================
 * SYNC FLOW (Automatic)
 * ============================================================================
 *
 * 1. User creates action while offline
 *    medicineRepo.addMedicine(medicine)
 *    └─ Queued in PendingAction table
 *
 * 2. Network reconnects
 *    DataSyncWorker.doWork() (periodic: 15 min)
 *
 * 3. SyncHelper.syncPendingActions() orchestrates:
 *
 *    Step 1: Pre-sync validation (SyncValidator)
 *            ✓ Quantity in [0-10K]?
 *            ✓ Expiry date in future?
 *            ✗ Invalid → dequeue, mark failed
 *
 *    Step 2: Check conflicts (ConflictResolver)
 *            ✓ No conflict → proceed
 *            ✓ Conflict detected → apply last-write-wins
 *            ✓ Local newer? → use local
 *            ✗ Remote newer? → skip (don't overwrite)
 *
 *    Step 3: Sync to Firestore
 *            POST to Firestore API
 *            ✓ Success → dequeue, mark synced
 *            ✗ Network error → retry with backoff
 *
 *    Step 4: Inventory management (orders only)
 *            Reduce pharmacy stock after sync
 *            ✗ Insufficient? → rollback
 *
 * 4. Cache updated with server timestamp
 *
 * ============================================================================
 * INTEGRATION IN VIEWMODELS
 * ============================================================================
 *
 * @HiltViewModel
 * class MedicineListViewModel @Inject constructor(
 *     private val context: Context
 * ) : ViewModel() {
 *
 *     private val medicineRepo = MedicineRepositoryOfflineEnabled(context)
 *
 *     private val _medicines = MutableLiveData<Resource<List<Medicine>>>()
 *     val medicines: LiveData<Resource<List<Medicine>>> = _medicines
 *
 *     private val _pendingCount = MutableLiveData<Int>(0)
 *     val pendingCount: LiveData<Int> = _pendingCount
 *
 *     init {
 *         // Observe pending operations
 *         viewModelScope.launch {
 *             medicineRepo.getPendingMedicineOperations().collect {
 *                 _pendingCount.postValue(it)
 *             }
 *         }
 *
 *         // Load medicines
 *         loadMedicines()
 *     }
 *
 *     private fun loadMedicines() {
 *         viewModelScope.launch {
 *             _medicines.value = Resource.Loading
 *
 *             // Reactive: emits cache, then network when available
 *             medicineRepo.getMedicinesFlow().collect { result ->
 *                 _medicines.postValue(result)
 *             }
 *         }
 *     }
 *
 *     fun addMedicine(medicine: Medicine) {
 *         viewModelScope.launch {
 *             val result = medicineRepo.addMedicine(medicine)
 *             // Result shows success even if offline (queued for sync)
 *             _medicines.postValue(result)
 *             loadMedicines()  // Refresh list
 *         }
 *     }
 * }
 *
 * ============================================================================
 * CACHE TTL & CLEANUP
 * ============================================================================
 *
 * CacheManager handles automatic TTL:
 *
 * MEDICINE_TTL_MS = 86_400_000         // 24 hours
 * APPOINTMENT_TTL_MS = 86_400_000      // 24 hours
 * HEALTHLOG_TTL_MS = 604_800_000       // 7 days
 *
 * Usage:
 *
 *   val cacheManager = CacheManager(context)
 *
 *   // Clean stale medicines (> 24h old)
 *   val cleanupResult = cacheManager.performCleanup()
 *   Log.d("Cache", "Deleted ${cleanupResult.medicinesDeleted} stale medicines")
 *
 *   // Called by RetentionCleanupWorker (daily)
 *
 * ============================================================================
 * ENCRYPTION (Sensitive Fields)
 * ============================================================================
 *
 * Sensitive data automatically encrypted in EncryptedSharedPreferences:
 *
 * val encryptor = EncryptionHelper(context)
 *
 * // Store encrypted phone
 * encryptor.storeSensitiveField(
 *     key = "user_phone_${userId}",
 *     value = "+919876543210",
 *     fieldType = FieldType.PHONE
 * )
 *
 * // Retrieve (automatically decrypted)
 * val phone = encryptor.retrieveSensitiveField(
 *     key = "user_phone_${userId}",
 *     fieldType = FieldType.PHONE
 * )
 * // phone = "+919876543210" (decrypted)
 *
 * ============================================================================
 * ERROR HANDLING
 * ============================================================================
 *
 * Resource<T> sealed class provides type-safe error handling:
 *
 * when (val result = medicineRepo.getMedicines()) {
 *     is Resource.Loading -> showLoadingBar()
 *
 *     is Resource.Success -> {
 *         // Has both data and optional error message
 *         updateUI(result.data)
 *         if (result.message != null) {
 *             showNotification("Showing cached data: ${result.message}")
 *         }
 *     }
 *
 *     is Resource.Error -> {
 *         // Error with optional data (stale cache)
 *         if (result.data != null) {
 *             showNotification("${result.message} - Using offline cache...")
 *             updateUI(result.data)
 *         } else {
 *             showError(result.message)
 *         }
 *     }
 * }
 *
 * ============================================================================
 * TESTING
 * ============================================================================
 *
 * Unit test with Room + Mockito:
 *
 *   @Test
 *   fun testAddMedicineOfflineQueueing() {
 *       val context = InstrumentationRegistry.getInstrumentation().context
 *       val repo = MedicineRepositoryOfflineEnabled(context)
 *       val medicine = Medicine(name="Aspirin", ...)
 *
 *       // Offline: should queue
 *       mockNetworkError()
 *       val result = runBlocking { repo.addMedicine(medicine) }
 *
 *       Assert.assertTrue(result is Resource.Success)
 *       Assert.assertTrue(pendingActionDao.getActionCount() > 0)
 *   }
 *
 * ============================================================================
 * NEXT STEPS
 * ============================================================================
 *
 * 1. Replace existing repository dependencies with *OfflineEnabled versions
 * 2. Update Hilt modules to provide offline-enabled repositories
 * 3. Test offline scenarios (disable network in Android Studio)
 * 4. Monitor DataSyncWorker logs during reconnection
 * 5. Verify cache cleanup runs daily (RetentionCleanupWorker)
 * 6. Test conflict scenarios (simultaneous edits online+offline)
 *
 * ============================================================================
 */
