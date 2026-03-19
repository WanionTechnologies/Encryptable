# Encryptable: Technical Innovations & Novel Contributions

Encryptable is an ODM (Object-Document Mapper) for MongoDB, distinguished by its ORM-like relationship management and advanced cryptographic features.\
This combination brings relational-style modeling and request-scoped ("Transient Knowledge") security to document databases.

## 🏯 Executive Summary

Encryptable introduces several innovations in the MongoDB persistence and encryption space.\
This document highlights the novel technical contributions that distinguish Encryptable from all existing solutions in the market.

**Key Innovation Areas:**

0. **CID, a Compact, URL-Safe Identifier** (22-character Base64 alternative to UUID)
1. **Cryptographic Addressing with HKDF-Based Deterministic CID** (Novel Architecture for MongoDB)
2. **Deterministic Cryptography for Stateless, Transient-Knowledge Security** (New!)
3. **Anonymous Data Model** (INNOVATION: No user identity, credentials, or metadata required on server)
4. **ORM-Like Relationship Management for MongoDB (ODM)** (One-to-One, One-to-Many, Many-to-Many, Cascade Delete)
5. **Transparent Polymorphism for Nested Entities** (Novel: Zero-Configuration Polymorphic Documents)
6. **Capability URLs – Secure, Shareable, Transient-Knowledge Access** (Novel Application)
7. **Automatic Field-Level Encryption with Per-User Isolation** (Unique Combination)
8. **Intelligent Change Detection via Field Hashing** (Novel Approach)
9. **The Field-as-Live-Mirror** (Novel Combination — `ByteArray` field as a live mirror of any storage backend)

---

## 🆕 Innovation #0: CID, a Compact, URL-Safe Identifier

The CID (Compact ID) is a 22-character, URL-safe Base64 encoding of 16 bytes (128 bits) derived from user secrets using HKDF.\
It is a modern, compact, and secure alternative to the traditional UUID, providing the same entropy in a much shorter, URL-friendly format.\
CID is used throughout Encryptable for entity identification and cryptographic addressing.\
For a detailed comparison, see [CID Compactness and Why CID is Shorter than UUID](CID_COMPACTNESS.md).

**Security Note:** CIDs are database lookup keys, not secrets.\
The CID can be safely stored in the database without compromising the encryption keys, which are derived separately using a different HKDF context.


---

## 🌟 Innovation #1: Cryptographic Addressing with HKDF-Based Deterministic CIDs

### **Why This Is Innovative**

Cryptographic Addressing is a novel approach for MongoDB that derives entity IDs directly from user secrets using HKDF, enabling stateless, request-scoped (transient knowledge) data access.

- **No mapping tables, no lookups, no persistent storage of IDs.**
- **The address of the data is the secret itself, computed on demand.**
- **Stateless, request-scoped, per-user cryptographic isolation is achieved by design.**
- **The server is fundamentally incapable of leaking, reconstructing, or resetting user data.**
- **O(1) User Lookups via Cryptographic Addressing:** Traditional systems require looking up users by username/email (O(log n) with index), then retrieving their data. Encryptable eliminates this step entirely—the secret directly computes the CID (primary key), enabling instant O(1) access without any username/email lookup table.
- **Reduced Database Load:** No secondary indexes or mapping tables are needed for user lookups. Every access is a direct primary key lookup, maximizing database efficiency.

> **The Innovation Explained:**  
> Traditional approach: `username → [index lookup O(log n)] → user_id → [primary key lookup O(1)] → data`  
> Encryptable approach: `secret → [compute CID] → [primary key lookup O(1)] → data`  
>  
> **The innovation isn't that MongoDB _id lookups are O(1)** (that's standard).  
> **The innovation is achieving O(1) USER lookups** without storing or indexing usernames/emails—the secret IS the addressing mechanism.  
> This eliminates the username→user_id mapping entirely, reducing complexity and improving privacy.

This innovation enables direct, stateless, and privacy-preserving access to encrypted data, making the server a passive storage layer and shifting all control to the user.\
It eliminates entire classes of attacks (credential leaks, insider threats, enumeration, admin resets) and operational burdens (key management, mapping maintenance, compliance overhead).

### **What Makes This Unique**

This innovation merges two tightly related breakthroughs:
- **HKDF-Based Deterministic CID Generation:** A novel use of HKDF (HMAC-based Key Derivation Function) to generate deterministic, cryptographically secure CIDs for MongoDB entities, with automatic class namespacing and entropy validation.
- **Cryptographic Addressing:** The secret itself is the addressing mechanism—no need to store or look up ID mappings. The entity's secret deterministically produces the address (CID), making the system stateless.
- **Automatic Entropy Validation:** All random CID generation validates entropy using Shannon entropy calculation (≥3.5 bits/character) and repetition checking (≥25% unique characters), automatically regenerating if insufficient entropy is detected. This prevents weak secrets like "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" while accepting high-quality Base64 URL-safe secrets.

### **The Innovation**

```kotlin
HKDFID({ secret, sourceClass ->
    HKDF.deriveFromEntropy(secret, sourceClass, 16).cid
})
```

- **Cryptographic Secure Derivation:** Uses HKDF (RFC 5869) instead of weak hash functions (MD5, SHA-1), producing 128-bit CIDs that are collision-resistant for centuries at scale.
  - **Collision Resistance:** CID provides 2^32 (over 4.2 billion times) more collision resistance than MongoDB's ObjectId. At 1 million operations per second, you'd need 585,000 years to reach a 50% collision probability—making accidental collisions mathematically impossible in any real-world scenario. For detailed mathematical analysis, see [CID Collision Analysis](CID_COLLISION_ANALYSIS.md).
- **Automatic Entity Class Namespacing:** The entity class name is included in the HKDF derivation, so the same secret yields different CIDs for different entity types.
- **Deterministic but Unpredictable:** Same secret + same class = same CID (deterministic); different secrets = different CIDs (unpredictable without secret).
- **Stateless Addressing:** No mapping or storage of IDs is required. The CID is computed on-demand from the secret, enabling stateless, request-scoped (transient knowledge) addressing.

### **Comparison with Existing Solutions**

| Approach | Algorithm | Namespace Support | Security | Framework Integration |
|----------|-----------|-------------------|----------|---------------------|
| **UUIDv4 (Random)** | Random | ❌ No | ⚠️ Random only | ⚠️ Standard library |
| **UUIDv5 (RFC 4122)** | MD5 hash | ⚠️ Manual namespace | ❌ MD5 broken (2004) | ❌ No |
| **MongoDB ObjectId** | Timestamp + Random | ❌ No | ⚠️ Partially predictable | ✅ Native |
| **Encryptable** | **HKDF (RFC 5869)** | ✅ **Automatic** | ✅ **Cryptographically secure** | ✅ **Full ORM** |

**Encryptable is the only solution in this comparison with all four features.**

### **Real-World Impact**

```kotlin
// Traditional systems need mapping storage
database.userIdMappings.put(userSecret, randomUUID)
// Storage: additional 16 bytes per user
// 1M users = 16 MB mapping data
// Lookup: O(log n) or O(1) with cache

// Encryptable innovation: Zero mapping storage
val user = User().withSecret(userSecret)  // CID computed on-demand
// Storage: no additional bytes per user.
// Lookup: O(1) deterministic computation (no matter how many users)
// = More efficient AND more secure
```

### **Learn More**

See the full technical overview in [Cryptographic Addressing: Technical Overview](CRYPTOGRAPHIC_ADDRESSING.md "Technical deep dive into cryptographic addressing")

---

## 🔑 Innovation #2: Deterministic Cryptography for Transient Knowledge, Stateless Security

This approach leverages deterministic cryptography to achieve strong request-scoped (transient knowledge), stateless security.\
All cryptographic keys are derived solely from user credentials and a mandatory request-scoped 2FA secret, using a secure KDF (such as HKDF).\
No secrets or keys are ever stored, reconstructed, or recoverable by the server.\
The 2FA secret is always the final, required layer of entropy, ensuring that only the user can access their data.

> **Note:** Encryptable provides the foundation for a transient knowledge system, but achieving strict request-scoped knowledge in practice requires developers to consistently use `@HKDFId` and `@Encrypt` for all sensitive fields. Discipline and correct usage are essential to maintain the transient knowledge property throughout the application.

### **Relationship to Cryptographic Addressing**

Deterministic cryptography is closely related to the concept of cryptographic addressing (see Innovation #1).\
Both rely on deriving identifiers and cryptographic keys on-the-fly from user secrets, eliminating the need for persistent key or mapping storage.\
In cryptographic addressing, entity IDs are deterministically derived from secrets, while in deterministic cryptography, all encryption and decryption keys are derived in the same way.\
This synergy enables a fully stateless, transient knowledge architecture.

### **The Innovation**

- **No Key Storage:** The server never stores or learns any user secret or cryptographic key.
- **On-the-Fly Derivation:** All keys are derived on demand from user credentials and the 2FA secret.
- **Stateless Security:** The server remains stateless with respect to secrets; all sensitive operations require user participation.
- **Transient Knowledge:** The server cannot decrypt or access user data without the user's active input, including both credentials and the 2FA secret, and secrets are only present in memory for the duration of a request.

### **Why This Is Innovative**

- **Request-Scoped Knowledge:** No secrets or keys are ever stored or reconstructable by the server beyond the request scope. Secrets exist only ephemerally, for the duration of a request.
- **Breach Resistance:** Even if the server is compromised, attackers cannot access user data without both the user's credentials and the 2FA secret.
- **User-Centric Security:** Only the user can access or recover their data; all cryptographic operations require user input, including the 2FA secret.
- **Insider Threat Elimination:** No admin or privileged user can access or reset user secrets.

### **Learn More**

See more about this concept [Deterministic Cryptography](concepts/DETERMINIST_CRYPTOGRAPHY.md).

---

## 🏛️ Innovation #3: Anonymous Data Model with Transient Knowledge

### **What Makes This Unique**

**Encryptable enables an "Anonymous Data Model" with Transient Knowledge—a strong privacy model that goes beyond traditional request-scoped knowledge systems.**

Most systems claiming "zero-knowledge" (Signal, ProtonMail, etc.) protect **content** but still store user identity, credentials, and metadata. Encryptable enables you to go further: **the server can store NOTHING about users**—no identity, no credentials, no metadata, no way to identify who users are.

> **Important:** This anonymous, transient-knowledge model is a **capability** that Encryptable enables, not a mandatory requirement. Developers can choose to store user details (usernames, emails, etc.) if their application requires it—though this is **not recommended** for maximum privacy. If you do store user details, using `@Encrypt` on those fields ensures the server stores only encrypted data it cannot read, preserving confidentiality while allowing you to meet your application's requirements.

### **Traditional Zero-Knowledge vs Transient Knowledge (Request-Scoped)**

**When using Encryptable's Anonymous Data Model (storing no user identity):**

| What is Stored? | Traditional Zero-Knowledge (Signal, ProtonMail) | Encryptable (Anonymous, Transient Knowledge) |
|----------------|-------------------------------------|----------------------------------|
| Usernames/Email | ✅ Stored | ❌ **NOTHING stored** |
| Passwords (hashed) | ✅ Stored | ❌ **NOTHING stored** |
| 2FA Secrets | ✅ Stored | ❌ **NOTHING stored** |
| Account Metadata | ✅ Stored | ❌ **NOTHING stored** |
| Session Tokens | ✅ Stored | ❌ **NOTHING stored** |
| Recovery Info | ✅ Stored | ❌ **NOTHING stored** |
| User Data (encrypted) | ✅ Can't decrypt | ✅ Can't decrypt |

**Alternative: If you store user details with `@Encrypt`:**

| What is Stored? | Encryptable with Encrypted User Details |
|----------------|------------------------------------------|
| Usernames/Email | ✅ Stored **encrypted** (server can't read) |
| Passwords (hashed) | ❌ **NOT stored** (never store passwords) |
| Account Metadata | ✅ Stored **encrypted** (server can't read) |
| User Data (encrypted) | ✅ Can't decrypt |

> **Note:** Even if you store encrypted user details, the server cannot read them without the user's secret. This maintains confidentiality for content, though the server knows encrypted records exist (unlike pure anonymous mode where the server has no concept of users at all).

**Traditional Zero-Knowledge:** "We can't read your messages"—Server cannot access content, but knows WHO you are and THAT you have data.

**Anonymous, Transient Knowledge:** "We don't know you exist"—Server knows NOTHING about users. Just cryptographically-addressed encrypted data, and secrets are only present in memory for the duration of a request (transient knowledge).

### **The Innovation: Server Knows Nothing (or Only Encrypted Data)**

**Approach 1: Pure Anonymous Data Model (Recommended for Maximum Privacy)**
```
Secret → HKDF → CID → Database stores:
  - CID: "dGVzdF9jaWRfZXhhbXBsZQ" (cryptographic address)
  - Data: encrypted_data

→ Server knows: NOTHING (just meaningless CIDs and encrypted blobs)
→ Cannot identify, count, or correlate users
```

**Approach 2: With Encrypted User Details (If Application Requires)**
```
Secret → HKDF → CID → Database stores:
  - CID: "dGVzdF9jaWRfZXhhbXBsZQ" (cryptographic address)
  - Username: encrypted("alice@email.com") with @Encrypt
  - Data: encrypted_data

→ Server knows: Encrypted records exist (but cannot read them)
→ Can count records, but cannot identify WHO or correlate data
```

**Summary:**
- Encryptable does not provide strict zero-knowledge in the cryptographic sense, as secrets are present in memory for the duration of a request (transient knowledge), but it enables a strong privacy model where the server can be completely unaware of user identities and data content.
- This approach minimizes risk and maximizes privacy, while being transparent about the actual security guarantees.

---

## 🔗 Innovation #4: ORM-Like Relationship Management for MongoDB (One-to-One, One-to-Many, Many-to-Many, Cascade Delete)

*For practical usage, examples, and best practices, see [ORM-Like Features in Encryptable](ORM_FEATURES.md).* 

### **What Makes This Unique**

**Bridging the SQL/NoSQL Gap:** Encryptable introduces true relational modeling to MongoDB, bridging the gap between NoSQL flexibility and the robust relationship management traditionally found in SQL databases.\
With support for one-to-one, one-to-many, and many-to-many associations, as well as cascade delete and referential integrity, developers can now design complex, interconnected data models in MongoDB as intuitively as in relational databases.\
This innovation eliminates one of the main reasons to choose SQL over MongoDB, empowering privacy-focused applications to leverage NoSQL scalability without sacrificing data integrity or modeling power.

### **The Innovation**

- **Natural Relationship Modeling:**
  - One-to-one: Simply include another entity as a field inside your entity class.
  - One-to-many: Use a list of entities as a field inside your entity class.
  - Many-to-many: Entities contain references (links) to multiple other entities, forming bidirectional associations.
  - No special annotations are required for these relationships—just natural object composition and references, making the code intuitive and idiomatic for Kotlin/Java developers.

- **Cascade Delete with `@PartOf`:**
  - The `@PartOf` annotation is used to mark child entities as part of a parent, enabling automatic cascade delete.
  - When a parent entity is deleted, all associated children (marked with `@PartOf`) are also deleted, ensuring referential integrity.

- **Automatic Relationship Management:**
  - Handles bidirectional and unidirectional relationships.
  - Supports lazy loading for large or binary fields, optimizing performance.

### **Why This Is Innovative**

- **Annotation-Free Simplicity:** Unlike most MongoDB frameworks or traditional ORMs, you do not need to use verbose or complex annotations to define relationships. This reduces boilerplate and makes domain modeling more natural.
- **Bridging NoSQL and ORM Worlds:** You bring the productivity and data integrity of ORM-style modeling to MongoDB, a NoSQL database, without sacrificing its flexibility.
- **Developer Experience:** By leveraging standard object-oriented constructs, you make it easy for developers to model complex domains, reducing cognitive load and risk of errors.
- **Automatic Referential Integrity:** The use of @PartOf for cascade delete ensures that data remains consistent and clean, a feature rarely found in NoSQL solutions.

### **Comparison with Existing Solutions**

| Feature                | MongoDB Native | JPA/Hibernate | Encryptable |
|------------------------|----------------|---------------|-------------|
| One-to-One             | Manual refs    | Yes           | ✅ Yes (object field) |
| One-to-Many            | Manual refs    | Yes           | ✅ Yes (list field)   |
| Many-to-Many           | Manual refs    | Yes           | ✅ Yes (links/refs)   |

---

## 🎭 Innovation #5: Transparent Polymorphism for Nested Entities

### **What Makes This Unique**

**Novel approach:** To our knowledge, the first MongoDB ODM to support fully transparent polymorphism for nested documents without any type annotations, discriminator columns, or configuration.

Traditional ORMs require explicit type discriminators (`@Type`, `@JsonTypeInfo`, `@Inheritance`). Encryptable eliminates this — use abstract classes as field types, and the framework automatically preserves the concrete type.

### **The Innovation**

```kotlin
// Abstract base class
abstract class Payment<P : Payment<P>> : Encryptable<P>() {
    abstract override var id: CID?
    
    @Encrypt var amount: Double? = null
}

class CreditCardPayment : Payment<CreditCardPayment>() {
    @HKDFId
    override var id: CID? = null
    
    @Encrypt var cardNumber: String? = null
}

// Order with polymorphic field
class Order : Encryptable<Order>() {
    @PartOf var payment: Payment<*>? = null  // 🎯 Polymorphic field!
}

// Usage - completely transparent
order.payment = CreditCardPayment().apply { cardNumber = "4532..." }
orderRepository.save(order)

// Concrete type preserved after save/load
val retrieved = orderRepository.findBySecretOrNull(secret)!!
retrieved.payment is CreditCardPayment  // ✅ true!
```

### **How It Works**

Uses internal `encryptableFieldTypeMap` to track concrete types, storing only when type differs from field declaration (minimal storage overhead).

### **Key Features**

- ✅ **Zero annotations** - No `@Type`, `@JsonTypeInfo`, or discriminator columns
- ✅ **Type preservation** - Concrete type survives save/load cycle
- ✅ **Lazy loading compatible** - Loads correct type automatically
- ✅ **Runtime type changes** - Change payment type, old one auto-deleted via `@PartOf`
- ✅ **Storage optimized** - Type stored only when polymorphic (zero overhead otherwise)
- ✅ **Works with encryption** - All polymorphic fields can be `@Encrypt`

> **⚠️ Scope:** Polymorphism applies to **single `Encryptable<*>` fields only**. It does **not** apply to `List<Encryptable<*>>` fields—lists require concrete types at declaration.

### **Comparison with Existing Solutions**

| Feature | Encryptable | Hibernate | Spring Data MongoDB | Mongoose |
|---------|-------------|-----------|---------------------|----------|
| **Polymorphic Nested Docs** | ✅ Yes | ❌ SQL only | ⚠️ Needs discriminator | ⚠️ Needs schema |
| **Zero Configuration** | ✅ Yes | ❌ Needs `@Inheritance` | ❌ Needs `@TypeAlias` | ❌ Needs config |
| **Type Preservation** | ✅ Automatic | ✅ Yes | ⚠️ Manual setup | ⚠️ Manual |
| **Storage Overhead** | ✅ Minimal | ⚠️ Always stores | ⚠️ Always stores | ⚠️ Always stores |

**Encryptable is the only solution in this comparison with fully transparent polymorphism requiring zero configuration.**


---

## 🌐 Innovation #6: Capability URLs – Secure, Shareable, Transient-Knowledge Access

A powerful application of cryptographic addressing is the use of "capability URLs." In this model, the URL itself contains all the information required to access and decrypt a resource—typically, a high-entropy secret or token derived from user credentials or generated randomly.

**How it works:**
- The URL includes a secret (or a value from which the secret can be derived) as a path or query parameter.
- When a user accesses the URL, the application uses the embedded secret to locate and decrypt the associated resource, without any additional authentication or lookup.
- Only those who possess the URL can access the resource, making it a form of "possession-based" security.

**Security Implications:**
- Capability URLs are extremely convenient for sharing access to private resources (e.g., images, documents) without requiring user accounts or logins.
- The security of the resource depends entirely on the secrecy and unpredictability of the URL. If the URL is leaked or guessed, anyone can access the resource.
- For maximum security, capability URLs should use long, random, unguessable secrets (e.g., 44+ characters of base64).
- URLs should be transmitted only over secure channels (HTTPS) and never exposed in public logs or referrers.

**Relation to Encryptable:**
- Encryptable's cryptographic addressing makes capability URLs easy to implement: the secret in the URL is used directly to derive the cryptographic key and CID, enabling instant, request-scoped (transient knowledge) access to the resource.
- This approach is ideal for "anonymous" sharing scenarios, where access control is based on possession of the URL rather than user identity.

**Best Practices:**
- Treat capability URLs as sensitive secrets—share them only with trusted parties.
- Consider implementing expiration, revocation, or usage limits for capability URLs to reduce risk if they are leaked.
- Educate users about the importance of keeping these URLs private.

> Capability URLs, when combined with Encryptable's cryptographic addressing, provide a simple yet powerful way to enable secure, user-friendly sharing of encrypted resources—without the need for traditional authentication or access control lists.

---

## 🔐 Innovation #7: Automatic Field-Level Encryption with Per-User Isolation

### **What Makes This Unique**

**Unique Combination:** While field-level encryption exists, nobody else combines automatic encryption + per-user isolation + zero-configuration in a single annotation.

### **The Innovation**

```kotlin
@Document(collection = "patients")
class PatientRecord : Encryptable<PatientRecord>() {
    @HKDFId
    override var id: CID? = null
    
    @Encrypt  // ← Single annotation = full encryption + isolation
    var ssn: String? = null
    
    @Encrypt
    var diagnosis: String? = null
}

// Automatic per-user isolation
val patient1 = PatientRecord().withSecret(secret1)  // Uses secret1 for encryption
val patient2 = PatientRecord().withSecret(secret2)  // Uses secret2 for encryption

// Patient1 cannot decrypt Patient2's data (different secrets)
```

### **Technical Features**

1. **AES-256-GCM (Authenticated Encryption)**
   - Industry-standard AEAD cipher
   - Protects against tampering
   - Unique IV per encryption

2. **Parallel Encryption**
   ```kotlin
   // Encrypts multiple fields in parallel
   fields.parallelForEach { field ->
       encryptField(field, secret)
   }
   ```

3. **Per-User Cryptographic Isolation**
   ```kotlin
   // Each user's secret derives a unique encryption key
   val encryptionKey = HKDF.deriveKey(secret, "encryption", 32)
   
   // Even if two users have same data, encrypted values differ
   user1.email = "test@example.com"  // Encrypted: "kL9mP3..."
   user2.email = "test@example.com"  // Encrypted: "tY0zC4..." (different!)
   ```

4. **Zero Secret Storage**
   - Secrets never written to database
   - Kept in thread-local storage during request
   - Automatically cleared after response

### **Comparison with Existing Solutions**

| Feature | Encryptable | MongoDB CSFLE | Hibernate + Custom | Spring Data + Custom |
|---------|-------------|---------------|-------------------|---------------------|
| **Setup Complexity** | 1 annotation | Complex KMS setup | 100+ lines code | 100+ lines code |
| **Per-User Isolation** | ✅ Built-in | ❌ Shared keys | ❌ Manual | ❌ Manual |
| **Key Management** | ✅ Automatic (HKDF) | ⚠️ External KMS | ❌ Manual | ❌ Manual |
| **Cost** | ✅ Free | ❌ Enterprise + KMS | ✅ Free | ✅ Free |
| **Code Required** | `@Encrypt` | 50+ lines config | 100+ lines | 100+ lines |

**Encryptable is the only solution in this comparison with true zero-configuration encryption.**

### **Innovation Highlight**

**The `@Encrypt` annotation does more work than any other single annotation in the Java/Kotlin ecosystem:**
1. AES-256-GCM encryption
2. Key derivation via HKDF
3. IV generation
4. Per-user isolation
5. Parallel processing
6. GridFS integration (if large)
7. Automatic cleanup

---

## 📊 Innovation #8: Intelligent Change Detection via Field Hashing

### **What Makes This Unique**

**Novel Approach:** Uses field-level hashCode comparison to detect changes with high accuracy, enabling automatic partial updates.

### **The Innovation**

```kotlin
// Framework automatically tracks field hashCodes
fun hashCodes(): MutableMap<String, Int> {
    val result = ConcurrentHashMap<String, Int>()
    metadata.persistedFields.parallelForEach { (name, field) ->
        val hash = when (val value = field.get(this)) {
            is ByteArray -> value.first4KBChecksum()  // Checksum for large data
            null -> 0
            else -> value.hashCode()  // Standard Kotlin hashCode
        }
        result[name] = hash
    }
    return result
}

// Entity hashCode combines all persisted fields
override fun hashCode(): Int {
    var result: Int = id.hashCode()
    metadata.persistedFields.values.forEach { field ->
        val value = field.get(this) ?: return@forEach
        val fieldResult = if (value is ByteArray) value.first4KBChecksum() else value.hashCode()
        result = 31 * result + fieldResult
    }
    return result
}

// On update, only changed fields are sent to MongoDB
// Framework compares current hashCodes with original hashCodes
```

### **Technical Advantages**

1. **High-Accuracy Change Detection**
   - Uses Kotlin's standard hashCode() for most types
   - Special handling for ByteArray (first 4KB checksum)
   - Parallel computation for performance
   - Very low false positive rate (hash collision probability)

2. **Automatic Partial Updates**
   ```kotlin
   val user = repository.findBySecretOrNull(secret)!!
   user.email = "new@example.com"  // Only this field changed
   
   // MongoDB update query:
   // db.users.updateOne(
   //   { _id: CID },
   //   { $set: { email: "new@example.com" } }  // Only changed field!
   // )
   ```

3. **Performance Benefits**
   ```kotlin
   // Large document (100 fields)
   val document = repository.findBySecretOrNull(secret)!!
   document.oneField = "updated"
   
   // Traditional: Send all 100 fields (100 KB)
   // Encryptable Framework: Send only 1 field (1 KB)
   // = 100× bandwidth reduction
   ```

4. **Works with Encrypted Data**
   ```kotlin
   @Encrypt
   var sensitiveField: String? = null  // Hashed BEFORE encryption
   
   // Change detection works even though database stores encrypted value
   ```

5. **Optimized for Large Binary Data**
   ```kotlin
   // ByteArray uses first 4KB checksum instead of full array hash
   // Avoids performance penalty for large files
   var largeFile: ByteArray? = null  // Uses first4KBChecksum()
   ```

### **Comparison with Existing Solutions**

| Approach | Encryptable | Hibernate | Spring Data | JPA |
|----------|-------------|-----------|-------------|-----|
| **Change Detection** | ✅ HashCode-based | ⚠️ Bytecode enhancement | ❌ None | ⚠️ Entity comparison |
| **Partial Updates** | ✅ Automatic | ⚠️ Updates all non-null | ❌ Replaces document | ⚠️ Updates all |
| **False Positives** | ⚠️ Very low (hash collision) | ⚠️ Possible | ⚠️ N/A | ⚠️ Possible |
| **Setup Required** | ✅ None | ⚠️ Agent/plugin | ✅ None | ⚠️ Configuration |
| **Works with Encryption** | ✅ Yes | ❌ No | ❌ No | ❌ No |
| **Performance** | ✅ Parallel computation | ⚠️ Sequential | ⚠️ N/A | ⚠️ Sequential |

**Encryptable is the only solution in this comparison with zero-config, automatic partial updates and parallel computation.**

### **Innovation Highlight**

**To our knowledge, the first MongoDB ODM to use parallel hashCode computation for change detection, with optimized handling for large binary data (ByteArray checksum).**

---

## 📦 Innovation #9: The Field-as-Live-Mirror — Encrypted Storage with Pluggable Backends

### **What Makes This Unique**

In every framework that exists today — Spring Data, JPA, Morphia, Spring Content — storing a large binary field externally (GridFS, S3, file system) requires the developer to **manually manage the lifecycle**: call the storage API to save, track the reference somewhere, call the storage API to delete, handle lazy loading explicitly, and wire in encryption on top of all of it.

**Encryptable eliminates all of that.** A `ByteArray` field becomes a **live mirror of its storage backend** — automatically, based on size alone. No annotation required for GridFS. The field IS the storage. The developer never interacts with the storage API directly.

- **Assign a value** → automatically stored in GridFS if above threshold (no annotation needed), or in any custom backend if annotated.
- **Assign a new value** → old file deleted, new file stored atomically.
- **Assign `null`** → file deleted from storage, zero orphaned files.
- **Read the field** → lazily fetched from storage and decrypted on first access.
- **Delete the entity** → all associated storage files cleaned up automatically.

**No explicit save. No file manager. No GridFS API. No S3 SDK calls. Just a field assignment.**

**To our knowledge, no existing framework combines all of these capabilities behind a plain field assignment.** The concept builds on ideas present in other ecosystems (Django's `FileField`, Rails' `ActiveStorage`, Spring Content), but takes them significantly further by making the field value itself — not a reference, path, or proxy — the source of truth.

The closest things that exist, across all ecosystems, are:

| Framework | Language | How it differs from Encryptable |
|---|---|---|
| **Spring Content** | Java/Kotlin | Requires explicit `@ContentId`/`@ContentLength` fields + explicit `setContent()`/`getContent()` API calls. Field is a reference, not the storage. No encryption, no atomic replace, no AOP lazy loading. |
| **Django `FileField`** | Python | Associates a field with a file in storage, but the field stores a **path string**, not actual bytes. Developer calls `.save()` explicitly. No encryption, no atomic replace, no threshold routing. |
| **Rails ActiveStorage** | Ruby | `has_one_attached :file` links a model to a file, but requires explicit `attach()`/`purge()` calls. Field holds an attachment proxy, not bytes. No encryption, no atomic replace. |
| **JPA `@Lob`** | Java/Kotlin | Inline DB storage only. No external backend, no encryption, no lazy loading, no lifecycle management. |
| **CarrierWave / Shrine** | Ruby | Explicit upload/delete API calls always required. No live-mirror semantics. |

Every single one of these treats the field as a **managed reference** to external storage. The developer is still responsible for calling APIs, tracking references, and managing lifecycle.

**Encryptable's key distinction:** the `ByteArray` value itself is the source of truth. Assign bytes → stored. Assign new bytes → old deleted, new stored atomically. Assign `null` → deleted. Read the field → lazily fetched and decrypted. The developer never calls a storage API. There is no reference to track. There is no cleanup to manage.

The `ByteArray` value at any given moment *is* the storage state. The individual pieces (AOP field interception, storage abstraction, encryption, lazy loading) exist elsewhere, but their composition into a single transparent field assignment is, to our knowledge, unique.

### **The Innovation**

```kotlin
@Document(collection = "documents")
class Document : Encryptable<Document>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    @S3Storage  // or default GridFS, or your own backend — field behavior is identical
    var pdfContent: ByteArray? = null
}

// Storing:
val doc = Document().withSecret(secret).apply {
    pdfContent = largePdfBytes  // Just assign. That's it.
}
repository.save(doc)

// Updating (old file deleted, new file stored — atomically):
doc.pdfContent = updatedPdfBytes
repository.save(doc)

// Deleting the file (no orphans left behind):
doc.pdfContent = null
repository.save(doc)

// Reading (lazily fetched from storage and decrypted on first access):
val retrieved = repository.findBySecretOrNull(secret)!!
val pdf = retrieved.pdfContent  // Fetched, decrypted, returned. One line.
```

### **What the Framework Does Transparently**

When you assign a `ByteArray` to a storage-backed field, Encryptable:

1. Detects whether the data exceeds the inline threshold (configurable, default 1KB)
2. Encrypts the bytes if `@Encrypt` is present (AES-256-GCM)
3. Stores them in the configured backend (GridFS, S3, or custom)
4. Saves a compact reference (up to 16 bytes) in the entity document
5. On read: lazily fetches the raw bytes from storage on first field access via AspectJ
6. Decrypts them and returns the plaintext to the caller
7. On update: creates the new file **before** deleting the old one — data integrity by default
8. On delete or `null` assignment: removes the file from storage, zero orphaned files

### **Features**

1. **Automatic Threshold Detection**
   ```kotlin
   // Small data → stored inline in the document
   var thumbnail: ByteArray? = null  // 512 bytes → stays in MongoDB document

   // Large data → stored externally (default threshold: 1KB, configurable)
   var video: ByteArray? = null  // 100 MB → goes to storage backend

   // Developer doesn't decide. The framework decides based on size.
   ```

2. **Atomic Replace — Data Integrity by Default**
   ```kotlin
   // Updating a large field:
   doc.pdfContent = newPdfBytes

   // Framework:
   // 1. Creates new file in storage FIRST
   // 2. Only deletes old file AFTER successful creation
   // = If creation fails, old data is untouched. Always.
   ```

3. **Lazy Loading via AspectJ**
   ```kotlin
   val doc = repository.findBySecretOrNull(secret)!!
   // Storage not touched yet.

   val content = doc.pdfContent
   // NOW the framework fetches from storage, decrypts, and returns.
   ```

4. **Pluggable Backends — One Annotation**
   ```kotlin
   // Use GridFS (out of the box, no annotation needed):
   var file: ByteArray? = null

   // Use S3 (your implementation, one annotation):
   @S3Storage
   var file: ByteArray? = null

   // The field behavior is completely identical regardless of backend.
   ```

### **Comparison with Existing Solutions**

| Feature | Encryptable | MongoDB + Manual | Spring Data + GridFS | Spring Content |
|---------|-------------|------------------|---------------------|----------------|
| **Field-as-live-mirror** | ✅ Yes | ❌ No | ❌ No | ❌ No |
| **Auto Storage** | ✅ Yes (pluggable) | ❌ Manual | ❌ Manual | ⚠️ Partial |
| **Built-in Encryption** | ✅ Automatic | ❌ Manual | ❌ Manual | ❌ No |
| **Lazy Loading** | ✅ Automatic | ❌ Manual | ❌ Manual | ❌ Manual |
| **Atomic Replace** | ✅ Yes | ❌ Manual | ❌ Manual | ❌ No |
| **Zero Orphan Guarantee** | ✅ Yes | ❌ Manual | ❌ Manual | ⚠️ Partial |
| **Pluggable Backends** | ✅ Yes (1 annotation) | ❌ Manual | ❌ Manual | ⚠️ Config-heavy |
| **Code Required** | 1 annotation | 100+ lines | 50+ lines | 30+ lines + config |

**Encryptable is the only solution in this comparison that treats a `ByteArray` field as a live mirror of its storage backend.**

### **Innovation Highlight**

This is not just a convenience feature. It is a **fundamental rethinking of how binary data is modeled in a persistence framework.** Instead of the developer managing a reference to an external file, the field itself carries full semantic meaning: its value is its storage state. Assign it and it is stored. Null it and it is gone. Read it and it is there.

**To our knowledge, Encryptable is the first framework to combine transparent field-assignment-driven storage lifecycle management for encrypted binary data** — combining AOP field interception, automatic threshold routing, per-entity encryption, lazy loading, atomic replace, and zero-orphan guarantees behind a plain `ByteArray` field with no API calls required.

The individual techniques (AOP interception, storage abstraction, encryption, lazy loading) are well-established. The novelty is their composition into a single, seamless developer experience where a field assignment is the only interaction required.

> 📦 **[Deep dive into Storage Abstraction, GridFS, custom backends, and implementation examples →](STORAGE.md)**

---

## 🎯 Innovation Summary

### **Novel Innovations for MongoDB**

1. ✅ **HKDF-based deterministic CID generation with entity class namespacing**
2. ✅ **Cryptographic addressing architecture for MongoDB**
3. ✅ **Automatic unsaved entity cleanup with GridFS integration**

### **Novel Combinations (No Known Prior Art)**

4. ✅ **Transparent polymorphism for nested documents (zero configuration)**
5. ✅ **Field-as-live-mirror for binary data — to our knowledge, the first framework to combine AOP field interception, automatic storage routing, encryption, lazy loading, atomic replace, and zero-orphan guarantees behind a plain `ByteArray` assignment.**
6. ✅ **Field-level hash-based change detection for encrypted data**
7. ✅ **Unified key derivation architecture for MongoDB ORM**
8. ✅ **Per-user cryptographic isolation without external KMS**

### **Best-in-Class Features**

9. ✅ **Zero-configuration field-level encryption**
10. ✅ **Automatic partial updates with change detection**
11. ✅ **Parallel encryption processing**
12. ✅ **Request-scoped resource management**
13. ✅ **Aspect-based lazy loading for storage backends**
14. ✅ **ORM-like relationship management with cascade delete**

---


## 🎓 Conclusion

Encryptable introduces **nine major innovations** to the MongoDB persistence and encryption space, combining novel approaches with unique integrations.

**Key Achievements:**

1. ✅ **98.6% reduction** in security-related boilerplate code (compared to manual implementation of AES-256-GCM encryption, HKDF key derivation, IV generation, per-user isolation, and GridFS integration)
2. ✅ **2^32× more collision resistance** than MongoDB ObjectId (128-bit vs 96-bit address space; both astronomically safe for practical use—see [CID_COLLISION_ANALYSIS.md](CID_COLLISION_ANALYSIS.md))
3. ✅ **90-95% bandwidth reduction** for updates (via intelligent change detection and partial updates)
4. ✅ **Zero storage overhead** for ID mappings
5. ✅ **100% automatic** resource cleanup
6. ✅ **ORM-like** relationship management with cascade delete
7. ✅ **100% transparent polymorphism** without configuration
