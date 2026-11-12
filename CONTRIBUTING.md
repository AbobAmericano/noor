# Contributing to Noor (النُّور)

Thank you for helping improve **Noor**. Contributions that keep the app lightweight, accessible, privacy-respecting, and faithful to Material Design 3 are welcome.

---

## Development Guidelines

1. **Architecture & Language**
   - Write all new application code in **Kotlin** with **Jetpack Compose** (avoid XML layouts outside AppWidgets).
   - Follow the existing MVVM + Repository split (`ui/`, `domain/`, `data/`, `sensors/`, `notifications/`, `widget/`, `billing/`).
   - Keep source files focused and modular (ideally under ~500 lines).

2. **UI, Accessibility & Play Policy**
   - Use `MaterialTheme.colorScheme` and typography tokens from `ui/theme/` rather than hardcoded colors.
   - Maintain a minimum `48.dp` touch target and meaningful `contentDescription` on interactive elements.
   - Add `Modifier.testTag("snake_case_name")` to primary interactive controls.
   - Never add restricted permissions (`READ_EXTERNAL_STORAGE`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, etc.) or third-party payment links alongside Google Play Billing.

3. **Security & Secrets**
   - Never commit `.env`, `*.jks`, release keystores, or personal API keys.
   - Keep `firestore.rules` strictly owner-scoped (`request.auth.uid == userId`) and update `firebase-blueprint.json` + tests whenever schemas change.

---

## Before Opening a Pull Request

1. Verify the app compiles cleanly:
   ```bash
   gradle :app:assembleDebug
   ```
2. Run unit and Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest \
     --tests "com.example.ExampleUnitTest" \
     --tests "com.example.ExampleRobolectricTest" \
     --tests "com.example.domain.solat.SolatCalculatorTest" \
     --tests "com.example.sensors.DhikrHapticHelperTest"
   ```
3. If you modified `firestore.rules`, verify the security rule test suite against the Firebase Emulator Suite (`node --test firestore.test.js`).
4. Summarize your changes clearly in the Pull Request template and attach screenshots for UI updates.
