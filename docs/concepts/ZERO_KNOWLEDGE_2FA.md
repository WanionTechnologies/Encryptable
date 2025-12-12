# Whitepaper: Zero Knowledge 2FA – Technical Overview & Innovation

## 🏁 Concept Overview

This whitepaper describes a new approach to Two-Factor Authentication (2FA) that is fully zero knowledge.

> **Note:** This is a conceptual proposal and is not implemented in the framework. It is presented as an idea for the broader security and privacy community.

---

## 🌐 Secure Communication Flow: Linking User, App, and Backend

A core innovation of this approach is the use of a secure communication flow that enables the backend to identify the user without ever learning their secret. This is achieved by:

- Providing a **dedicated, secure API endpoint** for the Zero Knowledge 2FA app to communicate with during authentication and recovery.
- Including a **cryptographic hash of `secret#login`** (e.g., SHA-256) alongside the encrypted `secret#2fa`. This hash allows the backend to identify which user or account the encrypted secret belongs to, without ever exposing the actual login secret.

**Security Consideration:**
- The hash of `secret#login` is irreversible and does not reveal the original secret, so it does not introduce a security risk.
- The app transmits the encrypted `secret#2fa` and the hash to the backend only via the secure endpoint, ensuring privacy and integrity.

---

## 🔐 Zero Knowledge 2FA: Step-by-Step Flow

1. **2FA is Mandatory:**
   - On registration, the user is informed that 2FA is required.

2. **Deterministic Secret Derivation:**
   - The user provides an email and a strong password (≥16 characters, with symbols, numbers, and letters).
   - These credentials are used to deterministically derive a secret, called `secret#login` (using a secure KDF such as HKDF).

3. **Random 2FA Secret Generation:**
   - The server generates a random, cryptographically secure secret, called `secret#2fa`.

4. **2FA Secret Encryption & QR Code:**
   - The server encrypts `secret#2fa` with `secret#login`.
   - A QR code is generated so the user can scan and store the encrypted 2FA secret in their authentication app.
   - **Note:** `secret#2fa` is never stored or persisted on the server.

5. **User Stores 2FA Secret:**
   - The user scans the QR code, saving the encrypted 2FA secret in their 2FA app, along with the hash of `secret#login`.
   - **Note:** The user and the app never see or handle the raw (decrypted) `secret#2fa`; only the encrypted value is stored and transmitted.

6. **2FA Challenge During Registration:**
   - The server prompts the user to authenticate using their 2FA app.

7. **User Authenticates:**
   - The user uses their app to send the encrypted `secret#2fa` and the hash of `secret#login` back to the server (the app does not decrypt it), via the secure endpoint.

8. **Server Decrypts 2FA Secret:**
   - The server re-derives `secret#login` from the user’s credentials and decrypts the received `secret#2fa`.

9. **Final User Secret Derivation:**
   - The server combines `secret#login` and `secret#2fa` to deterministically derive a final secret, `secret#user`.

10. **Cryptographic Addressing & Data Access:**
    - `secret#user` is used to access the user's data in the database (findBySecret), and as a decryption key for all user data.
    - No secret is ever stored on the server; all addressing and decryption are stateless.

---

## 🌟 What Makes This Innovative?

- **True Zero Knowledge:**
  - The server never stores, learns, or reconstructs any user secret, including the 2FA secret.
  - All authentication and data access require active user participation and knowledge.

- **Cryptographic 2FA Binding:**
  - The 2FA secret is deterministically bound to the user's login secret, not to any server-side record or mapping.
  - No server-side storage or lookup tables for 2FA secrets.

- **No Server-Side Recovery:**
  - If the user loses their secrets, the server cannot recover them—ensuring strong zero knowledge properties.

- **Integration with Cryptographic Addressing:**
  - The 2FA secret is part of the deterministic chain that derives the final user secret for authentication and data access.

- **Secure Communication Flow:**
  - The use of a secure endpoint and a hash of `secret#login` for backend identification further enhances privacy and security, ensuring the backend can identify the user without ever learning their secret.

- **Market Comparison:**
  - Most 2FA systems store the 2FA secret (or a hash) on the server, or rely on external providers.
  - This approach is unique: the server never stores or even knows the 2FA secret, and all authentication is user-driven and stateless.

---

## 📚 Further Reading

- [Innovations & Novel Contributions](../INNOVATIONS.md)
- [Zero Knowledge 2FA App – Requirements & User Experience](ZERO_KNOWLEDGE_2FA_APP.md)
