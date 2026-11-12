# Security Policy

## Supported Versions

Security updates are applied to the latest build on the default branch.

| Version | Supported |
| :--- | :--- |
| `1.0` (`main`) | Yes |

---

## Reporting a Vulnerability

If you discover a security or privacy issue (such as a Firestore rule bypass, authentication flaw, or unintended secret exposure):

1. **Do not** disclose the exploit details in a public GitHub issue.
2. Report it privately using GitHub's **Security Advisories** (*Security → Advisories → Report a vulnerability*) on this repository, or contact the repository maintainer directly.
3. Include steps to reproduce, affected files, and any suggested mitigation.

---

## Security Architecture Highlights

- **Owner-Only Firestore Rules (`firestore.rules`)**: All user documents (`saved_verses`, `dhikr_logs`, `dhikr_profile`) require Firebase Authentication and validate `request.auth.uid == userId`, strict field types, and server timestamps.
- **No Embedded AI Secrets**: Cloud AI requests route through **Firebase AI Logic** with **Firebase App Check**, avoiding hardcoded `GEMINI_API_KEY` values in the APK.
- **Release Signing Hygiene**: Release keystores (`*.jks`, `*.keystore`) and `.env` files are excluded via `.gitignore` and injected strictly via CI/CD or local build environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`).
