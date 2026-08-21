# Known limitations

Date: 2026-08-15

## Fabrication workshop alpha limitations

- The workshop currently generates the complete two-elbow, two-flange
  parallel-axis offset. Tees, reducers, rolling 3D offsets and an arbitrary
  drag-and-drop fitting chain are not yet part of this engine.
- The published elbow catalog row is 45°. Other selectable angles use the
  constant-radius relation `A = R × tan(α/2)` and are explicitly marked for
  verification against the actual manufactured or trimmed fitting.
- The stock chart uses deterministic first-fit decreasing packing. All cut
  lengths and kerf conservation are exact, but the number of stock bars is a
  practical plan rather than a proof of globally optimal cutting-stock usage.
- Type 11 flange axial dimensions cover DN 15–300 and PN 6/10/16/25/40 from
  the cited open manufacturer series. Facing, material, tolerances and the
  approved welding procedure still require job-specific verification.

## Photo data inspector alpha limitations

- GDepth base64 rasters and Dynamic Depth concatenated directory items are now
  decoded by content into a common metre map. JPEG/PNG/WebP/HEIF payload choice
  does not affect the domain representation; 8/16-bit grayscale PNG has a
  precision-preserving decoder and other supported image payloads use the
  Android codec selected from their byte signature.
- Native Apple HEIF/JPEG auxiliary disparity is detected but Android has no
  public equivalent of `CGImageSourceCopyAuxiliaryDataInfoAtIndex`; it remains
  explicitly `UNSUPPORTED_PAYLOAD` instead of being treated as metric depth.
- Decoded depth is not yet fitted to construction planes and is not silently
  applied as an image-wide calibration. That needs intrinsics/orientation
  mapping, robust plane fitting, uncertainty and a user confirmation step.
- EXIF `SubjectDistance` is treated only as a focus-plane hint. It is never
  applied as an image-wide scale. Ordinary camera metadata and Motion Photo
  still require a known length, confirmed metric depth or another metric anchor.
- Camera-profile observations stay on the device, are deduplicated by source
  SHA-256 and are called stable only after at least three distinct photos with
  relative median absolute deviation at or below 2%. Statistical stability is
  not a calibration certificate.
- The fast declaration scanner still reads 4 MiB from the beginning and 1 MiB
  from the end. For actual decoding, files up to 96 MiB receive full main XMP,
  JPEG extended-XMP, PNG iTXt and raw ISO-XMP inspection. Safety limits are
  32 MiB per depth payload and 16 million decoded depth samples.

## HVAC/CRM beta limitations

- `Preliminary planning` is the only executable heat-design profile. SIA
  384/2:2020+C1:2021, DIN EN 12831-1:2017/DIN TS 12831-1 and the prEN 12831-1
  draft are represented as locked metadata, not as compliance claims. They
  require licensed source texts, signed reference cases and Swiss HVAC review.
- Pump head is intentionally absent until a critical hydraulic circuit exists;
  UFH circuit lengths are absent until a routed layout exists. The app does not
  silently substitute rule-of-thumb constants for either value.
- Gas sizing and SVGW tables remain locked pending licensed data and expert
  acceptance. The current feature set must not be used to approve a gas system.
- The portable backup includes active projects, trash and CRM data, but external
  source PDF/image bytes referenced through Android SAF are not embedded. Those
  source documents must be retained separately.
- CRM 1.3 exposes local profiles, clients, sites in the data layer, work orders,
  stages and archive. Quotes, invoices, payments, calendar synchronisation and
  server collaboration are not part of this offline beta.
- All six locales have automated key coverage. HVAC terminology in generated
  French and Italian strings still needs native-speaker trade review before a
  production release.

## Closed on 2026-08-10

- Export used a non-truncating write mode, so exporting over an existing longer
  file left the previous tail behind and produced unreadable JSON/CSV/PDF. Fixed
  by writing with `wt` and covered by an explicit overwrite test.
- The annotated PDF now carries the project name, export timestamp, units,
  calibration and a per-category legend, and draws each measurement in its own
  colour instead of a fixed red.
- Legend, scale information and the CSV delimiter now reach `ExportRequest`;
  default unit, default stroke width, the unverified-scale warning and segment
  labels now change behaviour instead of only being stored.
- The renderer is tiled: above the page render density the visible rectangle is
  re-rendered in 512 pt tiles for both PDF and images.
- Layers have a UI (visibility, lock, rename, delete, move measurement).

## Still open

- The page render is still capped at a 4096 px edge; above that density the
  tiled path takes over. Tiles are capped at 2048 px per edge and at 12 tiles
  per viewport, so a very fast zoom shows the page render for a moment before
  the sharp tiles land.
- A human field smoke test and visual inspection of the exports in third-party
  viewers are still not done.
- Production signing, Play pre-launch testing and real-device field testing are
  intentionally not complete. The release APK is unsigned unless external
  signing variables are supplied.
- Lint reports 60 explained warnings and 0 errors: 51 dependency update
  notices, 6 Android Gradle Plugin update notices and 3 SDK/backup metadata
  notices. Toolchain upgrades are separated from this functional beta.
- On Windows, Gradle requires the documented `subst R:` workaround when the
  project resides in a path containing Cyrillic characters.
- Revision alignment is deliberately manual in this release. Automatic pixel
  difference highlighting is deferred until the manual workflow is validated
  on real construction drawings.

## Previous snapshot (2026-07-25)

The UI, SAF, process-death and measured-performance blockers addressed in that
pass are closed. The following items were then outside the verified RC scope:

- Separate real-SAF Compose journeys now cover PNG, two-page PDF, rotated JPEG
  and corrupt PDF. A human field smoke test is still not complete.
- PDF/CSV/JSON exports pass independent host validation with Poppler `pdfinfo`,
  PowerShell's CSV parser and a separate JSON parser. Visual inspection in
  third-party GUI applications remains a human acceptance step.
- Production signing, Play pre-launch testing and real-device field testing are
  intentionally not complete. The release APK is unsigned unless external
  signing variables are supplied.
- Lint reports 43 explained warnings: coordinated dependency/toolchain update
  notices and compatibility/resource notices. There are 0 errors.
- On Windows, Gradle requires the documented `subst R:` workaround when the
  project resides in a path containing Cyrillic characters.

The automatically verifiable RC blockers are closed. The documented quality
status remains **PROTOTYPE** only because the repository's strict definition
also requires a human field/manual acceptance pass. It is not Production-ready.
