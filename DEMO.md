# PropCare hackathon demo

Native Android demo: `app.propcare.homeos.demo`, version `1.2.1-demo`, Android 11+.

**[Landing page](https://propcare-homeos.vercel.app) · [Download the APK](https://propcare-homeos.vercel.app/downloads/PropCare-Demo.apk?v=1.2.1)**

Create a local account on first launch. There are no shared demo credentials. If an older demo fails to update because its signing certificate differs, uninstall it first; uninstalling deletes its local data.

## Judge walkthrough

1. Tap **Create account** after entering your name, email and chosen password. This is a local account, not cloud authentication. The app creates **My home** so you can start your first report immediately.
2. Tap **Report a repair**. Take or choose a photo; type or speak the description. English, Hindi and Tamil use the handset's speech service.
3. Tap **Check this issue**. Real photos use bundled ML Kit image labels plus description/safety rules when no HTTPS endpoint is configured. This is an offline first look, not a confirmed diagnosis or a bounding-box detector.
4. For a reproducible visual demo, tap **Use an example photo**. Its sample assessment is labelled. Dangerous description words still override the sample result.
5. Watch the blue logo processing animation, review possible causes, then tap **Identify the right technician**. The matching carousel settles on a sample profile. Review technician → repair brief → save.
6. Share through WhatsApp/email. The human confirms sending in the external app. Opening a share sheet does not prove delivery or booking.
7. Return Home, switch **Resident → Technician**, open the repair → **Complete repair** → choose 30/90/180 days or custom 1–730 days → **Mark completed**.
8. The warranty-issued stamp appears. The warranty page shows a live countdown, remaining calendar days and expiry. Completion date and duration persist in Room. Reopening/reclosing does not restart coverage; cancelled repairs cannot be completed.
9. **Report a problem again** creates a draft linked to the original repair, without extending its warranty.
10. Landlord mode shows properties/checks; Emergency help demonstrates safety overrides.

## Scope

Role switching and the technician directory are local demo features, not authentication or real dispatch. No technician is actually assigned. All warranty periods are examples, not PropCare's published policy.

No paid API is required. An optional Gemini-backed endpoint can use the existing `PROPCARE_API_BASE` build setting; the key stays server-side. No API key belongs in the APK.

Camera, speech and WhatsApp depend on handset apps; missing services show recovery messages. Automated checks cover the Compose/Room sample journey, FileProvider camera contract, coverage dates, persistence, cancellation and follow-ups. They do not constitute real-handset camera testing.

CI builds the APK and Android screenshots. Without private release-signing secrets, the APK is debug-signed. No store publication is implied.

## Asset provenance

- Logo: supplied `propcare-intro.html`, rendered with its original geometry.
- House: Binyamin Mellish / Pexels: https://www.pexels.com/photo/house-lights-turned-on-106399/
- Technician stock illustration: Artem Podrez / Pexels: https://www.pexels.com/photo/an-man-in-blue-coveralls-standing-at-an-auto-repair-shop-8986045/ — not an employee endorsement.
- Sample tap: Zulfugar Karimov / Pexels: https://www.pexels.com/photo/modern-chrome-kitchen-faucet-with-running-water-34295401/
- Inter: SIL OFL, `android/app/INTER-OFL.txt`, google/fonts commit `0b58fb370093f9a9f4ff785d94405710b79de67c`.
