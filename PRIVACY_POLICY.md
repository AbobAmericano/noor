# Privacy Policy for Noor (النُّور)

**Effective Date:** November 12, 2026

Noor ("the App") is designed as a privacy-first Islamic companion. This policy explains what information the App accesses, how it is used, and how you can control or delete your data.

---

## 1. Information We Access & Process

- **Google Account Information (Optional / Sync)**
  - When you sign in using Google Sign-In (via Android Credential Manager and Firebase Authentication), the App receives your Firebase User ID (`uid`), display name, email address, and profile photo URL solely to authenticate your session and sync your personal reflection archive, Salawat counters, and daily prayer checklists across devices.
- **Approximate Location (`ACCESS_COARSE_LOCATION`)**
  - Used only when granted to calculate local **Solat (prayer) timings**, **Qibla compass direction**, and nearby **Mosque / Surau / Musalla** listings. Noor does **not** request precise GPS (`ACCESS_FINE_LOCATION`) or background location tracking (`ACCESS_BACKGROUND_LOCATION`).
- **User-Authored Content**
  - Quran/Hadith bookmarks, personal reflection notes, and Salawat dhikr logs are stored privately in **Cloud Firestore** under your authenticated user ID (`/users/{userId}/...`).
- **Ask AI Queries**
  - Prompts submitted in the **Ask AI** tab are processed via **Firebase AI Logic** (protected by Firebase App Check) to generate responses. Avoid entering sensitive personal information into AI chat prompts.
- **In-App Tips (Google Play Billing)**
  - Optional developer tips (`sadaqah_tip_2myr`) are processed securely by **Google Play Billing**. Noor does not collect or store credit card, e-wallet, or banking details.

---

## 2. Third-Party Services Used

- **Firebase Authentication, Cloud Firestore, App Check & Firebase AI Logic** (Google LLC)
- **Google Play Billing** (Google LLC)
- **Public Spiritual & Place Directories**: `quran.com` API, `cdn.jsdelivr.net` Hadith editions, `api.aladhan.com` prayer timings, and OpenStreetMap Overpass API for mosque discovery.

---

## 3. Data Retention & Deletion

- You can delete individual saved verses or reflection notes directly inside the **Archive** tab at any time.
- You can sign out anytime from **Settings**, or clear local app preferences via Android System Settings.
- To request complete deletion of your Firebase Authentication and Cloud Firestore records, contact the developer through the support email listed on the Google Play Store page or via the repository issue tracker.

---

## 4. Children's Privacy

Noor does not knowingly collect personal information from children under 13 without parental consent.

---

## 5. Contact

For privacy questions or data deletion requests, please use the developer support contact listed on the Google Play Store listing for **Noor** or open an inquiry in this GitHub repository.
