# Local HVAC, CRM and backup implementation

Date: 2026-08-14  
Version: `1.3.0-beta1-local` (`versionCode 10300`)

## Delivered

- A native preliminary HVAC calculation with a visible formula trace, input
  validation and explicit non-compliance status.
- Water/glycol flow and Darcy-Weisbach critical-circuit pressure loss connected
  to the same state as the pipe drawing.
- UFH routing made of independent manifold-to-manifold circuits; the drawing
  shows both ends, feeders, loop colours, dimensions and calculated cut length.
- Local profiles protected by PIN, encrypted CRM fields, clients, sites in the
  repository model, work orders, stages, archive and profile isolation.
- Project trash with restore and separately confirmed permanent deletion.
- Password-encrypted, compressed, checksummed `.planruler-backup` export/import
  through Android's Storage Access Framework. No account or server is required.
- Six selectable locales: Polish, English, German, French, Italian and Russian,
  plus a build-time translation coverage gate.
- Rounded Material 3 cards and semantic engineering colours for supply, return,
  hot/cold water, gas, valid, warning and draft states.

## Security and privacy boundary

The app manifest has no `INTERNET` permission and Android cloud backup is
disabled. CRM sensitive values are encrypted using AES-GCM with an Android
Keystore key. PINs are not stored; they are verified with a salted PBKDF2
derivation. Portable backups use AES-256-GCM and a separate PBKDF2-derived key.
Room relation metadata remains protected by the Android application sandbox;
this is field encryption, not SQLCipher full-database encryption.

External PDFs and images selected with SAF are referenced by URI and are not
copied into the portable backup. The backup UI states this explicitly.

## Normative release gate

Normative SIA/DIN profiles are intentionally non-executable. A release that
claims compliance must add all of the following:

1. licensed, exact editions and national annexes;
2. versioned coefficients with clause/table provenance;
3. signed reference inputs and expected outputs;
4. independent engineering review for Swiss use;
5. regression tests that bind each profile version to those accepted cases.

Until then the UI labels the result as preliminary and warns against sizing or
approving equipment from that result alone.

## Remaining product work

- Swiss HVAC expert review and licensed normative acceptance cases;
- professional DE/FR/IT/PL trade-language review;
- quotes, invoices, payments and calendar in the offline CRM;
- optional embedding of source documents into a larger backup format;
- release signing, physical-device field test and Play pre-launch validation.

## Automated verification

- `gradlew check assembleDebug compileDebugAndroidTestKotlin --continue`: PASS;
- architecture dependency script: PASS;
- JVM test executions: 90, 0 failures (87 unique cases; localization runs for
  both debug and release variants);
- API 35 connected tests: 28, 0 failures (app journeys 13, CRM/Room 2,
  document gateway 8, export 5);
- lint: 0 errors, 60 update/metadata warnings;
- manifest privacy and six-locale coverage gates: PASS.
