# Release checklist

- [x] Gradle wrapper 8.11.1 and Android Gradle Plugin 8.9.1
- [x] Unit tests
- [x] Architecture check
- [x] Android lint: 0 errors; warnings explained
- [x] Instrumented tests on API 26 and API 35
- [x] Real system SAF OpenDocument/CreateDocument E2E
- [x] Measurement edit, properties and snapping UI
- [x] Polish/English/German language choice and persistence; Russian compatibility
- [x] Activity recreation and real `force-stop` cold restoration
- [x] A4/A3/A0/large-PNG renderer stress without OOM
- [x] Debug APK
- [x] Minified signed release AAB and APK
- [x] Dedicated Google Play upload key, kept outside version control
- [x] App icon and branding
- [x] No repository secrets/signing keys
- [x] Privacy and security review
- [x] Separate full Compose PDF, PNG, rotated-JPEG and corrupt-document journeys
- [x] Independent Poppler/CSV/JSON export validation
- [x] Modern Material 3 UI, readable tool colors and animated transitions
- [x] Export replaces an existing target file instead of overwriting its prefix
- [x] Annotated PDF carries project name, timestamp, units, calibration, legend
- [x] Export options (legend, scale, CSV delimiter) reach the export request
- [x] Layers UI: visibility, lock, rename, delete, move measurement
- [x] Tiled rendering of the visible rectangle above the page render density
- [x] Page revisions: replace, 2/3-point alignment, overlay and review workflow
- [x] Revision history and review state in JSON/CSV/PDF exports
- [ ] Human manual smoke and visual third-party viewer interoperability
- [x] Signed release APK smoke-tested on Android Emulator API 35
- [ ] Google Play App Signing enrollment and Play pre-launch report
- [ ] Real-device field test

Current honest status: **SIGNED RELEASE CANDIDATE pending human acceptance**.
Automatic technical RC gates and the signed-release emulator smoke test pass.
Google Play setup, pre-launch report, human third-party-viewer acceptance and
real-device field testing remain external production gates.
