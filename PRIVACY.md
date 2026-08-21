# Privacy

The photo data inspector processes imported camera files entirely on the
device. Its local camera profile stores the source SHA-256 and normalized
optical observations needed for median/MAD analysis. It does not store GPS,
camera serial numbers or upload camera metadata. Removing app data removes
these local observations.

Depth/XMP payloads are decoded only in process while the source photo is open.
The normalized depth array is not written to project JSON, CRM or backup and is
released with the opened document. File, payload and pixel limits prevent an
untrusted image from forcing unbounded memory allocation.

## Local CRM

The optional CRM stores client names, phone numbers, emails, addresses and work-order
notes — personal data about the user's own customers, not the user. It has no server,
email account or cloud sync of any kind (verified: no networking library is a dependency
of `core:crm-api`, `core:crm-local` or `feature:crm`).

Every account is protected by a PIN. The PIN itself is never stored; only a salted
PBKDF2-HMAC-SHA256 hash (180,000 iterations) is kept, and repeated wrong PINs are throttled
with a growing lockout. Client, site and work-order fields are encrypted at rest with
AES-256-GCM using a hardware-backed Android Keystore key that is generated per account and
never leaves the Keystore.

Deleting a client removes it and its work orders immediately and permanently — there is no
undo. Deleting an account removes everything under it and releases its Keystore key.
Archiving a client only hides it from the default list and can be reversed.

PlanRuler обрабатывает документы только на устройстве. Нет регистрации, рекламы, аналитики, сетевых SDK и загрузки планов на сервер.

Документ выбирается через Android SAF. Read-only URI permission, JSON проекта и backup находятся локально. Экспорт выполняется только в URI, выбранный пользователем.

Удаление проекта удаляет его JSON и backup, но не меняет исходник и ранее экспортированные файлы.
# Signing and export note

Signing credentials are never stored in the repository. Export generation uses a
private temporary file and copies it only to the destination explicitly selected
by the user. No network permission or cloud service is used.
