# Whitepaper: Transient Knowledge Authentication with Transient Knowledge 2FA – Default Design

## 🏁 Concept Overview

This whitepaper presents a fully request-scoped (transient) knowledge and keyless authentication system where Transient Knowledge 2FA is the default and central design.

> **Note:** This is a conceptual proposal and is not implemented in the framework. It is presented as an idea for the broader security and privacy community.

---

## 🔐 Transient Knowledge 2FA: Default Authentication Flow

### **Key Principles**
- **True request-scoped (transient) knowledge:** The server never stores, learns, or reconstructs any user secret, including the Transient Knowledge 2FA secret.
- **Keyless Addressing:** All user data is accessed and decrypted using secrets derived from user credentials and the Transient Knowledge 2FA secret.
- **No Server-Side Recovery:** If the user loses their credentials and Transient Knowledge 2FA secret, the server cannot recover them.
- **In-Memory Cryptography:** All cryptographic operations (decryption, key derivation) happen only in memory and are never persisted. Decrypted data remains in memory only as long as needed; only encrypted data is stored at rest (in the database) or in transit. **Developers must use `markForWiping` (or equivalent) to ensure user details, 2FA secrets, and all derivation material are securely cleared from memory after use.**

### **Step-by-Step Flow**

1. **Transient Knowledge 2FA is Mandatory:**
   - During registration, the user is informed that Transient Knowledge 2FA is required.
2. **Deterministic Secret Derivation:**
   - The user provides an email and a strong password (≥16 characters, with symbols, numbers, and letters).
   - These credentials are used to deterministically derive a secret, `secret#login`, using a secure KDF (e.g., HKDF).
   - **Best Practice:** Immediately call `markForWiping` on the email, password, and any intermediate values.
3. **Random Transient Knowledge 2FA Secret Generation:**
   - The server generates a random, cryptographically secure secret, `secret#2fa`.
   - **Best Practice:** Call `markForWiping` on the raw `secret#2fa` after use.
4. **2FA Secret Encryption & QR Code:**
   - The server encrypts `secret#2fa` with `secret#login`.
   - A QR code is generated so the user can scan and store the encrypted Transient Knowledge 2FA secret in their Transient Knowledge 2FA app.
   - **Note:** The QR code contains only the encrypted secret#2fa, not a TOTP seed or raw secret. The raw `secret#2fa` is never stored or persisted on the server, and is never revealed to the user or their app.
   - **Best Practice:** Call `markForWiping` on the encrypted value after transmission.
5. **User Stores Transient Knowledge 2FA Secret:**
   - The user scans the QR code, saving the encrypted secret#2fa in their Transient Knowledge 2FA app.
   - **Note:** Only the encrypted value is stored and transmitted; the user and app never see or handle the raw (decrypted) `secret#2fa`.
6. **Transient Knowledge 2FA Challenge During Registration or Login:**
   - The server prompts the user to authenticate using their Transient Knowledge 2FA app.
7. **User Authenticates:**
   - The user uses their app to send the encrypted `secret#2fa` back to the server (the app does not decrypt it).
   - **Note:** Registering for cleanup is not necessary for encrypted data, only for raw secrets and derivation material.
8. **Server Decrypts Transient Knowledge 2FA Secret:**
   - The server re-derives `secret#login` from the user’s credentials and decrypts the received `secret#2fa`.
   - **Best Practice:** Call `markForWiping` on all intermediate and decrypted values.
9. **Final User Secret Derivation:**
   - The server combines `secret#login` and `secret#2fa` to deterministically derive a final secret, `secret#user`.
   - **Best Practice:** Call `markForWiping` on all derivation material and `secret#user` after use.
10. **Cryptographic Addressing & Data Access:**
    - `secret#user` is used to access the user's data in the database (findBySecret), and as a decryption key for all user data.
    - No secret is ever stored on the server; all addressing and decryption are stateless.
    - **Best Practice:** Call `markForWiping` on all secrets and intermediate values after use.

---

## 🔄 Flows

### **Registration**
```
User Registration
        ↓
1. User provides email and strong password
        ↓
2. Derive secret#login = HKDF(email + password)
        ↓
3. Server generates random secret#2fa
        ↓
4. Encrypt secret#2fa with secret#login
        ↓
5. Show QR code to user (contains encrypted secret#2fa)
        ↓
6. User scans and stores encrypted secret#2fa in Transient Knowledge 2FA app
        ↓
7. User completes Transient Knowledge 2FA challenge (sends encrypted secret#2fa back)
        ↓
8. Server decrypts secret#2fa, derives secret#user = HKDF(secret#login + secret#2fa)
        ↓
9. Save user entity with secret#user
```

### **Login**
```
User Login
        ↓
1. User provides email and password
        ↓
2. Derive secret#login = HKDF(email + password)
        ↓
3. User retrieves encrypted secret#2fa from Transient Knowledge 2FA app
        ↓
4. User sends encrypted secret#2fa to server
        ↓
5. Server decrypts secret#2fa using secret#login
        ↓
6. Derive secret#user = HKDF(secret#login + secret#2fa)
        ↓
7. Find user entity by secret#user (findBySecret)
        ↓
8. If found → Authenticated! ✅
   If not found → Invalid credentials ❌
```

---

## 🔑 Recovery Keys (Encrypted, Transient Knowledge, Strictly Independent Recovery)

### **Key Principles**
- **Encrypted Storage:** Recovery keys are stored as encrypted fields (`@Encrypt`) in the user’s record, using `secret#user` as the encryption key.
- **One-Time Access:** After registration, the user is shown the recovery keys once and must save them securely. The server never sees the raw keys.
- **Separation of Keys:**
  - 5 keys for password recovery
  - 5 keys for Transient Knowledge 2FA recovery
- **Strictly Independent Recovery:**
  - Only one factor can be recovered at a time, and each recovery process requires the other factor.
  - Password recovery requires Transient Knowledge 2FA and a password recovery key.
  - Transient Knowledge 2FA recovery requires password and a Transient Knowledge 2FA recovery key.
  - The system enforces that both cannot be reset in a single session or with only recovery keys. (Session enforcement details are application-specific and out of scope for this document.)
  - Even if all recovery keys are leaked, account takeover is impossible without also knowing the password or Transient Knowledge 2FA.
- **Transient Knowledge:** The server cannot access or use the recovery keys, as they are always encrypted. Only the authenticated user can decrypt them.
- **Best Practice:** Developers should use `markForWiping` for all recovery keys and any material used in the recovery process to ensure memory hygiene.

### **How It Works**
1. **Registration:**
   - After successful registration and Transient Knowledge 2FA setup, 10 recovery keys are generated (5 for password, 5 for Transient Knowledge 2FA).
   - The user is shown these keys once and instructed to save them securely.
   - The keys are stored as encrypted fields in the user’s record, encrypted with `secret#user`.
2. **Access:**
   - Only after login (with `secret#user`) can the user decrypt and view their recovery keys.
   - The server never sees the plaintext keys.
3. **Recovery:**
   - **Password Recovery:**
     - The user must have their Transient Knowledge 2FA (and a password recovery key).
     - The user authenticates with Transient Knowledge 2FA, provides a password recovery key, and sets a new password.
   - **Transient Knowledge 2FA Recovery:**
     - The user must have their password (and a Transient Knowledge 2FA recovery key).
     - The user authenticates with password, provides a Transient Knowledge 2FA recovery key, and sets up a new Transient Knowledge 2FA secret.
   - **Strict Enforcement:**
     - The system enforces that both password and Transient Knowledge 2FA cannot be recovered in a single session or flow. (Session enforcement details are application-specific and out of scope for this document.)
     - Recovery keys from one set cannot be used to recover the other factor.
     - If all recovery keys are leaked, but the attacker does not have the password or Transient Knowledge 2FA, account takeover is still impossible.
   - The process never exposes the recovery key to the server in plaintext.
   - **Edge Case:** If the user loses their Transient Knowledge 2FA app but still has their password and a recovery key, they can recover their Transient Knowledge 2FA using the recovery process above. If the user loses both their password and Transient Knowledge 2FA (and all recovery keys), recovery is impossible (unless brute force, which is practically impossible). This is a deliberate, strict security choice. See the recovery section for more details.

### **Security & Usability**
- **User-Only Access:** Only the user, after authentication, can access the recovery keys.
- **No Server-Side Recovery:** If the user loses both their credentials, Transient Knowledge 2FA, and all recovery keys, recovery is impossible.
- **Best Practice:** Users should store recovery keys in a password manager or physically print them. Loss of both the Transient Knowledge 2FA key and all recovery keys means recovery is impossible (unless brute force, which is practically impossible).
- **Security Note:**
  - This design prevents a single point of compromise. Even if an attacker obtains all recovery keys, they cannot take over the account without also possessing either the password or Transient Knowledge 2FA.
  - The system strictly enforces that both cannot be reset at once, and that recovery keys alone are never sufficient for full account recovery.
  - This is much safer than allowing both to be recovered at once, and protects against "all keys leaked" scenarios.

---

## 🌟 What Makes This Innovative?
- **True request-scoped (transient) knowledge:** The server never stores, learns, or reconstructs any user secret, including the Transient Knowledge 2FA secret.
- **Cryptographic 2FA Binding:** The Transient Knowledge 2FA secret is deterministically bound to the user's login secret, not to any server-side record or mapping.
- **No Server-Side Recovery:** If the user loses their secrets, the server cannot recover them—ensuring strong zero knowledge properties.
- **Integration with Cryptographic Addressing:** The Transient Knowledge 2FA secret is part of the deterministic chain that derives the final user secret for authentication and data access.
- **Market Comparison:** Most 2FA systems store the 2FA secret (or a hash) on the server, or rely on external providers. This approach is unique: the server never stores or even knows the Transient Knowledge 2FA secret, and all authentication is user-driven and stateless.

---

## 📚 Further Reading
- [Innovations & Novel Contributions](../INNOVATIONS.md)
- [Cryptographic Addressing](../INNOVATIONS.md#-innovation-1-cryptographic-addressing-with-hkdf-based-deterministic-cids)

---

**Last Updated:** 2025-10-21  
**Framework Version:** 2.0