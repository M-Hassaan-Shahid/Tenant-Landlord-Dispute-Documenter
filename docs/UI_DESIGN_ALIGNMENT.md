# ProofNest UI design alignment

Reference: [`proofnest-ui-prototype.html`](proofnest-ui-prototype.html)  
Tokens: primary `#1B5E20`, secondary `#FFB300`, background `#F4F6F8`, splash `#0F1E14`.

**Last verified:** 2026-06-02 (exhaustive re-audit) — `./gradlew :app:assembleDebug` **SUCCESS**

**Layouts audited:** 30 XML files · **0** hardcoded UI strings · **0** system drawable icons in forms

## Screens (18)

| Prototype | Android | Status |
|-----------|---------|--------|
| splash | `activity_splash.xml` | ✅ Wordmark, tagline, amber loader |
| onboarding | `activity_onboarding.xml` | ✅ |
| login | `activity_login.xml` | ✅ |
| signup | `activity_sign_up.xml` | ✅ Role toggle buttons (prototype style) |
| forgot password | `activity_forgot_password.xml` | ✅ |
| dashboard | `fragment_dashboard.xml` | ✅ |
| notifications | `fragment_notifications.xml` | ✅ |
| profile | `fragment_profile.xml` | ✅ |
| create-property | `fragment_create_property.xml` | ✅ |
| join-property | `fragment_join_property.xml` | ✅ |
| property-details | `fragment_property_details.xml` | ✅ |
| room-setup | `fragment_room_setup.xml` | ✅ |
| inspection | `fragment_inspection.xml` + pager | ✅ |
| camera | `activity_camera_capture.xml` | ✅ |
| review-sign | `fragment_review_sign.xml` | ✅ |
| compare | `fragment_compare.xml` | ✅ |
| dispute / disputes-list | `fragment_dispute.xml`, `fragment_disputes_list.xml` | ✅ |
| report | `fragment_report.xml` | ✅ |
| shell | `activity_main.xml` | ✅ |

## Components (prototype → Android)

| Component | Prototype | Android implementation |
|-----------|-----------|------------------------|
| **Toolbar** | Green `#1B5E20`, white title, circular back | `Widget.ProofNest.Toolbar`, `ic_nav_back_circle` |
| **Bottom nav** | Surface bg, primary active, top pill | `bottom_nav_item_color`, `Widget.ProofNest.BottomNav.ActiveIndicator` |
| **Card** | 12dp radius, `#D8DEE3` stroke, 16dp padding | `Widget.ProofNest.Card` |
| **Property row** | 48dp thumb, title, chip, chevron, accent role line | `item_property.xml` + `PropertyStatusUi` |
| **Status chip** | active / occupied / pending colors | `PropertyStatusUi`, `chip_*_bg` colors |
| **Welcome banner** | Green gradient | `bg_welcome_banner` |
| **FAB** | Amber `#FFB300`, 16dp corner | `Widget.ProofNest.Fab` |
| **Primary button** | Green fill, 10dp radius | Theme `shapeAppearanceCornerSmall` |
| **Tonal / outlined** | M3 tonal & outlined | `Widget.Material3.Button.TonalButton` / `OutlinedButton` |
| **Text field** | Outlined, 8dp corners | `Widget.Material3.TextInputLayout.OutlinedBox` |
| **Role toggle** | Two bordered buttons, selected = primary container | `MaterialButtonToggleGroup` + `Widget.ProofNest.RoleToggle` |
| **Rating chips** | good / fair / damaged fill when selected | `ChecklistAdapter` + `rating_*_bg` |
| **Inspection progress** | 8dp track, green fill | `include_inspection_progress.xml` |
| **Room tabs** | Pill tabs, primary when active | `TabLayout` in `fragment_inspection.xml` |
| **Photo strip** | 52dp thumbs when photos exist | `item_photo_thumb.xml` + `ChecklistAdapter.bindPhotoStrip` |
| **Capture CTA** | Tonal + camera icon | `item_checklist` `buttonCapture` |
| **Camera** | Dark preview, GPS bar, white shutter ring | `activity_camera_capture.xml` |
| **Signature pad** | Dashed border | `bg_signature.xml` + `SignaturePadView` |
| **Compare summary** | Amber card + warning icon | `fragment_compare.xml` |
| **Compare columns** | Move-in / move-out + placeholders | `item_compare.xml` |
| **Invite card** | Primary container, letter-spaced code | `fragment_property_details` `cardInvite` |
| **Notification unread** | Left green accent | `unreadAccent` + `bg_card_unread` |
| **Notification read** | `#FAFBFC` muted title | `notification_read_surface` |
| **Notification icon** | Tinted icon in rounded box | `NotificationUi` + `bg_notif_icon` |
| **Dispute chip** | pending / resolved colors | `DisputeListAdapter` |
| **Splash** | Dark + amber logo + spinner | `activity_splash.xml` |
| **Onboarding feature** | Icon box + title + body | `item_onboarding_feature.xml` |

## Intentional differences

1. **Sign up** — Extra password/phone fields (Firebase Auth).
2. **Compare / checklist photos** — 52dp thumb slots with icon (Glide can load `localUri` later).
3. **Dispute** — Extra “Attach counter-evidence” button for clarity.

## Re-verify

```bash
cd docs && python3 -m http.server 8765
./gradlew :app:assembleDebug
```
