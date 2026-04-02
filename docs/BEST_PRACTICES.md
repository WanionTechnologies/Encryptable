# 🛡️ Encryptable: Best Practices

## 🌟 Overview

**Encryptable provides the tools for request-scoped (transient) knowledge, but maintaining strong privacy is your responsibility.**  
This guide covers essential practices for zerifying secrets, managing derivation material, and designing secure flows that minimize memory exposure—ensuring your application truly protects user privacy and eliminates developer liability.

---

## 🔍 What Should Be Zerified?

**Always zerify:**
- **Secrets themselves:** Passwords, cryptographic keys, tokens, and any value used for authentication or encryption.
- **Decrypted data:** Any plaintext result from decryption operations.
- **Derivation material:** Any value used to derive secrets, such as:
  - Usernames, emails, phone numbers (if used in key derivation)
  - 2FA secrets or codes
  - Recovery codes or backup keys
  - Any intermediate value in a cryptographic flow

---

## 🛠️ How to Zerify

Encryptable provides extension functions for secure memory clearing:
- `String.zerify()` — Overwrites the internal value and cached hash.
- `ByteArray.parallelFill(0)` — Fills the array with zeros in parallel.
- `SecretKeySpec.clear()` — Overwrites the internal key bytes.

**Register objects for clearing at the end of each request:**
```kotlin
markForWiping(userSecret, username, twoFASecret, decryptedData)
```

---

## 🧑‍💻 Example: Secure Flow

```kotlin
val username = getUsernameInput()
val password = getPasswordInput()
val twoFASecret = get2FAInput()

// Derive key
val keyMaterial = username + password + twoFASecret
val secretKey = deriveKey(keyMaterial)

// Use secretKey for encryption/decryption
val decryptedData = decrypt(secretKey, encryptedBlob)

// Register all sensitive/intermediate values for clearing
markForWiping(username, password, twoFASecret, keyMaterial, secretKey, decryptedData)
```

---

## ⏱️ When to Zerify
- **Immediately after use:** As soon as a secret or sensitive value is no longer needed, register it for clearing.
- **At the end of each request:** All objects registered for clearing will be securely wiped by Encryptable.

---

## 💡 Additional Tips
- Avoid storing secrets or derivation material in caches, logs, or long-lived objects.
- Do not print secrets to logs or error messages.
- Minimize the lifetime and scope of sensitive data in memory.
- For ultra-high-security, consider running the JVM in an encrypted memory enclave.

---

## 🔐 Maintaining Request-Scoped (Transient) Knowledge

**Encryptable provides the foundation for request-scoped (transient) knowledge, but achieving strong privacy requires developer discipline.**

### ❌ What NOT to Store in the Database

**Never store these in plaintext:**
- **Usernames** — If used for key derivation, storing them breaks request-scoped knowledge
- **Email addresses** — Unless encrypted with `@Encrypt`
- **Phone numbers** — Unless encrypted with `@Encrypt`
- **Passwords** — Should NEVER be stored, even hashed
- **Password hashes** — Defeats the purpose of cryptographic addressing
- **2FA secrets** — Unless encrypted with `@Encrypt`
- **Recovery codes** — Unless encrypted with `@Encrypt`
- **Any derivation material** — If it's used to derive the secret, don't store it

### ✅ Request-Scoped Knowledge Authentication Pattern

**Traditional (❌ Not Request-Scoped Knowledge):**
```kotlin
// DON'T DO THIS - Stores username and password hash
@Document
data class User(
    @Id val id: String,
    val username: String,           // ❌ Stored in plaintext
    val passwordHash: String        // ❌ Stored (even if hashed)
)

// Login: Find by username, compare hash
val user = userRepository.findByUsername(username)
if (passwordHash == hashPassword(password)) { /* login */ }
```

**Request-Scoped Knowledge with Encryptable (✅ Correct):**
```kotlin
// DO THIS - No username or password stored
@Document
data class User(
    @HKDFId val id: UUID,           // ✅ Derived from secret
    @Encrypt var email: String,     // ✅ Encrypted
)

// Login: Direct lookup by secret (no username needed)
val secret = deriveUserSecret(username, password, twoFA)
val user = userRepository.findBySecretOrNull(secret)

// Register secret for wiping
markForWiping(secret, username, password, twoFA)

if (user != null) { /* login successful */ }
```

### 🎯 Key Principles for Request-Scoped (Transient) Knowledge

1. **Secrets are addresses, not authentication credentials**
   - Use `findBySecretOrNull(secret)` instead of username/password comparison
   - The ability to retrieve the entity proves knowledge of the secret

2. **Never store what you use to derive secrets**
   - If `username + password` derives the secret → Don't store username or password
   - If `email + pin` derives the secret → Don't store email plaintext

3. **Encrypt all sensitive fields**
   - Use `@Encrypt` on emails, phone numbers, 2FA secrets, recovery codes, etc.
   - This ensures data is protected even if the database is compromised

4. **Think "database compromise = useless data"**
   - If an attacker steals the database, can they:
     - Identify users? ❌ No (no usernames/emails)
     - Decrypt data? ❌ No (keys not stored)
     - Correlate entities? ❌ No (IDs are secret-derived)
   - If you answer "yes" to any, you're not request-scoped knowledge

### 🧪 Request-Scoped Knowledge Checklist

Before deploying, ask yourself:

- [ ] Are usernames/emails stored in plaintext? → Encrypt them or don't store them
- [ ] Are passwords stored (even hashed)? → Remove them entirely
- [ ] Can I authenticate without comparing stored credentials? → Use `findBySecretOrNull`
- [ ] Is derivation material stored? → Remove or encrypt it
- [ ] Would a database dump reveal user identities? → Fix your schema
- [ ] Are secrets/decrypted data wiped after use? → Use `markForWiping`

---

## 🏰 Advanced: Deploying Encryptable in Memory Enclaves

> **Highly recommended:** For maximum security, an Encryptable application should be run inside a hardware-backed memory enclave whenever possible. This ensures that secrets remain protected even from the host OS and administrators.

For the highest level of security, run Encryptable inside a hardware-backed memory enclave (trusted execution environment). Enclaves protect secrets even from the host OS and administrators, ensuring that cryptographic material is only ever present in protected memory.

**Supported enclave platforms include:**
- Intel SGX (Software Guard Extensions)
- AWS Nitro Enclaves
- Oracle Cloud Infrastructure (OCI) AMD Secure Enclaves

**Best practices for enclave deployment:**
- Never log secrets or sensitive data
- Disable core dumps and memory swapping
- Use secure memory wiping for all cryptographic material
- Regularly audit code and operational procedures for leaks

For a full explanation of enclave benefits and limitations, see [Encrypted Memory Enclaves](ENCRYPTED_MEMORY_ENCLAVES.md).

---

### 📚 Related Documentation

- [Transient Knowledge Authentication](concepts/TRANSIENT_KNOWLEDGE_AUTH.md)
- [Recovery Codes](RECOVERY_CODES.md)
- [Memory Hygiene in Encryptable](MEMORY_HIGIENE_IN_ENCRYPTABLE.md)
