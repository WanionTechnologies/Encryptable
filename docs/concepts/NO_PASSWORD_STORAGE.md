# Whitepaper: Zero Password Storage – Authentication Without Stored Credentials

## 🏁 Concept Overview

This whitepaper explores the radical idea of authentication without storing password hashes. The concept is to derive entity UUIDs from user credentials via HKDF, enabling identity verification without ever storing or comparing password hashes.

> **Note:** This is a conceptual proposal and is not implemented in the framework. It is presented as an idea for the broader security and privacy community.

---

## 🔐 How Traditional Authentication Works (And Its Problems)

### Standard Authentication Flow

```kotlin
// User Registration
fun register(username: String, password: String) {
    val passwordHash = bcrypt.hash(password, cost = 12)  // ❌ Store hash
    database.save(User(
        username = username,
        passwordHash = passwordHash,  // ❌ Vulnerable to theft
        email = email,
        // ... other fields
    ))
}

// User Login
fun login(username: String, password: String): User? {
    val user = database.findByUsername(username)
    val inputHash = bcrypt.hash(password, cost = 12)
    if (bcrypt.verify(inputHash, user.passwordHash)) {  // ❌ Hash comparison
        return user  // Authenticated
    }
    return null  // Failed
}
```

### What Gets Stored

```json
{
  "_id": "507f1f77bcf86cd799439011",
  "username": "alice",
  "passwordHash": "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
  "email": "alice@example.com",
  "createdAt": "2023-10-15T10:30:00Z"
}
```

**Problem:** The password hash is **stored in the database** and can be **stolen**.

---

## 💥 The Password Hash Breach Epidemic

### Major Password Hash Breaches (Last 10 Years)

- LinkedIn (2012): 117M hashes stolen
- Adobe (2013): 153M hashes stolen
- Dropbox (2012): 68M hashes stolen
- MyFitnessPal (2018): 150M hashes stolen
- Equifax (2017): 147M hashes stolen

**Impact:** Stolen hashes enable offline brute-force attacks, credential stuffing, and identity theft.

---

## 🚀 Encryptable's Concept: Zero Password Storage

### How It Works

- No password hashes are stored in the database.
- The user's credentials are used to derive a cryptographic secret (via HKDF or similar KDF).
- The secret is used to deterministically generate the entity UUID and decrypt encrypted fields.
- If the credentials are correct, the correct UUID is derived and the entity is found and decrypted.
- If the credentials are incorrect, no entity is found and no data is decrypted.

> **Note:** The following is a simplified example for illustration purposes only. For actual production authentication flows and best practices, refer to [Transient-Knowledge Authentication](TRANSIENT_KNOWLEDGE_AUTH.md).

### Example
```kotlin
val secret = HKDF.deriveKey(username + password, salt)
val user = db.findBySecretOrNull(secret)
// No password hash stored or compared
```

---

## 🏆 Security Benefits

- **Eliminates password hash theft risk**
- **No offline brute-force attacks on hashes**
- **Request-scoped (transient) knowledge authentication**
- **Per-user cryptographic isolation**
- **No credential storage in the database**

---

## 🎬 Conclusion

Encryptable Framework sets a new standard for authentication security by eliminating password hash storage entirely. This approach removes a major attack vector and enables strong request-scoped (transient) knowledge authentication for modern applications.
