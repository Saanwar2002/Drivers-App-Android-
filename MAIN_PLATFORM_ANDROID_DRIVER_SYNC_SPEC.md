# Main Platform ↔ Android Driver App Sync & Integration Specification

**Target Repository:** Main AnyTrader / AnyRoller Web & Backend Platform (`server.ts`, `src/server/*`, `src/components/passenger/PassengerBooking.tsx`, `src/components/driver/RideChat.tsx`, `firestore.rules`)  
**Companion Client:** Standalone Native Android **AnyRoller Driver** App (`applicationId: com.anyroller.driver`)  
**Shared Firebase Project:** `anytradercombined`  
**Shared Named Firestore Database ID:** `ai-studio-anytrader-44dab8b3-bbc9-4352-b725-2cbe7a1dfd2a` *(NEVER `(default)`)*

---

## Executive Summary

The standalone Android **AnyRoller Driver** app is already aligned with all 32 existing Express REST endpoints, the named Firestore database, `firestore.rules`, and FCM push channels (`ride_offers_urgent` and `ride_chat_updates`).

However, a full line-by-line audit of the main AnyTrader/AnyRoller backend and `PassengerBooking.tsx` revealed **6 critical/high-impact bugs on the main platform** (plus **4 recommended enhancements**) that will cause broken offer acceptance, blank driver details on the passenger screen, silent 401 push failures, missing passenger live GPS dots, or stuck passenger screens during abandonment if not updated on the main platform before live deployment.

### Quick Priority Checklist
- [ ] **P0-1 (`src/server/driverAuthorization.ts`)**: Allow `POST /api/rides/accept` to accept a driver's own `"offered"` ride instead of returning `409 Conflict`.
- [ ] **P0-2 (`src/server/driverAuthorization.ts`)**: Stamp verified driver profile fields (`driverName`, `driverPhone`, `vehicleInfo`, `vehiclePlate`, `driverRating`, `driverRequirePasscode`, `acceptedAt`) onto `ride_requests/{rideId}` on accept.
- [ ] **P0-3 (`src/server/driverAuthorization.ts` + `firestore.rules` + `PassengerBooking.tsx`)**: Stamp `driverId` onto `live_tracking/{passengerId}` on accept (and clear on trip end) so the assigned driver passes `firestore.rules` and sees the passenger's live blue GPS dot.
- [ ] **P0-4 (`src/components/passenger/PassengerBooking.tsx` + `RideChat.tsx`)**: Fix `assignedDriverInfo?.driverId` → `assignedDriverInfo?.uid` AND attach `Authorization: Bearer <idToken>` headers to all `/api/chat-push` fetch calls (currently failing silently with `401 Unauthorized`).
- [ ] **P0-5 (`src/components/passenger/PassengerBooking.tsx`)**: Add a handler for `status === "rider_abandoned"` so the passenger UI does not freeze after driver 2-step abandonment.
- [ ] **P0-6 (`server.ts` background dispatch worker + `driverAuthorization.ts`)**: Send `ride_offers_urgent` FCM pushes when the fairness dispatch loop assigns an offer, use `.set(..., { merge: true })` on `driver_status`, and preserve the active ride's `passengerId` in `live_tracking/{driverId}` when accepting a stacked/queued 2nd ride.
- [ ] **P1 Enhancements**: Server-side `POST /api/rides/decline`, server-side FCM pushes inside `transition` & `complete`, `update_eta` (`liveEtaSeconds` / `stackedDriverDelay`) transition action, `POST /api/rides/rate-passenger`, two-way spoken ride chat read-aloud (`P1-6`), and atomic `totalBusinessMileage` increment on ride completion (`P1-7`).

---

## Priority 0 (P0) — Critical Main-Platform Fixes

### P0-1 & P0-2 & P0-3: Fix `executeAcceptRideCommand` (`src/server/driverAuthorization.ts`)

**Current Bugs (Verified at `src/server/driverAuthorization.ts:149-201`):**
1. **409 Conflict on `"offered"` rides (Line 153):**
   Both the server fairness dispatch worker (`server.ts:1285`) and `PassengerBooking.tsx:2443` set `status: "offered"` AND `assignedDriverId: bestDriver`. However, `driverAuthorization.ts:153` checks:
   ```ts
   if (rideData.driverId || rideData.assignedDriverId) {
     throw new ConflictError("Ride has already been accepted by another driver");
   }
   ```
   This rejects the **exact driver the ride was offered to** with HTTP `409`! (The Android app has a rule-compliant Firestore transaction fallback, which bypasses the 409, **but** direct client writes are blocked by `firestore.rules:479` from writing `driverName`, `vehicleInfo`, `vehiclePlate`, `driverPhone`, `driverRating`, `acceptedAt`!)
2. **Missing Driver Identity Fields on `ride_requests/{rideId}` (Lines 167-173):**
   `PassengerBooking.tsx:2513-2524` reads `data.driverName`, `data.vehicleInfo`, `data.vehiclePlate`, `data.driverPhone`, `data.driverRating`, `data.driverRequirePasscode`, and `data.acceptedAt` directly from `ride_requests/{rideId}`. Currently `driverAuthorization.ts:167` only writes `{ driverId, assignedDriverId, status: "accepted", assignedAt, updatedAt }`, causing the Passenger Terminal to show `"Driver"`, `"Taxi"`, `"UNKNOWN"`, no phone number, and ignoring the driver's `requirePasscode` setting.
3. **Stacked Ride Overwrites Active Passenger's Live Tracking Access (Lines 187-200):**
   If a driver already has an `in_progress` ride and accepts a stacked (2nd) ride, unconditionally overwriting `live_tracking/{driverId}` with the 2nd ride's `passengerId` immediately causes `permission-denied` for the **1st passenger's** live map tracker!
4. **Driver Blocked From Reading Passenger Live GPS (`live_tracking/{passengerId}`):**
   `PassengerBooking.tsx:2739` writes passenger GPS to `live_tracking/{user.uid}` with `{ passengerId: user.uid, lat, lng, isPassenger: true }`, without `driverId`. Because `firestore.rules:504-510` requires `driverId == auth.uid` for the driver to read `live_tracking/{passengerId}`, the driver's map cannot display the passenger's live blue GPS dot until `driverId` is stamped onto `live_tracking/{passengerId}`.

**Required Implementation (`src/server/driverAuthorization.ts`):**

Inside `db.runTransaction` in `executeAcceptRideCommand`:

```ts
// 1. Read userRef, rideRef, AND existing driver live_tracking BEFORE any transaction writes
const userRef = db.collection("users").doc(callerUid);
const rideRef = db.collection("ride_requests").doc(rideId);
const driverRef = db.collection("driver_status").doc(effectiveDriverId);
const liveTrackingRef = db.collection("live_tracking").doc(effectiveDriverId);

const [userDoc, rideDoc, existingTrackingDoc] = await Promise.all([
  transaction.get(userRef),
  transaction.get(rideRef),
  transaction.get(liveTrackingRef),
]);

const userData = userDoc.exists ? userDoc.data() || {} : null;
assertAuthoritativeDriverEligibility(userData, callerUid);

if (!rideDoc.exists) {
  throw new BadRequestError("Ride request not found");
}
const rideData = rideDoc.data() || {};

if (
  rideData.status !== "pending" &&
  rideData.status !== "searching" &&
  rideData.status !== "offered"
) {
  throw new ConflictError("Ride is no longer available");
}

// FIX P0-1: Allow the offered driver to accept their own "offered" ride.
// Reject only if already accepted (driverId set) or currently offered to a DIFFERENT driver.
const existingDriverId =
  typeof rideData.driverId === "string" ? rideData.driverId.trim() : "";
const existingAssignedDriverId =
  typeof rideData.assignedDriverId === "string" ? rideData.assignedDriverId.trim() : "";

if (existingDriverId) {
  throw new ConflictError("Ride has already been accepted by another driver");
}
if (existingAssignedDriverId && existingAssignedDriverId !== effectiveDriverId) {
  // Check if offer expired before rejecting (optional grace for expired offers)
  const rawExpiry = rideData.offerExpiresAt;
  const expiryMs =
    typeof rawExpiry === "number"
      ? rawExpiry
      : typeof rawExpiry === "string"
        ? Date.parse(rawExpiry)
        : rawExpiry?.toMillis?.() ?? 0;
  if (!expiryMs || expiryMs > Date.now()) {
    throw new ConflictError("Ride is currently offered to another driver");
  }
}

const passengerId = rideData.passengerId || rideData.userId || rideData.riderId;
if (passengerId === callerUid) {
  throw new BadRequestError("Passengers cannot accept their own ride requests as a driver.");
}
resolvedPassengerId = passengerId || undefined;

// FIX P0-2: Build authoritative driver metadata from users/{callerUid}
const driverName =
  userData?.displayName ||
  userData?.name ||
  [userData?.firstName, userData?.lastName].filter(Boolean).join(" ").trim() ||
  "AnyRoller Driver";

const vehicleInfo =
  userData?.vehicleInfo ||
  [userData?.vehicleColor, userData?.vehicleMake, userData?.vehicleModel]
    .filter(Boolean)
    .join(" ")
    .trim() ||
  userData?.carModel ||
  "Taxi";

const vehiclePlate =
  userData?.vehiclePlate ||
  userData?.licensePlate ||
  userData?.registrationPlate ||
  userData?.plateNumber ||
  "";

const driverPhone = userData?.phone || userData?.phoneNumber || "";
const driverRating =
  typeof userData?.driverRating === "number"
    ? userData.driverRating
    : typeof userData?.rating === "number"
      ? userData.rating
      : 4.9;
const driverRequirePasscode = userData?.requirePasscode === true;
const driverPhotoUrl = userData?.photoURL || userData?.avatarUrl || null;

// Atomic update on ride_requests/{rideId}
transaction.update(rideRef, {
  driverId: effectiveDriverId,
  assignedDriverId: effectiveDriverId,
  status: "accepted",
  driverName,
  driverPhone,
  vehicleInfo,
  vehiclePlate,
  driverRating,
  driverRequirePasscode,
  ...(driverPhotoUrl ? { driverPhotoUrl } : {}),
  acceptedAt: admin.firestore.FieldValue.serverTimestamp(),
  assignedAt: admin.firestore.FieldValue.serverTimestamp(),
  updatedAt: admin.firestore.FieldValue.serverTimestamp(),
});

// FIX P0-6 (Stacked ride safety): Do not overwrite activeRideId / passengerId if already on another active ride
const existingTracking = existingTrackingDoc.exists ? existingTrackingDoc.data() || {} : {};
const isStackedAccept =
  existingTracking.status === "on_ride" &&
  typeof existingTracking.activeRideId === "string" &&
  existingTracking.activeRideId.length > 0 &&
  existingTracking.activeRideId !== rideId;

transaction.set(
  driverRef,
  {
    status: "busy",
    isBusy: true,
    pendingRideId: admin.firestore.FieldValue.delete(),
    ...(isStackedAccept ? { queuedRideId: rideId } : { currentRideId: rideId }),
    lastActiveAt: admin.firestore.FieldValue.serverTimestamp(),
  },
  { merge: true }
);

if (isStackedAccept) {
  transaction.set(
    liveTrackingRef,
    {
      driverId: effectiveDriverId,
      queuedRideId: rideId,
      queuedPassengerId: passengerId || null,
      isStackingEnabled: false,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );
} else {
  transaction.set(
    liveTrackingRef,
    {
      driverId: effectiveDriverId,
      activeRideId: rideId,
      passengerId: passengerId || null,
      riderId: passengerId || null,
      status: "on_ride",
      dropoffLat: rideData.dropoffLat ?? null,
      dropoffLng: rideData.dropoffLng ?? null,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );
}

// FIX P0-3: Stamp assigned driverId onto live_tracking/{passengerId} so the driver can read
// the passenger's live blue GPS dot without hitting firestore.rules permission-denied.
if (passengerId && !isStackedAccept) {
  const passengerTrackingRef = db.collection("live_tracking").doc(passengerId);
  transaction.set(
    passengerTrackingRef,
    {
      passengerId,
      driverId: effectiveDriverId,
      activeRideId: rideId,
      isPassenger: true,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );
}
```

> **Also clean up `live_tracking/{passengerId}` on ride end:** Inside `executeCompleteRideCommand` and `executeCancelRideCommand` (`src/server/rideLifecycleCommands.ts`), when a ride finishes (`completed`, `cancelled`, `rider_abandoned`), clear `driverId` and `activeRideId` on `live_tracking/{passengerId}` via `FieldValue.delete()` so the former driver immediately loses read access to the passenger's live GPS once the trip ends.

---

### P0-4: Fix Passenger & Web Chat Push Calls (`PassengerBooking.tsx`, `RideChat.tsx`, `DriverTerminal.tsx`, `DispatchEngine.tsx`)

**Current Bugs (Verified):**
1. **`PassengerBooking.tsx:624-635` (Incoming Popup Quick Reply)** and **`PassengerBooking.tsx:2232-2243` (1-Tap Quick Messages)**:
   - Checks `if (assignedDriverInfo?.driverId)` and sends `recipientId: assignedDriverInfo.driverId`.
   - **Bug:** `assignedDriverInfo` stores the driver UID under `.uid` (`PassengerBooking.tsx:2513`), **NOT** `.driverId`. As a result, `assignedDriverInfo?.driverId` is always `undefined`, so **no FCM push is ever sent to the Android driver** when a passenger taps a Quick Message or Quick Reply!
2. **Missing `Authorization: Bearer <idToken>` Header on `/api/chat-push`:**
   - `server.ts:9272` protects `POST /api/chat-push` with `requireAuth`.
   - The following web callers invoke `fetch("/api/chat-push", ...)` with ONLY `"Content-Type": "application/json"` and **no `Authorization` header**, silently failing with `401 Unauthorized`:
     - `src/components/passenger/PassengerBooking.tsx` lines `625`, `2233`, `2450`
     - `src/components/driver/RideChat.tsx` lines `91`, `129`, `159`
     - `src/components/driver/DriverTerminal.tsx` lines `2795`, `2982`, `3113`, `3658`, `3687`
     - `src/components/anyroller/DispatchEngine.tsx` line `300`

**Required Implementation:**
Create or reuse an authenticated helper in the web client:
```ts
const token = await user.getIdToken();
await fetch("/api/chat-push", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`,
  },
  body: JSON.stringify({
    recipientId: assignedDriverInfo.uid, // <-- MUST be .uid, NOT .driverId!
    title: "New Message from Passenger",
    body: text,
    rideId: currentRideId,
    type: "chat",
    channelId: "ride_chat_updates",
  }),
});
```
Apply the `Authorization: Bearer ${token}` header to **all** `/api/chat-push` call sites listed above, and change `assignedDriverInfo?.driverId` → `assignedDriverInfo?.uid` at `PassengerBooking.tsx:624, 629, 2232, 2237`.

---

### P0-5: Handle `status === "rider_abandoned"` in `PassengerBooking.tsx`

**Current Bug (Verified at `PassengerBooking.tsx:2650-2675` & `rideLifecycleCommands.ts:279`):**
When an Android (or Web) driver completes the 2-step **Passenger Abandoned** flow (`POST /api/rides/complete` with `action: "abandonment"`), the server updates `ride_requests/{rideId}` to `status: "rider_abandoned"` and records `abandonmentFee` / `pendingCharges` / optional `paymentUrl`.
`PassengerBooking.tsx` currently handles `completed`, `cancelled`, `awaiting_payment`, and `awaiting_cash_confirm`, but has **no branch for `data.status === "rider_abandoned"`** — leaving the passenger stuck on the active-trip map screen indefinitely.

**Required Implementation (`src/components/passenger/PassengerBooking.tsx`, right after the `cancelled` handler around line 2673):**
```ts
if (data.status === "rider_abandoned") {
  setCompletedRideData({ ...data, id: currentRideId, isAbandoned: true });
  setStep("receipt"); // Or show a dedicated Abandonment Fee / Settlement notice
  setCurrentRideId(null);
  setAssignedDriverInfo(null);
  lastSoundStatusRef.current = null;
  hasTriggeredTipModalRef.current = false;
  setShowTipModal(false);
  toast.warning(
    data.abandonmentFee
      ? `Trip ended due to passenger abandonment. A £${Number(data.abandonmentFee).toFixed(2)} fee was applied.`
      : "This trip was closed by the driver due to passenger abandonment."
  );
  // Clear cached booking state & revoke driverId from passenger live_tracking
  localStorage.removeItem("passenger_temp_pickup");
  localStorage.removeItem("passenger_temp_dropoff");
  localStorage.removeItem("passenger_temp_comments");
  localStorage.removeItem("passenger_temp_pickup_coords");
  localStorage.removeItem("passenger_temp_dropoff_coords");
  localStorage.removeItem("passenger_temp_stops");
}
```

---

### P0-6: Wake Backgrounded Android Drivers from Backend Fairness Dispatch Worker (`server.ts:1281-1297`)

**Current Bugs (Verified at `server.ts:1281-1297`):**
1. When the background fairness dispatch worker assigns `bestDriver` (`status: "offered"`, `assignedDriverId: bestDriver`), it does **not** call `dispatchAuthoritativeFcmPush` (`src/server/rideOfferDispatch.ts`). If the Android Driver app is backgrounded or screen-locked, the driver will NOT wake up with the urgent ringtone/heads-up offer notification.
2. Line 1293 calls `db.collection("driver_status").doc(bestDriver).update({ pendingRideId: rideDoc.id })`, which throws `NOT_FOUND` if a driver's `driver_status` doc was deleted or not yet created.

**Required Implementation (`server.ts:1281-1297`):**
```ts
if (bestDriver) {
  const nowMs = Date.now();
  const offerTimeoutMs = (cachedRides?.dispatchTimeoutSeconds || 15) * 1000;
  const expiresAtMs = nowMs + offerTimeoutMs;

  await rideDoc.ref.update({
    status: "offered",
    assignedDriverId: bestDriver,
    offerExpiresAt: new Date(expiresAtMs).toISOString(),
    dispatchAttempts: admin.firestore.FieldValue.increment(1),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // Use .set(..., { merge: true }) instead of .update() to prevent NOT_FOUND crashes
  await db.collection("driver_status").doc(bestDriver).set(
    {
      pendingRideId: rideDoc.id,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  // Send high-priority FCM push matching Android AnyRollerMessagingService.kt payload keys
  const fareNum = Number(ride.fareEstimate ?? ride.estimatedFare ?? 0);
  const fareLabel = Number.isFinite(fareNum) && fareNum > 0 ? `£${fareNum.toFixed(2)}` : "Fare est.";
  const pickupShort = String(ride.pickupAddress || "Nearby Pickup").slice(0, 80);
  const dropoffShort = String(ride.dropoffAddress || "Destination").slice(0, 80);

  void dispatchAuthoritativeFcmPush(db, getFirebaseMessagingClient(), {
    recipientId: bestDriver,
    title: ride.isPriority ? "Priority Ride Offer" : "New Ride Offer",
    body: `${fareLabel} • ${pickupShort} -> ${dropoffShort}`,
    channelId: RIDE_OFFER_CHANNEL_ID, // "ride_offers_urgent"
    eventType: "ride_offer",
    data: {
      type: "ride_offer",
      rideId: rideDoc.id,
      pickupAddress: pickupShort,
      dropoffAddress: dropoffShort,
      pickupLat: String(ride.pickupLat ?? ""),
      pickupLng: String(ride.pickupLng ?? ""),
      dropoffLat: String(ride.dropoffLat ?? ""),
      dropoffLng: String(ride.dropoffLng ?? ""),
      fareEstimate: String(fareNum || ""),
      surgeMultiplier: String(ride.surgeMultiplier ?? 1),
      vehicleCategory: String(ride.carCategory || ride.vehicleCategory || "standard"),
      isPriority: String(Boolean(ride.isPriority)),
      isPetFriendly: String(Boolean(ride.isPetFriendly)),
      offeredAtMs: String(nowMs),
      expiresAtMs: String(expiresAtMs),
    },
  }).catch((err) => console.error("[Dispatch] FCM offer push failed:", err));
}
```
> **Note:** Also check `server.ts:1313` (expired offer cleanup) — replace `.update(...)` on `driver_status/{assignedDriverId}` with `.set(..., { merge: true })` for the same crash safety, and ensure the worker respects `ride.declinedDriverIds` (if present) so it doesn't immediately re-offer a ride to a driver who just declined it.

---

## Priority 1 (P1) — High-Value Backend & Rules Enhancements

### P1-1: Server-Authoritative Push Notifications on Ride Transitions & Completion (`server.ts`)
Currently, `POST /api/rides/accept` and `POST /api/rides/cancel` call `notifyRideCounterpartyPush`, whereas `POST /api/rides/transition` (`server.ts:4755`) and `POST /api/rides/complete` (`server.ts:4775`) do **not**.
Add `notifyRideCounterpartyPush` inside `server.ts` after successful transitions/completions:
- `action === "arrived"` → Push passenger: `"Driver Has Arrived"`, `"Your driver is outside at the pickup point."` (`eventType: "ride_arrived"`).
- `action === "in_progress"` → Push passenger: `"Trip Started"`, `"Your journey is now in progress."` (`eventType: "ride_started"`).
- `action === "stop_progress"` → Push passenger: `"Stop Completed"`, `"Heading to your next destination."` (`eventType: "ride_stop_progress"`).
- `complete` (`action === "qr_pending"`) → Push passenger: `"Payment Link Ready"`, `"Please complete payment for your trip."` (`eventType: "ride_payment_required"`).
- `complete` (`action === "cash_report"`) → Push passenger: `"Confirm Cash Payment"`, `"Please confirm the cash fare paid to your driver."` (`eventType: "ride_cash_confirm"`).
- `complete` (`action === "auto_card"` or `cash_finalize`) → Push passenger: `"Trip Completed"`, `"Thanks for riding with AnyRoller! Your receipt is ready."` (`eventType: "ride_completed"`).
- `complete` (`action === "abandonment"`) → Push passenger: `"Trip Closed — Abandonment"`, `"Your driver closed the trip due to abandonment."` (`eventType: "ride_abandoned"`).

### P1-2: Add `POST /api/rides/decline` Endpoint for Instant Re-Dispatch
Currently, when a driver declines an `"offered"` ride, `firestore.rules:466` prevents the driver from clearing `assignedDriverId` directly, so the passenger waits out the remaining 0–15s `offerExpiresAt` timeout before the next driver is offered the ride.
Add `POST /api/rides/decline` (`requireAuth`):
- Verifies `rideData.status === "offered" && rideData.assignedDriverId === caller.uid && !rideData.driverId`.
- Atomically updates `ride_requests/{rideId}`:
  - `status: "pending"`
  - `assignedDriverId: FieldValue.delete()`
  - `offerExpiresAt: FieldValue.delete()`
  - `declinedDriverIds: FieldValue.arrayUnion(caller.uid)`
  - `updatedAt: FieldValue.serverTimestamp()`
- Updates `driver_status/{caller.uid}`: clears `pendingRideId` and increments `consecutiveDeclines`.

### P1-3: Support `live_eta` and `stacked_delay` Actions in `POST /api/rides/transition`
Because `firestore.rules:466` limits direct driver updates on `ride_requests/{rideId}` to `['status', 'location', 'updatedAt']`, drivers cannot directly write `liveEtaSeconds`, `durationMinutes`, or `stackedDriverDelay` (which `PassengerBooking.tsx:2950` reads to display accurate ETA for stacked rides).
Add optional `action: "update_eta"` to `executeTransitionRideCommand`:
- Validates caller is `driverId` and ride is in `accepted | arrived | in_progress`.
- Accepts bounded numeric `liveEtaSeconds` (0..14400) and/or `stackedDriverDelay` (0..60) and writes them authoritatively.

### P1-4: Add `POST /api/rides/rate-passenger` for Driver → Passenger Reviews
Currently `ride_reviews` only permits passenger-authored reviews (`passengerId == auth.uid`). Add a server-authoritative `POST /api/rides/rate-passenger` endpoint so the Android Driver app's post-trip passenger rating (1–5 stars + tags/comment) is durably persisted to `passenger_reviews/{rideId}` and updates the passenger's rolling average rating on `users/{passengerId}`.

### P1-5: Verify `firestore.rules` for `live_tracking/{docId}` (Main Repo Only)
Verify `firestore.rules:503-537` in the main platform repo:
1. **Queued/Stacked passenger visibility (optional):** In `allow get, list`, add:
   ```
   resource.data.get('queuedPassengerId', '') == request.auth.uid
   ```
   so a passenger whose ride is accepted as a stacked (2nd) ride can still see the driver's live map position while the driver finishes ride #1.
2. **Passenger GPS updates after `driverId` is stamped:** Confirm lines `527-530` remain intact:
   ```
   (
     resource.data.get('passengerId', '') == request.auth.uid &&
     request.resource.data.get('passengerId', '') == request.auth.uid
   )
   ```
   Because this branch does not block the existing `driverId` field, the passenger browser can continue calling `setDoc(doc(db, "live_tracking", user.uid), { passengerId: user.uid, lat, lng, updatedAt, isPassenger: true }, { merge: true })` without `permission-denied` even after `executeAcceptRideCommand` stamps `driverId` onto `live_tracking/{passengerId}`.

### P1-6: Two-Way Spoken Ride Chat Read-Aloud & Arrival Outside Sound (`PassengerBooking.tsx`)
The Android Driver app now reads every incoming passenger message aloud hands-free (respecting the driver's Mute Voice toggle) and plays a confirmation chime + voice alert when pressing **Arrived Outside**.

On the Passenger Web Terminal (`src/components/passenger/PassengerBooking.tsx`):
1. **Arrived Outside sound + speech (already present at lines 2554-2569):**
   - Keep `playSound('notification')`, `navigator.vibrate([300, 150, 300, 150, 500])`, and `window.speechSynthesis.speak(new SpeechSynthesisUtterance("Your driver has arrived outside."))` active when `data.status === "arrived"`.
   - Ensure the `"ride_arrived"` FCM push also plays the system notification sound when the passenger's screen is locked or browser is backgrounded.
2. **Add Spoken Read-Aloud for Incoming Driver Chat Messages (`PassengerBooking.tsx:1769-1805`):**
   - Add a small `const [muteVoiceAlerts, setMuteVoiceAlerts] = useState(false);` toggle (e.g., speaker icon in the active ride header / chat drawer) so passengers can mute spoken messages anytime.
   - Inside the active ride chat `onSnapshot` listener (`collection(db, "ride_requests", currentRideId, "chat")` around line 1777), when a brand-new message arrives from the driver (`latestMsg && latestMsg.id !== lastSpokenChatIdRef.current && latestMsg.senderId !== user.uid`), skip the initial historical batch on mount and speak every new incoming driver message aloud:
   ```ts
   const lastSpokenChatIdRef = useRef<string | null>(null);
   const chatInitializedRef = useRef(false);

   // Inside onSnapshot(q, (snapshot) => { ... }):
   if (!chatInitializedRef.current) {
     chatInitializedRef.current = true;
     lastSpokenChatIdRef.current = latestMsg?.id || null;
   } else if (latestMsg && latestMsg.id !== lastSpokenChatIdRef.current) {
     lastSpokenChatIdRef.current = latestMsg.id;
     if (!muteVoiceAlerts && typeof window !== "undefined" && "speechSynthesis" in window) {
       try {
         const driverFirstName = (assignedDriverInfo?.name || "Your driver").split(" ")[0];
         const cleanText = String(latestMsg.text || "").trim().slice(0, 220);
         const spoken = latestMsg.imageUrl && cleanText
           ? `Photo message from ${driverFirstName}: ${cleanText}`
           : latestMsg.imageUrl
             ? `${driverFirstName} sent you a photo.`
             : cleanText
               ? `Message from ${driverFirstName}: ${cleanText}`
               : "";
         if (spoken) {
           const utterance = new SpeechSynthesisUtterance(spoken);
           utterance.lang = "en-GB";
           utterance.rate = 1.0;
           window.speechSynthesis.speak(utterance);
         }
       } catch (e) {
         console.warn("Passenger chat TTS notice:", e);
       }
     }
   }
   ```
   *(Reset `chatInitializedRef.current = false` and `lastSpokenChatIdRef.current = null` whenever `currentRideId` changes.)*

### P1-7: Keep `users/{driverId}.totalBusinessMileage` & Expense CRUD Synced (`rideLifecycleCommands.ts` + `firestore.rules`)
The Android Driver app now achieves **100% A-to-Z parity** with the web Driver Terminal (`DriverExpenses`, `DriverDocuments`, `DriverEarnings`, `DriverAvailability`, `DriverZones`, `DriverInbox`, `DriverJobs`, `RideChat`, `DriverTerminal`). To keep web and Android tax/mileage dashboards identical:
1. **HMRC Auto-Tracked Mileage (`rideLifecycleCommands.ts`):** When `executeCompleteRideCommand` finalizes a trip (`auto_card`, `cash_finalize`, or `qr_paid`), atomically increment `users/{driverId}.totalBusinessMileage` by `Number(rideData.distanceMiles || 0)` via `FieldValue.increment(...)` (Android falls back to summing `completedRides.distanceMiles` if `totalBusinessMileage == 0`, so server increment ensures lifetime accuracy beyond query window limits).
2. **`driver_expenses/{expenseId}` CRUD (`firestore.rules`):** Confirm authenticated drivers can `create`, `update`, and `delete` their own `driver_expenses` docs (`driverId == request.auth.uid`) across all 7 categories (`Fuel`, `Insurance`, `Maintenance`, `Charging`, `Tolls`, `Car Wash`, `Other`) plus `date`, `amount`, `notes`, `receiptImage`, and `updatedAt`.
3. **Pro Tax / Gold Unlock (`users/{uid}`):** Android checks `proFeaturesEnabled === true || subscriptionType === "driver_gold" || earningsReportExport === true` and invokes `POST /api/create-checkout-session` with `mode: "subscription"` and `metadata: { type: "driver_gold", subscriptionType: "driver_gold", userId }` — matching `DriverExpenses.tsx` verbatim.

---

## Verified Android ↔ Main Platform Contract Matrix

The Android Driver app already implements and expects the following exact contracts — **do NOT rename or break these routes/fields** when updating the main platform:

### 1. Firestore Collections & Named Database
- **Database ID:** `ai-studio-anytrader-44dab8b3-bbc9-4352-b725-2cbe7a1dfd2a`
- **Admin Controls (Real-time observed by Android):**
  - `platform_config/rides` (fares, surge rules, vehicle types, cancellation/wait fees, `maxDailyDriverHours`, `commissionRate`, `fixedTripFee`, `dispatchTimeoutSeconds`)
  - `platform_config/global_tiers` (`providerModels.on_demand_transport.tiers.STANDARD` & `GOLD`)
  - `platform_config/global` (`maintenanceMode`, `maintenanceMessage`, `taxiPortalMode`, `taxiComingSoon`)
- **Driver & Ride Collections:**
  - `users/{uid}` (driver profile, `capabilities`, `vehicleMake`, `vehicleModel`, `vehicleColor`, `vehicleInfo`, `vehiclePlate` / `licensePlate` / `registrationPlate` / `plateNumber`, `totalBusinessMileage`, `proFeaturesEnabled`, `subscriptionType`, `earningsReportExport`, preferences, `verificationDocs`, `earningsGoals`, `weeklySchedule` with custom `startTime`/`endTime`, `requirePasscode`, `muteHeadsUpVolume`, `muteRideOfferAlerts`, `stripeAccountId`, `driverApproved`, `pendingPlatformFees`)
  - `live_tracking/{uid}` (always written with `SetOptions.merge()` by Android so server-stamped `passengerId`/`riderId`/`activeRideId` are preserved; includes `isOnline`, `driverId`, `lat`, `lng`, `heading`, `speed`, `vehicleCategory`, `vehicleCategories`, `isPetFriendly`, `isStackingEnabled`, `isLastJob`, `destinationModeActive`, `homeLat`, `homeLng`, `zoneEnabled`, `zoneMaxDistance`, `dropoffLat`, `dropoffLng`)
  - `live_tracking/{passengerId}` (observed read-only by Android during active rides for passenger live blue GPS dot)
  - `driver_status/{uid}` (Android writes both `online` and `isOnline`, plus `isBusy`, `status`, `lat`, `lng` — compatible with both `server.ts` fairness dispatch and web UI)
  - `ride_requests/{rideId}` + `ride_requests/{rideId}/chat/{messageId}` (Android reads `priorityFee`, `abandonmentFee`, `stops`, `currentStopIndex`, `isModifiedByPassenger`, `liveEtaSeconds`, `stackedDriverDelay`, `riderNotes`, `handshakeCode`, `paymentUrl`)
  - `driver_metrics/{uid}`, `driver_expenses/{expenseId}` (full Create / Edit / Delete with 7 categories & receipt photos), `notifications`, `reported_hazards`, `partners`

### 2. Verified REST API Endpoints (All 32 Routes Confirmed Against `server.ts`)
| Group | Method & Path | Auth |
|---|---|---|
| Health | `GET /api/health`, `GET /api/maintenance/status` | Public |
| Account & GDPR | `POST /api/account/accept-terms`, `POST /api/account/sync-expired-docs`, `POST /api/account/gdpr/export`, `POST /api/account/gdpr/delete-account` | Bearer JWT |
| Notifications & WhatsApp | `POST /api/notifications/register-token`, `POST /api/notifications/unregister-token`, `POST /api/chat-push`, `GET /api/notifications/preferences`, `PATCH /api/notifications/preferences`, `POST /api/notifications/whatsapp-activation-link`, `POST /api/notifications/presence`, `PATCH /api/notifications/{id}/read`, `DELETE /api/notifications/{id}` | Bearer JWT |
| Chat Safety | `GET /api/chat/blocks`, `POST /api/chat/report-block`, `DELETE /api/chat/blocks/{targetUserId}` | Bearer JWT |
| Driver Ops | `POST /api/driver/metrics/heartbeat`, `GET /api/rides/demand-zones`, `POST /api/driver/analytics-pulse` | Bearer JWT |
| Ride Lifecycle | `POST /api/rides/accept`, `POST /api/rides/decline` *(P1-2)*, `POST /api/rides/transition` *(incl. `update_eta` P1-3)*, `POST /api/rides/create-trip-payment`, `POST /api/rides/complete`, `POST /api/rides/cancel`, `POST /api/rides/rate-passenger` *(P1-4)* | Bearer JWT |
| Stripe & Billing | `GET /api/driver/stripe-balance/{driverId}`, `POST /api/driver/stripe-payout/{driverId}`, `GET /api/stripe/account-status`, `POST /api/stripe/create-connect-account`, `POST /api/stripe/create-login-link`, `GET /api/driver/fee-settlement-status`, `POST /api/driver/settle-fees`, `POST /api/create-checkout-session`, `POST /api/create-customer-portal-session`, `POST /api/cancel-subscription`, `POST /api/reactivate-subscription` | Bearer JWT |

### 3. FCM Push Channels & Data Keys (`AnyRollerMessagingService.kt`)
- **Urgent Ride Offers Channel:** `ride_offers_urgent` (`IMPORTANCE_HIGH`, Play-Policy-compliant high-priority heads-up banner with `BigTextStyle`, ringtone + vibration, **no** `USE_FULL_SCREEN_INTENT`; automatically suppressed when the app is foregrounded so the in-app Job Offer card renders cleanly without a duplicate OS banner)
  - Expected `data` keys: `type` (`"ride_offer"` | `"new_ride_offer"` | `"stacked_ride_offer"`), `rideId`, `pickupAddress`, `dropoffAddress`, `pickupLat`, `pickupLng`, `dropoffLat`, `dropoffLng`, `fareEstimate`, `surgeMultiplier`, `vehicleCategory`, `distanceMiles`, `offeredAtMs`, `expiresAtMs`.
- **Chat & Ride Updates Channel:** `ride_chat_updates` (`IMPORTANCE_DEFAULT`)
  - Expected `data` keys: `type` (`"chat"` | `"ride_cancelled"` | `"ride_expired"` | `"ride_accepted"` | `"ride_update"`), `rideId`, `title`, `body`.

### 4. Android Full Web-Parity Sync Notes (v2 — Complete A–Z Mirror)
The Android Driver app now mirrors **100% of the Web Driver Terminal (`src/components/driver/*`)** while keeping the live Map Window strictly clutter-free (Owner-Locked transparent `#2563EB` Navigation HUD):
- **Live Cockpit & Navigation:** 3D Head-Up camera (50° tilt, bearing follow) with manual map-drag pause & Recenter/Head-Up toggle, step-by-step live turn-by-turn voice guidance (1000m / 400m / 100m prep + ≤50m double-turn chaining + arrival zoom), cost-capped off-route recalculation (max 3/leg with warning banner), 2-tap Decline→`CONFIRM` on incoming & stacked offers, horizontal `SwipeConfirmButton` on all 4 critical trip actions (`MARK AS ARRIVED`, `START TRIP`, `GO NEXT`, `COMPLETE`), collapsible active trip card, 10s floating incoming chat quick-reply popup + two-way spoken chat read-aloud, fullscreen chat photo lightbox, real-time 3-tier pulsing battery pill, and 1-tap `CANCEL` on 3s hazard countdown.
- **Earnings, Expenses & Tax:** 4-period Earnings (`Today | Week | Month | Year`) + Custom Date-Range Statement + CSV export; Expenses with all 7 categories, Add/Edit/Delete, custom date, Month/Year filters, Category Donut chart, and HMRC Auto-Tracked Business Mileage card; Trip History & My Jobs with Day/Month/Year filters and tap-to-expand full fare breakdown.
- **Documents, Availability, Zones, Inbox & Menu:** Documents Active Vehicle + plate badge + "Book an MOT via AnyTrader"; Weekly Availability Daily Driving Hours cap + custom `HH:mm` start/end hours; Zones inline Home Base editor; Analytics Top Earner priority badge; Inbox tap-to-open detail modal with contextual deep-links (`Open Documents` / `Open Earnings`); Menu accidental-tap confirmation dialogs on all sensitive shift, passcode, and audio toggles.

### 5. Part 2 Industry-Standard Android Cockpit Enhancements (Zero Breaking Backend Changes)
All 15 Part 2 features are implemented natively on Android while keeping the **active ride map strictly clutter-free** and requiring **zero breaking changes** on the backend:
- **Offer Intelligence (Offer Cards Only):** Live `£/hr` & `£/mi` profitability estimator, smart tags (`AIRPORT`, `RAIL STATION`, `LONG TRIP`), and Direction-of-Travel compass pill (`Heading NE • X.X mi` / `Toward Home`).
- **Auto-Launch External Navigation (ON by Default):** Automatically launches Google Maps or Waze (with multi-stop waypoints) on Accept Ride and Start Trip; toggleable OFF anytime in *Navigation & Cockpit Display* settings.
- **Cockpit Ergonomics & Display:** Picture-in-Picture (PiP) mini status window when switching to Google Maps/Waze, Auto-Night / Dark / Daylight high-contrast map theme, Keep Screen Awake (ON by default), and optional Auto-Brightness Boost (OFF by default).
- **Coffee Cup Break Mode (IDLE-Only):** 15m / 30m / Open break pauses incoming offers without stopping location tracking or losing shift streak. Writes `status: "busy", isBusy: true, onBreak: true` to `driver_status/{uid}` (and `status: "busy"` to `live_tracking/{uid}`) until resumed, so existing server dispatch naturally skips the driver during break.
- **Safety, Speed & Fatigue:** Live `mph` readout integrated cleanly inside the Owner-Locked transparent `#2563EB` Navigation HUD with gentle voice/haptic over-speed alert (`Off / 30–70 mph`); 1-Tap Safety Toolkit (live UK GPS address/postcode, Share Live Location with trusted contact, Quick Incident Log, 999 SOS) housed inside the existing Hazard/SOS modal (zero extra map buttons); Smart Fatigue Break Reminder (`Off / 2h–5h`, IDLE & Menu only).
- **Automation, Shift Analytics & Tax OCR:** Live `This Shift` summary (duration, trips, `£/hr`, paid vs. deadhead miles) in Menu & Earnings; opt-in Auto-Accept Mode (OFF by default, min fare £ & max pickup miles); on-device ML Kit Receipt OCR photo scanner in Driver Expenses; and Play-Policy-compliant local 45m & 15m Scheduled Job Reminder alarms (`AlarmManager.setAndAllowWhileIdle`).
- **Logically Grouped Driver Menu & Settings:** Reorganized into 6 operational sections (*1. Shift & Dispatch Controls → 2. Navigation & Cockpit Display → 3. Safety, Audio & Alerts → 4. Vehicle & Service Tiers → 5. Operations & Business → 6. AnyTrader Ecosystem & Account*).

---

## Production & Firebase Console Checklist

Before shipping the Android APK connected to the live platform:
1. **Firebase Console → Project `anytradercombined` → Android App `com.anyroller.driver`:**
   - Register **both** the Debug SHA-1/SHA-256 and Play App Signing Release SHA-1/SHA-256 certificates so Google Sign-In (`CredentialManager`) succeeds on release builds.
   - Confirm `google-services.json` in `app/google-services.json` matches the `com.anyroller.driver` OAuth client ID and API key.
2. **Google Cloud Console (Maps & Places):**
   - Ensure **Maps SDK for Android** and **Directions API** are enabled on the Maps API key configured in AI Studio Secrets (`MAPS_API_KEY`).
   - If using key restrictions, whitelist package `com.anyroller.driver` + its SHA-1 fingerprints.
3. **Backend Base URL & Stripe Return Targets:**
   - Ensure `API_BASE_URL` in the Android build points to the live production Cloud Run URL (not a dev preview URL).
   - Ensure `sanitizeRedirectUrl` on the backend allows the return URLs sent by Stripe onboarding/checkout (`returnTarget: "driver-terminal"`).
4. **Deploy Updated `firestore.rules` (Main Repo Only):**
   - Run existing Firestore rules unit tests in the main repo before deploying (`firebase deploy --only firestore:rules`).

---

## End-to-End Verification Script (Passenger Web ↔ Android Driver App)

Run this live walkthrough after applying the P0 fixes on the main platform:

1. **Go Online & Admin Sync:**
   - Sign in as an approved driver on Android (`com.anyroller.driver`) and tap **Go Online**.
   - Verify `driver_status/{uid}` has `online: true, isBusy: false` and `live_tracking/{uid}` updates GPS every ~5s.
   - Change a surge multiplier or fee in Admin Controls → verify Android fare/demand overlay updates in real time.
2. **Offer Dispatch & Accept (Tests P0-1, P0-2, P0-3, P0-6):**
   - Book a ride on the Passenger Web Terminal (`PassengerBooking.tsx`).
   - Background or lock the Android driver phone → confirm urgent FCM offer wakes the phone (`ride_offers_urgent`).
   - Tap **Accept** on Android → verify `POST /api/rides/accept` returns `200 OK` (not `409`).
   - Verify Passenger Web immediately displays the driver's real **name, vehicle info, plate, phone, rating**, and live moving car marker (no `"Driver" / "Taxi" / "UNKNOWN"`).
   - Verify Android driver map shows the **passenger's live blue GPS dot** without `permission-denied`.
3. **Two-Way Chat & Push (Tests P0-4):**
   - Send a 1-tap Quick Message and a photo from Passenger Web → confirm Android receives the chat message in real time, plays the chat chime, increments the unread badge, and receives an FCM push when backgrounded (no `401` in browser network tab).
   - Send a Quick Reply and photo from Android → confirm Passenger Web receives it immediately.
4. **Full Trip Lifecycle & Dynamic Updates:**
   - Tap **Arrived** on Android → Passenger Web plays arrival alert/TTS and starts the wait-time timer.
   - Verify 4-digit handshake passcode (if enabled) and tap **Start Trip** → Passenger Web transitions to `in_progress`, Android switches to tighter 3.5s/8m GPS tracking.
   - Add a mid-trip stop or change destination on Passenger Web → verify Android immediately refreshes route, stop chips, and updated fare.
   - Advance stops and complete trip via **Auto-Card**, **QR Payment**, and **Cash Confirmation** → verify Passenger Web transitions to the receipt screen and driver earnings settle cleanly.
5. **Abandonment Flow (Tests P0-5):**
   - Start a test ride and trigger 2-step **Passenger Abandoned** on Android → verify Passenger Web transitions cleanly out of the active ride screen and displays the abandonment notice/fee.
6. **Stacked Ride Flow (Tests P0-6):**
   - While `in_progress` on Ride A with Stacking enabled, accept Ride B → verify Passenger A keeps live GPS tracking until Ride A finishes, and Passenger B's live tracking activates immediately once Ride A completes.
