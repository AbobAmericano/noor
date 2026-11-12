<p align="center">
  <img src="app/src/main/res/drawable/img_noor_header_dark.jpg" alt="Noor Islamic Companion Header" width="100%" />
</p>

<h1 align="center">Noor (النُّور)</h1>

<p align="center">
  <strong>Minimalist, lightweight Android companion for daily Quran &amp; Hadith inspiration, Solat timings, Qibla guidance, nearby mosques, Salawat dhikr, and reflection sync.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.2.10" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose Material 3" />
  <img src="https://img.shields.io/badge/Android-Min%20SDK%2024%20%7C%20Target%2036-3DDC84?logo=android&logoColor=white" alt="Android Min SDK 24 Target 36" />
  <img src="https://img.shields.io/badge/Firebase-Auth%20%7C%20Firestore%20%7C%20AI%20Logic-FFCA28?logo=firebase&logoColor=black" alt="Firebase" />
  <img src="https://img.shields.io/badge/License-MIT-emerald" alt="MIT License" />
</p>

---

## Overview

**Noor** is a calm, low-footprint Android application built with Kotlin and Jetpack Compose. Designed for distraction-free daily worship and study across phones, foldables, and tablets, it pairs classical Arabic typography (`Amiri`, `Lora`, `Plus Jakarta Sans`) with offline-resilient astronomy calculations, real-time sensors, and cloud-synced reflection notes.

---

## Key Features

- **Daily Quran & Hadith Inspiration**
  - Pulls verses from the `quran.com` v4 API and Hadith from `fawazahmed0/hadith-api`, with curated offline fallback content.
  - One-tap share cards, deep links to **Tafsir**, **Sunnah.com**, **JAKIM Malaysia**, and academic research references, plus a **Home Screen Widget** (`NoorDailyWidgetProvider`).
- **Solat Schedule, Qibla Compass & Nearby Mosques**
  - Geolocation and regional fallback prayer timings (`AlAdhan` API + offline spherical-astronomy `SolatCalculator`) supporting JAKIM Malaysia, MWL, ISNA, Umm al-Qura, and Egyptian conventions.
  - Real-time magnetometer/accelerometer Qibla bearing (`QiblaSensorManager`) and nearby **Mosque / Surau / Musalla Finder** powered by OpenStreetMap Overpass.
  - Boot-persistent Solat & daily inspiration notifications (`NoorReminderReceiver`).
- **Salawat & Dhikr Counter with Tactile Haptics**
  - Lightweight bead counter with daily targets, 5-prayer completion checklist, lifetime milestones, spring tap physics, and distinct multi-pattern haptic feedback (`DhikrHapticHelper`).
- **Ask AI Guide (Zero API Key Needed)**
  - Multi-turn streaming reflection and mosque guidance via **Firebase AI Logic** (`gemini-3.8-flash` & `gemini-3.5-flash-lite`) protected by Firebase App Check, including Google Search and Google Maps grounding (`Tool.googleMaps()`).
- **Cloud Archive & Night Reading Mode**
  - Google Sign-In via Jetpack `CredentialManager` and real-time **Cloud Firestore** sync (`/users/{uid}/saved_verses`, `dhikr_logs`, `dhikr_profile`) with strict owner-only security rules.
  - Warm Sandstone light theme and Deep Night Emerald dark theme, adaptive layouts (`NavigationBar` / `NavigationRail`), and optional 2 MYR in-app Sadaqah tip via **Google Play Billing 9.1.0** (`sadaqah_tip_2myr`).

---

## Tech Stack & Libraries

| Layer | Technology |
| :--- | :--- |
| **Language & UI** | Kotlin `2.2.10`, Jetpack Compose BOM `2024.09.00`, Material Design 3, Navigation Compose `2.8.9` |
| **Build & Toolchain** | Android Gradle Plugin `9.1.1`, Gradle `9.3.1`, KSP `2.3.5`, `minSdk 24`, `targetSdk 36` |
| **Cloud & Auth** | Firebase BOM `34.17.0` (Auth, Firestore, App Check, Firebase AI Logic), Credential Manager `1.5.0` |
| **Networking & Data** | Retrofit `2.12.0`, OkHttp `4.10.0`, Moshi `1.15.2` (code-gen), DataStore Preferences `1.1.7` |
| **Location & Sensors** | Google Play Services Location `21.3.0`, Android `SensorManager`, `Vibrator` / `VibrationEffect` |
| **Monetization** | Google Play Billing KTX `9.1.0` |
| **Testing** | JUnit 4, Robolectric `4.16.1`, Kotlinx Coroutines Test `1.10.2`, Firebase Emulator Suite |

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Edge-to-edge shell, auth gate & adaptive navigation
├── billing/
│   └── SadaqahBillingManager.kt     # Google Play Billing 9.1.0 consumable tip flow
├── data/
│   ├── ai/                          # App Check + Firebase AI Logic repository
│   ├── model/                       # Firestore DTOs
│   ├── preferences/                 # DataStore user settings & counter preferences
│   ├── remote/                      # Quran.com, Hadith CDN, AlAdhan Retrofit services
│   └── repository/                  # Inspiration, Mosque Overpass & Firestore repositories
├── domain/
│   ├── model/                       # Domain models for verses, hadith & prayer times
│   └── solat/                       # Offline Solat & Qibla astronomical calculator
├── notifications/                   # Notification channels, scheduling & boot receiver
├── sensors/                         # Haptics, fused/coarse location & Qibla compass
├── ui/
│   ├── auth/                        # Google Sign-In screen (Credential Manager)
│   ├── components/                  # Settings sheet, Sadaqah dialog & reusable cards
│   ├── screens/                     # Today, Solat & Qibla, Mosque Finder, Salawat, Ask AI, Archive
│   └── theme/                       # Material 3 colors, typography & shapes
└── widget/
    └── NoorDailyWidgetProvider.kt   # Home screen AppWidget
```

---

## Getting Started

### Prerequisites

- **JDK 21** (required for Robolectric tests targeting Android SDK 36)
- **Android SDK 36 (`android-36.1`)** (`compileSdk = 36` minor API 1, `minSdk = 24`)
- **Gradle `9.3.1`** (or Android Studio Ladybug / Meerkat+)
- A **Firebase project** with Authentication (Google Sign-In provider), Cloud Firestore, App Check, and Firebase AI Logic enabled

### Local Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/AbobAmericano/noor.git
   cd noor
   ```
2. **Prepare the debug keystore** (if not generated automatically by Android Studio)
   ```bash
   base64 -d debug.keystore.base64 > debug.keystore
   ```
3. **Configure Firebase**
   - Place your Firebase Android config at `app/google-services.json`.
   - Ensure your debug SHA-1 and SHA-256 fingerprints are registered in **Firebase Console → Project Settings** so Google Sign-In (`CredentialManager`) succeeds locally.
   - Review `firebase-blueprint.json` and deploy `firestore.rules` to your Firestore database.
4. **Build a debug APK**
   ```bash
   gradle :app:assembleDebug
   ```

---

## Running Tests

### Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest \
  --tests "com.example.ExampleUnitTest" \
  --tests "com.example.ExampleRobolectricTest" \
  --tests "com.example.domain.solat.SolatCalculatorTest" \
  --tests "com.example.sensors.DhikrHapticHelperTest"
```

### Firestore Security Rules Tests
Start the Firebase Auth (`127.0.0.1:9099`) and Firestore (`127.0.0.1:8085`) emulators from `firebase.json`, then run:
```bash
node --test firestore.test.js
```
Or execute the Robolectric emulator suite:
```bash
gradle :app:testDebugUnitTest --tests "com.example.data.repository.NoorFirestoreRuleTest"
```

---

## Google Play & Billing Checklist

- **Application ID**: `com.aistudio.noor.kxmpzq` (override only for your own Play Console listing before first release).
- **In-App Product ID**: `sadaqah_tip_2myr` (one-time consumable product configured in Play Console → *Monetize → In-app products*).
- **Play App Signing**: Add the production **App signing key** SHA-1 and SHA-256 from Play Console into Firebase Console to keep Google Sign-In working on store installs.
- **Privacy Policy**: A ready-to-publish policy covering approximate location, Google Sign-In, Firestore sync, AI Logic, and Play Billing is available in [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md).

---

## Documentation & Community

- [`CONTRIBUTING.md`](CONTRIBUTING.md) — Code style, Compose `testTag` guidelines, and PR checklist
- [`SECURITY.md`](SECURITY.md) — Vulnerability reporting, zero-trust Firestore rules, and secret hygiene
- [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) — App permissions and user data handling
- [`LICENSE`](LICENSE) — Distributed under the MIT License

---

## Contributors

- **[@AbobAmericano](https://github.com/AbobAmericano)** — Project author & maintainer
- **Google AI Studio** — Contributor (application architecture, Compose implementation, Firebase integration & documentation)

