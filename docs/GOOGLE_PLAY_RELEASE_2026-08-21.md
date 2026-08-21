# Google Play release preparation — 2026-08-21

## Release identity

- App name: `PlanRuler`
- Application ID: `com.planruler.app`
- Version: `1.5.0` (`versionCode 10500`)
- Minimum Android: API 26
- Target and compile API: 36
- Proposed type and pricing: App, Free
- Proposed default store language: English (United Kingdom)
- Proposed category: Tools
- Play Console app ID: `4975469664960806969`
- Play Console status: AAB accepted and saved as an Internal testing draft
- Public release site: `https://yurlith.github.io/planruler/`
- Public privacy policy: `https://yurlith.github.io/planruler/privacy.html`
- GitHub Release: `https://github.com/yurlith/planruler/releases/tag/v1.5.0`
- Direct release APK: `https://github.com/yurlith/planruler/releases/download/v1.5.0/PlanRuler-1.5.0-10500.apk`

The proposed public release position is a field-work calculator and plan
measurement tool for installers. Preliminary HVAC calculations are estimates,
not engineering certification or a declaration of regulatory compliance.

## Verified release artifacts

| Artifact | SHA-256 |
| --- | --- |
| `artifacts/PlanRuler-1.5.0-10500.aab` | `18E2397F53A8D151A9FE93FD26EBDA17732C7330A343322C880CDC095716D5FA` |
| `artifacts/PlanRuler-1.5.0-10500.apk` | `3A76523A94C281385170287D16B0AFDAAF4FBD34784FC754601C12994C2EDDB0` |

Upload certificate SHA-256:
`E8D08B1216DC2ABDA8C7C187C3E25D8AFD3924E0FE66A9186CC645E8108C8DB1`.

The APK signature was verified with `apksigner`; the AAB passed
`bundletool 1.18.3 validate`. The signed APK was installed on the API 35
Android Emulator, launched as `com.planruler.app/.MainActivity`, and remained
running with an empty crash buffer.

Back up `release-signing/planruler-upload.jks` and `keystore.properties`
together in a secure location before relying on this upload key. Both paths are
ignored by Git and must never be committed or placed in the release artifact
folder.

## Store listing draft (English)

### Short description

Measure plans, calculate materials and manage installation work offline.

### Full description

PlanRuler is an offline-first work centre for installers who need practical
measurements and material estimates without an engineering-style interface.

Import PDF, PNG or JPEG plans, calibrate the scale and measure lengths,
polylines, areas, perimeters and angles. Organise measurements by project,
layer, material and template, then export results as CSV, JSON or annotated
PDF.

The workshop brings common installation tasks into one place: pipe and
fabrication calculations, flange-offset geometry, cutting lengths, welding
gaps, bolt-circle data and stock-cut planning. Projects and calculations stay
available without an internet connection.

PlanRuler also includes an optional local CRM for clients and work orders. CRM
data is stored on the device and sensitive fields are encrypted with Android
Keystore. There is no cloud account, advertising or analytics SDK.

Preliminary HVAC and installation calculations are field estimates. They do
not replace a qualified engineer, applicable standards or on-site verification.

## Data Safety draft

- Data collected by the developer: none.
- Data shared with third parties: none.
- Advertising: none.
- Analytics: none.
- Account creation: none; the optional PIN protects only a local CRM profile.
- Network permission: absent.
- Android cloud backup: disabled.
- User documents and CRM data stay on the device unless the user explicitly
  exports a file through Android's system file picker.

The public privacy-policy URL is now available at
`https://yurlith.github.io/planruler/privacy.html`. The repository
`PRIVACY.md` remains the source copy used to maintain the public page.

## Play Console gates before production

- [x] Create the free app record and accept the Play declarations.
- [x] Enrol in Play App Signing and upload the AAB to Internal testing as a draft.
- [x] Publish the release announcement, privacy policy and signed APK on GitHub Pages.
- [x] Complete the privacy policy, sign-in details, ads, target audience,
  Data Safety, government-apps, financial-features and health declarations.
- [x] Set the target audience to 18 and over and declare no collected or shared
  user data.
- [x] Set the app category to Tools and complete the English (UK) store listing.
- [x] Add four 1080×2340 phone screenshots, a 512×512 store icon and a
  1024×500 feature graphic.
- [x] Add `support@veraqis.tech` as the public Play Store support email and
  complete the IARC content-rating questionnaire with the account owner's
  approval.
- [ ] Create the closed-test tester list from at least 12 real Google-account
  addresses and collect 12 opt-ins. Closed release `1.5.0 (10500)` and the
  associated app setup were sent to Google for review on 21 August 2026.
- [ ] Keep at least 12 testers opted in continuously for at least 14 days, then
  apply for Production access and answer Google's closed-test questions.
- Run the Play pre-launch report and review every device failure.
- Complete a human installation workflow and annotated-export check on a real
  field device before production rollout.

Uploading the AAB to a draft or test track does not by itself approve a public
production release.

For this personal developer account, Play Console currently shows the
production-access gate as a closed test with at least 12 opted-in testers for
at least 14 days. No test rollout or production submission was started during
this preparation step.

## Play Console status at handoff

All **11 of 11** setup tasks are complete and closed testing is unlocked. The
IARC questionnaire was submitted on 21 August 2026. Current calculated ratings
are All ages (Brazil), Everyone (ESRB), PEGI 3, USK All ages, and Rated for 3+
for the remaining listed territories, with no content descriptors. The public
support address is `support@veraqis.tech`.

The closed Alpha track is currently in review. All 177 available countries and
regions are targeted, `support@veraqis.tech` is saved as the tester feedback
channel, and AAB `1.5.0 (10500)` is attached with approved English (UK) release
notes. Tester access still needs to be configured; the dashboard shows 0
testers currently opted in.

Publishing overview shows 14 changes in review, including the closed Alpha
release, global country targeting, store listing, content rating, target
audience, privacy policy, ads, Data Safety, health, Tools category and the
Advertising ID declaration (`No`). Managed publishing is off, so the closed
track will become active automatically after approval. Nothing has been
published to Production.

Store assets are kept locally under `artifacts/play-store/`:

- `app-icon-512.png`
- `feature-graphic-1024x500.png`
- `01-home.png`
- `02-projects.png`
- `03-workshop.png`
- `04-crm.png`
