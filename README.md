# Doc Scanner — Android App

Scan documents with your camera (auto edge-detection + crop, CamScanner-style),
turn them into a PDF, and send to WhatsApp or by email.

No passwords. No file editing. You set everything inside the app.

---

## Build the APK with no PC (GitHub Actions)

1. Create a free account at https://github.com
2. Click **New repository**, name it `DocScanner`, make it **Public**, click Create.
3. On the new repo page, click **uploading an existing file**.
4. Upload ALL files and folders from this project (keep the folder structure).
5. Click **Commit changes**.
6. Go to the **Actions** tab — a build called "Build APK" starts automatically.
7. Wait ~3–5 minutes for the green checkmark.
8. Click the finished run → **Artifacts** → download **DocScanner-APK**.
9. Unzip on your phone → you get `app-debug.apk`.

## Install on your phone

1. Open the downloaded APK.
2. Allow installing from this source when prompted.
3. Install, open, grant Camera (and Contacts, if you set a default) permission.

---

## How to use it

- **Scan Document** → camera opens, auto-detects edges, crop, add more pages,
  Done → a PDF is created and saved.
- **Send to WhatsApp** → if you set a default contact, it offers that contact OR
  "Choose someone else" (which opens WhatsApp's own chat list). You tap Send.
- **Send to Email** → opens your normal email app with the PDF attached and the
  default recipient filled in (if set). You tap Send. No password ever.
- **Settings** → tap **Choose contact** to pick your default WhatsApp recipient
  from your phone contacts. Optionally set a default email. Tap Save.

### Why one tap to send?
WhatsApp does not let any app send messages fully automatically (anti-spam).
Email works the same way here for safety — the app never logs into your account.
Everything up to the final Send is automatic.

---

## Notes
- Pages per scan: 20 (change in MainActivity.kt → setPageLimit).
- Scans are stored in the app's private folder under `scans/`.
- Debug build (auto-signed) so it installs with no key setup.
