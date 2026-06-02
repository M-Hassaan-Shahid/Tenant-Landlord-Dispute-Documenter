# ProofNest — Tenant-Landlord Dispute Documenter

A bilateral, timestamped property-record Android app (Kotlin · XML/Material 3 · Firebase) built as a semester final project. It prevents security-deposit disputes by letting landlords and tenants document a property's condition **together** at move-in and move-out, then auto-generates a tamper-resistant PDF report with both parties' digital signatures.

The high-level vision and module breakdown live in [`ProofNest_Project_Plan.md`](ProofNest_Project_Plan.md).

---

## ✨ What works

All 9 planned modules are implemented end-to-end.

| # | Module | Highlights |
|---|---|---|
| 1 | **Auth & roles** | Email/password signup, login, forgot-password, role selection (Landlord/Tenant), session via Firebase Auth, profile cached in Room |
| 2 | **Property management** | Landlord creates property, system generates 6-char invite code, tenant joins via code, landlord approves/rejects, status badges in dashboard |
| 3 | **Rooms & checklist** | Per-property rooms with custom names; per-room checklist items; **structure locks** once inspection begins |
| 4 | **Photo capture** | CameraX preview + capture, EXIF GPS + timestamp, burned-in HUD overlay (date, GPS, capturer UID), local cache via Room, background sync to Firebase Storage |
| 5 | **Dual verification + signatures** | Per-item Agree/Dispute review row, custom-canvas signature pad, both-party locking, FCM notification to the other party |
| 6 | **Move-out comparison** | Side-by-side Move-in vs Move-out card per item with per-photo strips, automatic delta classification (Unchanged / Improved / Degraded), damage summary table |
| 7 | **Disputes** | Either party can raise a dispute on any item with reason + counter-note; landlord can mark Resolved/Unresolved with notes |
| 8 | **PDF report** | One-tap multi-page PDF (Android `PdfDocument`) covering property meta, damage summary, every item with thumbnails + ratings + timestamps + GPS, all four signatures, dispute appendix. Shared via system share-sheet through a FileProvider |
| 9 | **Notifications** | FCM push service, in-app notification center, lease-ending reminders generated automatically when ≤ 14 days remain |

---

## 🏗️ Architecture

```
app/src/main/java/com/example/tenant_landlorddisputedocumenter/
├── ui/auth/SplashActivity.kt    ← LAUNCHER: sync + route to Main or Onboarding
├── MainActivity.kt              ← Bottom nav host (Dashboard / Notifications / Profile)
├── ProofNestApplication.kt      ← Owns the ServiceContainer
├── di/ServiceContainer.kt       ← Manual DI (auth, property, inspection, dispute, notification, report repos)
├── data/SyncCoordinator.kt      ← Full Firestore → Room sync + lease reminders
├── domain/model/                ← Pure Kotlin domain types (User, Property, Room, ChecklistItem, Photo, Signature, Dispute, …)
├── data/
│   ├── local/                   ← Room database + 8 DAOs + entities + TypeConverters
│   ├── remote/FirestorePaths.kt ← Canonical Firestore collection names
│   └── repository/              ← 6 repos coordinating Firebase + Room
├── notifications/
│   └── ProofNestMessagingService.kt
├── ui/
│   ├── SignaturePadView.kt      ← Custom canvas signature pad
│   └── auth, dashboard, property, rooms, inspection, dispute, report, profile, notifications (Fragments)
└── util/
    ├── DateUtils.kt
    ├── InviteCode.kt            ← 6-char ambiguous-glyph-free invite codes
    ├── Ids.kt
    ├── LocationHelper.kt        ← FusedLocationProvider wrapper
    ├── PhotoStamper.kt          ← Burns timestamp + GPS + UID into the photo + writes EXIF
    └── PdfReportGenerator.kt    ← Multi-page PdfDocument layout engine
```

**Pattern:** MVVM + Repository, `StateFlow` / `Flow` in ViewModels. ViewModels use `ViewModelProvider.Factory` with `ServiceContainer` — no Hilt to keep the build simple.

**Navigation:** `res/navigation/nav_graph.xml` (Navigation Component) with manual `Bundle` args (`propertyId`, `phase`).

**Offline-first:** every write goes to Room first (UI is instant), then propagates to Firebase. `SyncCoordinator` runs on cold start (`SplashActivity`) and dashboard pull-to-sync; pending photo uploads flush in `MainActivity.onResume` and after each capture.

---

## 🧰 Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.x |
| UI | XML layouts, Material 3, View Binding |
| Navigation | Navigation Component (Fragment) |
| DI | Manual (`ServiceContainer`) |
| Local DB | Room (+ KSP) |
| Remote DB | Firebase Firestore |
| Auth | Firebase Authentication (email + password) |
| Storage | Firebase Storage |
| Camera | CameraX (preview + image capture) |
| Location | Google Play Services Fused Location |
| Notifications | Firebase Cloud Messaging |
| Images | Glide (where loaded in lists) |
| PDF | Android `PdfDocument` |
| Permissions | Activity Result API |
| Async | Coroutines + Flow |

---

## 🚀 Building & running

### Prerequisites

* Android Studio (matching the AGP version pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml))
* JDK 17+ (Android Studio's bundled JBR works)
* A Firebase project (free tier is fine)

### One-time Firebase setup

1. Go to <https://console.firebase.google.com> and create a new project (e.g. `ProofNest`).
2. Add an **Android app** with package name `com.example.tenant_landlorddisputedocumenter`.
3. Download `google-services.json` and **replace** the placeholder file in `app/google-services.json`.
4. In Firebase Console enable:
   * Authentication → Sign-in method → **Email/Password**
   * Firestore Database → Create database (start in test mode for the demo)
   * Storage → Get started (start in test mode for the demo)
   * Cloud Messaging (no extra setup needed)
5. Deploy security rules from the repo root (tighten further for production):
   ```bash
   firebase deploy --only firestore:rules,storage
   ```
   Rule files: [`firestore.rules`](firestore.rules), [`storage.rules`](storage.rules).

### Running

```bash
./gradlew :app:assembleDebug
# or open the project in Android Studio and hit Run
```

The output APK lives at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📂 Firestore schema (mirrored locally in Room)

```
users/{uid}
  ├── email, displayName, phone, cnic, role, createdAtMillis, fcmToken

properties/{propertyId}
  ├── address, rent, deposit, leaseStartMillis, leaseEndMillis
  ├── landlordId, tenantId, status, inviteCode, createdAtMillis, updatedAtMillis
  │
  ├── (subgraph stored locally) rooms/{roomId}
  │   └── items/{itemId}
  │       ├── moveInPhotos[], moveInNote, moveInRating, moveInTimestamp
  │       └── moveOutPhotos[], moveOutNote, moveOutRating, moveOutTimestamp
  │
  ├── signatures/{signatureId}
  └── disputes/{disputeId}
```

Property status lifecycle:

```
PENDING ─── tenant joins ──▶ PENDING_APPROVAL ──┐
                                                 ├──reject──▶ REJECTED ──new join──▶ PENDING_APPROVAL
                                                 └──approve─▶ ACTIVE ──move-in signed──▶ OCCUPIED
                                                                          └──move-out──▶ MOVE_OUT ──signed──▶ CLOSED
```

---

## 📱 Demo flow

1. Sign up as a **Landlord** → create a property → share the 6-char invite code.
2. Sign up as a **Tenant** on another device (or sign out + sign up again) → join with the code.
3. Landlord opens the property → **approves** the tenant request.
4. Both parties add rooms + checklist items while the property is **Active**.
5. Either party opens **Move-in inspection** → rate items → finish → **Review & sign**. After both sign, status becomes **Occupied**.
6. Landlord taps **Start move-out inspection** → status **Move-out**; both parties run **Move-out inspection** and sign again → **Closed**.
7. Lease-ending reminders appear in-app when ≤ 14 days remain (sync on launch and dashboard refresh).
8. Open **Side-by-side compare** → see deltas + damage summary.
9. Resolve any **Disputes**.
10. Open **Final PDF report** → share via WhatsApp / Email / Drive.

---

## ✅ Final deliverables checklist

The plan calls for these by semester end:

- [x] A fully functional Android application
- [x] Two demo accounts supported (Landlord + Tenant)
- [x] Move-in / move-out inspection cycle wired end-to-end
- [x] PDF report generation
- [x] Clear architecture (this README + the project plan)
- [x] Buildable source tree
- [ ] GitHub repo with this README (you push)
- [ ] Demo video walkthrough (you record)

---

## 🛡️ Permissions used

* `INTERNET` / `ACCESS_NETWORK_STATE` — Firebase
* `CAMERA` — CameraX preview & capture
* `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — GPS stamp on each photo
* `POST_NOTIFICATIONS` (Android 13+) — FCM + lease reminders

All runtime permissions are requested via Accompanist when first needed.

---

## 🧪 Notes for graders

* This is a **pure software** project — no hardware accessories, no AI dependencies.
* The PDF generator uses raw Android `PdfDocument` so the legal evidence is fully on-device and works offline.
* Photo evidence is double-stamped: a visible bottom-strip overlay (date / GPS / capturer ID) **and** EXIF GPS coordinates, so the timestamp cannot be silently changed.
* The Room schema is offline-first; Firebase is treated as the source of truth across devices but everything still works without network.
