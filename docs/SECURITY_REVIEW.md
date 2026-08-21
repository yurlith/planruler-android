# Security Review

Date: 2026-07-25

- Only `MainActivity` is exported, for the launcher intent.
- No services, receivers, or providers are declared.
- No `INTERNET` or `MANAGE_EXTERNAL_STORAGE` permission is requested.
- Document access uses content/file descriptors and persisted read grants.
- No network transport exists.
- No document URI or annotation text logging was added.
- Corrupt files produce typed user-facing errors; obvious invalid PDFs are rejected
  before native `PdfRenderer` construction.
- Exports use a private temporary file and publish only after generation succeeds.
- Repository file names are sanitized and project writes use validated temporary
  files plus atomic move when supported.
- Signing secrets can only come from environment variables or ignored
  `keystore.properties`.
- `.gitignore` excludes keys, signing properties, local SDK properties, and builds.
- Test signing key lives under `%TEMP%\planruler-test-signing` and is not a
  production key.

No archive-based project import exists, so zip/path traversal is not currently an
attack surface. Dynamic security scanning and Play pre-launch testing are not done.
