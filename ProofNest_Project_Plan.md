# ProofNest
### Tenant-Landlord Dispute Documenter
**Semester Final Project — Android App (Kotlin)**

---

## 📌 Project Overview

**ProofNest** is a mobile application that creates a legally timestamped, mutually verified property record between landlords and tenants. It prevents security deposit disputes by enabling **bilateral documentation** of property condition at move-in and move-out.

### Problem Statement
When a tenant moves out, landlords often unfairly deduct security deposits claiming damage. Tenants have no proof of the property's original condition. This leads to costly disputes, broken trust, and legal battles. No existing app solves this through **dual-party verified documentation**.

### Solution
A pure software Android app where both landlord and tenant document the property's condition together. Photos are timestamped and GPS-locked, both parties digitally sign records, and the app auto-generates a side-by-side comparison report at move-out — creating tamper-proof legal evidence.

---

## 🎯 Core Value Proposition

| Factor | Detail |
|---|---|
| **Real Market Need** | Security deposit disputes cost billions globally each year |
| **No Direct Competitor** | No app does bilateral tamper-proof documentation |
| **Legal Relevance** | Courts accept timestamped digital evidence |
| **Scalable Idea** | Can be sold as SaaS to property management companies |
| **Local Relevance** | Renting disputes are extremely common with zero legal protection |

---

## 👥 User Roles

| Landlord | Tenant |
|---|---|
| Creates the property listing | Joins via invite code |
| Initiates move-in inspection | Confirms or disputes findings |
| Initiates move-out inspection | Counter-documents at move-out |
| Reviews final report | Downloads their copy of report |

---

## 📦 MODULE BREAKDOWN

The app is divided into **9 modules**, each with clearly defined responsibilities.

---

### MODULE 1: Authentication & User Role Management

**Purpose:** Handle signup, login, and role-based access.

**Features:**
- Email/password signup & login
- Role selection at signup: Landlord or Tenant
- Forgot password (email reset)
- Profile setup (name, CNIC, phone number)
- Session management (auto-login)
- Logout

**Tech:** Firebase Authentication + Firestore (user profile collection)

---

### MODULE 2: Property Management

**Purpose:** Landlord creates properties; tenant joins via code.

**Features:**
- Landlord creates property (address, rent, deposit, lease start/end dates)
- System generates a 6-digit unique invite code
- Tenant enters code to join the property
- Landlord receives request and approves or rejects tenant
- Property dashboard showing all linked properties
- Property status badges: Pending / Active / Move-Out / Closed

**Tech:** Firestore (properties collection), unique code generator function

---

### MODULE 3: Room & Inspection Setup

**Purpose:** Define what will be inspected.

**Features:**
- Add rooms to property (Bedroom, Kitchen, Bathroom, Lounge, etc.)
- Custom room names supported
- Add inspection checklist items per room (walls, floor, fan, lights, door)
- Editable until move-in inspection starts
- Once inspection begins, structure locks

**Tech:** Firestore subcollections (`property/rooms/items`)

---

### MODULE 4: Photo Capture & Documentation

**Purpose:** Capture tamper-proof evidence.

**Features:**
- Camera integration via CameraX
- Auto-stamp every photo with:
  - Date and time
  - GPS coordinates (via FusedLocationProvider)
  - User ID of who captured it
- Multiple photos per checklist item
- Add written notes per item
- Set condition rating: Good / Fair / Damaged
- Photos upload to Firebase Storage with unique paths
- Local cache via Room DB for offline support

**Tech:** CameraX, Firebase Storage, Room DB, Location Services

---

### MODULE 5: Dual Verification & Digital Signature

**Purpose:** The signature feature of the app — both parties must agree.

**Features:**
- After landlord submits inspection, tenant gets notified
- Tenant reviews each room item
- For each item, tenant can:
  - Agree
  - Dispute (add own photo and note)
- Both parties draw a digital signature on canvas
- Once both sign, record becomes read-only and locked
- Locked timestamp stored as legal proof

**Tech:** Custom Canvas View for signature, Firestore status flags, FCM notifications

---

### MODULE 6: Move-Out Inspection & Comparison

**Purpose:** Compare before vs after — manually, no AI.

**Features:**
- Landlord initiates move-out inspection
- Same room/item structure auto-loads
- Both parties capture new photos and notes
- App displays side-by-side view:
  - Left: Move-in photo + note + rating
  - Right: Move-out photo + note + rating
- Manual rating comparison auto-calculates:
  - Items unchanged
  - Items degraded (Good → Damaged, etc.)
- Damage summary table generated automatically
- Both parties review and sign again

**Tech:** ViewPager2 or horizontal RecyclerView for side-by-side UI, simple comparison logic

---

### MODULE 7: Dispute Management

**Purpose:** Handle disagreements cleanly.

**Features:**
- Either party can raise a dispute on any item
- Dispute screen shows:
  - Original photo and note (move-in)
  - Current photo and note (move-out)
  - Both parties' comments
- Add resolution notes
- Mark dispute as: Open / Resolved / Unresolved
- All disputes get included in the final PDF report

**Tech:** Firestore disputes subcollection

---

### MODULE 8: PDF Report Generation & Export

**Purpose:** The legal deliverable.

**Features:**
- One-tap PDF generation including:
  - Property and tenant/landlord details
  - All rooms with checklist items
  - Side-by-side move-in vs move-out photos
  - Notes, ratings, timestamps, GPS data
  - Both digital signatures
  - List of disputes
  - Final damage summary
- Save PDF to device storage
- Share via WhatsApp, Email, Drive
- View report history of all past inspections

**Tech:** Android `PdfDocument` API or iText7 library

---

### MODULE 9: Notifications (Supporting)

**Purpose:** Keep both users in sync.

**Features:**
- Push notification when:
  - Tenant joins a property
  - Inspection submitted for review
  - Signature requested
  - Dispute raised
  - Lease end approaching
- In-app notification center

**Tech:** Firebase Cloud Messaging (FCM)

---

## 🗓️ IMPLEMENTATION TIMELINE (14 Weeks)

| Phase | Weeks | Modules | Deliverables |
|---|---|---|---|
| Phase 1: Foundation | 1–2 | Module 1, 2 | Auth working, property creation, invite code system |
| Phase 2: Structure | 3–4 | Module 3 | Rooms + checklist setup, dashboard UI complete |
| Phase 3: Core Capture | 5–7 | Module 4 | Camera, photos, notes, ratings, location stamping |
| Phase 4: Verification | 8–9 | Module 5 | Dual review flow + digital signatures + record locking |
| Phase 5: Move-Out Flow | 10–11 | Module 6 | Side-by-side comparison + damage summary logic |
| Phase 6: Disputes | 12 | Module 7 | Dispute raise + resolve flow |
| Phase 7: Reports | 13 | Module 8 | PDF generation + export + sharing |
| Phase 8: Polish | 14 | Module 9 + fixes | Notifications, UI polish, demo video, final testing |

---

## 🏗️ TECH STACK

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (or XML with Material 3) |
| Architecture | MVVM + Repository Pattern |
| Local DB | Room |
| Cloud DB | Firebase Firestore |
| Auth | Firebase Authentication |
| Storage | Firebase Storage |
| Camera | CameraX |
| Location | Fused Location Provider |
| PDF | Android PdfDocument API |
| Signatures | Custom Canvas View |
| Notifications | Firebase Cloud Messaging |
| Async | Kotlin Coroutines + Flow |
| Image Loading | Coil |

---

## 📊 DATABASE SCHEMA (Firestore)

```
users/
  └── userId
      ├── name, email, phone, role, cnic

properties/
  └── propertyId
      ├── address, rent, deposit, leaseStart, leaseEnd
      ├── landlordId, tenantId, status, inviteCode
      │
      ├── rooms/
      │   └── roomId
      │       ├── name
      │       └── items/
      │           └── itemId
      │               ├── name
      │               ├── moveInPhotos[], moveInNote, moveInRating, moveInTimestamp
      │               ├── moveOutPhotos[], moveOutNote, moveOutRating, moveOutTimestamp
      │
      ├── signatures/
      │   ├── landlordMoveInSig, tenantMoveInSig
      │   └── landlordMoveOutSig, tenantMoveOutSig
      │
      └── disputes/
          └── disputeId
              ├── itemId, raisedBy, reason, status
```

---

## 📱 APP SCREENS

1. Splash / Onboarding
2. Register as Landlord or Tenant
3. Login
4. Dashboard (active properties)
5. Create Property (Landlord)
6. Join Property via Code (Tenant)
7. Property Details Screen
8. Add Rooms & Checklist Items
9. Inspection Screen (room-by-room)
10. Photo Capture & Notes Screen
11. Review & Sign Screen
12. Move-Out Inspection Screen
13. Side-by-Side Comparison Screen
14. Dispute Screen
15. PDF Report Preview & Export
16. Notification Center
17. Profile & Settings

---

## ✅ FINAL DELIVERABLES

By the end of the semester, the team will have:

1. A fully functional Android application
2. Two demo accounts (Landlord and Tenant)
3. One sample property fully inspected with move-in and move-out cycle
4. A generated PDF report to present to the teacher
5. A clear architecture diagram and module documentation
6. Source code hosted on GitHub with proper README
7. Demo video walkthrough

---

## 🎓 Why This Project Is Industry-Level

- **Solves a real-world problem** that millions face globally
- **Uses production-grade architecture** (MVVM, Firebase, Coroutines)
- **Combines multiple Android APIs** (Camera, Location, PDF, Push, Storage)
- **Has clear monetization potential** (SaaS for property management firms)
- **Is rare among student projects** — bilateral verified documentation is untouched territory
- **Pure software** — no hardware, no hybrid frameworks, no AI dependencies
