# PropCare HomeOS: build spec

Owner: A Benjamin Gabrial (individual entry). Hackathon: INFINEX 2K26 by PropCare NW. **Submission deadline: 7 Nov 2026, 11:55 PM IST.**
Judged on: innovation, technical implementation, real-world impact, user experience, scalability.
Submit: title, problem, solution, key features, tech stack, GitHub link, demo/deployment link, demo video or presentation.

## Deliverables
1. `android/`: native Android app, Kotlin + Jetpack Compose, distributed as an APK.
2. `site/`: landing page (mobile + desktop) deployed on Vercel, with a try-it-now AI photo check and an APK download button. `site/api/triage.ts` is a Vercel function holding the Gemini key.
3. `.github/workflows/android.yml`: builds a signed release APK and runs the tests.
4. Submission pack: README, write-up, 2-minute demo video script.

## Decisions (do not revisit)
- Offline-first hybrid. All data lives on the phone (Room). The AI photo check is online through our Vercel API; offline it falls back to on-device ML Kit labels plus the safety rules.
- Local login: a profile with name, phone and postcode. No server accounts. Optional app lock (BiometricPrompt, BIOMETRIC_WEAK or DEVICE_CREDENTIAL). Staff mode sits behind a PIN (PBKDF2 hash, lockout after 5 wrong tries).
- AI model: `gemini-2.5-flash-lite`, the cheapest vision model. The image is shrunk to 768px, output is capped at about 600 tokens, and thinking is off. The model name is configurable.
- "Send to PropCare" shares a formatted job sheet plus a photo via WhatsApp or email. No backend.
- White-label: everything company-specific is in `domain/Company.kt`. Prices there are **example values**, and the app always labels them as such.
- Sample data: onboarding offers "Load a sample home" (a rental with checks and two jobs), clearly marked as sample, so judges see a full app at once.
- minSdk 30, target/compile 35, Java 17. Versions are pinned in `android/app/build.gradle.kts`. Do not bump them without reason.
- Package: `app.propcare.homeos`.

## Features
Core:
- Report an issue: take a photo (TakePicture into a FileProvider uri, with no CAMERA permission declared) or pick one (PickVisualMedia), a description, and a property.
- **Voice report:** RecognizerIntent with language chips: English UK (en-GB), Hindi (hi-IN), Tamil (ta-IN). The spoken text fills the description.
- **AI triage:** issue, trade, urgency, likely causes, what you can do now, what a photo cannot show. **A box drawn on the photo where the issue is** (Gemini `box` = [ymin, xmin, ymax, xmax], 0-1000). It is labelled "AI first look, a technician confirms".
- **Offline fallback:** ML Kit labels plus keyword rules, labelled "Offline check".
- **Safety override, always:** gas smell, CO, sparking, water near electrics, structural or fire make it an emergency (see `domain/Safety.kt`; the description always wins over the AI).
- **Emergency mode:** a full-screen red screen with one-tap calls to PropCare and the National Gas Emergency line (0800 111 999), plus safe steps for gas, CO, electrics and flooding.
- **Price guide:** "Typically £X to £Y" from `Company.priceGuide`, with the note "Example price band, PropCare confirms after seeing the job".
- Job tickets PC-1001 and on, with a status timeline: Requested, Quote sent, Booked, In progress, Done (or Cancelled). Customers can cancel before the job is booked.
- **Send to PropCare:** WhatsApp (package com.whatsapp, falling back to a chooser) or email, with a job sheet and the photo.
- Properties: create, edit and delete, UK postcode check, type (home, rental, commercial), rooms you can edit and delete, repair history.
- **Landlord safety checks** (England): gas safety yearly, EICR every 5 years, EPC every 10 years, smoke/CO alarms each tenancy. States: missing, overdue, due soon (30 days) or OK. "Book" creates a job.
- **Reminders:** a daily WorkManager job sends a notification when a check is due within 30 days or overdue, once per check per state.
- **Home Health Score 0-100** per property (`domain/HealthScore.kt`), with tips that win points back. Shown as a ring on Home.
- **Maintenance passport PDF** per property (android.graphics.pdf.PdfDocument): checks, job history, photos. Shared through FileProvider.
- **Home-screen widget** (Glance): open jobs and the next due check.
- **Staff mode** (PIN): a job board sorted by urgency where staff set the status, quote in £, visit date/time, a message to the customer and internal notes.
- Account: edit profile, app lock toggle, staff mode, delete all data (database, photos, settings), about/concept label.

## UI rules (use the android-compose-craft skill)
- Material 3 with Navigation Compose. A bottom navigation bar: Home, Jobs, Properties, Account (plus Board in staff mode). One extended FAB, "Report issue", on Home and Jobs.
- Edge-to-edge with insets, a working system Back on every screen, 48dp touch targets, Material type roles in sp, colour roles only.
- Brand: navy `#1A2744` as primary, hi-vis yellow `#F2C230` for the main action, red for emergencies. Light and dark schemes.
- Material Icons (extended), never emoji or glyphs. Content descriptions on icon-only buttons.
- Every screen has loading, empty, error and offline states. Errors appear inline, and snackbars give feedback.
- Screens are stateless composables (`XScreen(state, actions)`), so tests can render them with sample data.

## Security (non-negotiable)
- No secrets in the app or the repo. The Gemini key only lives in Vercel env (`GEMINI_API_KEY`). `X-Client-Id` is a public identifier, not a secret.
- `allowBackup=false`, data extraction rules exclude everything, `network_security_config` is HTTPS-only with system CAs, and the FileProvider is not exported.
- Only these permissions: INTERNET, POST_NOTIFICATIONS, USE_BIOMETRIC. No CAMERA, no RECORD_AUDIO (both use system intents), no location.
- Release build: R8 minify plus resource shrinking. Signing comes from env vars in CI (keystore as a GitHub secret). The keystore is never committed.
- API (`site/api/triage.ts`): POST only; JSON size cap (1.5 MB image); mime whitelist (jpeg/png/webp); description capped at 500 chars; per-IP rate limit (for example 10 per 10 minutes, best-effort in memory); CORS limited to the site origin; generic error messages; no image logging; the safety rules re-applied server-side; Gemini called with the key in the `x-goog-api-key` header.
- Recommend that the owner sets a spending cap on the Google AI project and uses the paid tier for real customer photos (free-tier data may be used by Google).

## Status
Done in `android/` (unbuilt, so verify it compiles):
- Gradle setup: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `proguard-rules.pro`, `gradle.properties` (`propcare.apiBase`).
- `AndroidManifest.xml` and `res/` (theme, network security, data extraction rules, file paths, widget info, adaptive icon).
- `domain/`: Models, Safety (rules plus offline triage), Company, Compliance, HealthScore.
- `data/`: Db (Room entities and DAO), Settings (prefs, PBKDF2 staff PIN with lockout).
- `net/TriageApi.kt`, `util/Images.kt`, `util/OfflineLabeler.kt`.

To do:
- `PropCareApp` (Application: notification channel, schedule the reminder worker), `MainActivity` (FragmentActivity, enableEdgeToEdge, app lock on start and after 30 s in the background), `AppViewModel` (StateFlows from the DAO, plus all actions), the repository logic (create a job with its first event in `db.withTransaction`, status changes add events, delete-all, the sample-data seed).
- `ui/theme`, `ui/components` (job ticket card, health ring, triage card with box overlay, empty states), `ui/screens` (Onboarding, Lock, Home, Report flow, Emergency, Jobs, JobDetail, Properties, PropertyDetail, PropertyEdit, Account, StaffBoard, Services).
- `util/Share.kt` (WhatsApp/email job sheet), `util/PassportPdf.kt`, `work/ReminderWorker.kt`, `widget/HomeWidget.kt` + `HomeWidgetReceiver`.
- Gradle wrapper (8.11.1). Unit tests for Safety, Compliance and HealthScore. Roborazzi screenshot tests of each screen with sample data.
- `site/`: landing page plus `api/triage.ts` (port `../propcare-app/supabase/functions/_shared/triage-core.ts` and add the `box` field to the schema).
- `.github/workflows/android.yml`, README, submission write-up, demo script.

## How to verify
- Build: `cd android && ./gradlew testDebugUnitTest assembleRelease` (or in Android Studio).
- On a phone or emulator: run the full flow. Capture screenshots with `adb exec-out screencap -p > shot.png`. Test dark theme and font scale 1.3.
- Site: the browser-check skill at 1440, 1024, 768 and 390 widths.

## Reference
The earlier web version (React PWA) is at `../propcare-app`. Reuse its triage prompt, schema, safety rules and copy. Skills: android-compose-craft, web-design-craft, opensource-ui, browser-check.
