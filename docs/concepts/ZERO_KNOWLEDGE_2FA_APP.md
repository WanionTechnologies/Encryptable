# Whitepaper: Zero Knowledge 2FA App – Requirements & User Experience

## 🏁 Concept Overview

This whitepaper describes the requirements and expected behavior of a privacy-enhancing Zero Knowledge 2FA app.

> **Note:** This is a conceptual proposal and is not implemented in the framework. It is presented as an idea for the broader security and privacy community.

---

## 🌐 The Secure Communication Flow: Linking User, App, and Backend

A core innovation of this approach is the use of a secure communication flow that enables the backend to identify the user without ever learning their secret. This is achieved by:

- Providing a **dedicated, secure API endpoint** for the Zero Knowledge 2FA app to communicate with during authentication and recovery.
- Including a **cryptographic hash of `secret#login`** (e.g., SHA-256) alongside the encrypted `secret#2fa`. This hash allows the backend to identify which user or account the encrypted secret belongs to, without ever exposing the actual login secret.

**Security Consideration:**
- The hash of `secret#login` is irreversible and does not reveal the original secret, so it does not introduce a security risk.
- The app transmits the encrypted `secret#2fa` and the hash to the backend only via the secure endpoint, ensuring privacy and integrity.

---

## 🔑 Why a Custom App Is Required

- **Encrypted Secret Storage:** The app must store and transmit an encrypted blob (the encrypted `secret#2fa`), not a TOTP seed or raw secret.
- **No Secret Knowledge:** The app never sees or handles the raw (decrypted) `secret#2fa` at any point.
- **Stateless Operation:** The app does not generate codes or perform cryptographic operations; it simply stores and transmits the encrypted secret as needed.
- **QR Code Compatibility:** The app must be able to scan and import a QR code containing the encrypted `secret#2fa` during registration.
- **Export/Backup:** The app should allow users to export or backup their encrypted secret for device migration or backup purposes.

---

## 🛠️ App Requirements

1. **QR Code Scanning:**
   - Scan and import a QR code containing the encrypted `secret#2fa` during registration.
2. **Encrypted Secret Storage:**
   - Securely store the encrypted `secret#2fa` on the user's device.
   - Never decrypt or expose the raw secret.
3. **Transmit Encrypted Secret:**
   - On authentication, transmit the encrypted `secret#2fa` and the hash of `secret#login` to the server as part of the login or recovery flow.
4. **No Code Generation:**
   - The app does not generate TOTP or HOTP codes. It only stores and transmits the encrypted blob.
5. **Export/Backup Functionality:**
   - Allow users to export or backup their encrypted secret for device migration or backup.
6. **Multi-Account Support (Recommended):**
   - Support storing multiple encrypted secrets for users with multiple accounts or services.
7. **User Guidance:**
   - Clearly inform users that losing access to the app and all recovery keys will make account recovery impossible (unless brute force, which is practically impossible).

---

## 👩‍💻 User Experience Flow

### **Registration**
1. User scans QR code with the Zero Knowledge 2FA app.
2. App stores the encrypted `secret#2fa` securely, along with the hash of `secret#login`.

### **Login**
1. User opens the app and selects the account or service.
2. App transmits the encrypted `secret#2fa` and the hash of `secret#login` to the server as part of the authentication flow, via the secure endpoint.

### **Recovery**
1. If the user loses access to the app but has recovery keys, they can recover their Zero Knowledge 2FA as described by the service's recovery process.
2. If the user loses both the app and all recovery keys, recovery is impossible (unless brute force, which is practically impossible).

---

## ⚠️ Security Notes
- The app must never expose or attempt to decrypt the encrypted `secret#2fa`.
- All cryptographic operations (decryption, key derivation) are performed on the server (or service backend), in memory only.
- The app is a secure storage and transmission tool, not a code generator.
- The hash of `secret#login` is irreversible and only used for backend identification.

---

## 📚 Further Reading
- [Zero Knowledge 2FA](ZERO_KNOWLEDGE_2FA.md)
- [Zero-Knowledge Authentication](ZERO_KNOWLEDGE_AUTH.md)

---

**Last Updated:** 2025-10-26
