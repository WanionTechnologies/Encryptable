# Whitepaper: Deterministic Cryptography – Transient Knowledge Encryption & Stateless Security

## 🏁 Concept Overview

This whitepaper introduces the concept and technical approach of deterministic cryptography for transient knowledge, stateless security. By deriving cryptographic keys solely from user credentials and a mandatory request-scoped 2FA secret, systems can achieve strong request-scoped (transient knowledge) operation: no secrets or keys are ever stored or persistently retained by the server. All encryption and decryption are performed using keys deterministically reconstructed from user input as needed; these keys are never stored, only held in memory for the duration of the operation (typically, a single request). The 2FA secret is always the final, required layer of entropy in the system.

> **Note:** This is a conceptual proposal and is not implemented in the framework. It is presented as an idea for the broader security and privacy community.

---

## 🔑 Core Principle: Deterministic Key Derivation

- **No Key Storage:** The server never stores or persistently retains any user secret or cryptographic key. Keys are reconstructed on demand from user credentials (e.g., password, email) and a mandatory request-scoped 2FA secret, using a secure KDF (e.g., HKDF), and are only held in memory for the duration of the operation.
- **On-the-Fly Derivation:** All keys are derived on demand from user credentials (e.g., password, email) and a mandatory request-scoped 2FA secret, using a secure KDF (e.g., HKDF).
- **Stateless Security:** The server remains stateless with respect to secrets; all sensitive operations require user participation.
- **Transient Knowledge:** The server cannot decrypt or access user data without the user's active input, including both credentials and the 2FA secret, and secrets/keys are only present in memory for the duration of a request or operation.

---

## 🛠️ Technical Approach

1. **User Registration:**
   - User provides credentials (e.g., email + strong password).
   - Credentials are used as input to a secure KDF (e.g., HKDF) to derive a cryptographic key.
   - A random, cryptographically secure request-scoped 2FA secret is generated and included in the KDF input. 2FA is mandatory.

2. **Data Encryption:**
   - User data (or secrets, such as 2FA keys) are encrypted client-side or server-side using the derived key.
   - Encrypted data is stored; the key is never stored or transmitted.

3. **Authentication & Data Access:**
   - User provides credentials and the encrypted 2FA secret at login.
   - The same KDF process is used to re-derive the key, using credentials and the 2FA secret.
   - The derived key is used to decrypt data or secrets as needed, and is only present in memory for the duration of the operation.

4. **Mandatory Multi-Factor:**
   - The 2FA secret is always required and is the final layer of entropy for all cryptographic operations.

---

## 🌟 What Makes This Innovative?

- **Request-Scoped (Transient) Knowledge:** No secrets or keys are ever stored or persistently retained by the server. Keys are only reconstructed in memory as needed and never written to storage.
- **Statelessness:** The server does not need to manage or protect sensitive key material.
- **User-Centric Security:** Only the user can access or recover their data; all cryptographic operations require user input, including the 2FA secret.
- **Breach Resistance:** Even if the server is compromised, attackers cannot access user data without both the user's credentials and the 2FA secret.
- **Insider Threat Elimination:** No admin or privileged user can access or reset user secrets.

---

## 📊 Impact Assessment

| Category                 | Traditional Systems         | Deterministic Cryptography   | Impact                  |
|--------------------------|----------------------------|-----------------------------|-------------------------|
| **Key Storage**          | Required                   | None                        | 🟢 Eliminates risk      |
| **Data Breach Impact**   | Severe (data exposed)      | Minimal (encrypted only)    | 🟢 Drastic reduction    |
| **Insider Threat**       | High                       | None                        | 🟢 Eliminated           |
| **User Recovery**        | Admin reset possible       | User only (with credentials and 2FA) | 🟡 User diligence needed|
| **Compliance**           | Complex (HSM, audits)      | Simplified                  | 🟢 Lower cost           |

---

## 💡 Security & Privacy Implications

- **No server-side secrets to steal or misuse.**
- **User data is always encrypted at rest and in transit.**
- **Only users with the correct credentials and 2FA secret can access or recover their data.**
- **System is highly resistant to both external and internal threats.**

---

## 🧑‍💻 Example Flow

1. **Registration:**
   - User sets up account with email and strong password.
   - A request-scoped 2FA secret is generated and provided to the user (2FA is mandatory).
   - System derives key: `key = HKDF(email + password + 2FA secret)`
   - Data or secrets are encrypted with `key` and stored.

2. **Login & Access:**
   - User enters email and password and provides the encrypted 2FA secret.
   - System re-derives `key` and decrypts data/secrets as needed (key is only present in memory for the duration of the operation).

3. **Mandatory 2FA:**
   - The 2FA secret is always included in the KDF input for all cryptographic operations.

> For a deeper dive into request-scoped (transient knowledge) authentication flows, see [Transient-Knowledge Authentication](TRANSIENT_KNOWLEDGE_AUTH.md).

---

## ⚖️ Conclusion & Call to Action

Deterministic cryptography enables a new paradigm of request-scoped (transient knowledge), stateless security. By deriving all keys from user input—including credentials and a mandatory request-scoped 2FA secret—systems can eliminate key storage, minimize breach impact, and maximize user privacy and control. This approach is a foundation for the next generation of secure, user-centric applications.

> **Join the movement:** Consider adopting request-scoped (transient knowledge), stateless security principles in your own systems and advocate for user-centric privacy in your organization and community.
